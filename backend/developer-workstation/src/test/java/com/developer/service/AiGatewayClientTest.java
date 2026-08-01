package com.developer.service;

import com.developer.exception.AiGenerationException;
import com.developer.service.impl.AiGatewayClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
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
                () -> client.chat("prompt", "expired-am-token"));

        assertEquals("AI_GATEWAY_UNAUTHORIZED", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("sign in again"), ex.getMessage());
    }

    @Test
    void chat_whenGatewayReturns403_reportsUnauthorizedWithTheGatewayDetail() {
        AiGatewayClient client = clientReturning(HttpStatus.FORBIDDEN, "{\"error\":\"token expired\"}");

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> client.chat("prompt", "expired-am-token"));

        assertEquals("AI_GATEWAY_UNAUTHORIZED", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("token expired"), ex.getMessage());
    }

    /** 真正读不懂的响应体仍然是 BAD_RESPONSE —— 上面的分类没有把这条路径吞掉。 */
    @Test
    void chat_whenGatewayReturnsAnEmptyBodyWithHttp200_stillReportsBadResponse() {
        AiGatewayClient client = clientReturning(HttpStatus.OK, "");

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> client.chat("prompt", "valid-am-token"));

        assertEquals("AI_GATEWAY_BAD_RESPONSE", ex.getErrorCode());
    }

    @Test
    void chat_whenGatewayReturns500_leavesTheBodyToTheResponseParser() {
        AiGatewayClient client = clientReturning(HttpStatus.INTERNAL_SERVER_ERROR, "{\"message\":\"upstream down\"}");

        Map<String, Object> result = client.chat("prompt", "valid-am-token");

        assertEquals(500, result.get("status"));
        assertEquals("upstream down", ((Map<?, ?>) result.get("body")).get("message"));
    }

    @Test
    void chat_withoutAnAmToken_failsBeforeAnyCall() {
        AiGatewayClient client = newClient(mock(RestTemplate.class));

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> client.chat("prompt", "  "));

        assertEquals("AI_GATEWAY_TOKEN_MISSING", ex.getErrorCode());
    }
}
