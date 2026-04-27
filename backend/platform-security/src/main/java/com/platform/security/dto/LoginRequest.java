package com.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login request DTO.
 * Validates: Requirements 2.1
 */
public record LoginRequest(
    @NotBlank(message = "Username is required")
    @Size(min = 1, max = 50, message = "Username length must be between 1 and 50")
    String username,
    
    @NotBlank(message = "Password is required")
    @Size(min = 1, max = 100, message = "Password length must be between 1 and 100")
    String password
) {}
