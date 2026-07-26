package com.workflow.client;

import com.platform.common.util.SafeUrlInput;
import com.workflow.config.RestTemplateConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads uploaded file bytes from developer-workstation for Send Email attachments.
 */
@Slf4j
@Component
public class DeveloperWorkstationFileClient {

    private static final Pattern UPLOAD_PATH = Pattern.compile(
            ".*/upload/files/([^/?#]+)(?:\\?([^#]*))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORIGINAL_NAME = Pattern.compile(
            "(?:^|&)originalName=([^&]*)", Pattern.CASE_INSENSITIVE);

    private final RestTemplate restTemplate;

    @Value("${file-service.base-url:http://localhost:8083}")
    private String developerWorkstationBaseUrl;

    public DeveloperWorkstationFileClient(
            @Qualifier(RestTemplateConfig.INTERNAL_API_REST_TEMPLATE) RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Optional<DownloadedFile> downloadByStoredUrl(String storedUrl) {
        if (!StringUtils.hasText(storedUrl)) {
            return Optional.empty();
        }
        Matcher matcher = UPLOAD_PATH.matcher(storedUrl.trim());
        if (!matcher.find()) {
            log.warn("Not a platform upload URL; skip attachment download");
            return Optional.empty();
        }
        String storedName = matcher.group(1);
        String query = matcher.group(2);
        String originalName = extractOriginalName(query).orElse(storedName);
        try {
            String base = trimTrailingSlash(developerWorkstationBaseUrl);
            String url = base + "/api/v1/upload/files/" + SafeUrlInput.requirePathToken(storedName);
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    URI.create(url), HttpMethod.GET, null, byte[].class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Optional.empty();
            }
            return Optional.of(new DownloadedFile(originalName, response.getBody()));
        } catch (Exception e) {
            // FALLBACK(external): download failure returns empty; caller decides skip vs fail.
            log.error("Failed to download uploaded file {}: {}", storedName, e.getMessage());
            return Optional.empty();
        }
    }

    private static Optional<String> extractOriginalName(String query) {
        if (!StringUtils.hasText(query)) {
            return Optional.empty();
        }
        Matcher m = ORIGINAL_NAME.matcher(query);
        if (!m.find()) {
            return Optional.empty();
        }
        try {
            String decoded = java.net.URLDecoder.decode(m.group(1), StandardCharsets.UTF_8);
            return StringUtils.hasText(decoded) ? Optional.of(decoded.trim()) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String trimTrailingSlash(String base) {
        if (base == null || base.isEmpty()) {
            return "";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public record DownloadedFile(String fileName, byte[] content) {}
}
