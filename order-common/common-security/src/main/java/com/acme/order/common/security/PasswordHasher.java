package com.acme.order.common.security;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Salted PBKDF2 hashes; legacy plaintext passwords are deliberately rejected. */
public final class PasswordHasher {
    private static final int ITERATIONS = 600_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return "pbkdf2$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
            + "$" + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
    }

    public boolean matches(String password, String encoded) {
        if (password == null || encoded == null || !encoded.startsWith("pbkdf2$")) return false;
        try {
            String[] parts = encoded.split("\\$");
            if (parts.length != 4) return false;
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 100_000 || iterations > 2_000_000) return false;
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            return salt.length >= 16 && expected.length == 32
                && MessageDigest.isEqual(expected, derive(password, salt, iterations));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private byte[] derive(String password, byte[] salt, int iterations) {
        var spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("PBKDF2 unavailable", ex);
        } finally {
            spec.clearPassword();
        }
    }
}
