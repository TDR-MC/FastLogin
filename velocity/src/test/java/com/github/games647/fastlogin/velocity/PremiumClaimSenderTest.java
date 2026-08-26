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
import com.velocitypowered.api.event.player.configuration.PlayerConfigurationEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PremiumClaimSenderTest {

    @Test
    void authMeBridgeRunsOnlyAfterPremiumClaimWasDelivered() throws Exception {
        Fixture delivered = fixture(true, true);
        Fixture rejected = fixture(true, false);

        delivered.sender.send(delivered.event);
        rejected.sender.send(rejected.event);

        verify(delivered.bridge).sendVerifiedPremium(delivered.event, delivered.session);
        verify(rejected.bridge, never()).sendVerifiedPremium(any(), any());
    }

    @Test
    void nonPremiumConnectionCannotReachEitherBridge() throws Exception {
        Fixture fixture = fixture(false, true);

        fixture.sender.send(fixture.event);

        verify(fixture.server, never()).sendPluginMessage(any(ChannelIdentifier.class), any(byte[].class));
        verify(fixture.bridge, never()).sendVerifiedPremium(any(), any());
    }

    private static Fixture fixture(boolean onlineMode, boolean claimDelivered) throws Exception {
        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        PlayerConfigurationEvent event = mock(PlayerConfigurationEvent.class);
        Player player = mock(Player.class);
        ServerConnection server = mock(ServerConnection.class);
        ServerInfo serverInfo = mock(ServerInfo.class);
        AuthMePremiumLoginBridge bridge = mock(AuthMePremiumLoginBridge.class);
        @SuppressWarnings("unchecked")
        ConcurrentMap<InetSocketAddress, VelocityLoginSession> sessions = mock(ConcurrentMap.class);
        InetSocketAddress address = new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), 25565);
        UUID premiumUuid = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID effectiveUuid = UUID.fromString("00000000-0000-3000-8000-000000000002");
        StoredProfile profile = new StoredProfile(null, "PremiumName", false, FloodgateState.FALSE, "");
        VelocityLoginSession session = new VelocityLoginSession("PremiumName", false, profile);
        session.setVerifiedUsername("PremiumName");
        session.setUuid(premiumUuid);

        when(event.player()).thenReturn(player);
        when(event.server()).thenReturn(server);
        when(server.getServerInfo()).thenReturn(serverInfo);
        when(serverInfo.getName()).thenReturn("lobby");
        when(server.sendPluginMessage(any(ChannelIdentifier.class), any(byte[].class))).thenReturn(claimDelivered);
        when(player.getUsername()).thenReturn("PremiumName");
        when(player.getUniqueId()).thenReturn(effectiveUuid);
        when(player.getRemoteAddress()).thenReturn(address);
        when(player.isOnlineMode()).thenReturn(onlineMode);
        when(plugin.getSession()).thenReturn(sessions);
        when(plugin.getLog()).thenReturn(mock(Logger.class));
        when(sessions.get(address)).thenReturn(session);
        when(plugin.getProxyId()).thenReturn(UUID.fromString("00000000-0000-4000-8000-000000000003"));
        when(plugin.getAuthMePremiumLoginBridge()).thenReturn(bridge);

        PremiumClaimSender sender = new PremiumClaimSender(plugin, new byte[32], 10_000L);
        return new Fixture(sender, event, server, bridge, session);
    }

    private record Fixture(PremiumClaimSender sender, PlayerConfigurationEvent event, ServerConnection server,
                           AuthMePremiumLoginBridge bridge, VelocityLoginSession session) {
    }
}
