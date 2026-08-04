package com.developer.service;

import com.developer.exception.AiGenerationException;
import com.developer.service.impl.AiGatewayClient;
import com.developer.service.impl.AiPromptBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiGatewayClient 单元测试 —— 重点是响应状态的分类。
 *
 * <p>AMToken 过期时 gateway 回 401 且 body 为空。这条路径以前落进"body 解析失败"
 * （AI_GATEWAY_BAD_RESPONSE，前端文案"返回内容无法解析，请重试"），把登录过期说成网关故障，
 * 用户只会一直点重试。凭证被拒必须在解析 body 之前判掉。</p>
 */
class AiGatewayClientTest {

    private static final String GATEWAY_URL = "http://localhost:8080/chat/completions";

    private static final AiPromptBuilder.RenderedPrompt PROMPT =
            new AiPromptBuilder.RenderedPrompt("phase prompt", "session context");

    private AiGatewayClient newClient(RestTemplate restTemplate) {
        AiGatewayClient client = new AiGatewayClient(new ObjectMapper());
        ReflectionTestUtils.setField(client, "gatewayUrl", GATEWAY_URL);
        ReflectionTestUtils.setField(client, "timeoutSeconds", 30);
        ReflectionTestUtils.setField(client, "ssrfAllowedHosts", List.of("localhost"));
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
        return client;
    }

    /** 流式用例里被捕获的请求体，供断言 {@code stream: true}。 */
    private Map<String, Object> capturedStreamBody;

