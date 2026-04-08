package com.admin.dto.sso;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SsoLoginResponse {
    private String authorizationCode;
    private String state;
    private String redirectUri;
}
