package com.platform.security.config;

import jakarta.annotation.PostConstruct;
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
     * Access token expiration time in milliseconds. Required: no in-code default, see {@link #validateRequiredSettings()}.
     */
    private long expirationMs;

    /**
     * Refresh token expiration time in milliseconds; also the session idle timeout and the auth cookies' max-age.
     * Required: no in-code default, see {@link #validateRequiredSettings()}.
     */
    private long refreshExpirationMs;
    
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
     * Secret and token lifetimes come only from configuration (JWT_SECRET / JWT_EXPIRATION /
     * JWT_REFRESH_EXPIRATION); a service that forgot to bind them must fail at startup naming the
     * variable, instead of silently running with a guessed value. An unset {@code ${JWT_SECRET}} reaches
     * this binding as the literal placeholder text, hence the {@code "${"} check.
     */
    @PostConstruct
    void validateRequiredSettings() {
        if (secret == null || secret.isBlank() || secret.contains("${")) {
            throw new IllegalStateException("platform.security.jwt.secret must be configured (JWT_SECRET)");
        }
        if (expirationMs <= 0) {
            throw new IllegalStateException(
                    "platform.security.jwt.expiration-ms must be configured (JWT_EXPIRATION) and > 0");
        }
        if (refreshExpirationMs <= 0) {
            throw new IllegalStateException(
                    "platform.security.jwt.refresh-expiration-ms must be configured (JWT_REFRESH_EXPIRATION) and > 0");
        }
    }

    /**
     * Max-age in seconds for both auth cookies: the session lifetime (refresh token lifetime). The access
     * cookie deliberately outlives its token, so an expired access token still reaches the server, gets a
     * 401 from {@code JwtAuthenticationFilter}, and the frontend renews the session; a cookie that vanished
     * with its token would instead turn later requests anonymous.
     */
    public int getSessionCookieMaxAgeSeconds() {
        return (int) (refreshExpirationMs / 1000);
    }

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
