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
package com.github.games647.fastlogin.velocity.listener;

import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.FloodgateState;
import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.github.games647.fastlogin.velocity.VelocityLoginSession;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginMessageListenerTest {

    @Test
    void repeatedConcurrentSuccessAcknowledgementsPersistExactlyOnce() throws Exception {
        Fixture fixture = fixture(true, true);
        int repetitions = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(repetitions);
        try {
            for (int i = 0; i < repetitions; i++) {
                executor.submit(() -> {
                    start.await();
                    fixture.listener.onSuccessMessage(fixture.player);
                    return null;
                });
            }
            start.countDown();
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        verify(fixture.storage).save(fixture.profile);
        assertTrue(fixture.profile.isOnlinemodePreferred());
        assertTrue(fixture.session.isAlreadySaved());
    }

    @Test
    void missingAddressKeyedSessionIsObservableAndNullSafe() throws Exception {
        Fixture fixture = fixture(true, false);

        fixture.listener.onSuccessMessage(fixture.player);

        verify(fixture.storage, never()).save(fixture.profile);
        verify(fixture.logger).warn(anyString(), eq("PremiumName"));
    }

    @Test
    void nonPremiumPlayerCannotPersistFromForgedSuccessAcknowledgement() throws Exception {
        Fixture fixture = fixture(false, true);

        fixture.listener.onSuccessMessage(fixture.player);

        verify(fixture.storage, never()).save(fixture.profile);
    }

    private static Fixture fixture(boolean onlineMode, boolean sessionPresent) throws Exception {
        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        Logger logger = mock(Logger.class);
        @SuppressWarnings("unchecked")
        FastLoginCore<Player, CommandSource, FastLoginVelocity> core = mock(FastLoginCore.class);
        SQLStorage storage = mock(SQLStorage.class);
        @SuppressWarnings("unchecked")
        ConcurrentMap<InetSocketAddress, VelocityLoginSession> sessions = mock(ConcurrentMap.class);
        Player player = mock(Player.class);
        InetSocketAddress address = new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), 25565);
        StoredProfile profile = new StoredProfile(
                UUID.fromString("00000000-0000-4000-8000-000000000001"),
                "PremiumName",
                false,
                FloodgateState.FALSE,
                ""
        );
        VelocityLoginSession session = new VelocityLoginSession("PremiumName", false, profile);

        when(plugin.getName()).thenReturn("fastlogin");
        when(plugin.getLog()).thenReturn(logger);
        when(plugin.getCore()).thenReturn(core);
        when(plugin.getSession()).thenReturn(sessions);
        when(core.getStorage()).thenReturn(storage);
        when(player.getUsername()).thenReturn("PremiumName");
        when(player.getRemoteAddress()).thenReturn(address);
        when(player.isOnlineMode()).thenReturn(onlineMode);
        when(sessions.get(address)).thenReturn(sessionPresent ? session : null);

        return new Fixture(new PluginMessageListener(plugin), player, session, profile, storage, logger);
    }

    private record Fixture(PluginMessageListener listener, Player player, VelocityLoginSession session,
                           StoredProfile profile, SQLStorage storage, Logger logger) {
    }
}