    /**
     * 造一个"流式"客户端：桩掉 {@code RestTemplate.execute}，让 RequestCallback 把 body 写进内存、
     * 再把预置的 SSE 文本喂给 ResponseExtractor —— 等价于真实的一次 SSE 往返。
     */
    @SuppressWarnings("unchecked")
    private AiGatewayClient streamingClientReturning(HttpStatus status, String payload) throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.execute(any(URI.class), eq(HttpMethod.POST),
                any(RequestCallback.class), any(ResponseExtractor.class)))
                .thenAnswer(invocation -> {
                    ClientHttpRequest request = mock(ClientHttpRequest.class);
                    ByteArrayOutputStream sentBody = new ByteArrayOutputStream();
                    when(request.getHeaders()).thenReturn(new HttpHeaders());
                    when(request.getBody()).thenReturn(sentBody);
                    ((RequestCallback) invocation.getArgument(2)).doWithRequest(request);
                    capturedStreamBody = new ObjectMapper().readValue(sentBody.toByteArray(), Map.class);

                    ClientHttpResponse response = mock(ClientHttpResponse.class);
                    when(response.getStatusCode()).thenReturn(status);
                    when(response.getBody())
                            .thenReturn(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)));
                    return ((ResponseExtractor<Object>) invocation.getArgument(3)).extractData(response);
                });

        AiGatewayClient client = newClient(restTemplate);
        ReflectionTestUtils.setField(client, "streamEnabled", true);
        ReflectionTestUtils.setField(client, "apiKey", "sk-local-key");
        return client;
    }

    private AiGatewayClient clientReturning(HttpStatus status, String body) {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForEntity(any(URI.class), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(body, status));
        return newClient(restTemplate);
    }

    @Test
    void chat_whenGatewayRejectsAnExpiredTokenWithAnEmptyBody_reportsUnauthorized() {
        AiGatewayClient client = clientReturning(HttpStatus.UNAUTHORIZED, null);

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> client.chat(PROMPT, "expired-am-token"));

        assertEquals("AI_GATEWAY_UNAUTHORIZED", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("sign in again"), ex.getMessage());
    }

    @Test
    void chat_whenGatewayReturns403_reportsUnauthorizedWithTheGatewayDetail() {
        AiGatewayClient client = clientReturning(HttpStatus.FORBIDDEN, "{\"error\":\"token expired\"}");

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> client.chat(PROMPT, "expired-am-token"));

        assertEquals("AI_GATEWAY_UNAUTHORIZED", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("token expired"), ex.getMessage());
    }

    /** 真正读不懂的响应体仍然是 BAD_RESPONSE —— 上面的分类没有把这条路径吞掉。 */
    @Test
    void chat_whenGatewayReturnsAnEmptyBodyWithHttp200_stillReportsBadResponse() {
        AiGatewayClient client = clientReturning(HttpStatus.OK, "");

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> client.chat(PROMPT, "valid-am-token"));

        assertEquals("AI_GATEWAY_BAD_RESPONSE", ex.getErrorCode());
    }

    @Test
    void chat_whenGatewayReturns500_leavesTheBodyToTheResponseParser() {
        AiGatewayClient client = clientReturning(HttpStatus.INTERNAL_SERVER_ERROR, "{\"message\":\"upstream down\"}");

        Map<String, Object> result = client.chat(PROMPT, "valid-am-token");

        assertEquals(500, result.get("status"));
        assertEquals("upstream down", ((Map<?, ?>) result.get("body")).get("message"));
    }

    /**
     * 请求体形状：system + user 两条 message，顺序固定。
     *
     * <p>gateway 是 OpenAI 兼容接口，相位提示词只有落在 {@code system} 上才享有比用户文本更高的权重；
     * 一旦退回单条 user message（或两条顺序颠倒），模型对平台约束的遵守度会悄悄下滑，
     * 而链路全程 200，没有任何报错能提示这件事。</p>
     */
    @Test
    void chat_sendsThePhasePromptAsASystemMessageAheadOfTheUserMessage() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForEntity(any(URI.class), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"choices\":[]}", HttpStatus.OK));
        AiGatewayClient client = newClient(restTemplate);

        client.chat(PROMPT, "valid-am-token");

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(any(URI.class), captor.capture(), eq(String.class));

        Map<String, Object> body = captor.getValue().getBody();
        List<?> messages = (List<?>) body.get("messages");
        assertEquals(2, messages.size());

        Map<?, ?> system = (Map<?, ?>) messages.get(0);
        assertEquals("system", system.get("role"));
        assertEquals("phase prompt", system.get("content"));

        Map<?, ?> user = (Map<?, ?>) messages.get(1);
        assertEquals("user", user.get("role"));
        assertEquals("session context", user.get("content"));
    }

    @Test
    void chat_withoutAnAmToken_failsBeforeAnyCall() {
        AiGatewayClient client = newClient(mock(RestTemplate.class));

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> client.chat(PROMPT, "  "));

        assertEquals("AI_GATEWAY_TOKEN_MISSING", ex.getErrorCode());
    }

    /**
     * 静态 API key 的存在意义就是"本机没有 AMToken 也能打通"——所以它必须让缺失的 AMToken
     * 不再是失败条件，否则本地自测依旧卡在 AI_GATEWAY_TOKEN_MISSING。
     */
    @Test
    void chat_withAStaticApiKey_replacesTheMissingAmToken() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForEntity(any(URI.class), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"choices\":[]}", HttpStatus.OK));
        AiGatewayClient client = newClient(restTemplate);
        ReflectionTestUtils.setField(client, "apiKey", " sk-local-key ");

        client.chat(PROMPT, null);

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(any(URI.class), captor.capture(), eq(String.class));
        assertEquals(List.of("Bearer sk-local-key"), captor.getValue().getHeaders().get("Authorization"));
    }

    /**
     * 配了静态 key 就是明确要求"这台实例用固定凭证"：浏览器带上来的 AMToken 对 DeepSeek
     * 之类的端点毫无意义，不能因为它碰巧存在就把 key 顶掉。
     */
    @Test
    void chat_withAStaticApiKey_takesPrecedenceOverTheAmToken() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForEntity(any(URI.class), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"choices\":[]}", HttpStatus.OK));
        AiGatewayClient client = newClient(restTemplate);
        ReflectionTestUtils.setField(client, "apiKey", "sk-local-key");

        client.chat(PROMPT, "browser-am-token");

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(any(URI.class), captor.capture(), eq(String.class));
        assertEquals(List.of("Bearer sk-local-key"), captor.getValue().getHeaders().get("Authorization"));
    }

    /** 留空(含全空白)必须逐字回到原链路：AMToken 仍是唯一凭证，缺失仍然失败。 */
    @Test
    void chat_withABlankApiKey_keepsRequiringTheAmToken() {
        AiGatewayClient client = newClient(mock(RestTemplate.class));
        ReflectionTestUtils.setField(client, "apiKey", "   ");

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> client.chat(PROMPT, null));

        assertEquals("AI_GATEWAY_TOKEN_MISSING", ex.getErrorCode());
    }

    /**
     * 流式模式：SSE 增量必须被拼回成非流式信封，{@code AiResponseParser} 才能一行不改地复用。
     *
     * <p>DeepSeek 非流式下会把长请求的连接掐掉（实测 GENERATION prompt 约 61s 收到 0 字节 EOF），
     * 所以 GENERATION 阶段只能走流式；而解析层只认 {@code choices[0].message.content}。
     * 这条用例锁的就是这个转接：块拼接、跳过 {@code [DONE]}、丢弃 reasoning_content、保留 usage。</p>
     */
    @Test
    void chat_whenStreaming_collapsesSseDeltasIntoANonStreamEnvelope() throws Exception {
        String sse = String.join("\n",
                "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"thinking...\"}}]}",
                "",
                "data: {\"choices\":[{\"delta\":{\"content\":\"{\\\"ok\\\":\"}}]}",
                ": keep-alive",
                "data: {\"choices\":[{\"delta\":{\"content\":\"true}\"},\"finish_reason\":\"stop\"}],"
                        + "\"usage\":{\"completion_tokens\":7}}",
                "data: [DONE]",
                "");
        AiGatewayClient client = streamingClientReturning(HttpStatus.OK, sse);

        Map<String, Object> result = client.chat(PROMPT, null);

        assertEquals(200, result.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) result.get("body");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        assertEquals("{\"ok\":true}", message.get("content"));
        assertEquals("stop", choices.get(0).get("finish_reason"));
        assertEquals(Map.of("completion_tokens", 7), body.get("usage"));
    }

    /** 流式请求体必须带 {@code stream: true}，否则端点仍按非流式处理，长请求照样被掐。 */
    @Test
    void chat_whenStreaming_sendsStreamTrueInTheBody() throws Exception {
        AiGatewayClient client = streamingClientReturning(HttpStatus.OK,
                "data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}\ndata: [DONE]\n");

        client.chat(PROMPT, null);

        assertEquals(Boolean.TRUE, capturedStreamBody.get("stream"));
    }

    /** 非流式（默认）不得出现 stream 字段——集团 gateway 的请求体必须逐字不变。 */
    @Test
    void chat_whenNotStreaming_omitsTheStreamField() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForEntity(any(URI.class), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"choices\":[]}", HttpStatus.OK));
        AiGatewayClient client = newClient(restTemplate);

        client.chat(PROMPT, "valid-am-token");

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(any(URI.class), captor.capture(), eq(String.class));
        assertTrue(!captor.getValue().getBody().containsKey("stream"),
                "non-streaming requests must not carry a stream field");
    }

    /** 流式下的 4xx 不是 SSE：原始错误体必须原样交给既有的凭证/错误分支。 */
    @Test
    void chat_whenStreamingIsRejected_stillReportsTheCredentialFailure() throws Exception {
        AiGatewayClient client = streamingClientReturning(HttpStatus.UNAUTHORIZED,
                "{\"error\":{\"message\":\"invalid key\"}}");

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> client.chat(PROMPT, null));

        assertEquals("AI_GATEWAY_UNAUTHORIZED", ex.getErrorCode());
    }

    /** key 被拒时不能指导用户去"重新登录"——那条路修不好一把配错的 key。 */
    @Test
    void chat_whenTheStaticApiKeyIsRejected_pointsAtTheKeyNotTheSignIn() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForEntity(any(URI.class), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.UNAUTHORIZED));
        AiGatewayClient client = newClient(restTemplate);
        ReflectionTestUtils.setField(client, "apiKey", "sk-wrong-key");

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> client.chat(PROMPT, null));

        assertEquals("AI_GATEWAY_UNAUTHORIZED", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("AI_GATEWAY_API_KEY"),
                "message should name the key to check, got: " + ex.getMessage());
        assertTrue(!ex.getMessage().contains("sk-wrong-key"), "the key itself must never leak into the message");
    }
}
