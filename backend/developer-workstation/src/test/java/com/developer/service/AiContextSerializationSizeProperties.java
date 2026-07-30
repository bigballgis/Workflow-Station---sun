package com.developer.service;

import com.developer.dto.FunctionUnitContextDTO;
import com.developer.entity.*;
import com.developer.enums.*;
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
        public Optional<FunctionUnit> findById(Long id) {
            return Objects.equals(functionUnit.getId(), id) ? Optional.of(functionUnit) : Optional.empty();
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
                mock(AiPromptBuilder.class),
                mock(AiGatewayClient.class),
                mock(AiResponseParser.class),
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
                    .formType(FormType.PROCESS)
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
                .displayName("Test description")
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
                            .displayName("A small function unit for testing")
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
                                .displayName("Test table " + i)
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
                                .formType(FormType.PROCESS)
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
                .displayName("Test")
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
                    .formType(FormType.PROCESS)
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

    // --- Property 1: DTO 字段完整性 ---

    /**
     * Property 1: FunctionUnitContextDTO contains decisionDefinitions and tableRelations fields.
     *
     * <p>Verifies that the DTO has the required fields for the new entity types,
     * ensuring the context serialization contract is complete.</p>
     *
     * <p><b>Validates: Requirements 1.1, 2.2, 37.1</b></p>
     */
    @Property(tries = 100)
    void functionUnitContextDTOContainsDecisionDefinitionsAndTableRelationsFields(
            @ForAll @LongRange(min = 1, max = 10000) Long seed) {

        // Verify decisionDefinitions field exists and has correct type
        Class<?> dtoClass = FunctionUnitContextDTO.class;

        assertThat(dtoClass.getDeclaredFields())
                .as("FunctionUnitContextDTO should have a decisionDefinitions field")
                .anyMatch(f -> f.getName().equals("decisionDefinitions")
                        && f.getType().equals(List.class));

        assertThat(dtoClass.getDeclaredFields())
                .as("FunctionUnitContextDTO should have a tableRelations field")
                .anyMatch(f -> f.getName().equals("tableRelations")
                        && f.getType().equals(List.class));

        // Verify builder can set these fields and they round-trip correctly
        List<Map<String, Object>> testDecisions = List.of(Map.of("decisionKey", "dk_" + seed));
        List<Map<String, Object>> testRelations = List.of(Map.of("relationType", "ONE_TO_MANY"));

        FunctionUnitContextDTO dto = FunctionUnitContextDTO.builder()
                .functionUnitId(seed)
                .name("test")
                .decisionDefinitions(testDecisions)
                .tableRelations(testRelations)
                .build();

        assertThat(dto.getDecisionDefinitions())
                .as("decisionDefinitions should be set via builder")
                .isEqualTo(testDecisions);
        assertThat(dto.getTableRelations())
                .as("tableRelations should be set via builder")
                .isEqualTo(testRelations);
    }

    // --- Property 1 (extended): buildContextDTO 序列化完整性 ---

    /**
     * Feature: ai-function-unit-generation-refactor, Property 1: 上下文序列化完整性
     *
     * <p>Verifies that buildContextDTO() correctly serializes decisionDefinitions,
     * tableRelations, fieldPermissions, showLiveValues, and stageBindings.</p>
     *
     * <p><b>Validates: Requirements 1.2, 1.3, 2.3, 2.4, 3.1, 3.2, 3.3</b></p>
     */
    @Property(tries = 100)
    @Label("Property 1: buildContextDTO 序列化 decisionDefinitions/tableRelations/form 新字段")
    void buildContextDTOSerializesAllNewFields(
            @ForAll @LongRange(min = 1, max = 10000) Long functionUnitId,
            @ForAll @IntRange(min = 0, max = 3) int decisionCount,
            @ForAll @IntRange(min = 0, max = 3) int relationCount,
            @ForAll @IntRange(min = 0, max = 2) int formCount) {

        FunctionUnit fu = buildMinimalFunctionUnit(functionUnitId);

        // Add tables (needed for relation ID→name resolution)
        for (int i = 0; i < Math.max(2, relationCount); i++) {
            TableDefinition table = TableDefinition.builder()
                    .id((long) (i + 1))
                    .functionUnit(fu)
                    .tableName("table_" + i)
                    .tableType(TableType.MAIN)
                    .fieldDefinitions(new ArrayList<>())
                    .foreignKeys(new ArrayList<>())
                    .build();
            fu.getTableDefinitions().add(table);
        }

        // Add decisions
        for (int i = 0; i < decisionCount; i++) {
            DecisionDefinition dd = DecisionDefinition.builder()
                    .functionUnit(fu)
                    .decisionKey("dk_" + i)
                    .decisionName("Decision " + i)
                    .hitPolicy("FIRST")
                    .description("desc " + i)
                    .build();
            fu.getDecisionDefinitions().add(dd);
        }

        // Add relations
        for (int i = 0; i < relationCount; i++) {
            TableRelation tr = TableRelation.builder()
                    .functionUnit(fu)
                    .sourceTableId(1L)
                    .sourceFieldName("field_s_" + i)
                    .relationType("ONE_TO_MANY")
                    .targetTableId(2L)
                    .targetFieldName("field_t_" + i)
                    .build();
            fu.getTableRelations().add(tr);
        }

        // Add forms with fieldPermissions, showLiveValues, stageBindings
        for (int i = 0; i < formCount; i++) {
            FormDefinition form = FormDefinition.builder()
                    .functionUnit(fu)
                    .formName("form_" + i)
                    .formType(FormType.TASK)
                    .configJson(Map.of("layout", "default"))
                    .fieldPermissions(Map.of("name", "READONLY"))
                    .showLiveValues(false)
                    .tableBindings(new ArrayList<>())
                    .stageBindings(new ArrayList<>())
                    .build();
            FormStageBinding sb = FormStageBinding.builder()
                    .form(form)
                    .stageId("stage_" + i)
                    .stageName("Stage " + i)
                    .build();
            form.getStageBindings().add(sb);
            fu.getFormDefinitions().add(form);
        }

        AiGenerationServiceImpl service = createService(new TestFunctionUnitRepository(fu));
        FunctionUnitContextDTO dto = service.serializeFunctionUnitContext(functionUnitId);

        // Verify decisionDefinitions
        assertThat(dto.getDecisionDefinitions()).hasSize(decisionCount);
        for (int i = 0; i < decisionCount; i++) {
            Map<String, Object> d = dto.getDecisionDefinitions().get(i);
            assertThat(d).containsKeys("decisionKey", "decisionName", "dmnXml", "hitPolicy", "description");
            assertThat(d.get("decisionKey")).isEqualTo("dk_" + i);
        }

        // Verify tableRelations
        assertThat(dto.getTableRelations()).hasSize(relationCount);
        for (Map<String, Object> r : dto.getTableRelations()) {
            assertThat(r).containsKeys("sourceTableName", "sourceFieldName", "relationType", "targetTableName", "targetFieldName");
            // sourceTableId=1 → "table_0", targetTableId=2 → "table_1"
            assertThat(r.get("sourceTableName")).isEqualTo("table_0");
            assertThat(r.get("targetTableName")).isEqualTo("table_1");
        }

        // Verify form new fields
        for (int i = 0; i < formCount; i++) {
            Map<String, Object> f = dto.getFormDefinitions().get(i);
            assertThat(f).containsKeys("fieldPermissions", "showLiveValues", "stageBindings");
            assertThat(f.get("fieldPermissions")).isEqualTo(Map.of("name", "READONLY"));
            assertThat(f.get("showLiveValues")).isEqualTo(false);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stageBindings = (List<Map<String, Object>>) f.get("stageBindings");
            assertThat(stageBindings).hasSize(1);
            assertThat(stageBindings.get(0).get("stageId")).isEqualTo("stage_" + i);
        }
    }
}
