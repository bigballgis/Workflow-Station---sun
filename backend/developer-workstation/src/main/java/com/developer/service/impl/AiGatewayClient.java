package com.developer.service.impl;

import com.developer.exception.AiGenerationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.security.SsrfProtection;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 集团 AI gateway 客户端（OpenAI 兼容 {@code /chat/completions}）。
 *
 * <p>这是原 Activepieces flow 里 "Send Http request" 步骤的 Java 版：一次非流式 POST，
 * {@code Authorization: Bearer <AMToken>}，body 为标准 OpenAI 两条 message——相位提示词走
 * {@code system}，会话上下文与当前用户消息走 {@code user}。
 * AMToken 是**每用户**的浏览器侧 DSP token，由前端随 chat 请求透传进来（见
 * {@code AiGenerationController#chatStream}），因此调用在 gateway 侧审计到人。</p>
 *
 * <p>唯一的例外是本地自测：配了 {@code ai-generation.gateway.api-key}（{@code AI_GATEWAY_API_KEY}）
 * 就改用这把静态 key 作 Bearer，用来直连 DeepSeek 这类认固定 key 的 OpenAI 兼容端点。
 * 代价是审计不到人，所以 dev 之外一律留空——留空即原有链路，逐字不变。</p>
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

    /**
     * 可选的静态 API key（Bearer 凭证）。**配了就顶掉每用户的 AMToken**。
     *
     * <p>存在的唯一理由是本地/自测：DeepSeek 这类 OpenAI 兼容服务认的是一把固定 key，
     * 而集团 gateway 认的是浏览器带上来的 DSP AMToken，本机拿不到后者。</p>
     *
     * <p>代价必须写明：整个实例的模型调用都会记到这把 key 名下，gateway 侧**审计不到人**。
     * 所以 dev 之外的环境一律留空——留空即回落到原有的每用户 AMToken 链路，行为逐字不变。
     * 启动时会 warn 一次，让"当前在用共享 key"在日志里一眼可见，而不是靠翻配置反推。</p>
     */
    @Value("${ai-generation.gateway.api-key:}")
    private String apiKey;

    /**
     * 是否以 SSE 流式发起模型调用（响应仍在服务端拼回整段，对上层不可见）。
     *
     * <p>默认 false = 集团 gateway 的既有行为，逐字不变。置 true 是给"长请求会被掐连接"的端点用的：
     * DeepSeek 非流式下超过约一分钟就回 EOF（{@code AI_WEBHOOK_TIMEOUT: Premature EOF}），
     * 而 GENERATION 阶段几乎必然超过这个时长——短的需求/设计阶段却能成功，所以现象很像偶发抖动。</p>
     */
    @Value("${ai-generation.gateway.stream:false}")
    private boolean streamEnabled;

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
        log.info("Initialized AI gateway client: host={}, timeout={}ms, model={}, credential={}",
                hostOf(gatewayUrl), timeoutMs, model == null || model.isBlank() ? "(from URL path)" : model,
                staticApiKey() != null ? "static api key" : "per-user AMToken");
        if (staticApiKey() != null) {
            log.warn("AI gateway is using the static ai-generation.gateway.api-key (AI_GATEWAY_API_KEY): every model "
                    + "call on this instance is attributed to that shared key, so the gateway cannot audit per user. "
                    + "Intended for local development against an OpenAI-compatible endpoint; leave it empty elsewhere "
                    + "to keep the per-user AMToken path.");
        }
    }

    /** 静态 key 的规范化取值：留空/全空白视为未配置。key 本身绝不进日志。 */
    private String staticApiKey() {
        return apiKey != null && !apiKey.isBlank() ? apiKey.trim() : null;
    }

    /**
     * 发起一次 chat completion。
     *
     * @param prompt  {@link AiPromptBuilder} 渲染好的 system / user 两段
     * @param amToken 该用户的 AMToken，作 Bearer 凭证；缺失即失败，不做匿名调用。
     *                仅当配了静态 {@code ai-generation.gateway.api-key} 时才被顶掉（本地自测用）
     * @return {@code {status: Integer, body: Map}}，交给 {@link AiResponseParser}
     */
    public Map<String, Object> chat(AiPromptBuilder.RenderedPrompt prompt, String amToken) {
        if (gatewayUrl == null || gatewayUrl.isBlank()) {
            throw new AiGenerationException("AI_GATEWAY_NOT_CONFIGURED",
                    "AI gateway URL is not configured (ai-generation.gateway.url / GROUP_AI_GATEWAY_URL)");
        }
        // 静态 key 优先于 AMToken：配了它就是明确要求"这台实例用固定凭证打一个 OpenAI 兼容端点"，
        // 此时浏览器有没有 AMToken 都无关紧要（本地 DW 常常带着一个对 DeepSeek 毫无意义的 cookie）。
        String credential = staticApiKey();
        if (credential == null && (amToken == null || amToken.isBlank())) {
            throw new AiGenerationException("AI_GATEWAY_TOKEN_MISSING",
                    "Missing AMToken for the AI gateway call; sign in again so the browser can supply it "
                            + "(or set ai-generation.gateway.api-key / AI_GATEWAY_API_KEY for a local endpoint)");
        }
        if (credential == null) {
            credential = amToken;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(streamEnabled
                ? List.of(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                : List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(credential);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(buildRequestBody(prompt), headers);

        ResponseEntity<String> response;
        try {
            response = streamEnabled
                    ? collapseStream(entity)
                    : restTemplate.postForEntity(URI.create(gatewayUrl), entity, String.class);
        } catch (ResourceAccessException e) {
            throw new AiGenerationException("AI_WEBHOOK_TIMEOUT", "AI gateway call timed out: " + e.getMessage());
        } catch (Exception e) {
            throw new AiGenerationException("AI_WEBHOOK_CALL_FAILED", "AI gateway call failed: " + e.getMessage());
        }

        int status = response.getStatusCode().value();
        if (status == 401 || status == 403) {
            throw rejectedCredential(status, response.getBody());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("body", parseBody(response.getBody(), status));
        log.info("AI gateway responded: status={}, systemChars={}, userChars={}, stream={}",
                status, prompt.system().length(), prompt.user().length(), streamEnabled);
        return result;
    }

    /**
     * 以 SSE 流式发起调用，再把增量拼回成一个非流式响应体交给上层。
     *
     * <p>为什么必须有这条路：DeepSeek（v4-pro 这类推理模型尤甚）在非流式下会把长请求的连接直接掐断——
     * 实测同一份 GENERATION prompt，非流式约 61s 收到 0 字节 EOF，流式则跑满 131s、完整吐出 45900 字符。
     * Java 侧的表现就是 {@code AI_WEBHOOK_TIMEOUT: ... Premature EOF}，且短请求（需求/设计阶段）
     * 照样成功，于是很容易误判成"偶发网络抖动"。流式的意义在于让字节持续流动，中间环节不再认为连接空闲。</p>
     *
     * <p>拼装结果刻意伪装成非流式信封 {@code {choices:[{message:{content}}], usage}}，
     * 这样 {@link AiResponseParser} 一行都不用改——流式与否只是传输方式，不是契约差异。
     * {@code reasoning_content} 增量被丢弃：那是模型的思考过程，不属于回答。</p>
     */
    private ResponseEntity<String> collapseStream(HttpEntity<Map<String, Object>> entity) {
        return restTemplate.execute(URI.create(gatewayUrl), HttpMethod.POST,
                request -> {
                    request.getHeaders().addAll(entity.getHeaders());
                    objectMapper.writeValue(request.getBody(), entity.getBody());
                },
                response -> {
                    int status = response.getStatusCode().value();
                    String raw = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    // 错误响应不是 SSE，原样带出去交给既有的 parseBody / rejectedCredential。
                    if (status >= 400) {
                        return ResponseEntity.status(status).body(raw);
                    }
                    return ResponseEntity.status(status).body(mergeSseChunks(raw));
                });
    }

    /** 把 SSE 文本合并成非流式 JSON 信封；非 {@code data:} 行、心跳、{@code [DONE]} 一律跳过。 */
    private String mergeSseChunks(String sse) throws java.io.IOException {
        StringBuilder content = new StringBuilder();
        Object usage = null;
        String finishReason = null;
        int chunks = 0;

        for (String line : sse.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("data:")) {
                continue;
            }
            String payload = trimmed.substring("data:".length()).trim();
            if (payload.isEmpty() || "[DONE]".equals(payload)) {
                continue;
            }
            Map<String, Object> chunk;
            try {
                chunk = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() { });
            } catch (Exception e) {
                // 单个块坏掉不该毁掉整次生成；但要留痕，否则内容缺一段是查不出来的。
                log.warn("Skipping an unparsable SSE chunk from the AI gateway: {}", truncate(payload));
                continue;
            }
            chunks++;
            if (chunk.get("usage") != null) {
                usage = chunk.get("usage");
            }
            if (!(chunk.get("choices") instanceof List<?> choices) || choices.isEmpty()
                    || !(choices.get(0) instanceof Map<?, ?> choice)) {
                continue;
            }
            if (choice.get("finish_reason") instanceof String reason) {
                finishReason = reason;
            }
            if (choice.get("delta") instanceof Map<?, ?> delta
                    && delta.get("content") instanceof String text) {
                content.append(text);
            }
        }

        // 空回答在非流式下会被 AiResponseParser 判成 AI_GATEWAY_EMPTY_RESPONSE，这里保持同样的语义，
        // 但先把"流确实收到了 N 个块却没有一个字"这件事记下来——否则只看错误码会以为网关没回。
        if (content.isEmpty()) {
            log.warn("AI gateway stream produced no assistant content: chunks={}, finishReason={}",
                    chunks, finishReason);
        }
        if ("length".equals(finishReason)) {
            log.warn("AI gateway stream stopped at the model's output limit (finish_reason=length); "
                    + "the generated JSON is truncated and will fail to parse");
        }

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", content.toString());
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", finishReason);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("choices", List.of(choice));
        if (usage != null) {
            body.put("usage", usage);
        }
        return objectMapper.writeValueAsString(body);
    }

    /**
     * 凭证被拒必须在解析 body 之前判掉。
     *
     * <p>gateway 拒 AMToken 时回的是 401 + 空 body，走到 {@link #parseBody} 就成了
     * {@code AI_GATEWAY_BAD_RESPONSE}（前端文案"返回内容无法解析，请重试"），把"登录过期"说成
     * "网关抽风"——用户照着提示一直点重试，而重试用的还是同一个过期 token，永远好不了。</p>
     *
     * <p>注意 {@code amTokenPresent=true} 只说明请求里有这个字段：DW 页面开久了，AMToken cookie
     * 还在但 DSP 侧已经过期，凭证照样无效。所以这里说的是"被拒绝"，与"缺失"
     * （{@code AI_GATEWAY_TOKEN_MISSING}）分成两个码，日志和文案都能直接指向重新登录。</p>
     */
    private AiGenerationException rejectedCredential(int status, String rawBody) {
        String detail = rawBody != null && !rawBody.isBlank() ? ": " + truncate(rawBody) : "";
        // 用静态 key 时"重新登录"是错误指引：该查的是 key 本身。两种凭证分开说，别让排查跑偏。
        if (staticApiKey() != null) {
            log.warn("AI gateway rejected the static API key: status={}, bodyPresent={}. Check "
                    + "ai-generation.gateway.api-key (AI_GATEWAY_API_KEY) against the endpoint in "
                    + "ai-generation.gateway.url.", status, rawBody != null && !rawBody.isBlank());
            return new AiGenerationException("AI_GATEWAY_UNAUTHORIZED",
                    "AI gateway rejected the configured API key with HTTP " + status
                            + "; check AI_GATEWAY_API_KEY and the gateway URL" + detail);
        }
        log.warn("AI gateway rejected the AMToken: status={}, bodyPresent={}. The browser-side DSP token was "
                + "supplied but is no longer valid (typically an expired session on a long-lived DW page).",
                status, rawBody != null && !rawBody.isBlank());
        return new AiGenerationException("AI_GATEWAY_UNAUTHORIZED",
                "AI gateway rejected the AMToken with HTTP " + status
                        + "; the sign-in has expired, sign in again to refresh it" + detail);
    }

    private Map<String, Object> buildRequestBody(AiPromptBuilder.RenderedPrompt prompt) {
        Map<String, Object> systemMessage = new LinkedHashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", prompt.system());

        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt.user());

        Map<String, Object> body = new LinkedHashMap<>();
        if (model != null && !model.isBlank()) {
            body.put("model", model.trim());
        }
        body.put("messages", List.of(systemMessage, userMessage));
        if (streamEnabled) {
            body.put("stream", true);
        }
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
