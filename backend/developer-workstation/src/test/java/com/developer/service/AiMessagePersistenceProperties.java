package com.developer.service;

import com.developer.entity.AiMessage;
import com.developer.enums.AiMessageRole;
import com.developer.enums.AiPhase;
import com.developer.repository.AiDocumentRepository;
import com.developer.repository.AiMessageRepository;
import com.developer.repository.AiSessionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.service.impl.AiGenerationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Tag;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for message persistence round-trip correctness.
 *
 * <p><b>Validates: Requirements 14.1, 14.5</b></p>
 */
@Tag("Feature: ai-function-unit-generation, Property 12: 消息持久化往返")
class AiMessagePersistenceProperties {

    /**
     * Property 12: Messages saved and then loaded should maintain the same order, count, and content.
     *
     * Mock AiMessageRepository to store messages in a list and return them sorted by createdAt.
     * Save N random messages, then load them.
     * Assert: count matches, content matches, order matches.
     *
     * <p><b>Validates: Requirements 14.1, 14.5</b></p>
     */
    @Property(tries = 100)
    void savedMessagesShouldBeLoadedInSameOrder(
            @ForAll @IntRange(min = 1, max = 20) int messageCount) {

        // Setup mocks
        AiSessionRepository aiSessionRepository = mock(AiSessionRepository.class);
        AiMessageRepository aiMessageRepository = mock(AiMessageRepository.class);
        AiDocumentRepository aiDocumentRepository = mock(AiDocumentRepository.class);
        FunctionUnitRepository functionUnitRepository = mock(FunctionUnitRepository.class);

        AiGenerationServiceImpl service = new AiGenerationServiceImpl(
                aiSessionRepository, aiMessageRepository, aiDocumentRepository, functionUnitRepository,
                new ObjectMapper(), 102400);

        UUID sessionId = UUID.randomUUID();
        List<AiMessage> storedMessages = new ArrayList<>();
        AtomicLong idCounter = new AtomicLong(1);

        // Mock save: assign id and createdAt, store in list
        when(aiMessageRepository.save(any(AiMessage.class))).thenAnswer(invocation -> {
            AiMessage msg = invocation.getArgument(0);
            msg.setId(idCounter.getAndIncrement());
            msg.setCreatedAt(Instant.now().plusMillis(idCounter.get()));
            storedMessages.add(msg);
            return msg;
        });

        // Mock loadMessages: return stored messages sorted by createdAt ascending
        when(aiMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).thenAnswer(invocation -> {
            List<AiMessage> sorted = new ArrayList<>(storedMessages);
            sorted.sort(Comparator.comparing(AiMessage::getCreatedAt));
            return sorted;
        });

        // Save N messages alternating roles
        AiMessageRole[] roles = AiMessageRole.values();
        AiPhase[] phases = AiPhase.values();
        List<String> originalContents = new ArrayList<>();

        for (int i = 0; i < messageCount; i++) {
            String content = "Message-" + i + "-" + UUID.randomUUID();
            originalContents.add(content);
            service.saveMessage(sessionId, roles[i % roles.length], content, phases[i % phases.length]);
        }

        // Load messages
        List<AiMessage> loaded = service.loadMessages(sessionId);

        // Assert: count matches
        assertThat(loaded).hasSize(messageCount);

        // Assert: content matches in order
        for (int i = 0; i < messageCount; i++) {
            assertThat(loaded.get(i).getContent()).isEqualTo(originalContents.get(i));
        }

        // Assert: order matches (createdAt is monotonically increasing)
        for (int i = 1; i < loaded.size(); i++) {
            assertThat(loaded.get(i).getCreatedAt())
                    .isAfterOrEqualTo(loaded.get(i - 1).getCreatedAt());
        }
    }
}
