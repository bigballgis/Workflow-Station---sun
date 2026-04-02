package com.developer.component;

import com.developer.component.impl.ProcessDesignComponentImpl;
import com.developer.dto.ValidationResult;
import com.developer.entity.FieldDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.entity.TableDefinition;
import com.developer.enums.DataType;
import com.developer.enums.TableType;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.ProcessDefinitionRepository;
import com.developer.repository.TableDefinitionRepository;
import net.jqwik.api.*;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Property-based test for deployment validation of multi-instance sub-process configurations.
 * 
 * Feature: multi-instance-task-dispatch, Property 14: 部署验证正确性
 * 
 * Property: For any BPMN XML containing multi-instance sub-process, deployment validation passes
 * if and only if: collection variable name format is legal (letters, numbers, underscores) AND
 * the sub-process contains at least one userTask node.
 * 
 * **Validates: Requirements 8.1, 8.2**
 */
public class ProcessDesignComponentDeploymentValidationPropertyTest {

    @Property(tries = 100)
    @Label("Feature: multi-instance-task-dispatch, Property 14: 部署验证正确性")
    void deploymentValidationPassesIffCollectionVariableIsLegalAndUserTaskExists(
            @ForAll("deploymentScenarios") DeploymentScenario scenario) {

        // Setup mocks
        ProcessDefinitionRepository processDefRepo = Mockito.mock(ProcessDefinitionRepository.class);
        FunctionUnitRepository functionUnitRepo = Mockito.mock(FunctionUnitRepository.class);
        TableDefinitionRepository tableDefRepo = Mockito.mock(TableDefinitionRepository.class);
        FormDefinitionRepository formDefRepo = Mockito.mock(FormDefinitionRepository.class);

        ProcessDesignComponent component = new ProcessDesignComponentImpl(
                processDefRepo, functionUnitRepo, tableDefRepo, formDefRepo);

        // Setup table mock to pass other validations
        TableDefinition table = createValidTable(scenario.functionUnitId);
        when(tableDefRepo.findByIdWithFields(1L))
                .thenReturn(Optional.of(table));

        // Generate BPMN XML based on scenario
        String bpmnXml = generateBpmnXml(scenario.collectionVariable, scenario.hasUserTask);

        // Execute validation
        ValidationResult result = component.validateMultiInstance(bpmnXml, scenario.functionUnitId);

        // Verify: validation should pass if and only if both conditions are met
        boolean shouldPass = scenario.isCollectionVariableLegal && scenario.hasUserTask;

        if (shouldPass) {
            assertThat(result.isValid())
                    .as("Validation should pass when collection variable '%s' is legal and userTask exists=%s",
                            scenario.collectionVariable, scenario.hasUserTask)
                    .isTrue();
        } else {
            assertThat(result.isValid())
                    .as("Validation should fail when collection variable '%s' is legal=%s or userTask exists=%s",
                            scenario.collectionVariable, scenario.isCollectionVariableLegal, scenario.hasUserTask)
                    .isFalse();

            // Verify specific error messages
            if (!scenario.isCollectionVariableLegal) {
                assertThat(result.getErrors())
                        .as("Should contain collection variable error")
                        .anyMatch(error -> error.getCode().equals("INVALID_COLLECTION_VARIABLE") 
                                || error.getCode().equals("MISSING_COLLECTION_VARIABLE"));
            }

            if (!scenario.hasUserTask) {
                assertThat(result.getErrors())
                        .as("Should contain missing userTask error")
                        .anyMatch(error -> error.getCode().equals("MISSING_USER_TASK"));
            }
        }
    }

    private static class DeploymentScenario {
        final String collectionVariable;
        final boolean isCollectionVariableLegal;
        final boolean hasUserTask;
        final Long functionUnitId;

        DeploymentScenario(String collectionVariable, boolean hasUserTask, Long functionUnitId) {
            this.collectionVariable = collectionVariable;
            this.isCollectionVariableLegal = isLegalVariableName(collectionVariable);
            this.hasUserTask = hasUserTask;
            this.functionUnitId = functionUnitId;
        }

        /**
         * Check if variable name is legal: must contain only letters, numbers, and underscores,
         * and must start with a letter or underscore.
         */
        private static boolean isLegalVariableName(String name) {
            if (name == null || name.isEmpty()) {
                return false;
            }
            return name.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
        }
    }

