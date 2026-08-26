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

import net.md_5.bungee.config.Configuration;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PremiumNamePreflightTest {

    private static final InetAddress ADDRESS = loopback();

    @Test
    void firstPremiumAttemptIsDeferredAndRetryConsumesPermit() {
        MutableClock clock = new MutableClock();
        PremiumNamePreflight preflight = new PremiumNamePreflight(clock, Duration.ofSeconds(30), 10, "retry");

        assertTrue(preflight.shouldDefer(ADDRESS, "PremiumName", true));
        assertFalse(preflight.shouldDefer(ADDRESS, "premiumname", true));
        assertTrue(preflight.shouldDefer(ADDRESS, "PremiumName", true));
    }

    @Test
    void expiredPermitDoesNotAllowRetry() {
        MutableClock clock = new MutableClock();
        PremiumNamePreflight preflight = new PremiumNamePreflight(clock, Duration.ofSeconds(30), 10, "retry");

        assertTrue(preflight.shouldDefer(ADDRESS, "PremiumName", true));
        clock.advance(Duration.ofSeconds(31));
        assertTrue(preflight.shouldDefer(ADDRESS, "PremiumName", true));
    }

    @Test
    void nonPremiumNameNeverCreatesOrConsumesPermit() {
        MutableClock clock = new MutableClock();
        PremiumNamePreflight preflight = new PremiumNamePreflight(clock, Duration.ofSeconds(30), 10, "retry");

        assertFalse(preflight.shouldDefer(ADDRESS, "OfflineName", false));
        assertTrue(preflight.shouldDefer(ADDRESS, "OfflineName", true));
    }

    @Test
    void permitIsBoundToClientAddress() throws Exception {
        MutableClock clock = new MutableClock();
        PremiumNamePreflight preflight = new PremiumNamePreflight(clock, Duration.ofSeconds(30), 10, "retry");
        InetAddress otherAddress = InetAddress.getByAddress(new byte[]{127, 0, 0, 2});

        assertTrue(preflight.shouldDefer(ADDRESS, "PremiumName", true));
        assertTrue(preflight.shouldDefer(otherAddress, "PremiumName", true));
        assertFalse(preflight.shouldDefer(ADDRESS, "PremiumName", true));
    }

    @Test
    void cacheEvictsOldestPermitAtConfiguredBound() throws Exception {
        MutableClock clock = new MutableClock();
        PremiumNamePreflight preflight = new PremiumNamePreflight(clock, Duration.ofSeconds(30), 1, "retry");
        InetAddress otherAddress = InetAddress.getByAddress(new byte[]{127, 0, 0, 2});

        assertTrue(preflight.shouldDefer(ADDRESS, "FirstName", true));
        assertTrue(preflight.shouldDefer(otherAddress, "SecondName", true));
        assertTrue(preflight.shouldDefer(ADDRESS, "FirstName", true));
    }

    @Test
    void ttlAndCacheHaveHardUpperBounds() {
        MutableClock clock = new MutableClock();

        assertThrows(IllegalArgumentException.class,
                () -> new PremiumNamePreflight(clock, Duration.ofSeconds(61), 10, "retry"));
        assertThrows(IllegalArgumentException.class,
                () -> new PremiumNamePreflight(clock, Duration.ofSeconds(30), 10_001, "retry"));
    }

    @Test
    void missingLocalizedMessageUsesSafeFallback() {
        assertEquals(PremiumNamePreflight.DEFAULT_MESSAGE, PremiumNamePreflight.resolveMessage(null));
        assertEquals(PremiumNamePreflight.DEFAULT_MESSAGE, PremiumNamePreflight.resolveMessage("  "));
        assertEquals("custom", PremiumNamePreflight.resolveMessage("custom"));
    }

    @Test
    void incompatibleSecondAttemptCrackedIsForcedOffAndDisablesPreflight() {
        Configuration rootConfig = new Configuration();
        rootConfig.set("secondAttemptCracked", true);

        assertFalse(PremiumNamePreflight.enforceCompatibleCrackedFallback(rootConfig));
        assertFalse(rootConfig.getBoolean("secondAttemptCracked"));
    }

    @Test
    void protectedBaseFlowRemainsUnchangedForCompatibleConfig() {
        Configuration rootConfig = new Configuration();
        rootConfig.set("secondAttemptCracked", false);

        assertTrue(PremiumNamePreflight.enforceCompatibleCrackedFallback(rootConfig));
        assertFalse(rootConfig.getBoolean("secondAttemptCracked"));
    }

    private static InetAddress loopback() {
        try {
            return InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        } catch (Exception impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant = Instant.parse("2026-08-26T00:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
