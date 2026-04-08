package com.developer.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "platform.sso", name = "developer-exchange-enabled", havingValue = "true", matchIfMissing = true)
public class AdminCenterSsoClient {

    public static final String HEADER_INTERNAL = "X-Platform-Sso-Internal";

    private final RestTemplate restTemplate;
    private final String adminBaseUrl;
    private final String internalToken;

    public AdminCenterSsoClient(
            RestTemplate restTemplate,
            @Value("${admin-center.url:http://localhost:8090}") String adminBaseUrl,
            @Value("${platform.sso.internal-token:}") String internalToken) {
        this.restTemplate = restTemplate;
        this.adminBaseUrl = trimTrailingSlash(adminBaseUrl);
        this.internalToken = internalToken;
    }

    public SsoRedeemResult redeemDeveloperCode(String code) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalStateException("platform.sso.internal-token is not configured");
        }
        String url = adminBaseUrl + "/api/v1/admin/internal/sso/redeem";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HEADER_INTERNAL, internalToken);
        Map<String, String> body = Map.of(
                "code", code,
                "clientId", "developer-workstation"
        );
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class);
            Map<?, ?> m = response.getBody();
            if (m == null || m.get("userId") == null) {
                throw new IllegalArgumentException("Invalid redeem response");
            }
            return new SsoRedeemResult(String.valueOf(m.get("userId")));
        } catch (RestClientException e) {
            log.warn("SSO redeem failed: {}", e.getMessage());
            throw new IllegalArgumentException("SSO redeem failed", e);
        }
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public record SsoRedeemResult(String userId) {
    }
}
