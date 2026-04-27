package com.developer.service;

import com.developer.entity.AiSession;
import com.developer.enums.AiMode;
import com.developer.enums.AiPhase;
import com.developer.enums.AiSessionStatus;
import com.developer.repository.AiDocumentRepository;
import com.developer.repository.AiMessageRepository;
import com.developer.repository.AiSessionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.service.impl.AiGenerationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Tag;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for phase transition ordering correctness.
 *
 * <p><b>Validates: Requirements 3.4, 3.8</b></p>
 */
@Tag("Feature: ai-function-unit-generation, Property 16: phase transition ordering")
class AiPhaseTransitionProperties {

    private AiGenerationServiceImpl createService(AiSessionRepository aiSessionRepository) {
        AiMessageRepository aiMessageRepository = mock(AiMessageRepository.class);
        AiDocumentRepository aiDocumentRepository = mock(AiDocumentRepository.class);
        FunctionUnitRepository functionUnitRepository = mock(FunctionUnitRepository.class);
        return new AiGenerationServiceImpl(
                aiSessionRepository, aiMessageRepository, aiDocumentRepository, functionUnitRepository,
                new ObjectMapper(), 102400);
    }

    /**
     * Property 16 - Test 1: A new session starts with REQUIREMENTS phase.
     *
     * <p><b>Validates: Requirements 3.8</b></p>
     */
    @Property(tries = 100)
    void newSessionShouldStartWithRequirementsPhase(
            @ForAll @LongRange(min = 1, max = 10000) Long functionUnitId,
            @ForAll @StringLength(min = 1, max = 20) String userId) {

        AiSessionRepository aiSessionRepository = mock(AiSessionRepository.class);
        AiMessageRepository aiMessageRepository = mock(AiMessageRepository.class);
        AiDocumentRepository aiDocumentRepository = mock(AiDocumentRepository.class);
        FunctionUnitRepository functionUnitRepository = mock(FunctionUnitRepository.class);

        AiGenerationServiceImpl service = new AiGenerationServiceImpl(
                aiSessionRepository, aiMessageRepository, aiDocumentRepository, functionUnitRepository,
                new ObjectMapper(), 102400);

        when(aiSessionRepository.save(any(AiSession.class))).thenAnswer(inv -> {
            AiSession s = inv.getArgument(0);
            s.setId(1L);
            s.setCreatedAt(Instant.now());
            return s;
        });

        AiSession session = service.createSession(functionUnitId, userId, AiMode.NEW);

        assertThat(session.getCurrentPhase()).isEqualTo(AiPhase.REQUIREMENTS);
    }

    /**
     * Property 16 - Test 2: Phases can be set in order REQUIREMENTS → DESIGN → GENERATION.
     *
     * updateSessionPhase correctly updates the phase in the expected order.
     *
     * <p><b>Validates: Requirements 3.4</b></p>
     */
    @Property(tries = 100)
    void phasesShouldFollowCorrectOrder(
            @ForAll @LongRange(min = 1, max = 10000) Long functionUnitId) {

        AiSessionRepository aiSessionRepository = mock(AiSessionRepository.class);
        AiGenerationServiceImpl service = createService(aiSessionRepository);

        UUID sessionId = UUID.randomUUID();
        AiSession session = AiSession.builder()
                .id(1L)
                .sessionId(sessionId)
                .functionUnitId(functionUnitId)
                .userId("test-user")
                .currentPhase(AiPhase.REQUIREMENTS)
                .mode(AiMode.NEW)
                .status(AiSessionStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();

        when(aiSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session));
        when(aiSessionRepository.save(any(AiSession.class))).thenAnswer(inv -> inv.getArgument(0));

        // Start at REQUIREMENTS
        assertThat(session.getCurrentPhase()).isEqualTo(AiPhase.REQUIREMENTS);

        // Transition to DESIGN
        service.updateSessionPhase(sessionId.toString(), AiPhase.DESIGN);
        assertThat(session.getCurrentPhase()).isEqualTo(AiPhase.DESIGN);

        // Transition to GENERATION
        service.updateSessionPhase(sessionId.toString(), AiPhase.GENERATION);
        assertThat(session.getCurrentPhase()).isEqualTo(AiPhase.GENERATION);
    }

    /**
     * Property 16 - Test 3: Each phase transition updates the session correctly.
     *
     * For each valid phase, updateSessionPhase should set the session's currentPhase to the given phase.
     *
     * <p><b>Validates: Requirements 3.4, 3.8</b></p>
     */
    @Property(tries = 100)
    void updateSessionPhaseShouldSetCorrectPhase(
            @ForAll("validPhases") AiPhase targetPhase,
            @ForAll @LongRange(min = 1, max = 10000) Long functionUnitId) {

        AiSessionRepository aiSessionRepository = mock(AiSessionRepository.class);
        AiGenerationServiceImpl service = createService(aiSessionRepository);

        UUID sessionId = UUID.randomUUID();
        AiSession session = AiSession.builder()
                .id(1L)
                .sessionId(sessionId)
                .functionUnitId(functionUnitId)
                .userId("test-user")
                .currentPhase(AiPhase.REQUIREMENTS)
                .mode(AiMode.NEW)
                .status(AiSessionStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();

        when(aiSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session));
        when(aiSessionRepository.save(any(AiSession.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateSessionPhase(sessionId.toString(), targetPhase);

        assertThat(session.getCurrentPhase()).isEqualTo(targetPhase);
    }

    @Provide
    Arbitrary<AiPhase> validPhases() {
        return Arbitraries.of(AiPhase.values());
    }
}
