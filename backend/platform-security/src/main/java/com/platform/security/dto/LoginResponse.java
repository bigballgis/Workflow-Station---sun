package com.platform.security.dto;

/**
 * Login response DTO containing tokens and user info.
 * Validates: Requirements 2.1
 */
public record LoginResponse(
    String accessToken,
    String refreshToken,
    long expiresIn,
    UserInfo user
) {}
