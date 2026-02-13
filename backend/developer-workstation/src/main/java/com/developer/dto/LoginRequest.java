package com.developer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "{validation.username_required}")
    private String username;
    
    @NotBlank(message = "{validation.password_required}")
    private String password;
}
