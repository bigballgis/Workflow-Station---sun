package com.admin.util;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 checksum helper shared by function unit import and content persistence.
 */
@Slf4j
public final class ChecksumUtils {

    private ChecksumUtils() {
    }

    /**
     * SHA-256 hex digest of the given content; {@code null} for null/empty input
     * (matches the historical contract of FunctionUnitManagerComponent#calculateChecksum).
     */
    public static String sha256Hex(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to calculate checksum", e);
            return null;
        }
    }
}
