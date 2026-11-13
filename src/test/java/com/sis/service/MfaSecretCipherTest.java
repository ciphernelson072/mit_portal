package com.sis.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MfaSecretCipherTest {

    private MfaSecretCipher cipher;

    @BeforeEach
    void setUp() {
        cipher = new MfaSecretCipher();
        ReflectionTestUtils.setField(cipher, "encodedKey",
                Base64.getEncoder().encodeToString(new byte[32]));
    }

    @Test
    void encryptsAndDecryptsSecretWithRandomizedCiphertext() {
        String encrypted = cipher.encrypt("totp-seed");
        String encryptedAgain = cipher.encrypt("totp-seed");

        assertNotEquals("totp-seed", encrypted);
        assertNotEquals(encrypted, encryptedAgain);
        assertEquals("totp-seed", cipher.decrypt(encrypted));
    }

    @Test
    void acceptsLegacyPlaintextForLazyMigration() {
        assertEquals("legacy-seed", cipher.decrypt("legacy-seed"));
    }

    @Test
    void rejectsCiphertextTampering() {
        String encrypted = cipher.encrypt("totp-seed");
        String corrupted = encrypted.substring(0, encrypted.length() - 1)
                + (encrypted.endsWith("A") ? "B" : "A");

        assertThrows(IllegalStateException.class, () -> cipher.decrypt(corrupted));
    }

    @Test
    void requiresAValidKeyWhenMfaIsEnabled() {
        ReflectionTestUtils.setField(cipher, "encodedKey", "");
        ReflectionTestUtils.setField(cipher, "mfaEnabled", true);

        assertThrows(IllegalStateException.class, cipher::validateConfiguration);
    }
}
