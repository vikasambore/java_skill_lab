package com.emergencykit.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// Utility class for hashing passwords using SHA-256.
// We never store plain text passwords -- always the hash.
public class PasswordUtil {

    // Hashes a plain-text password using SHA-256 and returns a 64-character hex string.
    // This matches what MySQL's SHA2('password', 256) produces.
    //
    // IMPORTANT: We use String.format("%02x", b) for each byte.
    // This ensures each byte always becomes exactly 2 hex characters.
    // For example, byte value 5 becomes "05" not "5".
    // Using Integer.toHexString() would sometimes drop the leading zero,
    // causing about 6% of hashes to not match MySQL's SHA2() output.
    public static String hash(String plainPassword) {
        try {
            // Get the SHA-256 hashing algorithm
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Convert the password to bytes using UTF-8 (same encoding MySQL uses)
            byte[] hashBytes = digest.digest(plainPassword.getBytes(StandardCharsets.UTF_8));

            // Build the hex string byte by byte
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b)); // always 2 hex digits per byte
            }

            return hexString.toString(); // always exactly 64 characters
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available in Java, so this should never happen
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
