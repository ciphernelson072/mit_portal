package com.sis.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class MfaSecretCipher {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    @Value("${app.security.mfa.encryption-key:}")
    private String encodedKey;

    @Value("${app.security.mfa.enabled:false}")
    private boolean mfaEnabled;

    @PostConstruct
    public void validateConfiguration() {
        if (mfaEnabled) {
            keyBytes();
        }
    }

    public String encrypt(String secret) {
        if (secret == null) {
            return null;
        }
        if (isEncrypted(secret)) {
            throw new IllegalArgumentException("MFA secret is already encrypted");
        }

        byte[] iv = new byte[IV_LENGTH_BYTES];
        new SecureRandom().nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes(), "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to encrypt MFA secret", e);
        }
    }

    public String decrypt(String storedSecret) {
        if (storedSecret == null || !isEncrypted(storedSecret)) {
            return storedSecret;
        }

        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(storedSecret.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Stored MFA secret has an invalid encoding", e);
        }
        if (payload.length <= IV_LENGTH_BYTES) {
            throw new IllegalStateException("Stored MFA secret is incomplete");
        }

        byte[] iv = new byte[IV_LENGTH_BYTES];
        byte[] ciphertext = new byte[payload.length - IV_LENGTH_BYTES];
        ByteBuffer.wrap(payload).get(iv).get(ciphertext);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes(), "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to decrypt MFA secret; check the configured MFA key", e);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private byte[] keyBytes() {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException(
                    "MFA is enabled but MFA_ENCRYPTION_KEY is not configured");
        }

        byte[] key;
        try {
            key = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("MFA_ENCRYPTION_KEY must be Base64-encoded", e);
        }
        if (key.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException("MFA_ENCRYPTION_KEY must decode to exactly 32 bytes");
        }
        return key;
    }
}
