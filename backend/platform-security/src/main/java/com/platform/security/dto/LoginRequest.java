package com.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login request DTO.
 * Validates: Requirements 2.1
 */
public record LoginRequest(
    @NotBlank(message = "用户名不能为空")
    @Size(min = 1, max = 50, message = "用户名长度必须在1-50之间")
    String username,
    
    @NotBlank(message = "密码不能为空")
    @Size(min = 1, max = 100, message = "密码长度必须在1-100之间")
    String password
) {}
