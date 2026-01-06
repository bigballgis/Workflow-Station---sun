package com.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Token refresh request DTO.
 * Validates: Requirements 4.1
 */
public record RefreshRequest(
    @NotBlank(message = "刷新令牌不能为空")
    String refreshToken
) {}
