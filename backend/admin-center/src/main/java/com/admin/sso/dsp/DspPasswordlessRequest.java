package com.admin.sso.dsp;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DSP 免密登录请求（统一登录页 → admin-center）。
 */
@Data
public class DspPasswordlessRequest {

    /** SSO client（admin）。 */
    @NotBlank
    private String clientId;

    /** 回调地址（须命中允许前缀）。 */
    @NotBlank
    private String redirectUri;

    /** 透传 state。 */
    private String state;

    /** 浏览器侧从 DSP authenticate 取得的 AMToken。 */
    private String amToken;
}
