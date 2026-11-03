package com.developer.service.impl;

import com.developer.exception.AiGenerationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.security.SsrfProtection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DW → AP sync webhook → content-organizer 某个对话动作 → Content Organizer 的同一条链路，供 Hermes Master
 * 与 AI Studio 共用；两者只差 flow 业务键、超时、错误码前缀与请求体。
 *
 * <p>flow 按业务键（{@code metadata.hermesFlowKey}）在 AP 库里找，与引擎部署期解析同一把尺
 * （{@link AiAutomationFlowCatalogReader#findByKey}），所以 flow 跨环境导入后不用改任何配置。</p>
 *
 * <p>flow 契约：响应 {@code {reply}}，CO 失败时 {@code {error}}（对话步骤开了 continue on failure，由 Return Response
 * 把错误带回来）。flow 跑挂没有走到 Return Response 时 AP 回 204——那是"没有回复"，不是成功。</p>
 */
@Slf4j
final class AutomationSyncWebhook {

    private final String name;
    private final String codePrefix;
    private final AiAutomationFlowCatalogReader flowCatalog;
    private final ObjectMapper objectMapper;
    private final String flowKey;
    private final String webhookBaseUrl;
    private final int timeoutSeconds;
    private final Set<String> allowedHosts;
    private final RestTemplate restTemplate;

    /**
     * @param name       出现在错误文案里的调用方名字，如 "Hermes Master"
     * @param codePrefix 错误码前缀，如 {@code HERMES_MASTER} → {@code HERMES_MASTER_TIMEOUT}
     */
    AutomationSyncWebhook(String name, String codePrefix, AiAutomationFlowCatalogReader flowCatalog,
                          ObjectMapper objectMapper, String flowKey, String webhookBaseUrl, int timeoutSeconds,
                          List<String> ssrfAllowedHosts) {
        this.name = name;
        this.codePrefix = codePrefix;
        this.flowCatalog = flowCatalog;
        this.objectMapper = objectMapper;
        this.flowKey = flowKey;
        this.webhookBaseUrl = webhookBaseUrl;
        this.timeoutSeconds = timeoutSeconds;
        this.allowedHosts = ssrfAllowedHosts.stream()
                .map(h -> h.trim().toLowerCase())
                .filter(h -> !h.isEmpty())
                .collect(Collectors.toSet());

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(timeoutSeconds * 1000);
        this.restTemplate = new RestTemplate(factory);
        // 非 2xx 的响应体要原样拿来报错，不让 RestTemplate 自己抛
        this.restTemplate.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }

            @Override
            public void handleError(ClientHttpResponse response) {
                // never reached: hasError 恒为 false
            }
        });
    }

    /** 发送请求体，返回 flow 的 {@code reply}；其余一切情况各以自己的错误码失败。 */
    String call(Map<String, Object> body) {
        AiAutomationFlowCatalogReader.FlowEntry flow = flowCatalog.findByKey(flowKey)
                .filter(AiAutomationFlowCatalogReader.FlowEntry::published)
                .orElseThrow(() -> new AiGenerationException(codePrefix + "_FLOW_NOT_FOUND",
                        name + "'s Automation flow '" + flowKey + "' is not imported or not published "
                                + "in this environment"));
        String url = webhookUrl(flow.flowId());
        SsrfProtection.validate(url, allowedHosts);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(URI.create(url), new HttpEntity<>(body, headers), String.class);
        } catch (ResourceAccessException e) {
            // 读超时与连不上要分开说：后者多半是 webhook-base-url 配错，"请重试"会把排查带偏
            if (e.getCause() instanceof SocketTimeoutException) {
                throw new AiGenerationException(codePrefix + "_TIMEOUT",
                        name + " did not answer within " + timeoutSeconds + "s, please try again");
            }
            log.warn("{} could not reach Automation at {}: {}", name, webhookBaseUrl, e.getMessage());
            throw new AiGenerationException(codePrefix + "_UNREACHABLE",
                    name + " could not reach Automation; check ACTIVEPIECES_WEBHOOK_BASE_URL");
        }

        int status = response.getStatusCode().value();
        if (status == 204) {
            throw new AiGenerationException(codePrefix + "_FLOW_NO_RESPONSE",
                    name + "'s Automation flow ended without a reply; check its latest run in Automation");
        }
        Map<String, Object> result = parse(response.getBody());
        if (status < 200 || status >= 300 || result == null) {
            throw new AiGenerationException(codePrefix + "_FLOW_FAILED",
                    "Automation answered HTTP " + status + ": " + truncate(response.getBody()));
        }
        if (result.get("reply") instanceof String reply && !reply.isBlank()) {
            return reply;
        }
        String error = errorMessage(result.get("error"));
        if (error != null) {
            throw new AiGenerationException(codePrefix + "_FLOW_FAILED", "Content Organizer failed: " + truncate(error));
        }
        throw new AiGenerationException("AI_GATEWAY_EMPTY_RESPONSE", name + " returned an empty reply");
    }

    private String webhookUrl(String flowId) {
        String base = webhookBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/api/v1/webhooks/" + flowId + "/sync";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            Object parsed = objectMapper.readValue(raw, Object.class);
            return parsed instanceof Map ? (Map<String, Object>) parsed : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 取出 piece 异常的纯文本；成功时 flow 送回空串，视为没有错误。
     *
     * <p>AP 包了两层（dev 实测）：{@code {"message": "{\"__apErrorVersion\":1,\"message\":\"...\",\"raw\":\"<stack>\"}"}}，
     * 所以按 "字符串是 JSON 就解开、取 message" 一直剥到纯文本为止。</p>
     */
    private String errorMessage(Object error) {
        Object value = error;
        for (int depth = 0; depth < 3; depth++) {
            if (value instanceof String text) {
                Map<String, Object> wrapped = parse(text);
                if (wrapped == null) {
                    return text.isBlank() ? null : text;
                }
                value = wrapped;
            }
            if (!(value instanceof Map<?, ?> map)) {
                break;
            }
            value = map.get("message");
        }
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 297) + "..." : s;
    }
}
