package com.admin.config;

import com.platform.common.constant.PlatformConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Explicit guard for internal-only controllers. A browser JWT is not sufficient:
 * callers must prove first-party service identity and provide the delegated user.
 */
@Component
public class InternalServiceAuthenticator {

    private final String expectedToken;

    public InternalServiceAuthenticator(@Value("${service.internal-token:}") String expectedToken) {
        this.expectedToken = expectedToken;
    }

    public String requireDelegatedUserId(HttpServletRequest request) {
        if (!hasValidServiceToken(request)) {
            throw new AuthenticationCredentialsNotFoundException("Trusted service authentication required");
        }
        String userId = request.getHeader(PlatformConstants.HEADER_USER_ID);
        if (userId == null || userId.isBlank() || "system".equals(userId)) {
            throw new AuthenticationCredentialsNotFoundException("Delegated user identity required");
        }
        return userId.trim();
    }

    boolean hasValidServiceToken(HttpServletRequest request) {
        if (expectedToken == null || expectedToken.isBlank()) {
            return false;
        }
        String provided = request.getHeader(PlatformConstants.HEADER_SERVICE_TOKEN);
        return provided != null && !provided.isEmpty()
                && MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                expectedToken.getBytes(StandardCharsets.UTF_8));
    }
}
