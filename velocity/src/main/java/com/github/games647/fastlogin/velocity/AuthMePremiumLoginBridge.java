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

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.player.configuration.PlayerConfigurationEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.md_5.bungee.config.Configuration;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sends AuthMe's signed perform.login message before its blocking configuration-phase dialog gate.
 *
 * <p>This bridge is intentionally narrower than AuthMeVelocity's normal authenticated-session forwarding. It is
 * eligible only after FastLogin completed Mojang online-mode verification and successfully delivered the TDR premium
 * claim for the same connection. The AuthMe shared secret is loaded from an environment variable and never logged.</p>
 */
public final class AuthMePremiumLoginBridge {

    static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.create("authme", "main");
    static final String PERFORM_LOGIN = "perform.login";

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String AUTHME_VELOCITY_PLUGIN_ID = "authmevelocity";
    private static final String ENV_NAME_PATTERN = "[A-Za-z_][A-Za-z0-9_]{0,127}";
    private static final String SECRET_PATTERN = "[0-9a-fA-F]{64}";
    private static final String SERVER_NAME_PATTERN = "[A-Za-z0-9_.-]{1,64}";

    private final FastLoginVelocity plugin;
    private final String sharedSecret;
    private final Set<String> authServers;
    private final Clock clock;

    AuthMePremiumLoginBridge(FastLoginVelocity plugin, String sharedSecret, Set<String> authServers, Clock clock) {
        this.plugin = plugin;
        this.sharedSecret = sharedSecret;
        this.authServers = authServers;
        this.clock = clock;
    }

    static AuthMePremiumLoginBridge load(FastLoginVelocity plugin) {
        Configuration config = plugin.getCore().getConfig().getSection("authMePremiumLoginBridge");
        if (!config.getBoolean("enabled", false)) {
            return null;
        }
        if (!plugin.isPluginInstalled(AUTHME_VELOCITY_PLUGIN_ID)) {
            plugin.getLog().error("authMePremiumLoginBridge is disabled: AuthMeVelocity is not installed");
            return null;
        }

        String variableName = config.getString("sharedSecretEnvironmentVariable", "").trim();
        if (!variableName.matches(ENV_NAME_PATTERN)) {
            plugin.getLog().error("authMePremiumLoginBridge is disabled: invalid shared-secret environment "
                    + "variable name");
            return null;
        }
        String sharedSecret = System.getenv(variableName);
        if (sharedSecret == null || !sharedSecret.matches(SECRET_PATTERN)) {
            plugin.getLog().error("authMePremiumLoginBridge is disabled: shared secret must be exactly 32 bytes "
                    + "encoded as hexadecimal");
            return null;
        }

        Set<String> authServers = config.getStringList("authServers").stream()
                .map(String::trim)
                .filter(server -> server.matches(SERVER_NAME_PATTERN))
                .map(server -> server.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        if (authServers.isEmpty() || authServers.size() != config.getStringList("authServers").size()) {
            plugin.getLog().error("authMePremiumLoginBridge is disabled: authServers must contain only exact, "
                    + "non-empty Velocity server names");
            return null;
        }

        plugin.getLog().info("Enabled configuration-phase AuthMe premium login bridge for {} auth server(s)",
                authServers.size());
        return new AuthMePremiumLoginBridge(plugin, sharedSecret, authServers, Clock.systemUTC());
    }

    boolean sendVerifiedPremium(PlayerConfigurationEvent event, VelocityLoginSession session) {
        ServerConnection server = event.server();
        if (server == null || session == null) {
            return false;
        }

        Player player = event.player();
        String serverName = server.getServerInfo().getName();
        String normalizedName = player.getUsername().toLowerCase(Locale.ROOT);
        if (!authServers.contains(serverName.toLowerCase(Locale.ROOT))
                || !player.isOnlineMode()
                || session.getUuid() == null
                || !normalizedName.equals(session.getUsername().toLowerCase(Locale.ROOT))) {
            return false;
        }

        boolean sent = server.sendPluginMessage(CHANNEL, encode(normalizedName, clock.millis()));
        if (sent) {
            plugin.getLog().info("Sent configuration-phase AuthMe premium login for {} to {}",
                    player.getUsername(), serverName);
        } else {
            plugin.getLog().warn("Failed to send configuration-phase AuthMe premium login for {} to {}; "
                    + "AuthMe must keep its password gate closed", player.getUsername(), serverName);
        }
        return sent;
    }

    byte[] encode(String normalizedName, long timestamp) {
        String hmac = computeHmac(sharedSecret, normalizedName, timestamp);
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF(PERFORM_LOGIN);
        output.writeUTF(normalizedName);
        output.writeLong(timestamp);
        // The empty optional UUID selects AuthMe's signed proxy-session path. FastLogin already proved Mojang
        // ownership and only invokes this bridge after its independently signed premium claim was delivered.
        output.writeUTF("");
        output.writeUTF(hmac);
        return output.toByteArray();
    }

    static String computeHmac(String secret, String normalizedName, long timestamp) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] signed = mac.doFinal((normalizedName + ':' + timestamp + ':')
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signed);
        } catch (NoSuchAlgorithmException | InvalidKeyException error) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", error);
        }
    }
}
