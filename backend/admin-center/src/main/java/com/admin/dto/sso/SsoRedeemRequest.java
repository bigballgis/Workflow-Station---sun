package com.admin.dto.sso;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SsoRedeemRequest {

    @NotBlank
    private String code;

    /**
     * 必须与签发 code 时的 clientId 一致（portal / admin / developer-workstation）
     */
    @NotBlank
    private String clientId;
}
