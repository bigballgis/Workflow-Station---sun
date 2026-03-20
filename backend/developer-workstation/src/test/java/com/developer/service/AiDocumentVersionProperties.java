package com.developer.service;

import com.developer.entity.AiDocument;
import com.developer.enums.AiDocumentType;
import com.developer.repository.AiDocumentRepository;
import com.developer.repository.AiMessageRepository;
import com.developer.repository.AiSessionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.service.impl.AiGenerationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Tag;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for document version monotonic increment correctness.
 *
 * <p><b>Validates: Requirements 5.5, 5.6, 5.8</b></p>
 */
@Tag("Feature: ai-function-unit-generation, Property 11: 文档版本单调递增")
class AiDocumentVersionProperties {

    /**
     * Property 11: Each new document version = current max version + 1, no duplicate versions.
     *
     * Mock AiDocumentRepository to track saved documents.
     * Save N documents sequentially.
     * Assert: versions are 1, 2, 3, ..., N (monotonically increasing).
     * Assert: no duplicate version numbers.
     *
     * <p><b>Validates: Requirements 5.5, 5.6, 5.8</b></p>
     */
    @Property(tries = 100)
    void documentVersionsShouldBeMonotonicallyIncreasing(
            @ForAll @LongRange(min = 1, max = 10000) Long functionUnitId,
            @ForAll @IntRange(min = 1, max = 15) int documentCount) {

        // Setup mocks
        AiSessionRepository aiSessionRepository = mock(AiSessionRepository.class);
        AiMessageRepository aiMessageRepository = mock(AiMessageRepository.class);
        AiDocumentRepository aiDocumentRepository = mock(AiDocumentRepository.class);
        FunctionUnitRepository functionUnitRepository = mock(FunctionUnitRepository.class);

        AiGenerationServiceImpl service = new AiGenerationServiceImpl(
                aiSessionRepository, aiMessageRepository, aiDocumentRepository, functionUnitRepository,
                new ObjectMapper(), 102400);

        AiDocumentType docType = AiDocumentType.REQUIREMENTS;
        List<AiDocument> savedDocuments = new ArrayList<>();
        AtomicLong idCounter = new AtomicLong(1);

        // Mock findTopByFunctionUnitIdAndDocumentTypeOrderByVersionDesc to return the last saved document
        when(aiDocumentRepository.findTopByFunctionUnitIdAndDocumentTypeOrderByVersionDesc(
                eq(functionUnitId), eq(docType)))
                .thenAnswer(invocation -> {
                    if (savedDocuments.isEmpty()) {
                        return Optional.empty();
                    }
                    return Optional.of(savedDocuments.get(savedDocuments.size() - 1));
                });

        // Mock save: assign id and createdAt, store in list
        when(aiDocumentRepository.save(any(AiDocument.class))).thenAnswer(invocation -> {
            AiDocument doc = invocation.getArgument(0);
            doc.setId(idCounter.getAndIncrement());
            doc.setCreatedAt(Instant.now());
            savedDocuments.add(doc);
            return doc;
        });

        // Save N documents sequentially
        for (int i = 0; i < documentCount; i++) {
            service.saveDocument(functionUnitId, docType,
                    "Content-" + i, "Summary-" + i, "user-" + i);
        }

        // Assert: versions are 1, 2, 3, ..., N
        assertThat(savedDocuments).hasSize(documentCount);
        for (int i = 0; i < documentCount; i++) {
            assertThat(savedDocuments.get(i).getVersion()).isEqualTo(i + 1);
        }

        // Assert: no duplicate version numbers
        Set<Integer> versions = new HashSet<>();
        for (AiDocument doc : savedDocuments) {
            assertThat(versions.add(doc.getVersion()))
                    .as("Version %d should be unique", doc.getVersion())
                    .isTrue();
        }

        // Assert: monotonically increasing
        for (int i = 1; i < savedDocuments.size(); i++) {
            assertThat(savedDocuments.get(i).getVersion())
                    .isGreaterThan(savedDocuments.get(i - 1).getVersion());
        }
    }
}
