package com.developer.service;

import com.developer.exception.AiGenerationException;
import com.developer.service.impl.AiGatewayClient;
import com.developer.service.impl.AiPromptBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

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
}
