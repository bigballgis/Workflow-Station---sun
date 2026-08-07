package com.developer.component.impl;

import com.developer.dto.ValidationResult;
import com.developer.entity.FieldDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.entity.TableDefinition;
import com.developer.enums.TableType;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.TableDefinitionRepository;
import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Whether a bound form places the {@code miAssignment} component is the developer's
 * own call (see MiAssignmentFormGuard) — these tests cover only what
 * ProcessBpmnValidator itself still enforces: per-mode required-field validation,
 * stale subTableId fallback resolution, and BPMN nodes conflicting with each other
 * on the same Sub Table's assignment contract.
 */
@ExtendWith(MockitoExtension.class)
class ProcessBpmnValidatorMiAssignmentTest {

    private static final long FUNCTION_UNIT_ID = 10L;
    private static final long TABLE_ID = 20L;

    @Mock private TableDefinitionRepository tableDefinitionRepository;
    @Mock private FormDefinitionRepository formDefinitionRepository;
    @Mock private I18nService i18nService;

    private ProcessBpmnValidator validator;
    private TableDefinition table;

    @BeforeEach
    void setUp() {
        validator = new ProcessBpmnValidator(
                tableDefinitionRepository, formDefinitionRepository, i18nService);
        table = table(TABLE_ID, "participants", "owner_id", "role_id", "bu_code");
        lenient().when(tableDefinitionRepository.findByIdWithFields(TABLE_ID)).thenReturn(Optional.of(table));
    }

    @Test
    void bothModeValidatesBothFieldGroupsAndConfiguredBuField() {
        String bpmn = bpmn(miNode(
                "sp-1", "task-1", TABLE_ID, "participants", "both",
                "missing_owner", "missing_role", "missing_bu"));

        ValidationResult result = validator.validateMultiInstance(bpmn, FUNCTION_UNIT_ID);

        assertCodes(result,
                "ASSIGNEE_FIELD_NOT_FOUND",
                "ROLE_FIELD_NOT_FOUND",
                "BU_FIELD_NOT_FOUND");
    }

    @Test
    void userAndRoleModesValidateOnlyTheirRequiredGroups() {
        String bpmn = bpmn(
                miNode("sp-user", "task-user", TABLE_ID, "participants-user",
                        "user", "owner_id", null, null)
                        + miNode("sp-role", "task-role", TABLE_ID, "participants-role",
                                "role", null, "role_id", null));

        ValidationResult result = validator.validateMultiInstance(bpmn, FUNCTION_UNIT_ID);

        assertThat(result.getErrors()).noneMatch(error ->
                "MISSING_ASSIGNEE_FIELD".equals(error.getCode())
                        || "MISSING_ROLE_FIELD".equals(error.getCode()));
    }

    @Test
    void staleTableIdFallsBackToSameFuTableNameAndBinding() {
        long staleTableId = 999L;
        when(tableDefinitionRepository.findByIdWithFields(staleTableId)).thenReturn(Optional.empty());
        when(tableDefinitionRepository.findByFunctionUnitIdAndTableName(FUNCTION_UNIT_ID, "participants"))
                .thenReturn(Optional.of(table));
        String bpmn = bpmn(miNode(
                "sp-1", "task-1", staleTableId, "participants", "user",
                "owner_id", null, null));

        ValidationResult result = validator.validateMultiInstance(bpmn, FUNCTION_UNIT_ID);

        assertThat(result.getErrors()).noneMatch(error -> "SUBTABLE_NOT_FOUND".equals(error.getCode()));
    }

    @Test
    void rejectsConflictingConfigsForSameSubTableName() {
        String bpmn = bpmn(
                miNode("sp-1", "task-1", TABLE_ID, "participants",
                        "user", "owner_id", null, null)
                        + miNode("sp-2", "task-2", TABLE_ID, "participants",
                                "role", null, "role_id", null));

        ValidationResult result = validator.validateMultiInstance(bpmn, FUNCTION_UNIT_ID);

        assertCodes(result, "CONFLICTING_MI_ASSIGNMENT_CONFIG");
    }

    @Test
    void noComponentAnywhereDoesNotBlock() {
        String bpmn = bpmn(miNode(
                "sp-1", "task-1", TABLE_ID, "participants", "user",
                "owner_id", null, null));

        ValidationResult result = validator.validateMultiInstance(bpmn, FUNCTION_UNIT_ID);

        assertThat(result.getErrors()).noneMatch(error ->
                "MISSING_MI_ASSIGNMENT_COMPONENT".equals(error.getCode()));
    }

    private static void assertCodes(ValidationResult result, String... expectedCodes) {
        assertThat(result.getErrors()).extracting(ValidationResult.ValidationError::getCode)
                .contains(expectedCodes);
    }

    private static TableDefinition table(long id, String name, String... fields) {
        FunctionUnit functionUnit = FunctionUnit.builder().id(FUNCTION_UNIT_ID).build();
        TableDefinition table = TableDefinition.builder()
                .id(id)
                .functionUnit(functionUnit)
                .tableName(name)
                .tableType(TableType.SUB)
                .build();
        List<FieldDefinition> definitions = new ArrayList<>();
        for (String field : fields) {
            definitions.add(FieldDefinition.builder()
                    .fieldName(field)
                    .tableDefinition(table)
                    .build());
        }
        table.setFieldDefinitions(definitions);
        return table;
    }

    private static String bpmn(String subProcesses) {
        return """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:flowable="http://flowable.org/bpmn"
                                  xmlns:custom="http://workflow.platform/schema/bpmn">
                  <bpmn:process id="process">%s</bpmn:process>
                </bpmn:definitions>
                """.formatted(subProcesses);
    }

    private static String miNode(
            String subProcessId,
            String taskId,
            long subTableId,
            String subTableName,
            String mode,
            String assigneeField,
            String roleField,
            String buField) {
        return """
                <bpmn:subProcess id="%s">
                  <bpmn:multiInstanceLoopCharacteristics flowable:collection="rows"/>
                  <bpmn:userTask id="%s"><bpmn:extensionElements><custom:properties>
                    %s
                  </custom:properties></bpmn:extensionElements></bpmn:userTask>
                </bpmn:subProcess>
                """.formatted(
                subProcessId,
                taskId,
                property("subTableId", String.valueOf(subTableId))
                        + property("subTableName", subTableName)
                        + property("assigneeMode", mode)
                        + property("assigneeField", assigneeField)
                        + property("roleField", roleField)
                        + property("buField", buField));
    }

    private static String property(String name, String value) {
        return value == null ? "" : "<custom:property name=\"" + name + "\" value=\"" + value + "\"/>";
    }
}
