/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 games647 and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.games647.fastlogin.velocity;

import com.github.games647.fastlogin.core.premium.PremiumClaim;
import com.github.games647.fastlogin.core.premium.PremiumClaimProtocol;
import com.velocitypowered.api.event.player.configuration.PlayerConfigurationEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.md_5.bungee.config.Configuration;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

/**
 * Sends verified premium identity before a Paper backend leaves configuration state.
 */
public final class PremiumClaimSender {

    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.create(
            "fastlogin", PremiumClaimProtocol.CHANNEL);

    private static final int DEFAULT_TTL_SECONDS = 10;
    private static final int MAX_TTL_SECONDS = 30;
    private static final String ENV_NAME_PATTERN = "[A-Za-z_][A-Za-z0-9_]{0,127}";

    private final FastLoginVelocity plugin;
    private final byte[] secret;
    private final long ttlMillis;

    private PremiumClaimSender(FastLoginVelocity plugin, byte[] secret, long ttlMillis) {
        this.plugin = plugin;
        this.secret = secret;
        this.ttlMillis = ttlMillis;
    }

    public static PremiumClaimSender load(FastLoginVelocity plugin) {
        Configuration config = plugin.getCore().getConfig().getSection("earlyPremiumClaim");
        if (!config.getBoolean("enabled", false)) {
            return null;
        }

        String variableName = config.getString("sharedSecretEnvironmentVariable", "").trim();
        if (!variableName.matches(ENV_NAME_PATTERN)) {
            plugin.getLog().error("earlyPremiumClaim is disabled: invalid shared-secret environment variable name");
            return null;
        }
        String encodedSecret = System.getenv(variableName);
        if (encodedSecret == null || encodedSecret.isEmpty()) {
            plugin.getLog().error("earlyPremiumClaim is disabled: configured shared-secret "
                    + "environment variable is empty");
            return null;
        }

        byte[] secret;
        try {
            secret = PremiumClaimProtocol.parseHexSecret(encodedSecret);
        } catch (IllegalArgumentException ex) {
            plugin.getLog().error("earlyPremiumClaim is disabled: shared secret must be at least 32 bytes of hex");
            return null;
        }

        int configuredTtl = config.getInt("ttlSeconds", DEFAULT_TTL_SECONDS);
        if (configuredTtl < 1 || configuredTtl > MAX_TTL_SECONDS) {
            plugin.getLog().error("earlyPremiumClaim is disabled: ttlSeconds must be between 1 and {}",
                    MAX_TTL_SECONDS);
            return null;
        }

        plugin.getLog().info("Enabled configuration-phase premium claims with a {} second TTL", configuredTtl);
        return new PremiumClaimSender(plugin, secret, Duration.ofSeconds(configuredTtl).toMillis());
    }

    public void send(PlayerConfigurationEvent event) {
        if (event.server() == null) {
            plugin.getLog().warn("Cannot send configuration-phase premium claim without an in-flight backend");
            return;
        }
        Player player = event.player();
        VelocityLoginSession session = plugin.getSession().get(player.getRemoteAddress());
        if (!player.isOnlineMode() || session == null || session.getUuid() == null) {
            return;
        }

        InetAddress address = player.getRemoteAddress().getAddress();
        if (address == null) {
            plugin.getLog().warn("Cannot bind early premium claim for {} to an unresolved client address",
                    player.getUsername());
            return;
        }

        long issuedAt = System.currentTimeMillis();
        UUID mojangUuid = session.getUuid();
        PremiumClaim claim = PremiumClaim.builder()
                .issuerProxyId(plugin.getProxyId())
                .audience(event.server().getServerInfo().getName())
                .username(player.getUsername().toLowerCase(Locale.ROOT))
                .effectiveUuid(player.getUniqueId())
                .mojangUuid(mojangUuid)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt + ttlMillis)
                .nonce(UUID.randomUUID())
                .clientAddressHash(PremiumClaimProtocol.hashAddress(address))
                .build();
        boolean sent = event.server().sendPluginMessage(CHANNEL, PremiumClaimProtocol.encode(claim, secret));
        if (sent) {
            plugin.getLog().info("Sent configuration-phase premium claim for {} to {}",
                    player.getUsername(), event.server().getServerInfo().getName());
        } else {
            plugin.getLog().warn("Failed to send configuration-phase premium claim for {} to {}; "
                    + "the backend must not bypass authentication", player.getUsername(),
                    event.server().getServerInfo().getName());
        }
    }
}
