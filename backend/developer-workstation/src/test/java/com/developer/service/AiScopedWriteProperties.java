package com.developer.service;

import com.developer.dto.AiGeneratedData;
import com.developer.entity.*;
import com.developer.enums.*;
import com.developer.service.impl.AiWriteServiceImpl;
import net.jqwik.api.*;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Tag;

import java.util.LinkedHashMap;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for AiWriteService scoped regeneration isolation.
 *
 * <p>Tests verify that when a specific regenerateScope is provided,
 * only the targeted entity type is cleared and rewritten, while other
 * entity types remain untouched.</p>
 *
 * <p><b>Validates: Requirements 42.3</b></p>
 */
@Tag("Feature: ai-function-unit-generation-refactor, Property 20: incremental regeneration scope isolation")
class AiScopedWriteProperties {

    /**
     * Property 20a: FORMS scope only clears formDefinitions, leaves other entities intact.
     *
     * <p><b>Validates: Requirements 42.3</b></p>
     */
    @Property(tries = 50)
    void formsScopeOnlyClearsFormDefinitions(
            @ForAll @LongRange(min = 1, max = 1000) Long functionUnitId) {

        FunctionUnit fu = buildFunctionUnitWithAllData(functionUnitId);
        int originalTableCount = fu.getTableDefinitions().size();
        int originalActionCount = fu.getActionDefinitions().size();
        int originalDecisionCount = fu.getDecisionDefinitions().size();
        int originalRelationCount = fu.getTableRelations().size();

        AiGeneratedData data = AiGeneratedData.builder()
                .formDefinitions(List.of(buildFormData("new_form", "PROCESS")))
                .build();

        AiWriteServiceImpl writeService = new AiWriteServiceImpl(
                new AiFullReplacementWriteProperties.FixedFunctionUnitRepository(fu),
                new AiTransactionAtomicityProperties.StubIconRepository(),
                new AiTransactionAtomicityProperties.NoOpEntityManager()
        );

        writeService.applyGeneratedData(functionUnitId, data, "FORMS");

        // Forms should be replaced
        assertThat(fu.getFormDefinitions()).hasSize(1);
        assertThat(fu.getFormDefinitions().get(0).getFormName()).isEqualTo("new_form");

        // Other entities should remain untouched
        assertThat(fu.getTableDefinitions()).hasSize(originalTableCount);
        assertThat(fu.getActionDefinitions()).hasSize(originalActionCount);
        assertThat(fu.getDecisionDefinitions()).hasSize(originalDecisionCount);
        assertThat(fu.getTableRelations()).hasSize(originalRelationCount);
    }

    /**
     * Property 20b: TABLES scope clears both tableRelations and tableDefinitions.
     *
     * <p><b>Validates: Requirements 42.3</b></p>
     */
    @Property(tries = 50)
    void tablesScopeClearsTableRelationsAndTableDefinitions(
            @ForAll @LongRange(min = 1, max = 1000) Long functionUnitId) {

        FunctionUnit fu = buildFunctionUnitWithAllData(functionUnitId);
        int originalFormCount = fu.getFormDefinitions().size();
        int originalActionCount = fu.getActionDefinitions().size();

        AiGeneratedData data = AiGeneratedData.builder()
                .tableDefinitions(List.of(Map.of(
                        "tableName", "new_table",
                        "tableType", "MAIN",
                        "fieldDefinitions", List.of(Map.of(
                                "fieldName", "id", "dataType", "INTEGER",
                                "isPrimaryKey", true, "sortOrder", 1
                        ))
                )))
                .build();

        AiWriteServiceImpl writeService = new AiWriteServiceImpl(
                new AiFullReplacementWriteProperties.FixedFunctionUnitRepository(fu),
                new AiTransactionAtomicityProperties.StubIconRepository(),
                new AiTransactionAtomicityProperties.NoOpEntityManager()
        );

        writeService.applyGeneratedData(functionUnitId, data, "TABLES");

        // Tables and relations should be replaced
        assertThat(fu.getTableDefinitions()).hasSize(1);
        assertThat(fu.getTableDefinitions().get(0).getTableName()).isEqualTo("new_table");
        assertThat(fu.getTableRelations()).isEmpty();

        // Other entities should remain untouched
        assertThat(fu.getFormDefinitions()).hasSize(originalFormCount);
        assertThat(fu.getActionDefinitions()).hasSize(originalActionCount);
    }

