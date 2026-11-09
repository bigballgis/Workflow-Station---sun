package com.platform.security.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Checks the {@code X-Service-Token} a first-party service sends ({@code service.internal-token}), in constant time.
 * The inbound counterpart of {@link com.platform.security.client.ServiceTokenRequestInterceptor}; services register it
 * as a bean for the endpoints that only other platform services may call.
 */
public final class ServiceTokenVerifier {

    private final byte[] expected;

    /** @param serviceToken {@code service.internal-token}; required — without it no service call could be admitted */
    public ServiceTokenVerifier(String serviceToken) {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalStateException("service.internal-token must be configured (SERVICE_INTERNAL_TOKEN)");
        }
        this.expected = serviceToken.getBytes(StandardCharsets.UTF_8);
    }

    public boolean matches(String providedToken) {
        return providedToken != null && !providedToken.isEmpty()
                && MessageDigest.isEqual(providedToken.getBytes(StandardCharsets.UTF_8), expected);
    }
}
