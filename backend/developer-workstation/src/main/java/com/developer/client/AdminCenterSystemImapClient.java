package com.developer.client;

import com.platform.common.constant.PlatformConstants;
import com.platform.common.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Fetches global IMAP settings from admin-center (service-to-service).
 */
@Slf4j
@Component
public class AdminCenterSystemImapClient {

    private final RestTemplate restTemplate;
    private final String adminBaseUrl;
    private final String serviceInternalToken;

    public AdminCenterSystemImapClient(
            RestTemplate restTemplate,
            @Value("${admin-center.url:http://localhost:8090}") String adminBaseUrl,
            @Value("${service.internal-token:}") String serviceInternalToken) {
        this.restTemplate = restTemplate;
        this.adminBaseUrl = trimTrailingSlash(adminBaseUrl);
        this.serviceInternalToken = serviceInternalToken;
    }

    public record SystemImapEndpoint(String host, int port, boolean useSsl) {
    }

    public SystemImapEndpoint fetchSystemImapEndpoint() {
        String url = adminBaseUrl + "/api/v1/admin/internal/system-imap";
        HttpHeaders headers = new HttpHeaders();
        if (serviceInternalToken != null && !serviceInternalToken.isBlank()) {
            headers.set(PlatformConstants.HEADER_SERVICE_TOKEN, serviceInternalToken);
        }
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                throw new IllegalStateException("System IMAP response empty or non-success");
            }
            if (body.containsKey("error")) {
                throw new IllegalStateException(String.valueOf(body.getOrDefault("message", body.get("error"))));
            }
            Object hostObj = body.get("host");
            Object portObj = body.get("port");
            Object sslObj = body.get("useSsl");
            if (hostObj == null || portObj == null || sslObj == null) {
                throw new IllegalStateException("System IMAP payload missing host/port/useSsl");
            }
            int port = ((Number) portObj).intValue();
            boolean useSsl = Boolean.TRUE.equals(sslObj) || "true".equalsIgnoreCase(String.valueOf(sslObj));
            return new SystemImapEndpoint(String.valueOf(hostObj).trim(), port, useSsl);
        } catch (HttpStatusCodeException ex) {
            String detail = extractAdminErrorMessage(ex.getResponseBodyAsString());
            log.warn("System IMAP fetch rejected by admin-center: status={} body={}",
                    ex.getStatusCode(), detail);
            throw new IllegalStateException(
                    detail != null && !detail.isBlank() ? detail : "System IMAP not configured",
                    ex);
        } catch (RestClientException ex) {
            log.warn("Failed to fetch system IMAP from admin-center: {}", ex.getMessage());
            throw new IllegalStateException("Unable to reach Admin Center for system IMAP settings", ex);
        }
    }

    private String extractAdminErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String trimmed = body.trim();
        if (!JsonUtils.isValidJson(trimmed)) {
            return trimmed;
        }
        try {
            Map<String, Object> parsed = JsonUtils.fromJson(trimmed, new TypeReference<Map<String, Object>>() {});
            Object message = parsed.get("message");
            if (message != null && !String.valueOf(message).isBlank()) {
                return String.valueOf(message).trim();
            }
        } catch (RuntimeException ex) {
            log.debug("Could not parse admin IMAP error JSON: {}", ex.getMessage());
        }
        return trimmed;
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
