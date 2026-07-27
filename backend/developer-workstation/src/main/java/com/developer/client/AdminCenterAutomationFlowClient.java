package com.developer.client;

import com.developer.exception.DeveloperBusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.constant.PlatformConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Automation flow 随 FU 导出包迁移的服务间通道（DW → admin-center）。
 *
 * <p>flow 本体存在 Activepieces,不在 DW 库里；导出时按 BPMN 的 {@code ap:flowId} 取
 * 可携带 JSON 打进 ZIP，导入时在目标环境补齐缺失的 flow。写 AP 一律由 admin-center
 * 经 AP API 完成（DECISIONS Q5/Q7），DW 只是调用方。</p>
 *
 * <p>门禁与引擎的部署期 flowId 解析同款：C-3 的 {@code X-Service-Token}。未配置
 * token ⇒ 通道不可用，此时若 BPMN 确实引用了 flow 就显式报错——导出一个悄悄少了
 * 自动化的包，正是本类要修的 bug。</p>
 */
@Slf4j
@Component
public class AdminCenterAutomationFlowClient {

    /** admin-center 的 context-path 是 {@code /api/v1/admin}（与引擎的 flow-resolve-url 同前缀） */
    private static final String FLOWS_PATH = "/api/v1/admin/automation/flows";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String adminBaseUrl;
    private final String serviceInternalToken;

    public AdminCenterAutomationFlowClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${admin-center.url:http://localhost:8090}") String adminBaseUrl,
            @Value("${service.internal-token:}") String serviceInternalToken) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.adminBaseUrl = adminBaseUrl != null && adminBaseUrl.endsWith("/")
                ? adminBaseUrl.substring(0, adminBaseUrl.length() - 1)
                : adminBaseUrl;
        this.serviceInternalToken = serviceInternalToken;
    }

    /** 通道是否可用（admin-center 地址 + 服务密钥都已配置） */
    public boolean isEnabled() {
        return adminBaseUrl != null && !adminBaseUrl.isBlank()
                && serviceInternalToken != null && !serviceInternalToken.isBlank();
    }

    /**
     * 取 flow 的可携带 JSON。
     *
     * @param flowRef BPMN 里的 {@code ap:flowId}
     * @return 导出 JSON 原文；本环境查无此 flow 时 empty
     * @throws DeveloperBusinessException 通道未配置或 admin-center 不可达
     */
    public Optional<byte[]> exportFlow(String flowRef) {
        assertEnabled();
        String url = UriComponentsBuilder.fromUriString(adminBaseUrl + FLOWS_PATH + "/internal/export")
                .queryParam("ref", flowRef)
                .toUriString();
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(serviceHeaders(null)), byte[].class);
            byte[] body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null || body.length == 0) {
                throw new DeveloperBusinessException("AP_FLOW_EXPORT_FAILED",
                        "Admin Center returned no automation flow payload for '" + flowRef
                                + "' (HTTP " + response.getStatusCode() + ")");
            }
            return Optional.of(body);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new DeveloperBusinessException("AP_FLOW_EXPORT_FAILED",
                    "Unable to reach Admin Center to export automation flow '" + flowRef + "': "
                            + e.getMessage());
        }
    }

    /**
     * 在本环境补齐导入包携带的 flow（已存在的不覆盖）。
     *
     * @param flowExports 包里的 flow 导出 JSON
     * @return 每个 flow 的还原结果（flowKey / displayName / flowId / status / detail）
     * @throws DeveloperBusinessException 通道未配置、admin-center 不可达或还原被拒
     */
    public List<Map<String, Object>> restoreFlows(List<Map<String, Object>> flowExports) {
        if (flowExports == null || flowExports.isEmpty()) {
            return List.of();
        }
        assertEnabled();
        String url = adminBaseUrl + FLOWS_PATH + "/internal/restore";
        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of("flows", flowExports));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new DeveloperBusinessException("AP_FLOW_RESTORE_FAILED",
                    "Automation flow payload in the package is not serializable: " + e.getMessage());
        }
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST,
                    new HttpEntity<>(body, serviceHeaders(MediaType.APPLICATION_JSON)),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Object data = response.getBody() != null ? response.getBody().get("data") : null;
            if (!response.getStatusCode().is2xxSuccessful() || !(data instanceof List<?> list)) {
                throw new DeveloperBusinessException("AP_FLOW_RESTORE_FAILED",
                        "Admin Center rejected the automation flow restore (HTTP "
                                + response.getStatusCode() + ")");
            }
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> typed = (Map<String, Object>) item;
                        return typed;
                    })
                    .toList();
        } catch (HttpClientErrorException e) {
            throw new DeveloperBusinessException("AP_FLOW_RESTORE_FAILED",
                    "Admin Center rejected the automation flow restore: "
                            + new String(e.getResponseBodyAsByteArray(), StandardCharsets.UTF_8));
        } catch (RestClientException e) {
            throw new DeveloperBusinessException("AP_FLOW_RESTORE_FAILED",
                    "Unable to reach Admin Center to restore automation flows: " + e.getMessage());
        }
    }

    private void assertEnabled() {
        if (!isEnabled()) {
            throw new DeveloperBusinessException("AP_FLOW_CHANNEL_UNAVAILABLE",
                    "Automation flow migration channel is not configured "
                            + "(admin-center.url / service.internal-token)");
        }
    }

    private HttpHeaders serviceHeaders(MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(PlatformConstants.HEADER_SERVICE_TOKEN, serviceInternalToken);
        if (contentType != null) {
            headers.setContentType(contentType);
        }
        return headers;
    }
}