    /**
     * Property 20c: ACTIONS scope only clears actionDefinitions.
     *
     * <p><b>Validates: Requirements 42.3</b></p>
     */
    @Property(tries = 50)
    void actionsScopeOnlyClearsActionDefinitions(
            @ForAll @LongRange(min = 1, max = 1000) Long functionUnitId) {

        FunctionUnit fu = buildFunctionUnitWithAllData(functionUnitId);
        int originalTableCount = fu.getTableDefinitions().size();
        int originalFormCount = fu.getFormDefinitions().size();

        AiGeneratedData data = AiGeneratedData.builder()
                .actionDefinitions(List.of(Map.of(
                        "actionName", "new_action",
                        "actionType", "APPROVE",
                        "configJson", Map.of("enabled", true)
                )))
                .build();

        AiWriteServiceImpl writeService = new AiWriteServiceImpl(
                new AiFullReplacementWriteProperties.FixedFunctionUnitRepository(fu),
                new AiTransactionAtomicityProperties.StubIconRepository(),
                new AiTransactionAtomicityProperties.NoOpEntityManager()
        );

        writeService.applyGeneratedData(functionUnitId, data, "ACTIONS");

        // Actions should be replaced
        assertThat(fu.getActionDefinitions()).hasSize(1);
        assertThat(fu.getActionDefinitions().get(0).getActionName()).isEqualTo("new_action");

        // Other entities should remain untouched
        assertThat(fu.getTableDefinitions()).hasSize(originalTableCount);
        assertThat(fu.getFormDefinitions()).hasSize(originalFormCount);
    }

    /**
     * Property 20d: DECISIONS scope only clears decisionDefinitions.
     *
     * <p><b>Validates: Requirements 42.3</b></p>
     */
    @Property(tries = 50)
    void decisionsScopeOnlyClearsDecisionDefinitions(
            @ForAll @LongRange(min = 1, max = 1000) Long functionUnitId) {

        FunctionUnit fu = buildFunctionUnitWithAllData(functionUnitId);
        int originalTableCount = fu.getTableDefinitions().size();
        int originalFormCount = fu.getFormDefinitions().size();
        int originalActionCount = fu.getActionDefinitions().size();

        AiGeneratedData data = AiGeneratedData.builder()
                .decisionDefinitions(List.of(Map.of(
                        "decisionKey", "new_decision",
                        "decisionName", "New Decision"
                )))
                .build();

        AiWriteServiceImpl writeService = new AiWriteServiceImpl(
                new AiFullReplacementWriteProperties.FixedFunctionUnitRepository(fu),
                new AiTransactionAtomicityProperties.StubIconRepository(),
                new AiTransactionAtomicityProperties.NoOpEntityManager()
        );

        writeService.applyGeneratedData(functionUnitId, data, "DECISIONS");

        // Decisions should be replaced
        assertThat(fu.getDecisionDefinitions()).hasSize(1);
        assertThat(fu.getDecisionDefinitions().get(0).getDecisionKey()).isEqualTo("new_decision");

        // Other entities should remain untouched
        assertThat(fu.getTableDefinitions()).hasSize(originalTableCount);
        assertThat(fu.getFormDefinitions()).hasSize(originalFormCount);
        assertThat(fu.getActionDefinitions()).hasSize(originalActionCount);
    }

    /**
     * Property 20e: PROCESS scope only clears processDefinition.
     *
     * <p><b>Validates: Requirements 42.3</b></p>
     */
    @Property(tries = 50)
    void processScopeOnlyClearsProcessDefinition(
            @ForAll @LongRange(min = 1, max = 1000) Long functionUnitId) {

        FunctionUnit fu = buildFunctionUnitWithAllData(functionUnitId);
        int originalTableCount = fu.getTableDefinitions().size();
        int originalFormCount = fu.getFormDefinitions().size();

        AiGeneratedData data = AiGeneratedData.builder()
                .processDefinition(Map.of("bpmnXml", "<new-bpmn/>"))
                .build();

        AiWriteServiceImpl writeService = new AiWriteServiceImpl(
                new AiFullReplacementWriteProperties.FixedFunctionUnitRepository(fu),
                new AiTransactionAtomicityProperties.StubIconRepository(),
                new AiTransactionAtomicityProperties.NoOpEntityManager()
        );

        writeService.applyGeneratedData(functionUnitId, data, "PROCESS");

        // Process should be replaced
        assertThat(fu.getProcessDefinition()).isNotNull();
        assertThat(fu.getProcessDefinition().getBpmnXml()).isEqualTo("<new-bpmn/>");

        // Other entities should remain untouched
        assertThat(fu.getTableDefinitions()).hasSize(originalTableCount);
        assertThat(fu.getFormDefinitions()).hasSize(originalFormCount);
    }

