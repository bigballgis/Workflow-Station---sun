package com.developer.service;

import com.developer.dto.FunctionUnitContextDTO;
import com.developer.entity.*;
import com.developer.enums.*;
import com.developer.exception.AiGenerationException;
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

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Property-based tests for AI context serialization size limit.
 *
 * <p>Tests verify that {@code serializeFunctionUnitContext} always produces output
 * within the 100KB limit, or throws AiGenerationException if it can't be reduced.</p>
 *
 * <p><b>Validates: Requirements 4.7</b></p>
 */
@Tag("Feature: ai-function-unit-generation, Property 14: 上下文序列化大小限制")
class AiContextSerializationSizeProperties {

    private static final int MAX_CONTEXT_SIZE_BYTES = 102400; // 100KB
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // --- Test double: FunctionUnitRepository that returns a FunctionUnit via findByIdWithRelations ---

    static class TestFunctionUnitRepository extends AiTransactionAtomicityProperties.EmptyFunctionUnitRepository {
        private final FunctionUnit functionUnit;

        TestFunctionUnitRepository(FunctionUnit functionUnit) {
            this.functionUnit = functionUnit;
        }

        @Override
        public Optional<FunctionUnit> findByIdWithRelations(Long id) {
            return Objects.equals(functionUnit.getId(), id) ? Optional.of(functionUnit) : Optional.empty();
        }
    }

    private AiGenerationServiceImpl createService(FunctionUnitRepository repo) {
        return new AiGenerationServiceImpl(
                mock(AiSessionRepository.class),
                mock(AiMessageRepository.class),
                mock(AiDocumentRepository.class),
                repo,
                OBJECT_MAPPER,
                MAX_CONTEXT_SIZE_BYTES
        );
    }

    // --- Property Tests ---

    /**
     * Property 14a: Small FunctionUnit context is within size limit.
     *
     * <p>For any FunctionUnit with reasonable data (a few tables, forms, actions),
     * the serialized context should be well within 100KB.</p>
     *
     * <p><b>Validates: Requirements 4.7</b></p>
     */
    @Property(tries = 100)
    void smallFunctionUnitContextIsWithinSizeLimit(
            @ForAll @LongRange(min = 1, max = 10000) Long functionUnitId,
            @ForAll("smallFunctionUnit") FunctionUnit fu) {

        fu.setId(functionUnitId);
        AiGenerationServiceImpl service = createService(new TestFunctionUnitRepository(fu));

        FunctionUnitContextDTO dto = service.serializeFunctionUnitContext(functionUnitId);

        byte[] jsonBytes = toJsonBytes(dto);
        assertThat(jsonBytes.length)
                .as("Small context should be well within 100KB limit")
                .isLessThanOrEqualTo(MAX_CONTEXT_SIZE_BYTES);
    }

    /**
     * Property 14b: Large bpmnXml gets truncated.
     *
     * <p>For any FunctionUnit with a very large bpmnXml (>100KB), the serialization
     * should truncate it and still produce output within the limit.</p>
     *
     * <p><b>Validates: Requirements 4.7</b></p>
     */
    @Property(tries = 20)
    void largeBpmnXmlGetsTruncated(
            @ForAll @LongRange(min = 1, max = 10000) Long functionUnitId,
            @ForAll @IntRange(min = 110000, max = 200000) int bpmnSize) {

        FunctionUnit fu = buildMinimalFunctionUnit(functionUnitId);

        // Create a large bpmnXml string
        String largeBpmn = "x".repeat(bpmnSize);
        ProcessDefinition pd = ProcessDefinition.builder()
                .functionUnit(fu)
                .functionUnitVersionId(functionUnitId)
                .bpmnXml(largeBpmn)
                .build();
        fu.setProcessDefinition(pd);

        AiGenerationServiceImpl service = createService(new TestFunctionUnitRepository(fu));

        FunctionUnitContextDTO dto = service.serializeFunctionUnitContext(functionUnitId);

        byte[] jsonBytes = toJsonBytes(dto);
        assertThat(jsonBytes.length)
                .as("After bpmnXml truncation, context should be within 100KB limit")
                .isLessThanOrEqualTo(MAX_CONTEXT_SIZE_BYTES);

        // Verify bpmnXml was actually truncated
        assertThat(dto.getProcessDefinition()).isNotNull();
        String truncatedBpmn = (String) dto.getProcessDefinition().get("bpmnXml");
        assertThat(truncatedBpmn).contains("[truncated]");
    }

