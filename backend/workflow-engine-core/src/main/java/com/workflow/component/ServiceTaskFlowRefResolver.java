package com.workflow.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

/**
 * 部署期 flowId 解析（DECISIONS Q7）。
 *
 * <p>FU 的 BPMN 携带的是<b>源环境</b>的 {@code ap:flowId}；flow 本体经 admin-center
 * 的 flow 迁移通道导入本环境后拿到新 id，映射记在目标 flow 的
 * {@code metadata.hermesFlowKey}。本组件在 BPMN 部署期调 admin-center 的
 * {@code /automation/flows/resolve}（C-3 X-Service-Token 门禁）把引用换成本环境
 * 实际 flowId——BPMN 保持环境可携带，部署产物落环境实值。</p>
 *
 * <p>{@code resolve-url} 或 {@code service.internal-token} 未配置 ⇒ 解析关闭
 * （{@link Outcome#UNAVAILABLE}），部署按原引用继续（与既有行为一致）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceTaskFlowRefResolver {

    public enum Outcome { RESOLVED, NOT_FOUND, UNAVAILABLE }

    public record Resolution(Outcome outcome, String flowId) {
        static Resolution resolved(String flowId) {
            return new Resolution(Outcome.RESOLVED, flowId);
        }
        static Resolution notFound() {
            return new Resolution(Outcome.NOT_FOUND, null);
        }
        static Resolution unavailable() {
            return new Resolution(Outcome.UNAVAILABLE, null);
        }
    }

    private final RestTemplate restTemplate;

    /** admin-center resolve 端点；空 = 解析关闭 */
    @Value("${service-task.flow-resolve-url:}")
    private String resolveUrl;

    /** C-3 服务间共享密钥（与 admin-center 的 SERVICE_INTERNAL_TOKEN 同值） */
    @Value("${service.internal-token:}")
    private String serviceInternalToken;

    public Resolution resolve(String flowRef) {
        if (resolveUrl == null || resolveUrl.isBlank()
                || serviceInternalToken == null || serviceInternalToken.isBlank()) {
            return Resolution.unavailable();
        }
        String url = UriComponentsBuilder.fromUriString(resolveUrl)
                .queryParam("ref", flowRef)
                .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Service-Token", serviceInternalToken);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
            Object data = response.getBody() != null ? response.getBody().get("data") : null;
            Object flowId = data instanceof Map<?, ?> map ? map.get("flowId") : null;
            if (response.getStatusCode().is2xxSuccessful() && flowId != null
                    && !flowId.toString().isBlank()) {
                return Resolution.resolved(flowId.toString());
            }
            log.warn("Flow ref resolve returned no flowId for '{}' (HTTP {})",
                    flowRef, response.getStatusCode());
            return Resolution.unavailable();
        } catch (HttpClientErrorException.NotFound e) {
            return Resolution.notFound();
        } catch (RestClientException e) {
            log.warn("Flow ref resolve unavailable for '{}': {}", flowRef, e.getMessage());
            return Resolution.unavailable();
        }
    }
}
