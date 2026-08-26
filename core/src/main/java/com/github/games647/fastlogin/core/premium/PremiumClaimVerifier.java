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
package com.github.games647.fastlogin.core.premium;

import com.github.games647.fastlogin.core.premium.PremiumClaimProtocol.ClaimFormatException;

import java.net.InetAddress;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Validates connection binding, time bounds, issuer and one-time use after signature verification.
 */
public final class PremiumClaimVerifier {

    private final byte[] secret;
    private final String audience;
    private final long maxTtlMillis;
    private final long maxFutureSkewMillis;
    private final Predicate<UUID> issuerAllowed;
    private final Map<UUID, Long> usedNonces = new ConcurrentHashMap<>();

    public PremiumClaimVerifier(byte[] secret, String audience, long maxTtlMillis, long maxFutureSkewMillis,
                                Predicate<UUID> issuerAllowed) {
        this.secret = Objects.requireNonNull(secret, "secret").clone();
        this.audience = Objects.requireNonNull(audience, "audience");
        this.maxTtlMillis = maxTtlMillis;
        this.maxFutureSkewMillis = maxFutureSkewMillis;
        this.issuerAllowed = Objects.requireNonNull(issuerAllowed, "issuerAllowed");
    }

    public VerificationResult verify(byte[] payload, String expectedUsername, UUID expectedEffectiveUuid,
                                     InetAddress expectedAddress, long now) {
        PremiumClaim claim;
        try {
            claim = PremiumClaimProtocol.decodeAndVerify(payload, secret);
        } catch (ClaimFormatException ex) {
            return VerificationResult.rejected(mapFormatReason(ex.getReason()));
        }

        if (!audience.equals(claim.getAudience())) {
            return VerificationResult.rejected(Status.WRONG_AUDIENCE);
        }
        if (!issuerAllowed.test(claim.getIssuerProxyId())) {
            return VerificationResult.rejected(Status.UNTRUSTED_ISSUER);
        }
        String normalizedUsername = expectedUsername.toLowerCase(Locale.ROOT);
        if (!normalizedUsername.equals(claim.getUsername())
                || !expectedEffectiveUuid.equals(claim.getEffectiveUuid())) {
            return VerificationResult.rejected(Status.WRONG_IDENTITY);
        }
        byte[] expectedAddressHash = PremiumClaimProtocol.hashAddress(expectedAddress);
        if (!MessageDigest.isEqual(expectedAddressHash, claim.getClientAddressHash())) {
            return VerificationResult.rejected(Status.WRONG_ADDRESS);
        }
        if (claim.getIssuedAt() > now + maxFutureSkewMillis) {
            return VerificationResult.rejected(Status.NOT_YET_VALID);
        }
        long ttl = claim.getExpiresAt() - claim.getIssuedAt();
        if (ttl <= 0 || ttl > maxTtlMillis) {
            return VerificationResult.rejected(Status.TTL_EXCEEDED);
        }
        if (claim.getExpiresAt() < now) {
            return VerificationResult.rejected(Status.EXPIRED);
        }

        removeExpiredNonces(now);
        if (usedNonces.putIfAbsent(claim.getNonce(), claim.getExpiresAt()) != null) {
            return VerificationResult.rejected(Status.REPLAYED);
        }
        return VerificationResult.accepted(claim);
    }

    private void removeExpiredNonces(long now) {
        Iterator<Map.Entry<UUID, Long>> iterator = usedNonces.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (entry.getValue() < now) {
                usedNonces.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private Status mapFormatReason(ClaimFormatException.Reason reason) {
        switch (reason) {
            case INVALID_SIGNATURE:
                return Status.INVALID_SIGNATURE;
            case UNSUPPORTED_VERSION:
                return Status.UNSUPPORTED_VERSION;
            case MALFORMED:
            default:
                return Status.MALFORMED;
        }
    }

    public enum Status {
        VALID,
        MALFORMED,
        INVALID_SIGNATURE,
        UNSUPPORTED_VERSION,
        WRONG_AUDIENCE,
        UNTRUSTED_ISSUER,
        WRONG_IDENTITY,
        WRONG_ADDRESS,
        NOT_YET_VALID,
        TTL_EXCEEDED,
        EXPIRED,
        REPLAYED
    }

    public static final class VerificationResult {

        private final Status status;
        private final PremiumClaim claim;

        private VerificationResult(Status status, PremiumClaim claim) {
            this.status = status;
            this.claim = claim;
        }

        public static VerificationResult accepted(PremiumClaim claim) {
            return new VerificationResult(Status.VALID, claim);
        }

        public static VerificationResult rejected(Status status) {
            return new VerificationResult(status, null);
        }

        public boolean isValid() {
            return status == Status.VALID;
        }

        public Status getStatus() {
            return status;
        }

        public PremiumClaim getClaim() {
            return claim;
        }
    }
}
