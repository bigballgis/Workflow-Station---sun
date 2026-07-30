package com.developer.service;

import com.developer.entity.AiSession;
import com.developer.enums.AiMode;
import com.developer.enums.AiPhase;
import com.developer.enums.AiSessionStatus;
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
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Tag;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for session state machine correctness.
 *
 * <p><b>Validates: Requirements 13.1, 13.6, 13.7</b></p>
 */
@Tag("Feature: ai-function-unit-generation, Property 13: session state machine correctness")
class AiSessionStateMachineProperties {

    private AiGenerationServiceImpl createService(AiSessionRepository aiSessionRepository) {
        AiMessageRepository aiMessageRepository = mock(AiMessageRepository.class);
        AiDocumentRepository aiDocumentRepository = mock(AiDocumentRepository.class);
        FunctionUnitRepository functionUnitRepository = mock(FunctionUnitRepository.class);
        return new AiGenerationServiceImpl(
                aiSessionRepository, aiMessageRepository, aiDocumentRepository, functionUnitRepository,
                new ObjectMapper(), mock(AiPromptBuilder.class), mock(AiGatewayClient.class), mock(AiResponseParser.class), 102400);
    }

    private AiSession buildSession(UUID sessionId, AiSessionStatus status) {
        return AiSession.builder()
                .id(1L)
                .sessionId(sessionId)
                .functionUnitId(1L)
                .userId("test-user")
                .currentPhase(AiPhase.REQUIREMENTS)
                .mode(AiMode.NEW)
                .status(status)
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Property 13 - Test 1: ACTIVE → COMPLETED succeeds.
     *
     * <p><b>Validates: Requirements 13.6</b></p>
     */
    @Property(tries = 100)
    void activeToCompletedShouldSucceed(
            @ForAll @LongRange(min = 1, max = 10000) Long functionUnitId) {

        AiSessionRepository aiSessionRepository = mock(AiSessionRepository.class);
        AiGenerationServiceImpl service = createService(aiSessionRepository);

        UUID sessionId = UUID.randomUUID();
        AiSession session = buildSession(sessionId, AiSessionStatus.ACTIVE);
        session.setFunctionUnitId(functionUnitId);

        when(aiSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session));
        when(aiSessionRepository.save(any(AiSession.class))).thenAnswer(inv -> inv.getArgument(0));

        // Should not throw
        service.updateSessionStatus(sessionId.toString(), AiSessionStatus.COMPLETED);

        assertThat(session.getStatus()).isEqualTo(AiSessionStatus.COMPLETED);
    }

    /**
     * Property 13 - Test 2: ACTIVE → CANCELLED succeeds.
     *
     * <p><b>Validates: Requirements 13.7</b></p>
     */
    @Property(tries = 100)
    void activeToCancelledShouldSucceed(
            @ForAll @LongRange(min = 1, max = 10000) Long functionUnitId) {

        AiSessionRepository aiSessionRepository = mock(AiSessionRepository.class);
        AiGenerationServiceImpl service = createService(aiSessionRepository);

        UUID sessionId = UUID.randomUUID();
        AiSession session = buildSession(sessionId, AiSessionStatus.ACTIVE);
        session.setFunctionUnitId(functionUnitId);

        when(aiSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session));
        when(aiSessionRepository.save(any(AiSession.class))).thenAnswer(inv -> inv.getArgument(0));

        // Should not throw
        service.updateSessionStatus(sessionId.toString(), AiSessionStatus.CANCELLED);

        assertThat(session.getStatus()).isEqualTo(AiSessionStatus.CANCELLED);
    }

    /**
     * Property 13 - Test 3: COMPLETED → ACTIVE throws exception.
     *
     * <p><b>Validates: Requirements 13.6</b></p>
     */
    @Property(tries = 100)
    void completedToActiveShouldThrow(
            @ForAll @LongRange(min = 1, max = 10000) Long functionUnitId) {

        AiSessionRepository aiSessionRepository = mock(AiSessionRepository.class);
        AiGenerationServiceImpl service = createService(aiSessionRepository);

        UUID sessionId = UUID.randomUUID();
        AiSession session = buildSession(sessionId, AiSessionStatus.COMPLETED);
        session.setFunctionUnitId(functionUnitId);

        when(aiSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() ->
                service.updateSessionStatus(sessionId.toString(), AiSessionStatus.ACTIVE))
                .isInstanceOf(AiGenerationException.class);
    }

    /**
     * Property 13 - Test 4: CANCELLED → ACTIVE throws exception.
     *
     * <p><b>Validates: Requirements 13.7</b></p>
     */
    @Property(tries = 100)
    void cancelledToActiveShouldThrow(
            @ForAll @LongRange(min = 1, max = 10000) Long functionUnitId) {

        AiSessionRepository aiSessionRepository = mock(AiSessionRepository.class);
        AiGenerationServiceImpl service = createService(aiSessionRepository);

        UUID sessionId = UUID.randomUUID();
        AiSession session = buildSession(sessionId, AiSessionStatus.CANCELLED);
        session.setFunctionUnitId(functionUnitId);

        when(aiSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() ->
                service.updateSessionStatus(sessionId.toString(), AiSessionStatus.ACTIVE))
                .isInstanceOf(AiGenerationException.class);
    }

    /**
     * Property 13 - Test 5: COMPLETED → CANCELLED throws exception.
     *
     * <p><b>Validates: Requirements 13.6</b></p>
     */
    @Property(tries = 100)
    void completedToCancelledShouldThrow(
            @ForAll @LongRange(min = 1, max = 10000) Long functionUnitId) {

        AiSessionRepository aiSessionRepository = mock(AiSessionRepository.class);
        AiGenerationServiceImpl service = createService(aiSessionRepository);

        UUID sessionId = UUID.randomUUID();
        AiSession session = buildSession(sessionId, AiSessionStatus.COMPLETED);
        session.setFunctionUnitId(functionUnitId);

        when(aiSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() ->
                service.updateSessionStatus(sessionId.toString(), AiSessionStatus.CANCELLED))
                .isInstanceOf(AiGenerationException.class);
    }

    /**
     * Property 13 - Test 6: New session starts as ACTIVE.
     *
     * <p><b>Validates: Requirements 13.1</b></p>
     */
    @Property(tries = 100)
    void newSessionShouldStartAsActive(
            @ForAll @LongRange(min = 1, max = 10000) Long functionUnitId,
            @ForAll @StringLength(min = 1, max = 20) String userId) {

        AiSessionRepository aiSessionRepository = mock(AiSessionRepository.class);
        FunctionUnitRepository functionUnitRepository = mock(FunctionUnitRepository.class);
        AiMessageRepository aiMessageRepository = mock(AiMessageRepository.class);
        AiDocumentRepository aiDocumentRepository = mock(AiDocumentRepository.class);

        AiGenerationServiceImpl service = new AiGenerationServiceImpl(
                aiSessionRepository, aiMessageRepository, aiDocumentRepository, functionUnitRepository,
                new ObjectMapper(), mock(AiPromptBuilder.class), mock(AiGatewayClient.class), mock(AiResponseParser.class), 102400);

        when(aiSessionRepository.save(any(AiSession.class))).thenAnswer(inv -> {
            AiSession s = inv.getArgument(0);
            s.setId(1L);
            s.setCreatedAt(Instant.now());
            return s;
        });

        AiSession session = service.createSession(functionUnitId, userId, AiMode.NEW);

        assertThat(session.getStatus()).isEqualTo(AiSessionStatus.ACTIVE);
        assertThat(session.getCurrentPhase()).isEqualTo(AiPhase.REQUIREMENTS);
    }
}
