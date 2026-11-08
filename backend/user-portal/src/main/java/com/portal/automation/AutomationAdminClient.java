package com.portal.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.constant.PlatformConstants;
import com.platform.common.enums.ErrorCode;
import com.platform.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/** Reads runtime identity and published scope from admin-center. Activepieces is not the caller. */
@Component
public class AutomationAdminClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;
    private final String adminBaseUrl;
    private final String serviceToken;

    public AutomationAdminClient(RestTemplate restTemplate, ObjectMapper mapper,
                                 @Value("${admin-center.url:http://localhost:8090}") String adminBaseUrl,
                                 @Value("${service.internal-token:}") String serviceToken) {
        this.restTemplate = restTemplate;
        this.mapper = mapper;
        this.adminBaseUrl = adminBaseUrl;
        this.serviceToken = serviceToken;
    }

    public Identity identity(String externalProjectId, String flowId) {
        String url = adminBaseUrl + "/api/v1/admin/internal/automation-runtime?externalProjectId="
                + encode(externalProjectId);
        if (flowId != null && !flowId.isBlank()) {
            url += "&flowId=" + encode(flowId);
        }
        JsonNode data = get(url, false);
        return new Identity(data.path("userId").asText(), data.path("displayName").asText());
    }

    public Scope scope(String externalProjectId, String flowId) {
        JsonNode data = get(adminBaseUrl + "/api/v1/admin/internal/automation-scopes?externalProjectId="
                + encode(externalProjectId) + "&flowId=" + encode(flowId), true);
        Set<String> capabilities = new LinkedHashSet<>();
        data.path("capabilities").forEach(node -> capabilities.add(node.asText()));
        return new Scope(data.path("functionUnitId").asLong(), data.path("catalogIds").path(0).asText(), capabilities);
    }

    private JsonNode get(String uri, boolean missingScopeIsForbidden) {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Service token is not configured");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(PlatformConstants.HEADER_SERVICE_TOKEN, serviceToken);
        try {
            JsonNode body = restTemplate.exchange(uri, HttpMethod.GET,
                    new HttpEntity<>(headers), JsonNode.class).getBody();
            JsonNode data = body == null ? null : body.get("data");
            if (data == null || data.isNull()) {
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Admin returned an empty automation response");
            }
            return data;
        } catch (HttpStatusCodeException ex) {
            throw translate(ex, missingScopeIsForbidden);
        } catch (RestClientException ex) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Admin automation service is unavailable");
        }
    }

    private BusinessException translate(HttpStatusCodeException ex, boolean missingScopeIsForbidden) {
        int status = ex.getStatusCode().value();
        if (status == 404 && missingScopeIsForbidden) {
            return new BusinessException(ErrorCode.FORBIDDEN, "Published scope was not found");
        }
        if (status == 503) {
            return new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, message(ex));
        }
        if (status == 403 || status == 404) {
            return new BusinessException(ErrorCode.FORBIDDEN, message(ex));
        }
        return new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Admin automation service is unavailable");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String message(HttpStatusCodeException ex) {
        try {
            JsonNode body = mapper.readTree(ex.getResponseBodyAsByteArray());
            String text = body.path("error").path("message").asText("");
            if (!text.isBlank()) {
                return text;
            }
        } catch (Exception ignored) {
            // The status code still decides the exception type.
        }
        return ex.getStatusText();
    }

    public record Identity(String userId, String displayName) {
    }

    public record Scope(long functionUnitId, String catalogId, Set<String> capabilities) {
        public boolean allows(String capability) {
            return capabilities != null && capabilities.contains(capability);
        }

        public Set<String> capabilitySet() {
            return capabilities == null ? Set.of() : capabilities;
        }
    }
}
