package com.platform.common.util;

import java.util.UUID;

/**
 * Utility for generating idempotency keys for cross-service REST calls.
 * Usage: Add X-Idempotency-Key header to POST/PUT requests to prevent duplicate operations.
 */
public final class IdempotencyUtils {
    
    public static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    
    private IdempotencyUtils() {}
    
    /**
     * Generate a unique idempotency key based on operation context.
     * Format: {operation}:{entityId}:{timestamp}
     */
    public static String generateKey(String operation, String entityId) {
        return operation + ":" + entityId + ":" + System.currentTimeMillis();
    }
    
    /**
     * Generate a random idempotency key.
     */
    public static String generateKey() {
        return UUID.randomUUID().toString();
    }
}
