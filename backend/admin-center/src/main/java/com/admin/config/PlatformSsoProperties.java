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

    @Data
    public static class SsoClient {
        /** 为 false 时不接受该 client 的登录（例如生产关闭 developer-workstation） */
        private boolean enabled = true;
        private List<String> redirectUriPrefixes = new ArrayList<>();
    }
}
