package com.platform.common.util;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * String utility class.
 */
public final class StringUtils {
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );
    
    private StringUtils() {
        // Utility class
    }
    
    /**
     * Check if string is null or empty
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }
    
    /**
     * Check if string is null, empty, or contains only whitespace
     */
    public static boolean isBlank(String str) {
        return str == null || str.isBlank();
    }
    
    /**
     * Check if string is not null and not empty
     */
    public static boolean isNotEmpty(String str) {
        return str != null && !str.isEmpty();
    }
    
    /**
     * Check if string is not null, not empty, and not only whitespace
     */
    public static boolean isNotBlank(String str) {
        return str != null && !str.isBlank();
    }
    
    /**
     * Generate a random UUID string
     */
    public static String generateUuid() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Check if string is a valid email format
     */
    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
    
    /**
     * Check if string is a valid UUID format
     */
    public static boolean isValidUuid(String uuid) {
        return uuid != null && UUID_PATTERN.matcher(uuid).matches();
    }
    
    /**
     * Truncate string to max length
     */
    public static String truncate(String str, int maxLength) {
        if (str == null) return null;
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength);
    }
    
    /**
     * Truncate string with ellipsis
     */
    public static String truncateWithEllipsis(String str, int maxLength) {
        if (str == null) return null;
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
    
    /**
     * Convert camelCase to snake_case
     */
    public static String camelToSnake(String str) {
        if (str == null) return null;
        return str.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
    
    /**
     * Convert snake_case to camelCase
     */
    public static String snakeToCamel(String str) {
        if (str == null) return null;
        StringBuilder result = new StringBuilder();
        boolean nextUpper = false;
        for (char c : str.toCharArray()) {
            if (c == '_') {
                nextUpper = true;
            } else if (nextUpper) {
                result.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
    
    /**
     * Mask sensitive data (show first and last 2 chars)
     */
    public static String mask(String str) {
        if (str == null || str.length() <= 4) return "****";
        return str.substring(0, 2) + "****" + str.substring(str.length() - 2);
    }
}
