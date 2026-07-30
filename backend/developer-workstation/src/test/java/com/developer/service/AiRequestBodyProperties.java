package com.developer.service;

import com.developer.dto.FunctionUnitContextDTO;
import com.developer.enums.*;
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
import net.jqwik.api.constraints.StringLength;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Property-based tests for AI webhook request body completeness.
 *
 * <p>Verifies that {@code buildAiRequestBody()} includes complete {@code schemaMetadata},
 * {@code includeExplanations}, and {@code regenerateScope} fields.</p>
 *
 * <p><b>Validates: Requirements 15</b></p>
 */
@Tag("Feature: ai-function-unit-generation-refactor, Property 12: AI webhook request body contains complete schemaMetadata")
class AiRequestBodyProperties {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AiGenerationServiceImpl generationService;

    private void setupService() {
        generationService = new AiGenerationServiceImpl(
                mock(AiSessionRepository.class),
                mock(AiMessageRepository.class),
                mock(AiDocumentRepository.class),
                mock(FunctionUnitRepository.class),
                OBJECT_MAPPER,
                mock(AiPromptBuilder.class),
                mock(AiGatewayClient.class),
                mock(AiResponseParser.class),
                102400);
        ReflectionTestUtils.setField(generationService, "aiCallTimeoutSeconds", 120);
    }

    /**
     * Invoke the private buildAiRequestBody method via reflection.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeBuildAiWebhookRequestBody(
            UUID sessionId, String message, AiPhase phase, AiMode mode,
            FunctionUnitContextDTO context, Long functionUnitId,
            List<Map<String, String>> existingDocuments,
            List<Map<String, String>> conversationHistory,
            String regenerateScope) {
        return (Map<String, Object>) ReflectionTestUtils.invokeMethod(
                generationService, "buildAiRequestBody",
                sessionId, message, phase, mode, context, functionUnitId,
                existingDocuments, conversationHistory, regenerateScope);
    }

    // --- Property 12: schemaMetadata 包含所有必需字段 ---

    /**
     * Property 12: AI webhook 请求体包含完整 schemaMetadata.
     *
     * <p>For any valid combination of phase, mode, and regenerateScope,
     * the request body's {@code schemaMetadata} must contain all required fields:
     * {@code formTypes}, {@code tableTypes}, {@code actionTypes},
     * {@code configJsonExtensions}, {@code visibilityConditionFormat}, {@code newEntities}.</p>
     *
     * <p><b>Validates: Requirements 15</b></p>
     */
    @Property(tries = 100)
    @Label("Property 12: schemaMetadata contains formTypes/tableTypes/actionTypes/configJsonExtensions/visibilityConditionFormat/newEntities")
    @SuppressWarnings("unchecked")
    void schemaMetadataContainsAllRequiredFields(
            @ForAll AiPhase phase,
            @ForAll AiMode mode,
            @ForAll("regenerateScopes") String regenerateScope) {
        setupService();

        UUID sessionId = UUID.randomUUID();
        FunctionUnitContextDTO context = FunctionUnitContextDTO.builder()
                .functionUnitId(1L).name("test").description("desc")
                .tableDefinitions(List.of()).formDefinitions(List.of())
                .actionDefinitions(List.of()).build();

        Map<String, Object> body = invokeBuildAiWebhookRequestBody(
                sessionId, "test message", phase, mode, context, 1L,
                List.of(), null, regenerateScope);

        assertThat(body).containsKey("schemaMetadata");
        Map<String, Object> metadata = (Map<String, Object>) body.get("schemaMetadata");
        assertThat(metadata).isNotNull();

        // All six required top-level keys must be present
        assertThat(metadata).containsKeys(
                "formTypes", "tableTypes", "actionTypes",
                "configJsonExtensions", "visibilityConditionFormat", "newEntities");

        // formTypes must match FormType enum values
        List<String> formTypes = (List<String>) metadata.get("formTypes");
        List<String> expectedFormTypes = Arrays.stream(FormType.values())
                .map(Enum::name).collect(Collectors.toList());
        assertThat(formTypes).containsExactlyInAnyOrderElementsOf(expectedFormTypes);

        // tableTypes must match TableType enum values
        List<String> tableTypes = (List<String>) metadata.get("tableTypes");
        List<String> expectedTableTypes = Arrays.stream(TableType.values())
                .map(Enum::name).collect(Collectors.toList());
        assertThat(tableTypes).containsExactlyInAnyOrderElementsOf(expectedTableTypes);

        // actionTypes must match ActionType enum values
        List<String> actionTypes = (List<String>) metadata.get("actionTypes");
        List<String> expectedActionTypes = Arrays.stream(ActionType.values())
                .map(Enum::name).collect(Collectors.toList());
        assertThat(actionTypes).containsExactlyInAnyOrderElementsOf(expectedActionTypes);

        // configJsonExtensions must contain all extension keys
        Map<String, Object> extensions = (Map<String, Object>) metadata.get("configJsonExtensions");
        assertThat(extensions).containsKeys(
                "formulas", "linkages", "crossFieldRules", "summaryRules", "subTableValidation");

        // visibilityConditionFormat must describe ConditionExpression
        Map<String, Object> vcFormat = (Map<String, Object>) metadata.get("visibilityConditionFormat");
        assertThat(vcFormat).containsKey("validOperators");
        List<String> operators = (List<String>) vcFormat.get("validOperators");
        assertThat(operators).contains("equals", "not-equals", "contains",
                "greater-than", "less-than", "is-empty", "is-not-empty");

        // newEntities must describe decisionDefinitions, tableRelations, formStageBindings
        Map<String, Object> newEntities = (Map<String, Object>) metadata.get("newEntities");
        assertThat(newEntities).containsKeys(
                "decisionDefinitions", "tableRelations", "formStageBindings");
    }

