package com.developer.service;

import com.developer.repository.AiDocumentRepository;
import com.developer.repository.AiMessageRepository;
import com.developer.repository.AiSessionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.service.impl.AiGenerationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for AI webhook success timestamp tracking.
 *
 * <p>Verifies that successful AI webhook calls update {@code lastAiWebhookSuccessTime},
 * and failed calls do not modify it.</p>
 *
 * <p><b>Validates: Requirements 45.1, 45.2</b></p>
 */
@Tag("Feature: ai-function-unit-generation-refactor, Property 24: AI webhook success timestamp tracking")
class AiWebhookResilienceProperties {

    @SuppressWarnings("unchecked")
    private AiGenerationServiceImpl createServiceWithMockRestTemplate(RestTemplate mockRestTemplate) {
        AiGenerationServiceImpl service = new AiGenerationServiceImpl(
                mock(AiSessionRepository.class),
                mock(AiMessageRepository.class),
                mock(AiDocumentRepository.class),
                mock(FunctionUnitRepository.class),
                new ObjectMapper(),
                102400);
        ReflectionTestUtils.setField(service, "aiWebhookUrl", "http://localhost:5678/webhook/test");
        ReflectionTestUtils.setField(service, "aiWebhookTimeoutSeconds", 30);
        ReflectionTestUtils.setField(service, "aiWebhookRestTemplate", mockRestTemplate);
        return service;
    }

    /**
     * Property 24a: Successful AI webhook call updates lastAiWebhookSuccessTime.
     *
     * <p>After a successful call to doCallAiWebhookWithRetry, the
     * lastAiWebhookSuccessTime field should be set to a recent Instant.</p>
     *
     * <p><b>Validates: Requirements 45.1</b></p>
     */
    @Property(tries = 100)
    @Label("Property 24a: successful call updates lastAiWebhookSuccessTime")
    void successfulCallUpdatesTimestamp(@ForAll("arbitrarySessionId") String sessionId) {
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        AiGenerationServiceImpl service = createServiceWithMockRestTemplate(mockRestTemplate);

        // Mock successful response
        Map<String, Object> responseBody = Map.of("reply", "test response");
        ResponseEntity<Map> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(mockRestTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(responseEntity);

        // Ensure lastAiWebhookSuccessTime is null initially
        assertThat(ReflectionTestUtils.getField(service, "lastAiWebhookSuccessTime")).isNull();

        // Invoke the retry wrapper via reflection (private method)
        Instant before = Instant.now();
        try {
            java.lang.reflect.Method method = AiGenerationServiceImpl.class
                    .getDeclaredMethod("doCallAiWebhookWithRetry", Map.class);
            method.setAccessible(true);
            method.invoke(service, Map.of("sessionId", sessionId, "message", "test"));
        } catch (Exception e) {
            // Should not fail for successful call
            throw new RuntimeException("Unexpected exception", e);
        }
        Instant after = Instant.now();

        Instant lastSuccess = (Instant) ReflectionTestUtils.getField(service, "lastAiWebhookSuccessTime");
        assertThat(lastSuccess)
                .as("lastAiWebhookSuccessTime should be updated after successful call")
                .isNotNull()
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    /**
     * Property 24b: Failed AI webhook call (non-retryable) does not modify lastAiWebhookSuccessTime.
     *
     * <p>When the AI webhook call fails with a non-retryable error code,
     * lastAiWebhookSuccessTime should remain unchanged.</p>
     *
     * <p><b>Validates: Requirements 45.2</b></p>
     */
    @Property(tries = 100)
    @Label("Property 24b: failed non-retryable call does not modify lastAiWebhookSuccessTime")
    void failedNonRetryableCallDoesNotUpdateTimestamp(@ForAll("arbitrarySessionId") String sessionId) {
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        AiGenerationServiceImpl service = createServiceWithMockRestTemplate(mockRestTemplate);

        // Set a known initial timestamp
        Instant initialTimestamp = Instant.parse("2026-01-01T00:00:00Z");
        ReflectionTestUtils.setField(service, "lastAiWebhookSuccessTime", initialTimestamp);

        // Mock a non-retryable exception (e.g., empty response)
        when(mockRestTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        try {
            java.lang.reflect.Method method = AiGenerationServiceImpl.class
                    .getDeclaredMethod("doCallAiWebhookWithRetry", Map.class);
            method.setAccessible(true);
            method.invoke(service, Map.of("sessionId", sessionId, "message", "test"));
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Expected: AI_WEBHOOK_EMPTY_RESPONSE is not retryable, should throw
        } catch (Exception e) {
            // Expected failure
        }

        Instant lastSuccess = (Instant) ReflectionTestUtils.getField(service, "lastAiWebhookSuccessTime");
        assertThat(lastSuccess)
                .as("lastAiWebhookSuccessTime should not be modified after non-retryable failure")
                .isEqualTo(initialTimestamp);
    }

    @Provide
    Arbitrary<String> arbitrarySessionId() {
        return Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20);
    }
}
