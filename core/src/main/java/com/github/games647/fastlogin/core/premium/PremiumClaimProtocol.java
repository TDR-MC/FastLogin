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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/**
 * Versioned binary codec for configuration-phase verified premium claims.
 */
public final class PremiumClaimProtocol {

    public static final String CHANNEL = "premium_claim";
    public static final int MAGIC = 0x464C5043;
    public static final int VERSION = 1;
    public static final int ADDRESS_HASH_LENGTH = 32;
    public static final int SIGNATURE_LENGTH = 32;
    public static final int MAX_PAYLOAD_LENGTH = 1_024;
    public static final int MIN_SECRET_LENGTH = 32;

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String HASH_ALGORITHM = "SHA-256";

    private PremiumClaimProtocol() {
    }

    public static byte[] encode(PremiumClaim claim, byte[] secret) {
        requireSecret(secret);
        byte[] unsigned = encodeUnsigned(claim);
        byte[] signature = hmac(secret, unsigned);

        byte[] payload = Arrays.copyOf(unsigned, unsigned.length + signature.length);
        System.arraycopy(signature, 0, payload, unsigned.length, signature.length);
        return payload;
    }

    public static PremiumClaim decodeAndVerify(byte[] payload, byte[] secret) throws ClaimFormatException {
        requireSecret(secret);
        if (payload == null || payload.length <= SIGNATURE_LENGTH || payload.length > MAX_PAYLOAD_LENGTH) {
            throw new ClaimFormatException(ClaimFormatException.Reason.MALFORMED, "Invalid payload length");
        }

        int unsignedLength = payload.length - SIGNATURE_LENGTH;
        byte[] unsigned = Arrays.copyOf(payload, unsignedLength);
        byte[] providedSignature = Arrays.copyOfRange(payload, unsignedLength, payload.length);
        byte[] expectedSignature = hmac(secret, unsigned);
        if (!MessageDigest.isEqual(expectedSignature, providedSignature)) {
            throw new ClaimFormatException(ClaimFormatException.Reason.INVALID_SIGNATURE, "Invalid signature");
        }

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(unsigned))) {
            int magic = input.readInt();
            int version = input.readUnsignedByte();
            if (magic != MAGIC || version != VERSION) {
                throw new ClaimFormatException(ClaimFormatException.Reason.UNSUPPORTED_VERSION,
                        "Unsupported premium claim protocol");
            }

            UUID issuer = readUuid(input);
            String audience = input.readUTF();
            String username = input.readUTF();
            UUID effectiveUuid = readUuid(input);
            UUID mojangUuid = readUuid(input);
            long issuedAt = input.readLong();
            long expiresAt = input.readLong();
            UUID nonce = readUuid(input);
            byte[] addressHash = new byte[ADDRESS_HASH_LENGTH];
            input.readFully(addressHash);
            if (input.available() != 0) {
                throw new ClaimFormatException(ClaimFormatException.Reason.MALFORMED, "Trailing claim data");
            }

            validateText(audience, username);
            return PremiumClaim.builder()
                    .issuerProxyId(issuer)
                    .audience(audience)
                    .username(username)
                    .effectiveUuid(effectiveUuid)
                    .mojangUuid(mojangUuid)
                    .issuedAt(issuedAt)
                    .expiresAt(expiresAt)
                    .nonce(nonce)
                    .clientAddressHash(addressHash)
                    .build();
        } catch (EOFException ex) {
            throw new ClaimFormatException(ClaimFormatException.Reason.MALFORMED, "Truncated claim", ex);
        } catch (IOException | IllegalArgumentException ex) {
            throw new ClaimFormatException(ClaimFormatException.Reason.MALFORMED, "Malformed claim", ex);
        }
    }

    public static byte[] parseHexSecret(String secret) {
        if (secret == null || (secret.length() & 1) != 0) {
            throw new IllegalArgumentException("Premium claim secret must be even-length hexadecimal");
        }

        byte[] decoded = new byte[secret.length() / 2];
        for (int i = 0; i < decoded.length; i++) {
            int high = Character.digit(secret.charAt(i * 2), 16);
            int low = Character.digit(secret.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Premium claim secret must be hexadecimal");
            }
            decoded[i] = (byte) ((high << 4) | low);
        }
        requireSecret(decoded);
        return decoded;
    }

    public static byte[] hashAddress(InetAddress address) {
        if (address == null) {
            throw new IllegalArgumentException("Client address is required");
        }
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM).digest(address.getAddress());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static byte[] encodeUnsigned(PremiumClaim claim) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeInt(MAGIC);
            output.writeByte(VERSION);
            writeUuid(output, claim.getIssuerProxyId());
            output.writeUTF(claim.getAudience());
            output.writeUTF(claim.getUsername().toLowerCase(Locale.ROOT));
            writeUuid(output, claim.getEffectiveUuid());
            writeUuid(output, claim.getMojangUuid());
            output.writeLong(claim.getIssuedAt());
            output.writeLong(claim.getExpiresAt());
            writeUuid(output, claim.getNonce());
            byte[] addressHash = claim.getClientAddressHash();
            if (addressHash.length != ADDRESS_HASH_LENGTH) {
                throw new IllegalArgumentException("Client address hash must be 32 bytes");
            }
            output.write(addressHash);
            output.flush();
            if (buffer.size() + SIGNATURE_LENGTH > MAX_PAYLOAD_LENGTH) {
                throw new IllegalArgumentException("Premium claim payload is too large");
            }
            return buffer.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to encode premium claim", ex);
        }
    }

    private static byte[] hmac(byte[] secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is unavailable", ex);
        }
    }

    private static void requireSecret(byte[] secret) {
        if (secret == null || secret.length < MIN_SECRET_LENGTH) {
            throw new IllegalArgumentException("Premium claim secret must contain at least 32 bytes");
        }
    }

    private static void validateText(String audience, String username) throws ClaimFormatException {
        if (audience.isEmpty() || audience.length() > 64) {
            throw new ClaimFormatException(ClaimFormatException.Reason.MALFORMED, "Invalid audience");
        }
        if (username.isEmpty() || username.length() > 16
                || !username.equals(username.toLowerCase(Locale.ROOT))
                || !username.matches("[a-z0-9_]+")) {
            throw new ClaimFormatException(ClaimFormatException.Reason.MALFORMED, "Invalid username");
        }
    }

    private static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    public static final class ClaimFormatException extends Exception {

        private final Reason reason;

        public ClaimFormatException(Reason reason, String message) {
            super(message);
            this.reason = reason;
        }

        public ClaimFormatException(Reason reason, String message, Throwable cause) {
            super(message, cause);
            this.reason = reason;
        }

        public Reason getReason() {
            return reason;
        }

        public enum Reason {
            MALFORMED,
            INVALID_SIGNATURE,
            UNSUPPORTED_VERSION
        }
    }
}