    /**
     * Property 12b: includeExplanations is always true.
     *
     * <p>Regardless of input parameters, the request body must always set
     * {@code includeExplanations} to {@code true}.</p>
     *
     * <p><b>Validates: Requirements 15</b></p>
     */
    @Property(tries = 100)
    @Label("Property 12b: includeExplanations is always true")
    void includeExplanationsIsAlwaysTrue(
            @ForAll AiPhase phase,
            @ForAll AiMode mode) {
        setupService();

        UUID sessionId = UUID.randomUUID();
        Map<String, Object> body = invokeBuildAiWebhookRequestBody(
                sessionId, "msg", phase, mode, null, 1L,
                List.of(), null, null);

        assertThat(body).containsKey("includeExplanations");
        assertThat(body.get("includeExplanations")).isEqualTo(true);
    }

    /**
     * Property 12c: regenerateScope defaults to "ALL" when null.
     *
     * <p>When {@code regenerateScope} is null, the request body must default to "ALL".
     * When a valid scope is provided, it must be passed through unchanged.</p>
     *
     * <p><b>Validates: Requirements 15</b></p>
     */
    @Property(tries = 100)
    @Label("Property 12c: regenerateScope defaults to ALL when null, passes through when non-null")
    void regenerateScopeDefaultsToAllWhenNull(
            @ForAll AiPhase phase,
            @ForAll AiMode mode,
            @ForAll("nullableRegenerateScopes") String regenerateScope) {
        setupService();

        UUID sessionId = UUID.randomUUID();
        Map<String, Object> body = invokeBuildAiWebhookRequestBody(
                sessionId, "msg", phase, mode, null, 1L,
                List.of(), null, regenerateScope);

        assertThat(body).containsKey("regenerateScope");
        if (regenerateScope == null) {
            assertThat(body.get("regenerateScope"))
                    .as("regenerateScope should default to ALL when input is null")
                    .isEqualTo("ALL");
        } else {
            assertThat(body.get("regenerateScope"))
                    .as("regenerateScope should pass through the provided value")
                    .isEqualTo(regenerateScope);
        }
    }

    // --- Arbitrary Providers ---

    @Provide
    Arbitrary<String> regenerateScopes() {
        return Arbitraries.of("ALL", "TABLES", "FORMS", "ACTIONS", "DECISIONS", "PROCESS", "TABLE_RELATIONS");
    }

    @Provide
    Arbitrary<String> nullableRegenerateScopes() {
        return Arbitraries.of(null, "ALL", "TABLES", "FORMS", "ACTIONS", "DECISIONS", "PROCESS", "TABLE_RELATIONS");
    }
}