    private String generateBpmnXml(String collectionVariable, boolean includeUserTask) {
        String userTaskSection = includeUserTask ? String.format("""
                  <bpmn:startEvent id="MI_Start_1" />
                  <bpmn:userTask id="MI_UserTask_1" name="Test Task">
                    <bpmn:extensionElements>
                      <custom:properties>
                        <custom:property name="assigneeType" value="ELEMENT_VARIABLE" />
                        <custom:property name="subTableId" value="1" />
                        <custom:property name="subTableName" value="test_table" />
                        <custom:property name="assigneeField" value="assignee_id" />
                        <custom:property name="rowIdVariable" value="currentItem.rowId" />
                      </custom:properties>
                    </bpmn:extensionElements>
                  </bpmn:userTask>
                  <bpmn:endEvent id="MI_End_1" />
                """) : """
                  <bpmn:startEvent id="MI_Start_1" />
                  <bpmn:endEvent id="MI_End_1" />
                """;

        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:flowable="http://flowable.org/bpmn"
                              xmlns:custom="http://custom.namespace">
              <bpmn:process id="testProcess">
                <bpmn:subProcess id="MultiInstance_SubTable_1" name="Multi-Instance SubProcess">
                  <bpmn:multiInstanceLoopCharacteristics isSequential="false">
                    <bpmn:extensionElements>
                      <flowable:collection>%s</flowable:collection>
                      <flowable:elementVariable>currentItem</flowable:elementVariable>
                    </bpmn:extensionElements>
                  </bpmn:multiInstanceLoopCharacteristics>
%s
                </bpmn:subProcess>
              </bpmn:process>
            </bpmn:definitions>
            """, collectionVariable, userTaskSection);
    }

    private TableDefinition createValidTable(Long functionUnitId) {
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(functionUnitId)
                .name("function_unit_" + functionUnitId)
                .build();

        TableDefinition table = TableDefinition.builder()
                .id(1L)
                .functionUnit(functionUnit)
                .tableName("test_table")
                .tableDisplayName("Test Table")
                .tableType(TableType.SUB)
                .fieldDefinitions(new ArrayList<>())
                .build();

        table.getFieldDefinitions().add(FieldDefinition.builder()
                .id(1L)
                .tableDefinition(table)
                .fieldName("id")
                .dataType(DataType.BIGINT)
                .nullable(false)
                .isPrimaryKey(true)
                .sortOrder(0)
                .build());

        table.getFieldDefinitions().add(FieldDefinition.builder()
                .id(2L)
                .tableDefinition(table)
                .fieldName("assignee_id")
                .dataType(DataType.VARCHAR)
                .length(64)
                .nullable(true)
                .sortOrder(1)
                .build());

        return table;
    }

    @Provide
    Arbitrary<DeploymentScenario> deploymentScenarios() {
        // Generate legal variable names (start with letter or underscore, followed by alphanumeric or underscore)
        Arbitrary<String> legalVariableNames = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1)
                .ofMaxLength(1)
                .flatMap(prefix -> Arbitraries.strings()
                        .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_")
                        .ofMinLength(0)
                        .ofMaxLength(20)
                        .map(suffix -> prefix + suffix));

        // Generate illegal variable names (various invalid patterns)
        Arbitrary<String> illegalVariableNames = Arbitraries.oneOf(
                // Start with number
                Arbitraries.strings()
                        .withCharRange('0', '9')
                        .ofMinLength(1)
                        .ofMaxLength(1)
                        .flatMap(prefix -> Arbitraries.strings()
                                .withChars("abcdefghijklmnopqrstuvwxyz0123456789_")
                                .ofMinLength(0)
                                .ofMaxLength(10)
                                .map(suffix -> prefix + suffix)),
                // Contains special characters
                Arbitraries.of(
                        "var-name", "var.name", "var name", "var@name", "var#name",
                        "var$name", "var%name", "var&name", "var*name", "var+name",
                        "var=name", "var[name]", "var{name}", "var(name)", "var<name>"
                ),
                // Contains only special characters
                Arbitraries.of("@#$", "---", "...", "***", "123", "456abc")
        );

        // Combine legal and illegal variable names
        Arbitrary<String> collectionVariables = Arbitraries.oneOf(
                legalVariableNames,
                illegalVariableNames
        );

        Arbitrary<Boolean> hasUserTask = Arbitraries.of(true, false);
        Arbitrary<Long> functionUnitIds = Arbitraries.longs().between(1L, 100L);

        return Combinators.combine(collectionVariables, hasUserTask, functionUnitIds)
                .as(DeploymentScenario::new);
    }
}