    /**
     * Property 14c: Large configJson gets truncated.
     *
     * <p>For any FunctionUnit with many forms/actions with large configJson,
     * the serialization should truncate configJson and still produce output within the limit.</p>
     *
     * <p><b>Validates: Requirements 4.7</b></p>
     */
    @Property(tries = 20)
    void largeConfigJsonGetsTruncated(
            @ForAll @LongRange(min = 1, max = 10000) Long functionUnitId,
            @ForAll @IntRange(min = 5, max = 15) int formCount) {

        FunctionUnit fu = buildMinimalFunctionUnit(functionUnitId);

        // Create many forms with large configJson to exceed 100KB
        List<FormDefinition> forms = new ArrayList<>();
        for (int i = 0; i < formCount; i++) {
            Map<String, Object> largeConfig = new LinkedHashMap<>();
            // Each config entry ~10KB
            largeConfig.put("layout_" + i, "v".repeat(10000));
            FormDefinition form = FormDefinition.builder()
                    .functionUnit(fu)
                    .formName("form_" + i)
                    .formType(FormType.MAIN)
                    .configJson(largeConfig)
                    .tableBindings(new ArrayList<>())
                    .build();
            forms.add(form);
        }
        fu.setFormDefinitions(forms);

        AiGenerationServiceImpl service = createService(new TestFunctionUnitRepository(fu));

        FunctionUnitContextDTO dto = service.serializeFunctionUnitContext(functionUnitId);

        byte[] jsonBytes = toJsonBytes(dto);
        assertThat(jsonBytes.length)
                .as("After configJson truncation, context should be within 100KB limit")
                .isLessThanOrEqualTo(MAX_CONTEXT_SIZE_BYTES);
    }

    /**
     * Property 14d: Output never exceeds configured max size.
     *
     * <p>For any FunctionUnit where truncation is sufficient, the output JSON bytes
     * should never exceed maxContextSizeBytes.</p>
     *
     * <p><b>Validates: Requirements 4.7</b></p>
     */
    @Property(tries = 50)
    void outputNeverExceedsConfiguredMaxSize(
            @ForAll @LongRange(min = 1, max = 10000) Long functionUnitId,
            @ForAll("variousSizeFunctionUnit") FunctionUnit fu) {

        fu.setId(functionUnitId);
        AiGenerationServiceImpl service = createService(new TestFunctionUnitRepository(fu));

        try {
            FunctionUnitContextDTO dto = service.serializeFunctionUnitContext(functionUnitId);
            byte[] jsonBytes = toJsonBytes(dto);
            assertThat(jsonBytes.length)
                    .as("Output should never exceed max context size")
                    .isLessThanOrEqualTo(MAX_CONTEXT_SIZE_BYTES);
        } catch (AiGenerationException e) {
            // If truncation is not sufficient, an exception is expected
            assertThat(e.getMessage()).contains("超过限制");
        }
    }

    // --- Helper Methods ---

    private FunctionUnit buildMinimalFunctionUnit(Long id) {
        return FunctionUnit.builder()
                .id(id)
                .code("fu-ctx-" + id)
                .name("Context Test FU " + id)
                .description("Test description")
                .tableDefinitions(new ArrayList<>())
                .formDefinitions(new ArrayList<>())
                .actionDefinitions(new ArrayList<>())
                .versions(new ArrayList<>())
                .build();
    }

