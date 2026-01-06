package com.platform.security.dto;

/**
 * Token refresh response DTO.
 * Validates: Requirements 4.1
 */
public record TokenResponse(
    String accessToken,
    long expiresIn
) {}
