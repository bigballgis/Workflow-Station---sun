package com.platform.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
    private String secret = "platform-default-secret-key-change-in-production-minimum-256-bits";
    
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
}
