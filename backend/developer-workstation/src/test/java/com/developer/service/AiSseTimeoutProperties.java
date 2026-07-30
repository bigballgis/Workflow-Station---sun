package com.developer.service;

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
import net.jqwik.api.constraints.IntRange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Property-based tests for SSE emitter dynamic timeout calculation.
 *
 * <p>Verifies that {@code createChatEmitter()} computes timeout as
 * {@code aiCallTimeoutSeconds * 2 * 1000 + 60000}, ensuring the SSE connection
 * outlives the worst-case AI webhook call duration (including one retry).</p>
 *
 * <p><b>Validates: Requirements 24</b></p>
 */
@Tag("Feature: ai-function-unit-generation-refactor, Property 16: SSE emitter dynamic timeout calculation")
class AiSseTimeoutProperties {

    private AiGenerationServiceImpl createService(int aiCallTimeoutSeconds) {
        AiGenerationServiceImpl service = new AiGenerationServiceImpl(
                mock(AiSessionRepository.class),
                mock(AiMessageRepository.class),
                mock(AiDocumentRepository.class),
                mock(FunctionUnitRepository.class),
                new ObjectMapper(),
                mock(AiPromptBuilder.class),
                mock(AiGatewayClient.class),
                mock(AiResponseParser.class),
                102400);
        ReflectionTestUtils.setField(service, "aiCallTimeoutSeconds", aiCallTimeoutSeconds);
        return service;
    }

    /**
     * Property 16: SSE emitter 动态超时计算.
     *
     * <p>For any {@code aiCallTimeoutSeconds} in [10, 600], the emitter timeout
     * must equal {@code aiCallTimeoutSeconds * 2 * 1000 + 60000}.</p>
     *
     * <p><b>Validates: Requirements 24</b></p>
     */
    @Property(tries = 100)
    @Label("Property 16: emitter.getTimeout() == aiCallTimeoutSeconds * 2 * 1000 + 60000")
    void emitterTimeoutMatchesFormula(
            @ForAll @IntRange(min = 10, max = 600) int aiCallTimeoutSeconds) {

        AiGenerationServiceImpl service = createService(aiCallTimeoutSeconds);

        SseEmitter emitter = service.createChatEmitter(1L, "user1");

        long expectedTimeout = (long) aiCallTimeoutSeconds * 2 * 1000 + 60_000L;
        assertThat(emitter.getTimeout())
                .as("Timeout for aiCallTimeoutSeconds=%d should be %d ms", aiCallTimeoutSeconds, expectedTimeout)
                .isEqualTo(expectedTimeout);
    }
}
