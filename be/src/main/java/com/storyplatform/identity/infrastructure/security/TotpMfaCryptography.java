package com.storyplatform.identity.infrastructure.security;

import com.storyplatform.identity.application.port.MfaCryptography;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

public final class TotpMfaCryptography implements MfaCryptography {

    private static final char[] BASE32 =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final int TOTP_STEP_SECONDS = 30;
    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec recoveryKey;
    private final SecureRandom random;

    public TotpMfaCryptography(byte[] key, SecureRandom random) {
        if (key == null || key.length != 32) {
            throw new IllegalArgumentException(
                    "MFA encryption key must contain exactly 32 bytes"
            );
        }
        this.encryptionKey = new SecretKeySpec(key.clone(), "AES");
        this.recoveryKey = new SecretKeySpec(key.clone(), "HmacSHA256");
        this.random = random;
    }

    @Override
    public EnrollmentSecret generateSecret() {
        byte[] raw = new byte[20];
        random.nextBytes(raw);
        return new EnrollmentSecret(base32Encode(raw), protect(raw));
    }

    @Override
    public boolean verifyTotp(
            String protectedSecret,
            String code,
            Instant at
    ) {
        if (code == null || !code.matches("[0-9]{6}")) {
            return false;
        }
        byte[] secret;
        try {
            secret = unprotect(protectedSecret);
        } catch (RuntimeException exception) {
            return false;
        }
        long counter = at.getEpochSecond() / TOTP_STEP_SECONDS;
        for (long drift = -1; drift <= 1; drift++) {
            String expected = totp(secret, counter + drift);
            if (MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    code.getBytes(StandardCharsets.US_ASCII)
            )) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<RecoveryCode> generateRecoveryCodes() {
        List<RecoveryCode> codes = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            byte[] raw = new byte[12];
            random.nextBytes(raw);
            String value = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(raw);
            codes.add(new RecoveryCode(value, hashRecoveryCode(value)));
        }
        return List.copyOf(codes);
    }

    @Override
    public String hashRecoveryCode(String rawCode) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(recoveryKey);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(rawCode.trim()
                            .toUpperCase(Locale.ROOT)
                            .getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to hash recovery code",
                    exception
            );
        }
    }

    private String protect(byte[] raw) {
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    encryptionKey,
                    new GCMParameterSpec(128, nonce)
            );
            byte[] encrypted = cipher.doFinal(raw);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ByteBuffer.allocate(nonce.length + encrypted.length)
                            .put(nonce)
                            .put(encrypted)
                            .array()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to protect MFA secret");
        }
    }

    private byte[] unprotect(String value) {
        try {
            byte[] combined = Base64.getUrlDecoder().decode(value);
            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] nonce = new byte[12];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    encryptionKey,
                    new GCMParameterSpec(128, nonce)
            );
            return cipher.doFinal(encrypted);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid MFA secret");
        }
    }

    private static String totp(byte[] secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] digest = mac.doFinal(
                    ByteBuffer.allocate(Long.BYTES)
                            .putLong(counter)
                            .array()
            );
            int offset = digest[digest.length - 1] & 0x0f;
            int binary = ((digest[offset] & 0x7f) << 24)
                    | ((digest[offset + 1] & 0xff) << 16)
                    | ((digest[offset + 2] & 0xff) << 8)
                    | (digest[offset + 3] & 0xff);
            return "%06d".formatted(binary % 1_000_000);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate TOTP");
        }
    }

    private static String base32Encode(byte[] bytes) {
        StringBuilder encoded = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 5) {
                encoded.append(BASE32[(buffer >> (bits - 5)) & 31]);
                bits -= 5;
            }
        }
        if (bits > 0) {
            encoded.append(BASE32[(buffer << (5 - bits)) & 31]);
        }
        return encoded.toString();
    }
}
