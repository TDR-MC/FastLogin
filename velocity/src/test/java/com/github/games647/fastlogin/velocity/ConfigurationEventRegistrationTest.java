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

import com.github.games647.fastlogin.core.antibot.AntiBotService;
import com.github.games647.fastlogin.core.hooks.bedrock.FloodgateService;
import com.github.games647.fastlogin.core.scheduler.AsyncScheduler;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.velocity.listener.ConnectListener;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.configuration.PlayerConfigurationEvent;
import com.velocitypowered.api.event.player.configuration.PlayerEnteredConfigurationEvent;
import com.velocitypowered.api.proxy.Player;
import net.md_5.bungee.config.Configuration;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConfigurationEventRegistrationTest {

    @Test
    void shouldSendFromAwaitedInitialBackendConfigurationEvent() throws Exception {
        Method method = ConnectListener.class.getDeclaredMethod(
                "onPlayerConfiguration", PlayerConfigurationEvent.class);

        assertNotNull(method.getAnnotation(Subscribe.class));
        assertNotNull(PremiumClaimSender.class.getDeclaredMethod("send", PlayerConfigurationEvent.class));
    }

    @Test
    void shouldNeverUseReconfigurationOnlyEnteredEventForInitialClaim() {
        boolean listensForEnteredEvent = Arrays.stream(ConnectListener.class.getDeclaredMethods())
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .anyMatch(PlayerEnteredConfigurationEvent.class::equals);
        boolean senderAcceptsEnteredEvent = Arrays.stream(PremiumClaimSender.class.getDeclaredMethods())
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .anyMatch(PlayerEnteredConfigurationEvent.class::equals);

        assertFalse(listensForEnteredEvent);
        assertFalse(senderAcceptsEnteredEvent);
    }

    @Test
    void legacyBackendAuthMessagesRemainUpstreamCompatibleByDefault() throws Exception {
        assertTrue(legacyBridgeSetting(new Configuration()));
    }

    @Test
    void disabledLegacyBackendBridgeCannotScheduleForceLogin() throws Exception {
        Configuration config = new Configuration();
        config.set("sendLegacyBackendAuthMessages", false);

        assertFalse(legacyBridgeSetting(config));
    }

    @Test
    void disabledLegacyBackendBridgeShortCircuitsBeforeFloodgateAndScheduling() {
        Configuration config = new Configuration();
        config.set("sendLegacyBackendAuthMessages", false);

        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        @SuppressWarnings("unchecked")
        FastLoginCore<Player, CommandSource, FastLoginVelocity> core = mock(FastLoginCore.class);
        FloodgateService floodgateService = mock(FloodgateService.class);
        FloodgatePlayer floodgatePlayer = mock(FloodgatePlayer.class);
        AsyncScheduler scheduler = mock(AsyncScheduler.class);
        ServerConnectedEvent event = mock(ServerConnectedEvent.class);
        Player player = mock(Player.class);

        when(plugin.getCore()).thenReturn(core);
        when(core.getConfig()).thenReturn(config);
        when(plugin.getFloodgateService()).thenReturn(floodgateService);
        when(plugin.getScheduler()).thenReturn(scheduler);
        when(event.getPlayer()).thenReturn(player);
        when(floodgateService.getBedrockPlayer(player.getUniqueId())).thenReturn(floodgatePlayer);

        ConnectListener listener = new ConnectListener(plugin, mock(AntiBotService.class));
        listener.onServerConnected(event);

        verify(plugin, never()).getFloodgateService();
        verify(plugin, never()).getSession();
        verify(plugin, never()).getScheduler();
        verifyNoInteractions(floodgateService, scheduler);
    }

    private static boolean legacyBridgeSetting(Configuration config) throws Exception {
        Method method = ConnectListener.class.getDeclaredMethod(
                "shouldSendLegacyBackendAuthMessages", Configuration.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, config);
    }
}
