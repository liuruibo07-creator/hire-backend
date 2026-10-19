package com.hire.user.security;

import cn.hutool.crypto.digest.DigestUtil;
import com.hire.common.constant.JwtConstant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordHashesTest {
    @Test void bcryptIsSaltedAndRejectsWrongPasswords() {
        String first = PasswordHashes.encode("correct-password");
        String second = PasswordHashes.encode("correct-password");
        assertNotEquals(first, second);
        assertTrue(PasswordHashes.matches("correct-password", first));
        assertFalse(PasswordHashes.matches("wrong-password", first));
        assertFalse(PasswordHashes.legacy(first));
    }

    @Test void acceptsLegacyOnlyWhenDigestMatches() {
        String legacy = DigestUtil.sha256Hex("old-password");
        assertTrue(PasswordHashes.legacy(legacy));
        assertTrue(PasswordHashes.matches("old-password", legacy));
        assertFalse(PasswordHashes.matches("wrong", legacy));
        assertFalse(PasswordHashes.matches("old-password", "corrupted-hash"));
        assertFalse(PasswordHashes.matches(null, legacy));
    }

    @Test void rejectsWeakConfiguredSigningKey() {
        String previous = System.getProperty("hire.jwt.secret");
        try {
            System.setProperty("hire.jwt.secret", "weak");
            assertThrows(IllegalStateException.class, JwtConstant::secretKey);
            System.setProperty("hire.jwt.secret", "a-valid-signing-key-with-at-least-32-bytes");
            assertEquals("a-valid-signing-key-with-at-least-32-bytes", JwtConstant.secretKey());
        } finally {
            if (previous == null) System.clearProperty("hire.jwt.secret");
            else System.setProperty("hire.jwt.secret", previous);
        }
    }
}
