package com.developer.client;

import com.developer.exception.DeveloperBusinessException;
import com.platform.common.constant.PlatformConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Optional;

/**
 * Automation flow 解析的服务间通道（DW → admin-center）。
 *
 * <p>FR-B12/B13 之后 FU 包不再携带 flow 本体：flow 一律经 DW Automation 页的迁移通道
 * 单独导入。本客户端只剩<b>导入前校验</b>一件事——把 BPMN 里的 flow 引用
 * （{@code ap:flowKey} 业务键 / legacy {@code ap:flowId}）拿到 admin-center 的
 * {@code /automation/flows/resolve} 验证本环境可解析（且已发布，FR-C05），
 * 解析不到就让导入显式失败，而不是落一个 service task 空转的半残 FU。</p>
 *
 * <p>门禁与引擎的部署期 flowId 解析同款：C-3 的 {@code X-Service-Token}。未配置
 * token ⇒ 通道不可用——BPMN 确实引用了 flow 时必须显式报错（NFR-304 不静默）。</p>
 */
@Slf4j
@Component
public class AdminCenterAutomationFlowClient {

    /** admin-center 的 context-path 是 {@code /api/v1/admin}（与引擎的 flow-resolve-url 同前缀） */
    private static final String FLOWS_PATH = "/api/v1/admin/automation/flows";

    private final RestTemplate restTemplate;
    private final String adminBaseUrl;
    private final String serviceInternalToken;

    public AdminCenterAutomationFlowClient(
            RestTemplate restTemplate,
            @Value("${admin-center.url:http://localhost:8090}") String adminBaseUrl,
            @Value("${service.internal-token:}") String serviceInternalToken) {
        this.restTemplate = restTemplate;
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
     * 解析 flow 引用：本环境存在（且已发布）时返回实际 flowId。
     *
     * @param flowRef BPMN 里的 {@code ap:flowKey} 业务键或 legacy {@code ap:flowId}
     * @return 本环境实际 flowId；解析不到（含 flow 未发布）时 empty
     * @throws DeveloperBusinessException 通道未配置或 admin-center 不可达（不静默降级）
     */
    public Optional<String> resolveFlow(String flowRef) {
        assertEnabled();
        String url = UriComponentsBuilder.fromUriString(adminBaseUrl + FLOWS_PATH + "/resolve")
                .queryParam("ref", flowRef)
                .toUriString();
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(serviceHeaders()),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Object data = response.getBody() != null ? response.getBody().get("data") : null;
            Object flowId = data instanceof Map<?, ?> map ? map.get("flowId") : null;
            if (response.getStatusCode().is2xxSuccessful() && flowId != null
                    && !flowId.toString().isBlank()) {
                return Optional.of(flowId.toString());
            }
            throw new DeveloperBusinessException("AP_FLOW_RESOLVE_FAILED",
                    "Admin Center returned no flowId for automation flow reference '" + flowRef
                            + "' (HTTP " + response.getStatusCode() + ")");
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new DeveloperBusinessException("AP_FLOW_RESOLVE_FAILED",
                    "Unable to reach Admin Center to resolve automation flow '" + flowRef + "': "
                            + e.getMessage());
        }
    }

    private void assertEnabled() {
        if (!isEnabled()) {
            throw new DeveloperBusinessException("AP_FLOW_CHANNEL_UNAVAILABLE",
                    "Automation flow resolution channel is not configured "
                            + "(admin-center.url / service.internal-token)");
        }
    }

    private HttpHeaders serviceHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(PlatformConstants.HEADER_SERVICE_TOKEN, serviceInternalToken);
        return headers;
    }
}
