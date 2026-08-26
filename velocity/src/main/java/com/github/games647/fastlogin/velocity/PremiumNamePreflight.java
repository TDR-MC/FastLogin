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

import com.github.games647.fastlogin.core.premium.PremiumClaimProtocol;
import net.md_5.bungee.config.Configuration;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Gives an unknown Mojang-owned name one explanatory disconnect before normal online-mode negotiation.
 *
 * <p>The permit is not an authentication credential. It is bound to the normalized name and client IP, consumed
 * once, expires quickly, and only allows FastLogin to perform its existing online-mode authentication on retry.</p>
 */
public final class PremiumNamePreflight {

    static final int MAX_TTL_SECONDS = 60;
    static final int MAX_CACHE_ENTRIES = 10_000;
    static final String DEFAULT_MESSAGE = "&cThis name is protected by a paid Minecraft account. If you own it, "
            + "reconnect now using the official client. Otherwise choose another name.";

    private final Clock clock;
    private final long ttlMillis;
    private final int maximumSize;
    private final String message;
    private final Map<PermitKey, Long> permits = new LinkedHashMap<>();

    PremiumNamePreflight(Clock clock, Duration ttl, int maximumSize, String message) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttlMillis = Objects.requireNonNull(ttl, "ttl").toMillis();
        if (ttlMillis <= 0 || ttlMillis > Duration.ofSeconds(MAX_TTL_SECONDS).toMillis()) {
            throw new IllegalArgumentException("Preflight permit TTL must be between 1 ms and 60 seconds");
        }

        if (maximumSize <= 0 || maximumSize > MAX_CACHE_ENTRIES) {
            throw new IllegalArgumentException("Preflight cache size must be between 1 and " + MAX_CACHE_ENTRIES);
        }

        this.maximumSize = maximumSize;
        this.message = Objects.requireNonNull(message, "message");
    }

    static PremiumNamePreflight load(FastLoginVelocity plugin) {
        Configuration rootConfig = plugin.getCore().getConfig();
        Configuration config = rootConfig.getSection("premiumNamePreflight");
        if (!config.getBoolean("enabled", false)) {
            return null;
        }

        if (!enforceCompatibleCrackedFallback(rootConfig)) {
            plugin.getLog().error("premiumNamePreflight is disabled: secondAttemptCracked=true can release a "
                    + "Mojang-owned name after failed online authentication. FastLogin forced secondAttemptCracked "
                    + "off for this runtime; set it to false in config.yml before enabling preflight");
            return null;
        }

        int ttlSeconds = config.getInt("permitTtlSeconds", 30);
        int maximumSize = config.getInt("maximumPendingPermits", 2_000);
        if (ttlSeconds <= 0 || ttlSeconds > MAX_TTL_SECONDS
                || maximumSize <= 0 || maximumSize > MAX_CACHE_ENTRIES) {
            plugin.getLog().error("premiumNamePreflight is disabled: permitTtlSeconds must be 1..{} and "
                    + "maximumPendingPermits must be 1..{}", MAX_TTL_SECONDS, MAX_CACHE_ENTRIES);
            return null;
        }

        plugin.getLog().info("Enabled premium-name preflight with a {} second single-use permit", ttlSeconds);
        return new PremiumNamePreflight(Clock.systemUTC(), Duration.ofSeconds(ttlSeconds), maximumSize,
                resolveMessage(plugin.getCore().getMessage("premium-name-preflight")));
    }

    static boolean enforceCompatibleCrackedFallback(Configuration rootConfig) {
        if (!rootConfig.getBoolean("secondAttemptCracked", false)) {
            return true;
        }

        rootConfig.set("secondAttemptCracked", false);
        return false;
    }

    static String resolveMessage(String configuredMessage) {
        if (configuredMessage == null || configuredMessage.trim().isEmpty()) {
            return DEFAULT_MESSAGE;
        }

        return configuredMessage;
    }

    public boolean deferFirstAttempt(VelocityLoginSource source, String username) {
        InetAddress address = source.getAddress().getAddress();
        if (address == null || shouldDefer(address, username, true)) {
            source.kick(message);
            return true;
        }

        return false;
    }

    synchronized boolean shouldDefer(InetAddress address, String username, boolean mojangOwnedName) {
        if (!mojangOwnedName) {
            return false;
        }

        long now = clock.millis();
        removeExpired(now);
        PermitKey key = new PermitKey(PremiumClaimProtocol.hashAddress(address), username.toLowerCase(Locale.ROOT));
        Long existingPermit = permits.remove(key);
        if (existingPermit != null && existingPermit > now) {
            return false;
        }

        while (permits.size() >= maximumSize) {
            Iterator<PermitKey> oldest = permits.keySet().iterator();
            oldest.next();
            oldest.remove();
        }

        permits.put(key, now + ttlMillis);
        return true;
    }

    private void removeExpired(long now) {
        permits.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private static final class PermitKey {

        private final byte[] addressHash;
        private final String username;

        private PermitKey(byte[] addressHash, String username) {
            this.addressHash = addressHash.clone();
            this.username = username;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }

            if (!(other instanceof PermitKey)) {
                return false;
            }

            PermitKey that = (PermitKey) other;
            return Arrays.equals(addressHash, that.addressHash) && username.equals(that.username);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(addressHash) + username.hashCode();
        }
    }
}
