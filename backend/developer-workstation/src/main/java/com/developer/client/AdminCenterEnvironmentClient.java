package com.developer.client;

import com.developer.dto.VaultEnvOption;
import com.platform.common.constant.PlatformConstants;
import com.platform.common.util.SafeUrlInput;
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

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AdminCenterEnvironmentClient {

    private final RestTemplate restTemplate;
    private final String adminBaseUrl;
    private final String serviceInternalToken;

    public AdminCenterEnvironmentClient(
            RestTemplate restTemplate,
            @Value("${admin-center.url:http://localhost:8090}") String adminBaseUrl,
            @Value("${service.internal-token:}") String serviceInternalToken) {
        this.restTemplate = restTemplate;
        this.adminBaseUrl = trimTrailingSlash(adminBaseUrl);
        this.serviceInternalToken = serviceInternalToken;
    }

    public List<VaultEnvOption> listVaultVariables() {
        String url = adminBaseUrl + "/api/v1/admin/internal/environment-variables?kind=VAULT";
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(serviceHeaders()),
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            List<Map<String, Object>> body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                throw new IllegalStateException("Environment variable list empty or non-success");
            }
            return body.stream()
                    .map(row -> new VaultEnvOption(
                            String.valueOf(row.get("varKey")),
                            row.get("displayName") != null
                                    ? String.valueOf(row.get("displayName"))
                                    : String.valueOf(row.get("varKey"))))
                    .toList();
        } catch (RestClientException ex) {
            log.warn("Failed to list VAULT environment variables: {}", ex.getMessage());
            throw new IllegalStateException("Unable to list VAULT environment variables from Admin Center", ex);
        }
    }

    public String resolveVaultPassword(String varKey) {
        String url = adminBaseUrl + "/api/v1/admin/internal/environment-variables/vault-password?varKey="
                + SafeUrlInput.encodeQueryValue(varKey);
        try {
            ResponseEntity<Map<String, String>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(serviceHeaders()),
                    new ParameterizedTypeReference<Map<String, String>>() {});
            Map<String, String> body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null || body.get("password") == null) {
                throw new IllegalStateException("Vault password response empty");
            }
            return body.get("password");
        } catch (HttpStatusCodeException ex) {
            log.warn("Vault password resolve rejected: status={}", ex.getStatusCode());
            throw new IllegalStateException("Unable to resolve VAULT password from Admin Center", ex);
        } catch (RestClientException ex) {
            log.warn("Vault password resolve failed: {}", ex.getMessage());
            throw new IllegalStateException("Unable to reach Admin Center for VAULT password", ex);
        }
    }

    private HttpHeaders serviceHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (serviceInternalToken != null && !serviceInternalToken.isBlank()) {
            headers.set(PlatformConstants.HEADER_SERVICE_TOKEN, serviceInternalToken);
        }
        return headers;
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
