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

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * A short-lived proof that a proxy completed Mojang authentication for one connection.
 */
public final class PremiumClaim {

    private final UUID issuerProxyId;
    private final String audience;
    private final String username;
    private final UUID effectiveUuid;
    private final UUID mojangUuid;
    private final long issuedAt;
    private final long expiresAt;
    private final UUID nonce;
    private final byte[] clientAddressHash;

    private PremiumClaim(Builder builder) {
        this.issuerProxyId = Objects.requireNonNull(builder.issuerProxyId, "issuerProxyId");
        this.audience = Objects.requireNonNull(builder.audience, "audience");
        this.username = Objects.requireNonNull(builder.username, "username").toLowerCase(Locale.ROOT);
        this.effectiveUuid = Objects.requireNonNull(builder.effectiveUuid, "effectiveUuid");
        this.mojangUuid = Objects.requireNonNull(builder.mojangUuid, "mojangUuid");
        this.issuedAt = builder.issuedAt;
        this.expiresAt = builder.expiresAt;
        this.nonce = Objects.requireNonNull(builder.nonce, "nonce");
        this.clientAddressHash = Objects.requireNonNull(builder.clientAddressHash, "clientAddressHash").clone();
    }

    public static Builder builder() {
        return new Builder();
    }

    public UUID getIssuerProxyId() {
        return issuerProxyId;
    }

    public String getAudience() {
        return audience;
    }

    public String getUsername() {
        return username;
    }

    public UUID getEffectiveUuid() {
        return effectiveUuid;
    }

    public UUID getMojangUuid() {
        return mojangUuid;
    }

    public long getIssuedAt() {
        return issuedAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public UUID getNonce() {
        return nonce;
    }

    public byte[] getClientAddressHash() {
        return clientAddressHash.clone();
    }

    @Override
    public String toString() {
        return PremiumClaim.class.getSimpleName() + '{'
            + "issuerProxyId=" + issuerProxyId
            + ", audience='" + audience + '\''
            + ", username='" + username + '\''
            + ", effectiveUuid=" + effectiveUuid
            + ", mojangUuid=<redacted>"
            + ", issuedAt=" + issuedAt
            + ", expiresAt=" + expiresAt
            + ", nonce=<redacted>"
            + ", clientAddressHash=<redacted>"
            + '}';
    }

    public static final class Builder {

        private UUID issuerProxyId;
        private String audience;
        private String username;
        private UUID effectiveUuid;
        private UUID mojangUuid;
        private long issuedAt;
        private long expiresAt;
        private UUID nonce;
        private byte[] clientAddressHash;

        private Builder() {
        }

        public Builder issuerProxyId(UUID issuerProxyId) {
            this.issuerProxyId = issuerProxyId;
            return this;
        }

        public Builder audience(String audience) {
            this.audience = audience;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder effectiveUuid(UUID effectiveUuid) {
            this.effectiveUuid = effectiveUuid;
            return this;
        }

        public Builder mojangUuid(UUID mojangUuid) {
            this.mojangUuid = mojangUuid;
            return this;
        }

        public Builder issuedAt(long issuedAt) {
            this.issuedAt = issuedAt;
            return this;
        }

        public Builder expiresAt(long expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder nonce(UUID nonce) {
            this.nonce = nonce;
            return this;
        }

        public Builder clientAddressHash(byte[] clientAddressHash) {
            this.clientAddressHash = clientAddressHash;
            return this;
        }

        public PremiumClaim build() {
            return new PremiumClaim(this);
        }
    }
}
