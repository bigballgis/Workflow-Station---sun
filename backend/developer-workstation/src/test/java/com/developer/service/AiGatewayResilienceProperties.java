package com.developer.service;

import com.developer.exception.AiGenerationException;
import com.developer.repository.AiDocumentRepository;
import com.developer.repository.AiMessageRepository;
import com.developer.repository.AiSessionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.service.impl.AiGatewayClient;
import com.developer.service.impl.AiGenerationServiceImpl;
import com.developer.service.impl.AiPromptBuilder;
import com.developer.service.impl.AiResponseParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for AI gateway success timestamp tracking.
 *
 * <p>Verifies that successful AI gateway calls update {@code lastAiCallSuccessTime},
 * and failed calls do not modify it.</p>
 *
 * <p><b>Validates: Requirements 45.1, 45.2</b></p>
 */
@Tag("Feature: ai-function-unit-generation-refactor, Property 24: AI gateway success timestamp tracking")
class AiGatewayResilienceProperties {

    private static final String AM_TOKEN = "am-token-for-test";

    private AiGenerationServiceImpl createService(AiGatewayClient gatewayClient, AiResponseParser responseParser) {
        AiPromptBuilder promptBuilder = mock(AiPromptBuilder.class);
        when(promptBuilder.build(any())).thenReturn("rendered prompt");
        AiGenerationServiceImpl service = new AiGenerationServiceImpl(
                mock(AiSessionRepository.class),
                mock(AiMessageRepository.class),
                mock(AiDocumentRepository.class),
                mock(FunctionUnitRepository.class),
                new ObjectMapper(),
                promptBuilder,
                gatewayClient,
                responseParser,
                102400);
        ReflectionTestUtils.setField(service, "aiCallTimeoutSeconds", 30);
        return service;
    }

    /** doCallAiWithRetry 是私有的：按名字反射调用，避免为测试放宽可见性。 */
    private void invokeWithRetry(AiGenerationServiceImpl service, String sessionId) throws Exception {
        java.lang.reflect.Method method = AiGenerationServiceImpl.class
                .getDeclaredMethod("doCallAiWithRetry", Map.class, String.class);
        method.setAccessible(true);
        method.invoke(service, Map.of("sessionId", sessionId, "message", "test"), AM_TOKEN);
    }

    /**
     * Property 24a: Successful AI gateway call updates lastAiCallSuccessTime.
     *
     * <p><b>Validates: Requirements 45.1</b></p>
     */
    @Property(tries = 100)
    @Label("Property 24a: successful call updates lastAiCallSuccessTime")
    void successfulCallUpdatesTimestamp(@ForAll("arbitrarySessionId") String sessionId) throws Exception {
        AiGatewayClient gatewayClient = mock(AiGatewayClient.class);
        AiResponseParser responseParser = mock(AiResponseParser.class);
        when(gatewayClient.chat(anyString(), anyString()))
                .thenReturn(Map.of("status", 200, "body", Map.of()));
        when(responseParser.parse(any())).thenReturn(Map.of("reply", "test response"));

        AiGenerationServiceImpl service = createService(gatewayClient, responseParser);
        assertThat(ReflectionTestUtils.getField(service, "lastAiCallSuccessTime")).isNull();

        Instant before = Instant.now();
        invokeWithRetry(service, sessionId);
        Instant after = Instant.now();

        Instant lastSuccess = (Instant) ReflectionTestUtils.getField(service, "lastAiCallSuccessTime");
        assertThat(lastSuccess)
                .as("lastAiCallSuccessTime should be updated after successful call")
                .isNotNull()
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    /**
     * Property 24b: Failed AI gateway call (non-retryable) does not modify lastAiCallSuccessTime.
     *
     * <p><b>Validates: Requirements 45.2</b></p>
     */
    @Property(tries = 100)
    @Label("Property 24b: failed non-retryable call does not modify lastAiCallSuccessTime")
    void failedNonRetryableCallDoesNotUpdateTimestamp(@ForAll("arbitrarySessionId") String sessionId) {
        AiGatewayClient gatewayClient = mock(AiGatewayClient.class);
        AiResponseParser responseParser = mock(AiResponseParser.class);
        // AI_GATEWAY_EMPTY_RESPONSE 不在可重试白名单里：重发同一个 prompt 只会得到同样的空回答。
        when(gatewayClient.chat(anyString(), anyString()))
                .thenThrow(new AiGenerationException("AI_GATEWAY_EMPTY_RESPONSE", "empty assistant response"));

        AiGenerationServiceImpl service = createService(gatewayClient, responseParser);
        Instant initialTimestamp = Instant.parse("2026-01-01T00:00:00Z");
        ReflectionTestUtils.setField(service, "lastAiCallSuccessTime", initialTimestamp);

        assertThatThrownBy(() -> invokeWithRetry(service, sessionId));

        Instant lastSuccess = (Instant) ReflectionTestUtils.getField(service, "lastAiCallSuccessTime");
        assertThat(lastSuccess)
                .as("lastAiCallSuccessTime should not be modified after non-retryable failure")
                .isEqualTo(initialTimestamp);
    }

    @Provide
    Arbitrary<String> arbitrarySessionId() {
        return Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20);
    }
}
