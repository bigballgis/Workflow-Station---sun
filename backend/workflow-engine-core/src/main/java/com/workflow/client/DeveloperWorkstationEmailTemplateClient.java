package com.workflow.client;

import com.platform.common.util.SafeUrlInput;
import com.workflow.config.RestTemplateConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * Fetches Email Template subject/body from developer-workstation for Send Email runtime.
 */
@Slf4j
@Component
public class DeveloperWorkstationEmailTemplateClient {

    private final RestTemplate restTemplate;

    @Value("${file-service.base-url:http://localhost:8083}")
    private String developerWorkstationBaseUrl;

    public DeveloperWorkstationEmailTemplateClient(
            @Qualifier(RestTemplateConfig.INTERNAL_API_REST_TEMPLATE) RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Optional<EmailTemplateContent> getTemplate(String functionUnitId, String templateId) {
        if (!StringUtils.hasText(functionUnitId) || !StringUtils.hasText(templateId)) {
            return Optional.empty();
        }
        try {
            String base = trimTrailingSlash(developerWorkstationBaseUrl);
            String url = base + "/api/v1/internal/function-units/"
                    + SafeUrlInput.requirePathToken(functionUnitId)
                    + "/email-templates/"
                    + SafeUrlInput.requirePathToken(templateId);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Optional.empty();
            }
            Map<String, Object> body = response.getBody();
            String subject = body.get("subject") != null ? body.get("subject").toString() : "";
            String bodyHtml = body.get("bodyHtml") != null ? body.get("bodyHtml").toString() : "";
            return Optional.of(new EmailTemplateContent(subject, bodyHtml));
        } catch (Exception e) {
            // FALLBACK(external): template lookup failure surfaces to SendEmailTaskDelegate as missing template.
            log.error("Failed to load email template {} for functionUnit {}: {}",
                    templateId, functionUnitId, e.getMessage());
            return Optional.empty();
        }
    }

    private static String trimTrailingSlash(String base) {
        if (base == null || base.isEmpty()) {
            return "";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public record EmailTemplateContent(String subject, String bodyHtml) {}
}
