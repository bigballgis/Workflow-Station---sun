package com.portal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 管理端等服务调用门户内部清理 API 时使用的共享密钥（与 admin-center user-portal.internal-api-token 一致）
 */
@Data
@ConfigurationProperties(prefix = "portal.internal")
public class PortalInternalApiProperties {

    /**
     * 未配置时拒绝内部清理请求，避免误暴露
     */
    private String apiToken = "";

    public void requireValidToken(String token) {
        if (apiToken == null || apiToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "portal.internal.api-token 未配置");
        }
        if (token == null || !apiToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无效的 X-Internal-Token");
        }
    }
}
