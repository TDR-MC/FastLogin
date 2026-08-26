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
package com.github.games647.fastlogin.bukkit.listener;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.event.BukkitFastLoginPremiumClaimEvent;
import com.github.games647.fastlogin.core.message.NamespaceKey;
import com.github.games647.fastlogin.core.premium.PremiumClaimProtocol;
import com.github.games647.fastlogin.core.premium.PremiumClaimVerifier;
import com.github.games647.fastlogin.core.premium.PremiumClaimVerifier.VerificationResult;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConnection;
import net.md_5.bungee.config.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.time.Duration;
import java.util.UUID;

/**
 * Validates early premium claims while Paper is still configuring a connection.
 */
public final class PaperPremiumClaimListener implements PluginMessageListener {

    public static final String CHANNEL = NamespaceKey.getCombined("fastlogin", PremiumClaimProtocol.CHANNEL);

    private static final int DEFAULT_MAX_TTL_SECONDS = 30;
    private static final int DEFAULT_FUTURE_SKEW_SECONDS = 5;
    private static final String ENV_NAME_PATTERN = "[A-Za-z_][A-Za-z0-9_]{0,127}";

    private final FastLoginBukkit plugin;
    private final PremiumClaimVerifier verifier;

    private PaperPremiumClaimListener(FastLoginBukkit plugin, PremiumClaimVerifier verifier) {
        this.plugin = plugin;
        this.verifier = verifier;
    }

    public static PaperPremiumClaimListener load(FastLoginBukkit plugin) {
        Configuration config = plugin.getCore().getConfig().getSection("earlyPremiumClaim");
        if (!config.getBoolean("enabled", false)) {
            return null;
        }

        String audience = config.getString("audience", "").trim();
        if (audience.isEmpty() || audience.length() > 64) {
            plugin.getLog().error("earlyPremiumClaim is disabled: backend audience must contain "
                    + "its Velocity server name");
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

        int maxTtl = config.getInt("maxTtlSeconds", DEFAULT_MAX_TTL_SECONDS);
        int futureSkew = config.getInt("maxFutureSkewSeconds", DEFAULT_FUTURE_SKEW_SECONDS);
        if (maxTtl < 1 || maxTtl > DEFAULT_MAX_TTL_SECONDS || futureSkew < 0 || futureSkew > 30) {
            plugin.getLog().error("earlyPremiumClaim is disabled: invalid TTL or clock-skew bounds");
            return null;
        }

        PremiumClaimVerifier verifier = new PremiumClaimVerifier(
                secret,
                audience,
                Duration.ofSeconds(maxTtl).toMillis(),
                Duration.ofSeconds(futureSkew).toMillis(),
                plugin.getBungeeManager()::isProxyAllowed
        );
        plugin.getLog().info("Enabled configuration-phase premium claim verification for audience '{}'", audience);
        return new PaperPremiumClaimListener(plugin, verifier);
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        // Play-phase force login continues through BungeeListener.
    }

    @Override
    public void onPluginMessageReceived(String channel, PlayerConnection connection, byte[] message) {
        if (!CHANNEL.equals(channel) || !(connection instanceof PlayerConfigurationConnection)) {
            return;
        }

        PlayerConfigurationConnection configurationConnection = (PlayerConfigurationConnection) connection;
        String profileName = configurationConnection.getProfile().getName();
        UUID profileUuid = configurationConnection.getProfile().getUniqueId();
        InetAddress address = configurationConnection.getClientAddress().getAddress();
        if (profileName == null || profileUuid == null || address == null) {
            plugin.getLog().warn("Rejected configuration-phase premium claim: incomplete connection binding");
            return;
        }
        VerificationResult result = verifier.verify(
                message,
                profileName,
                profileUuid,
                address,
                System.currentTimeMillis()
        );
        if (!result.isValid()) {
            plugin.getLog().warn("Rejected configuration-phase premium claim: {}", result.getStatus());
            return;
        }

        plugin.getServer().getPluginManager().callEvent(
                new BukkitFastLoginPremiumClaimEvent(configurationConnection, result.getClaim())
        );
    }
}
