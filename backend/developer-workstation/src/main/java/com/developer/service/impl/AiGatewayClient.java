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

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 集团 AI gateway 客户端（OpenAI 兼容 {@code /chat/completions}）。
 *
 * <p>这是原 Activepieces flow 里 "Send Http request" 步骤的 Java 版：一次非流式 POST，
 * {@code Authorization: Bearer <AMToken>}，body 为标准 OpenAI 单条 user message。
 * AMToken 是**每用户**的浏览器侧 DSP token，由前端随 chat 请求透传进来（见
 * {@code AiGenerationController#chatStream}），因此调用在 gateway 侧审计到人。</p>
 *
 * <p>返回值刻意做成 {@code {status, body}} —— 与 AP HTTP piece 的输出同形，
 * 好让 {@link AiResponseParser} 逐字复用 {@code GenAI/parse_response.md} 的解析逻辑。</p>
 */
@Slf4j
@Component
public class AiGatewayClient {

    /** OpenAI 兼容 chat/completions 端点全 URL（模型名已在路径里，故 body 默认不发 model）。 */
    @Value("${ai-generation.gateway.url:}")
    private String gatewayUrl;

    /** 可选：body 里的 model 字段。留空则不发——URL 路径已经选定了模型。 */
    @Value("${ai-generation.gateway.model:}")
    private String model;

    /** 与原 AP 链路一致的 300s：推理模型生成 DESIGN 文档实测可达 ~230s。 */
    @Value("${ai-generation.gateway.timeout-seconds:300}")
    private int timeoutSeconds;

    @Value("${ssrf.allowed-hosts:localhost,activepieces}")
    private List<String> ssrfAllowedHosts;

    private final ObjectMapper objectMapper;

    private RestTemplate restTemplate;

    public AiGatewayClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMs = timeoutSeconds * 1000;
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.restTemplate = new RestTemplate(factory);
        // 4xx/5xx 的响应体要交给 AiResponseParser 提取 gateway 的错误文案，
        // 所以这里不让 RestTemplate 自己抛——状态码原样带出去，由 parser 显式失败。
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

        if (gatewayUrl == null || gatewayUrl.isBlank()) {
            // AI Generate 是可关功能（ai-generation.enabled），未配置 gateway 不应拖垮 DW 启动；
            // 真正调用时 fail-closed 抛 AI_GATEWAY_NOT_CONFIGURED，不会静默降级。
            log.warn("AI gateway URL is not configured (ai-generation.gateway.url); "
                    + "AI Generate will fail with AI_GATEWAY_NOT_CONFIGURED until it is set");
            return;
        }
        SsrfProtection.validate(gatewayUrl, allowedHosts());
        log.info("Initialized AI gateway client: host={}, timeout={}ms, model={}",
                hostOf(gatewayUrl), timeoutMs, model == null || model.isBlank() ? "(from URL path)" : model);
    }

    /**
     * 发起一次 chat completion。
     *
     * @param prompt  {@link AiPromptBuilder} 渲染好的完整 prompt
     * @param amToken 该用户的 AMToken，作 Bearer 凭证；缺失即失败，不做匿名调用
     * @return {@code {status: Integer, body: Map}}，交给 {@link AiResponseParser}
     */
    public Map<String, Object> chat(String prompt, String amToken) {
        if (gatewayUrl == null || gatewayUrl.isBlank()) {
            throw new AiGenerationException("AI_GATEWAY_NOT_CONFIGURED",
                    "AI gateway URL is not configured (ai-generation.gateway.url / GROUP_AI_GATEWAY_URL)");
        }
        if (amToken == null || amToken.isBlank()) {
            throw new AiGenerationException("AI_GATEWAY_TOKEN_MISSING",
                    "Missing AMToken for the AI gateway call; sign in again so the browser can supply it");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(amToken);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(buildRequestBody(prompt), headers);

        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(URI.create(gatewayUrl), entity, String.class);
        } catch (ResourceAccessException e) {
            throw new AiGenerationException("AI_WEBHOOK_TIMEOUT", "AI gateway call timed out: " + e.getMessage());
        } catch (Exception e) {
            throw new AiGenerationException("AI_WEBHOOK_CALL_FAILED", "AI gateway call failed: " + e.getMessage());
        }

        int status = response.getStatusCode().value();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("body", parseBody(response.getBody(), status));
        log.info("AI gateway responded: status={}, promptChars={}", status, prompt.length());
        return result;
    }

    private Map<String, Object> buildRequestBody(String prompt) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> body = new LinkedHashMap<>();
        if (model != null && !model.isBlank()) {
            body.put("model", model.trim());
        }
        body.put("messages", List.of(message));
        return body;
    }

    /** 响应体必须是 JSON 对象；HTML 错误页 / 空体一律显式失败，避免把网关噪音当成模型回答。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBody(String raw, int status) {
        if (raw == null || raw.isBlank()) {
            throw new AiGenerationException("AI_GATEWAY_BAD_RESPONSE",
                    "AI gateway returned an empty body with HTTP " + status);
        }
        try {
            Object parsed = objectMapper.readValue(raw, Object.class);
            if (parsed instanceof Map) {
                return (Map<String, Object>) parsed;
            }
        } catch (Exception e) {
            // 落到下面统一报错，附上截断的原文便于在集群里定位是网关还是代理返回的
        }
        throw new AiGenerationException("AI_GATEWAY_BAD_RESPONSE",
                "AI gateway returned a non-JSON body with HTTP " + status + ": " + truncate(raw));
    }

    private Set<String> allowedHosts() {
        return ssrfAllowedHosts.stream()
                .map(h -> h.trim().toLowerCase())
                .filter(h -> !h.isEmpty())
                .collect(Collectors.toSet());
    }

    private static String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return "(unparseable)";
        }
    }

    private static String truncate(String s) {
        return s.length() > 300 ? s.substring(0, 297) + "..." : s;
    }
}
