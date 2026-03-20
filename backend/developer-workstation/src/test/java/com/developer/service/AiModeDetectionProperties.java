package com.developer.service;

import com.developer.entity.ActionDefinition;
import com.developer.entity.FormDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.entity.TableDefinition;
import com.developer.enums.AiMode;
import com.developer.repository.AiDocumentRepository;
import com.developer.repository.AiMessageRepository;
import com.developer.repository.AiSessionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.service.impl.AiGenerationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Tag;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for AI mode detection correctness.
 *
 * <p><b>Validates: Requirements 4.1, 4.2, 4.3</b></p>
 */
@Tag("Feature: ai-function-unit-generation, Property 1: 模式判定正确性")
class AiModeDetectionProperties {

    /**
     * Property 1 - Test 1: FunctionUnit with component data → MODIFY
     *
     * If a FunctionUnit has any component data (processDefinition, tableDefinitions,
     * formDefinitions, actionDefinitions), the mode should be MODIFY.
     *
     * <p><b>Validates: Requirements 4.1, 4.3</b></p>
     */
    @Property(tries = 100)
    void functionUnitWithComponentDataShouldBeModifyMode(
            @ForAll @LongRange(min = 1, max = 10000) Long functionUnitId,
            @ForAll Boolean hasProcess,
            @ForAll Boolean hasTables,
            @ForAll Boolean hasForms,
            @ForAll Boolean hasActions) {

        // Ensure at least one component has data
        Assume.that(hasProcess || hasTables || hasForms || hasActions);

        // Setup mocks
        FunctionUnitRepository functionUnitRepository = mock(FunctionUnitRepository.class);
        AiSessionRepository aiSessionRepository = mock(AiSessionRepository.class);
        AiMessageRepository aiMessageRepository = mock(AiMessageRepository.class);
        AiDocumentRepository aiDocumentRepository = mock(AiDocumentRepository.class);

        AiGenerationServiceImpl service = new AiGenerationServiceImpl(
                aiSessionRepository, aiMessageRepository, aiDocumentRepository, functionUnitRepository,
                new ObjectMapper(), 102400);

        // Create mock FunctionUnit with randomly selected component data
        FunctionUnit mockFunctionUnit = mock(FunctionUnit.class);
        when(mockFunctionUnit.getProcessDefinition())
                .thenReturn(hasProcess ? mock(ProcessDefinition.class) : null);
        when(mockFunctionUnit.getTableDefinitions())
                .thenReturn(hasTables ? List.of(mock(TableDefinition.class)) : Collections.emptyList());
        when(mockFunctionUnit.getFormDefinitions())
                .thenReturn(hasForms ? List.of(mock(FormDefinition.class)) : Collections.emptyList());
        when(mockFunctionUnit.getActionDefinitions())
                .thenReturn(hasActions ? List.of(mock(ActionDefinition.class)) : Collections.emptyList());

        when(functionUnitRepository.findById(functionUnitId)).thenReturn(Optional.of(mockFunctionUnit));

        // Act
        AiMode mode = service.determineMode(functionUnitId);

        // Assert
        assertThat(mode).isEqualTo(AiMode.MODIFY);
    }

    /**
     * Property 1 - Test 2: FunctionUnit with no component data → NEW
     *
     * If a FunctionUnit has no component data (all component lists empty/null
     * and processDefinition null), the mode should be NEW.
     *
     * <p><b>Validates: Requirements 4.1, 4.2</b></p>
     */
    @Property(tries = 100)
    void functionUnitWithNoComponentDataShouldBeNewMode(
            @ForAll @LongRange(min = 1, max = 10000) Long functionUnitId) {

        // Setup mocks
        FunctionUnitRepository functionUnitRepository = mock(FunctionUnitRepository.class);
        AiSessionRepository aiSessionRepository = mock(AiSessionRepository.class);
        AiMessageRepository aiMessageRepository = mock(AiMessageRepository.class);
        AiDocumentRepository aiDocumentRepository = mock(AiDocumentRepository.class);

        AiGenerationServiceImpl service = new AiGenerationServiceImpl(
                aiSessionRepository, aiMessageRepository, aiDocumentRepository, functionUnitRepository,
                new ObjectMapper(), 102400);

        // Create FunctionUnit with no component data
        FunctionUnit mockFunctionUnit = mock(FunctionUnit.class);
        when(mockFunctionUnit.getProcessDefinition()).thenReturn(null);
        when(mockFunctionUnit.getTableDefinitions()).thenReturn(Collections.emptyList());
        when(mockFunctionUnit.getFormDefinitions()).thenReturn(Collections.emptyList());
        when(mockFunctionUnit.getActionDefinitions()).thenReturn(Collections.emptyList());

        when(functionUnitRepository.findById(functionUnitId)).thenReturn(Optional.of(mockFunctionUnit));

        // Act
        AiMode mode = service.determineMode(functionUnitId);

        // Assert
        assertThat(mode).isEqualTo(AiMode.NEW);
    }
}
