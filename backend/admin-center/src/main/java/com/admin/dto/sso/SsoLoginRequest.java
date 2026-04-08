package com.admin.dto.sso;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SsoLoginRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    /**
     * portal | admin | developer-workstation
     */
    @NotBlank
    private String clientId;

    @NotBlank
    private String redirectUri;

    private String state;
}
