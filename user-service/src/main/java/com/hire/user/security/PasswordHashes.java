package com.hire.user.security;

import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.crypto.digest.DigestUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** New passwords use BCrypt; legacy SHA-256 hashes are accepted only for migration. */
public final class PasswordHashes {
    private PasswordHashes() { }

    public static String encode(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(12));
    }

    public static boolean legacy(String hash) {
        return hash != null && hash.matches("[a-fA-F0-9]{64}");
    }

    public static boolean matches(String password, String hash) {
        if (password == null || hash == null) return false;
        if (legacy(hash)) {
            return MessageDigest.isEqual(DigestUtil.sha256Hex(password).getBytes(StandardCharsets.US_ASCII),
                    hash.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
        }
        try {
            return BCrypt.checkpw(password, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
