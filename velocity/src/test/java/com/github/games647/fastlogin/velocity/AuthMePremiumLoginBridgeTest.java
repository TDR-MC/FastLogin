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

import com.github.games647.fastlogin.core.shared.FloodgateState;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.player.configuration.PlayerConfigurationEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthMePremiumLoginBridgeTest {

    private static final String SECRET = "ab".repeat(32);
    private static final Instant NOW = Instant.parse("2026-08-26T00:00:00Z");
    private static final UUID PREMIUM_UUID = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Test
    void payloadMatchesAuthMeVelocityPerformLoginContract() {
        AuthMePremiumLoginBridge bridge = bridge(Set.of("lobby"));

        ByteArrayDataInput input = ByteStreams.newDataInput(bridge.encode("premiumname", NOW.toEpochMilli()));

        assertEquals("perform.login", input.readUTF());
        assertEquals("premiumname", input.readUTF());
        assertEquals(NOW.toEpochMilli(), input.readLong());
        assertEquals("", input.readUTF());
        assertEquals("4a12a12ed704d8d2899f72a71bbb12d83fbad17854816ad828db9799855c2624",
                input.readUTF());
    }

    @Test
    void verifiedPremiumCanReachExactAuthServerDuringConfiguration() {
        AuthMePremiumLoginBridge bridge = bridge(Set.of("lobby"));
        Fixture fixture = fixture("lobby", true, "PremiumName", "PremiumName", PREMIUM_UUID);
        when(fixture.server.sendPluginMessage(any(ChannelIdentifier.class), any(byte[].class))).thenReturn(true);

        assertTrue(bridge.sendVerifiedPremium(fixture.event, fixture.session));

        verify(fixture.server).sendPluginMessage(any(ChannelIdentifier.class), any(byte[].class));
    }

    @Test
    void failedAndNonPremiumEligibilityNeverSendPerformLogin() {
        AuthMePremiumLoginBridge bridge = bridge(Set.of("lobby"));
        Fixture offline = fixture("lobby", false, "PremiumName", "PremiumName", PREMIUM_UUID);
        Fixture missingProof = fixture("lobby", true, "PremiumName", "PremiumName", null);
        Fixture mismatchedIdentity = fixture("lobby", true, "PremiumName", "OtherName", PREMIUM_UUID);
        Fixture wrongServer = fixture("survival", true, "PremiumName", "PremiumName", PREMIUM_UUID);

        assertFalse(bridge.sendVerifiedPremium(offline.event, offline.session));
        assertFalse(bridge.sendVerifiedPremium(missingProof.event, missingProof.session));
        assertFalse(bridge.sendVerifiedPremium(mismatchedIdentity.event, mismatchedIdentity.session));
        assertFalse(bridge.sendVerifiedPremium(wrongServer.event, wrongServer.session));
        assertFalse(bridge.sendVerifiedPremium(offline.event, null));

        verify(offline.server, never()).sendPluginMessage(any(ChannelIdentifier.class), any(byte[].class));
        verify(missingProof.server, never()).sendPluginMessage(any(ChannelIdentifier.class), any(byte[].class));
        verify(mismatchedIdentity.server, never())
                .sendPluginMessage(any(ChannelIdentifier.class), any(byte[].class));
        verify(wrongServer.server, never()).sendPluginMessage(any(ChannelIdentifier.class), any(byte[].class));
    }

    private static AuthMePremiumLoginBridge bridge(Set<String> authServers) {
        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        when(plugin.getLog()).thenReturn(mock(Logger.class));
        return new AuthMePremiumLoginBridge(
                plugin,
                SECRET,
                authServers,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static Fixture fixture(String serverName, boolean onlineMode, String playerName,
                                   String verifiedName, UUID premiumUuid) {
        PlayerConfigurationEvent event = mock(PlayerConfigurationEvent.class);
        Player player = mock(Player.class);
        ServerConnection server = mock(ServerConnection.class);
        ServerInfo serverInfo = mock(ServerInfo.class);
        when(event.player()).thenReturn(player);
        when(event.server()).thenReturn(server);
        when(server.getServerInfo()).thenReturn(serverInfo);
        when(serverInfo.getName()).thenReturn(serverName);
        when(player.getUsername()).thenReturn(playerName);
        when(player.isOnlineMode()).thenReturn(onlineMode);

        StoredProfile profile = new StoredProfile(null, playerName, false, FloodgateState.FALSE, "");
        VelocityLoginSession session = new VelocityLoginSession(playerName, false, profile);
        session.setVerifiedUsername(verifiedName);
        session.setUuid(premiumUuid);
        return new Fixture(event, server, session);
    }

    private record Fixture(PlayerConfigurationEvent event, ServerConnection server, VelocityLoginSession session) {
    }
}
