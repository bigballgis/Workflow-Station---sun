package com.platform.security.dto;

/**
 * Token refresh response DTO.
 * Validates: Requirements 4.1
 */
public record TokenResponse(
    String accessToken,
    long expiresIn,
    /** New refresh token when rotation is enabled; clients must persist it. */
    String refreshToken
) {}
