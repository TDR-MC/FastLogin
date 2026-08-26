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

import com.github.games647.fastlogin.velocity.listener.ConnectListener;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.configuration.PlayerConfigurationEvent;
import com.velocitypowered.api.event.player.configuration.PlayerEnteredConfigurationEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
