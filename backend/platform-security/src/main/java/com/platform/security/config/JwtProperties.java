package com.platform.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * JWT configuration properties.
 */
@Data
@Component
@ConfigurationProperties(prefix = "platform.security.jwt")
public class JwtProperties {
    
    /**
     * Secret key for signing JWT tokens (minimum 256 bits / 32 characters)
     */
    private String secret = "";
    
    /**
     * Token expiration time in milliseconds (default: 1 hour)
     */
    private long expirationMs = 3600000;
    
    /**
     * Refresh token expiration time in milliseconds (default: 7 days)
     */
    private long refreshExpirationMs = 604800000;
    
    /**
     * Token issuer
     */
    private String issuer = "platform";
    
    /**
     * Whether to validate token issuer
     */
    private boolean validateIssuer = true;

    /**
     * Access-token httpOnly cookie 名（按优先级排序）。
     * <p>
     * 三端共用 {@code localhost:3000} 单一来源时若都写名为 {@code access_token} 的 Cookie 会互相覆盖，
     * 进而把 user-portal 的工作台 claim（{@code activeBusinessUnitId} / {@code activeRoleId}）冲掉，
     * 导致 {@code ProcessComponent} 在发起流程时报 "associated with a business unit role"。
     * <p>
     * 每个服务在 application.yml 中以服务前缀单独配置，列表中的第一项是本服务写出 Cookie 时使用的名称；
     * {@code workflow-engine-core} 同时接收三端的 WebSocket，可配置完整列表以兼容。
     */
    private List<String> cookieNames = List.of("access_token");

    /**
     * Refresh-token httpOnly cookie 名（每个服务写自己的名字，刷新接口也按此名读取）。
     */
    private String refreshCookieName = "refresh_token";

    /**
     * 便捷获取本服务写出 access cookie 的名称（{@link #cookieNames} 中的第一项）。
     */
    public String getPrimaryCookieName() {
        if (cookieNames == null || cookieNames.isEmpty()) {
            return "access_token";
        }
        return cookieNames.get(0);
    }
}
