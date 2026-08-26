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
package com.github.games647.fastlogin.core;

import com.github.games647.fastlogin.core.premium.PremiumClaim;
import com.github.games647.fastlogin.core.premium.PremiumClaimProtocol;
import com.github.games647.fastlogin.core.premium.PremiumClaimProtocol.ClaimFormatException;
import com.github.games647.fastlogin.core.premium.PremiumClaimVerifier;
import com.github.games647.fastlogin.core.premium.PremiumClaimVerifier.Status;
import com.github.games647.fastlogin.core.premium.PremiumClaimVerifier.VerificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PremiumClaimProtocolTest {

    private static final long NOW = 1_800_000_000_000L;
    private static final UUID ISSUER = UUID.fromString("a6ac91f7-4151-4eec-adf6-3c6c7c62da9b");
    private static final UUID EFFECTIVE_UUID = UUID.fromString("f599ca9c-581f-3ef8-a18a-3a213479a45f");
    private static final UUID MOJANG_UUID = UUID.fromString("c6064d12-bf17-4ff8-aee1-e82162c7a858");
    private static final UUID NONCE = UUID.fromString("fc3fefad-a950-47d9-a762-a4092cb7c6fe");

    private byte[] secret;
    private InetAddress address;

    @BeforeEach
    void setUp() throws Exception {
        secret = PremiumClaimProtocol.parseHexSecret(
                "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        address = InetAddress.getByName("203.0.113.42");
    }

    @Test
    void shouldKeepEffectiveAndMojangUuidsSeparate() throws Exception {
        PremiumClaim decoded = PremiumClaimProtocol.decodeAndVerify(encodeClaim(NOW, NOW + 10_000), secret);

        assertEquals(EFFECTIVE_UUID, decoded.getEffectiveUuid());
        assertEquals(MOJANG_UUID, decoded.getMojangUuid());
        assertNotEquals(decoded.getEffectiveUuid(), decoded.getMojangUuid());
        assertEquals("alice", decoded.getUsername());
        assertEquals("lobby", decoded.getAudience());
    }

    @Test
    void shouldRejectTamperedPayload() {
        byte[] payload = encodeClaim(NOW, NOW + 10_000);
        payload[20] ^= 1;

        ClaimFormatException exception = assertThrows(ClaimFormatException.class,
                () -> PremiumClaimProtocol.decodeAndVerify(payload, secret));
        assertEquals(ClaimFormatException.Reason.INVALID_SIGNATURE, exception.getReason());
    }

    @Test
    void shouldRejectReplayAtomically() {
        PremiumClaimVerifier verifier = verifier();
        byte[] payload = encodeClaim(NOW, NOW + 10_000);

        assertTrue(verify(verifier, payload, NOW).isValid());
        assertEquals(Status.REPLAYED, verify(verifier, payload, NOW + 1).getStatus());
    }

    @Test
    void shouldRejectExpiredAndOverlongClaims() {
        PremiumClaimVerifier verifier = verifier();

        assertEquals(Status.EXPIRED, verify(verifier, encodeClaim(NOW - 20_000, NOW - 10_000), NOW).getStatus());
        assertEquals(Status.TTL_EXCEEDED, verify(verifier, encodeClaim(NOW, NOW + 31_000), NOW).getStatus());
    }

    @Test
    void shouldRejectWrongConnectionBinding() throws Exception {
        PremiumClaimVerifier verifier = verifier();
        byte[] payload = encodeClaim(NOW, NOW + 10_000);

        VerificationResult wrongIdentity = verifier.verify(payload, "bob", EFFECTIVE_UUID, address, NOW);
        assertEquals(Status.WRONG_IDENTITY, wrongIdentity.getStatus());

        InetAddress otherAddress = InetAddress.getByName("198.51.100.17");
        VerificationResult wrongAddress = verifier.verify(payload, "alice", EFFECTIVE_UUID, otherAddress, NOW);
        assertEquals(Status.WRONG_ADDRESS, wrongAddress.getStatus());
    }

    @Test
    void shouldRejectWrongAudienceAndIssuer() {
        byte[] payload = encodeClaim(NOW, NOW + 10_000);
        PremiumClaimVerifier wrongAudience = new PremiumClaimVerifier(secret, "survival", 30_000, 5_000,
                issuer -> true);
        PremiumClaimVerifier wrongIssuer = new PremiumClaimVerifier(secret, "lobby", 30_000, 5_000,
                issuer -> false);

        assertEquals(Status.WRONG_AUDIENCE, verify(wrongAudience, payload, NOW).getStatus());
        assertEquals(Status.UNTRUSTED_ISSUER, verify(wrongIssuer, payload, NOW).getStatus());
    }

    private PremiumClaimVerifier verifier() {
        return new PremiumClaimVerifier(secret, "lobby", 30_000, 5_000, ISSUER::equals);
    }

    private VerificationResult verify(PremiumClaimVerifier verifier, byte[] payload, long now) {
        return verifier.verify(payload, "alice", EFFECTIVE_UUID, address, now);
    }

    private byte[] encodeClaim(long issuedAt, long expiresAt) {
        PremiumClaim claim = PremiumClaim.builder()
                .issuerProxyId(ISSUER)
                .audience("lobby")
                .username("Alice")
                .effectiveUuid(EFFECTIVE_UUID)
                .mojangUuid(MOJANG_UUID)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .nonce(NONCE)
                .clientAddressHash(PremiumClaimProtocol.hashAddress(address))
                .build();
        return PremiumClaimProtocol.encode(claim, secret);
    }
}
