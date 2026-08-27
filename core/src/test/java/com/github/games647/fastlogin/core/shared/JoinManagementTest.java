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
package com.github.games647.fastlogin.core.shared;

import com.github.games647.craftapi.model.Profile;
import com.github.games647.craftapi.resolver.MojangResolver;
import com.github.games647.craftapi.resolver.RateLimitException;
import com.github.games647.fastlogin.core.shared.event.FastLoginPreLoginEvent;
import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import net.md_5.bungee.config.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JoinManagementTest {

    private static final String USERNAME = "Alice";
    private static final String UNAVAILABLE_MESSAGE = "resolver unavailable";

    private FastLoginCore<Object, Object, PlatformPlugin<Object>> core;
    private MojangResolver resolver;
    private TestJoinManagement management;
    private TestLoginSource source;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        core = mock(FastLoginCore.class);
        resolver = mock(MojangResolver.class);
        SQLStorage storage = mock(SQLStorage.class);
        PlatformPlugin<Object> plugin = mock(PlatformPlugin.class);

        Configuration config = new Configuration();
        config.set("autoRegister", true);
        config.set("nameChangeCheck", false);
        config.set("switchMode", false);

        StoredProfile profile = new StoredProfile(UUID.randomUUID(), USERNAME, false, FloodgateState.FALSE, null);
        when(core.getConfig()).thenReturn(config);
        when(core.getStorage()).thenReturn(storage);
        when(core.getResolver()).thenReturn(resolver);
        when(core.getPlugin()).thenReturn(plugin);
        when(core.getMessage("premium-name-check-unavailable")).thenReturn(UNAVAILABLE_MESSAGE);
        when(plugin.getLog()).thenReturn(mock(Logger.class));
        when(storage.loadProfile(USERNAME)).thenReturn(profile);

        management = new TestJoinManagement(core);
        source = new TestLoginSource();
    }

    @Test
    void shouldDenyWhenPremiumNameLookupIsRateLimited() throws Exception {
        when(resolver.findProfile(USERNAME)).thenThrow(new RateLimitException());

        management.onLogin(USERNAME, source);

        assertEquals(UNAVAILABLE_MESSAGE, source.kickMessage);
        assertEquals(0, management.premiumLogins);
        assertEquals(0, management.crackedLogins);
    }

    @Test
    void shouldDenyWhenPremiumNameLookupFails() throws Exception {
        when(resolver.findProfile(USERNAME)).thenThrow(new IOException("network unavailable"));

        management.onLogin(USERNAME, source);

        assertEquals(UNAVAILABLE_MESSAGE, source.kickMessage);
        assertEquals(0, management.premiumLogins);
        assertEquals(0, management.crackedLogins);
    }

    @Test
    void shouldRequestPremiumLoginForResolvedPaidName() throws Exception {
        when(resolver.findProfile(USERNAME)).thenReturn(Optional.of(new Profile(UUID.randomUUID(), USERNAME)));

        management.onLogin(USERNAME, source);

        assertNull(source.kickMessage);
        assertEquals(1, management.premiumLogins);
        assertEquals(0, management.crackedLogins);
    }

    @Test
    void shouldStartCrackedSessionForAvailableName() throws Exception {
        when(resolver.findProfile(USERNAME)).thenReturn(Optional.empty());

        management.onLogin(USERNAME, source);

        assertNull(source.kickMessage);
        assertEquals(0, management.premiumLogins);
        assertEquals(1, management.crackedLogins);
    }

    @Test
    void shouldPreserveNameChangeOnlyBehaviorWhenLookupFails() throws Exception {
        Configuration config = core.getConfig();
        config.set("autoRegister", false);
        config.set("nameChangeCheck", true);
        when(resolver.findProfile(USERNAME)).thenThrow(new IOException("network unavailable"));

        management.onLogin(USERNAME, source);

        assertNull(source.kickMessage);
        assertEquals(0, management.premiumLogins);
        assertEquals(0, management.crackedLogins);
        verify(core, never()).getMessage("premium-name-check-unavailable");
    }

    private static final class TestJoinManagement
            extends JoinManagement<Object, Object, TestLoginSource> {

        private int premiumLogins;
        private int crackedLogins;

        private TestJoinManagement(FastLoginCore<Object, Object, ?> core) {
            super(core, null, null);
        }

        @Override
        public FastLoginPreLoginEvent callFastLoginPreLoginEvent(String username, TestLoginSource source,
                                                                 StoredProfile profile) {
            return null;
        }

        @Override
        public void requestPremiumLogin(TestLoginSource source, StoredProfile profile, String username,
                                        boolean registered) {
            premiumLogins++;
        }

        @Override
        public void startCrackedSession(TestLoginSource source, StoredProfile profile, String username) {
            crackedLogins++;
        }
    }

    private static final class TestLoginSource implements LoginSource {

        private String kickMessage;

        @Override
        public void enableOnlinemode() {
        }

        @Override
        public void kick(String message) {
            kickMessage = message;
        }

        @Override
        public InetSocketAddress getAddress() {
            return new InetSocketAddress("192.0.2.10", 25565);
        }
    }
}
