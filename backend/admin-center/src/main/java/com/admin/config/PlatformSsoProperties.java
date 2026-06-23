package com.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一登录（SSO）配置：签发短期 authorization code，供各子系统 exchange。
 */
@Data
@Component
@ConfigurationProperties(prefix = "platform.sso")
public class PlatformSsoProperties {

    /**
     * 服务间 redeem 调用使用的共享密钥；必须与 user-portal / developer-workstation 的 SSO_INTERNAL_TOKEN 一致。
     */
    private String internalToken = "";

    /**
     * authorization code 在 Redis 中的 TTL（秒）
     */
    private int codeTtlSeconds = 300;

    /**
     * clientId -&gt; 允许的 redirect_uri 前缀列表（完整 URL 必须以前缀开头，防开放重定向）
     */
    private Map<String, SsoClient> clients = new LinkedHashMap<>();

    /**
     * DSP 免密（Passwordless）SSO 配置（前缀 {@code platform.sso.dsp}）。
     */
    private Dsp dsp = new Dsp();

    @Data
    public static class SsoClient {
        /** 为 false 时不接受该 client 的登录（例如生产关闭 developer-workstation） */
        private boolean enabled = true;
        private List<String> redirectUriPrefixes = new ArrayList<>();
    }

    /**
     * DSP 免密链路：浏览器拿 AMToken → 后端用 AMToken 调 translator 换 E2E/JWT → 解析 claims 定位用户。
     *
     * <p>注意（安全整改点）：当前对 E2E/JWT 仅 Base64URL 解 payload，<b>不验签</b>；
     * {@link #manifestLocations} 已承接配置但尚未用于验签，后续需接入 JWKS/manifest 公钥校验。</p>
     */
    @Data
    public static class Dsp {
        /** 是否启用 DSP 免密。 */
        private boolean enabled = false;
        /** 浏览器侧 DSP authenticate 地址（后端仅保存，主要给前端）。 */
        private String authenticateUrl = "";
        /** 后端 token 交换地址（AMToken → E2E/JWT）。 */
        private String translatorUrl = "";
        /** 公钥 manifest 地址（验签用，当前未启用）。 */
        private String manifestLocations = "";
        private String clientId = "";
        /** translator 调用凭证（仅环境注入）。 */
        private String clientSecret = "";
        private String acceptApiVersion = "protocol=1.0,resource=1.0";
        /** AMToken 在 Cookie/Header 中的名字。 */
        private String amTokenName = "AMToken";
        /** 下游 E2E 信任令牌请求头名。 */
        private String e2eHeaderName = "X-HSBC-E2E-Trust-Token";
        private String inputTokenType = "SSOTOKEN";
        private String outputTokenType = "JWT";
        /** 是否接受网关已注入的 E2E header（默认 false，须显式开启）。 */
        private boolean acceptGatewayE2eToken = false;
        /** 从 claims 取 employeeId 的候选键（按序）。 */
        private List<String> employeeIdClaimNames = new ArrayList<>();
        /** 从 claims 取 username 的候选键（按序）。 */
        private List<String> usernameClaimNames = new ArrayList<>();
    }
}