    /**
     * Property 20f: TABLE_RELATIONS scope only clears tableRelations.
     *
     * <p><b>Validates: Requirements 42.3</b></p>
     */
    @Property(tries = 50)
    void tableRelationsScopeOnlyClearsTableRelations(
            @ForAll @LongRange(min = 1, max = 1000) Long functionUnitId) {

        FunctionUnit fu = buildFunctionUnitWithAllData(functionUnitId);
        int originalTableCount = fu.getTableDefinitions().size();
        int originalFormCount = fu.getFormDefinitions().size();

        AiWriteServiceImpl writeService = new AiWriteServiceImpl(
                new AiFullReplacementWriteProperties.FixedFunctionUnitRepository(fu),
                new AiTransactionAtomicityProperties.StubIconRepository(),
                new AiTransactionAtomicityProperties.NoOpEntityManager()
        );

        // Apply with TABLE_RELATIONS scope and no new relations
        AiGeneratedData data = AiGeneratedData.builder().build();
        writeService.applyGeneratedData(functionUnitId, data, "TABLE_RELATIONS");

        // Table relations should be cleared
        assertThat(fu.getTableRelations()).isEmpty();

        // Other entities should remain untouched
        assertThat(fu.getTableDefinitions()).hasSize(originalTableCount);
        assertThat(fu.getFormDefinitions()).hasSize(originalFormCount);
    }

    // --- Helper Methods ---

    private FunctionUnit buildFunctionUnitWithAllData(Long id) {
        FunctionUnit fu = FunctionUnit.builder()
                .id(id)
                .code("fu-scoped-" + id)
                .name("Scoped Test FU")
                .tableDefinitions(new ArrayList<>())
                .formDefinitions(new ArrayList<>())
                .actionDefinitions(new ArrayList<>())
                .decisionDefinitions(new ArrayList<>())
                .tableRelations(new ArrayList<>())
                .versions(new ArrayList<>())
                .build();

        // Add table
        TableDefinition table = TableDefinition.builder()
                .functionUnit(fu)
                .tableName("existing_table")
                .tableType(TableType.MAIN)
                .fieldDefinitions(new ArrayList<>())
                .foreignKeys(new ArrayList<>())
                .build();
        fu.getTableDefinitions().add(table);

        // Add form
        FormDefinition form = FormDefinition.builder()
                .functionUnit(fu)
                .formName("existing_form")
                .formType(FormType.PROCESS)
                .configJson(Map.of("layout", "old"))
                .tableBindings(new ArrayList<>())
                .build();
        fu.getFormDefinitions().add(form);

        // Add action
        ActionDefinition action = ActionDefinition.builder()
                .functionUnit(fu)
                .actionName("existing_action")
                .actionType(ActionType.APPROVE)
                .configJson(Map.of("key", "old"))
                .build();
        fu.getActionDefinitions().add(action);

        // Add decision
        DecisionDefinition decision = DecisionDefinition.builder()
                .functionUnit(fu)
                .decisionKey("existing_decision")
                .decisionName("Existing Decision")
                .build();
        fu.getDecisionDefinitions().add(decision);

        // Add table relation
        TableRelation relation = TableRelation.builder()
                .functionUnit(fu)
                .sourceTableId(1L)
                .sourceFieldName("id")
                .relationType("ONE_TO_MANY")
                .targetTableId(2L)
                .targetFieldName("parent_id")
                .build();
        fu.getTableRelations().add(relation);

        // Add process definition
        ProcessDefinition process = ProcessDefinition.builder()
                .functionUnit(fu)
                .functionUnitVersionId(id)
                .bpmnXml("<old-bpmn/>")
                .build();
        fu.setProcessDefinition(process);

        return fu;
    }

    private Map<String, Object> buildFormData(String formName, String formType) {
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("formName", formName);
        formData.put("formType", formType);
        // Mutable on purpose: ensureFormConfigJsonStructure() fills in missing form-create keys
        // via configJson.put(...). An immutable Map.of() here threw UnsupportedOperationException
        // before the property under test was ever evaluated. Production configJson comes from
        // Jackson deserialization, which is mutable, so this now matches reality.
        Map<String, Object> configJson = new LinkedHashMap<>();
        configJson.put("layout", "default");
        formData.put("configJson", configJson);
        return formData;
    }
}
