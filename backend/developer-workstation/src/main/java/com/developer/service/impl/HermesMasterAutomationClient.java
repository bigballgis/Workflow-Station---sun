package com.developer.service.impl;

import com.developer.exception.AiGenerationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.security.SsrfProtection;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Hermes Master 的 Automation 链路：DW → AP sync webhook → content-organizer 的 Chat 动作 → Content Organizer。
 *
 * <p>flow 按业务键（{@code metadata.hermesFlowKey}，默认 {@code hermes-master-chat}）在 AP 库里找，与引擎部署期解析
 * 同一把尺（{@link AiAutomationFlowCatalogReader#findByKey}），所以 flow 跨环境导入后不用改任何配置。
 * 提示词不在这里：它是 CO 上的 prompt setting「Hermes Master」，DW 只送它的变量（页面 + 设计摘要 + 对话转写）。</p>
 *
 * <p>flow 契约：请求 {@code {message, userInput, staffId}}；响应 {@code {reply}}，CO 失败时 {@code {error}}
 * （Chat 步骤开了 continue on failure，由 Return Response 把错误带回来）。flow 跑挂没有走到 Return Response 时
 * AP 回 204——那是"没有回复"，不是成功。</p>
 */
@Slf4j
@Component
public class HermesMasterAutomationClient {

    @Value("${ai-generation.hermes-master.automation.flow-key:hermes-master-chat}")
    private String flowKey;

    @Value("${ai-generation.hermes-master.automation.webhook-base-url:http://localhost:8086}")
    private String webhookBaseUrl;

    /** 比前端 120s 短：让 DW 先给出可读的超时，而不是让浏览器自己掐断。 */
    @Value("${ai-generation.hermes-master.automation.timeout-seconds:110}")
    private int timeoutSeconds;

    @Value("${ssrf.allowed-hosts:localhost,activepieces}")
    private List<String> ssrfAllowedHosts;

    private final AiAutomationFlowCatalogReader flowCatalog;
    private final ObjectMapper objectMapper;

    private RestTemplate restTemplate;

    public HermesMasterAutomationClient(AiAutomationFlowCatalogReader flowCatalog, ObjectMapper objectMapper) {
        this.flowCatalog = flowCatalog;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
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

    /**
     * @param message   本轮用户消息
     * @param userInput 填进 CO prompt setting 变量的整段上下文
     * @param staffId   工号，CO 用它审计
     * @return 模型回复（Markdown）
     */
    public String chat(String message, String userInput, String staffId) {
        AiAutomationFlowCatalogReader.FlowEntry flow = flowCatalog.findByKey(flowKey)
                .filter(AiAutomationFlowCatalogReader.FlowEntry::published)
                .orElseThrow(() -> new AiGenerationException("HERMES_MASTER_FLOW_NOT_FOUND",
                        "Hermes Master's Automation flow '" + flowKey + "' is not imported or not published "
                                + "in this environment"));
        String url = webhookUrl(flow.flowId());
        SsrfProtection.validate(url, allowedHosts());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("userInput", userInput);
        body.put("staffId", staffId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(URI.create(url), new HttpEntity<>(body, headers), String.class);
        } catch (ResourceAccessException e) {
            // 读超时与连不上要分开说：后者多半是 webhook-base-url 配错，"请重试"会把排查带偏
            if (e.getCause() instanceof SocketTimeoutException) {
                throw new AiGenerationException("HERMES_MASTER_TIMEOUT",
                        "Hermes Master did not answer within " + timeoutSeconds + "s, please try again");
            }
            log.warn("Hermes Master could not reach Automation at {}: {}", webhookBaseUrl, e.getMessage());
            throw new AiGenerationException("HERMES_MASTER_UNREACHABLE",
                    "Hermes Master could not reach Automation; check ACTIVEPIECES_WEBHOOK_BASE_URL");
        }

        int status = response.getStatusCode().value();
        if (status == 204) {
            throw new AiGenerationException("HERMES_MASTER_FLOW_NO_RESPONSE",
                    "Hermes Master's Automation flow ended without a reply; check its latest run in Automation");
        }
        Map<String, Object> result = parse(response.getBody());
        if (status < 200 || status >= 300 || result == null) {
            throw new AiGenerationException("HERMES_MASTER_FLOW_FAILED",
                    "Automation answered HTTP " + status + ": " + truncate(response.getBody()));
        }
        if (result.get("reply") instanceof String reply && !reply.isBlank()) {
            return reply;
        }
        String error = errorMessage(result.get("error"));
        if (error != null) {
            throw new AiGenerationException("HERMES_MASTER_FLOW_FAILED", "Content Organizer failed: " + truncate(error));
        }
        throw new AiGenerationException("AI_GATEWAY_EMPTY_RESPONSE", "Hermes Master returned an empty reply");
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

    private Set<String> allowedHosts() {
        return ssrfAllowedHosts.stream()
                .map(h -> h.trim().toLowerCase())
                .filter(h -> !h.isEmpty())
                .collect(Collectors.toSet());
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 297) + "..." : s;
    }
}
