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
     * Access-token httpOnly cookie names (priority order).
     * <p>
     * When all three frontends share a single origin such as {@code localhost:3000}, writing a cookie named
     * {@code access_token} overwrites the others and can clear user-portal workspace claims
     * ({@code activeBusinessUnitId} / {@code activeRoleId}), causing ProcessComponent to reject process start with
     * "associated with a business unit role".
     * <p>
     * Each service configures a prefixed list in application.yml; the first entry is the name used when writing cookies.
     * {@code workflow-engine-core} may list all names because it accepts WebSockets from every frontend.
     */
    private List<String> cookieNames = List.of("access_token");

    /**
     * Refresh-token httpOnly cookie name (each service writes and reads its own on refresh).
     */
    private String refreshCookieName = "refresh_token";

    /**
     * Cookie {@code Domain} attribute for the auth cookies. Empty (default) -> host-only cookie
     * (correct for single-host dev). Set to a parent domain like {@code .example.com} so the
     * platform JWT is shared across sub-domains (e.g. the Activepieces / Superset gateway hosts),
     * enabling cross-subdomain SSO. MUST be applied identically when setting AND clearing cookies,
     * or a domain-scoped cookie can't be cleared on logout.
     */
    private String cookieDomain = "";

    /**
     * Primary access cookie name for this service ({@link #cookieNames} first entry).
     */
    public String getPrimaryCookieName() {
        if (cookieNames == null || cookieNames.isEmpty()) {
            return "access_token";
        }
        return cookieNames.get(0);
    }
}