    private byte[] toJsonBytes(FunctionUnitContextDTO dto) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(dto);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize DTO", e);
        }
    }

    // --- Arbitrary Providers ---

    @Provide
    Arbitrary<FunctionUnit> smallFunctionUnit() {
        return Arbitraries.integers().between(0, 3).flatMap(tableCount ->
            Arbitraries.integers().between(0, 2).flatMap(formCount ->
                Arbitraries.integers().between(0, 2).map(actionCount -> {
                    FunctionUnit fu = FunctionUnit.builder()
                            .id(1L)
                            .code("fu-small")
                            .name("Small FU")
                            .description("A small function unit for testing")
                            .tableDefinitions(new ArrayList<>())
                            .formDefinitions(new ArrayList<>())
                            .actionDefinitions(new ArrayList<>())
                            .versions(new ArrayList<>())
                            .build();

                    // Add tables with fields
                    for (int i = 0; i < tableCount; i++) {
                        TableDefinition table = TableDefinition.builder()
                                .functionUnit(fu)
                                .tableName("table_" + i)
                                .tableType(TableType.MAIN)
                                .tableDisplayName("Table " + i)
                                .description("Test table " + i)
                                .fieldDefinitions(new ArrayList<>())
                                .foreignKeys(new ArrayList<>())
                                .build();
                        FieldDefinition field = FieldDefinition.builder()
                                .tableDefinition(table)
                                .fieldName("id")
                                .dataType(DataType.INTEGER)
                                .isPrimaryKey(true)
                                .sortOrder(1)
                                .build();
                        table.getFieldDefinitions().add(field);
                        fu.getTableDefinitions().add(table);
                    }

                    // Add forms
                    for (int i = 0; i < formCount; i++) {
                        FormDefinition form = FormDefinition.builder()
                                .functionUnit(fu)
                                .formName("form_" + i)
                                .formType(FormType.MAIN)
                                .configJson(Map.of("layout", "default"))
                                .tableBindings(new ArrayList<>())
                                .build();
                        fu.getFormDefinitions().add(form);
                    }

                    // Add actions
                    for (int i = 0; i < actionCount; i++) {
                        ActionDefinition action = ActionDefinition.builder()
                                .functionUnit(fu)
                                .actionName("action_" + i)
                                .actionType(ActionType.APPROVE)
                                .configJson(Map.of("enabled", true))
                                .build();
                        fu.getActionDefinitions().add(action);
                    }

                    return fu;
                })
            )
        );
    }

    @Provide
    Arbitrary<FunctionUnit> variousSizeFunctionUnit() {
        return Arbitraries.oneOf(
                // Small: no data
                Arbitraries.just(buildVariousFU(0, 0, 0, 0, false)),
                // Medium: some tables and forms
                Arbitraries.integers().between(1, 5).map(n -> buildVariousFU(n, n, n, 500, false)),
                // Large bpmnXml only
                Arbitraries.integers().between(120000, 200000).map(size -> buildVariousFU(0, 0, 0, 0, true, size)),
                // Large configJson
                Arbitraries.integers().between(5, 12).map(n -> buildVariousFU(0, n, 0, 10000, false)),
                // Mixed large
                Arbitraries.integers().between(3, 8).map(n -> buildVariousFU(n, n, n, 5000, true, 50000))
        );
    }

    private FunctionUnit buildVariousFU(int tableCount, int formCount, int actionCount,
                                         int configSize, boolean withProcess) {
        return buildVariousFU(tableCount, formCount, actionCount, configSize, withProcess, 120000);
    }

    private FunctionUnit buildVariousFU(int tableCount, int formCount, int actionCount,
                                         int configSize, boolean withProcess, int bpmnSize) {
        FunctionUnit fu = FunctionUnit.builder()
                .id(1L)
                .code("fu-various")
                .name("Various FU")
                .description("Test")
                .tableDefinitions(new ArrayList<>())
                .formDefinitions(new ArrayList<>())
                .actionDefinitions(new ArrayList<>())
                .versions(new ArrayList<>())
                .build();

        for (int i = 0; i < tableCount; i++) {
            TableDefinition table = TableDefinition.builder()
                    .functionUnit(fu)
                    .tableName("tbl_" + i)
                    .tableType(TableType.MAIN)
                    .fieldDefinitions(new ArrayList<>())
                    .foreignKeys(new ArrayList<>())
                    .build();
            FieldDefinition field = FieldDefinition.builder()
                    .tableDefinition(table)
                    .fieldName("id")
                    .dataType(DataType.INTEGER)
                    .isPrimaryKey(true)
                    .sortOrder(1)
                    .build();
            table.getFieldDefinitions().add(field);
            fu.getTableDefinitions().add(table);
        }

        for (int i = 0; i < formCount; i++) {
            Map<String, Object> config = new LinkedHashMap<>();
            if (configSize > 0) {
                config.put("data", "x".repeat(configSize));
            }
            FormDefinition form = FormDefinition.builder()
                    .functionUnit(fu)
                    .formName("frm_" + i)
                    .formType(FormType.MAIN)
                    .configJson(config)
                    .tableBindings(new ArrayList<>())
                    .build();
            fu.getFormDefinitions().add(form);
        }

        for (int i = 0; i < actionCount; i++) {
            Map<String, Object> config = new LinkedHashMap<>();
            if (configSize > 0) {
                config.put("data", "y".repeat(configSize));
            }
            ActionDefinition action = ActionDefinition.builder()
                    .functionUnit(fu)
                    .actionName("act_" + i)
                    .actionType(ActionType.SAVE)
                    .configJson(config)
                    .build();
            fu.getActionDefinitions().add(action);
        }

        if (withProcess) {
            ProcessDefinition pd = ProcessDefinition.builder()
                    .functionUnit(fu)
                    .functionUnitVersionId(1L)
                    .bpmnXml("b".repeat(bpmnSize))
                    .build();
            fu.setProcessDefinition(pd);
        }

        return fu;
    }
}
