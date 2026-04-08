package com.admin.dto.sso;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SsoRedeemResponse {
    private String userId;
    private String username;
}
