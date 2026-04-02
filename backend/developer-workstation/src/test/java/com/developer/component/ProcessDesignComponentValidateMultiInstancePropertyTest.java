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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class ProcessDesignComponentValidateMultiInstancePropertyTest {

    @Property(tries = 100)
    @Label("Feature: multi-instance-task-dispatch, Property 4")
    void validationPassesIffSubTableBelongsToFunctionUnitAndIsSubTypeAndAssigneeFieldExists(
            @ForAll("validationScenarios") ValidationScenario scenario) {

        ProcessDefinitionRepository processDefRepo = Mockito.mock(ProcessDefinitionRepository.class);
        FunctionUnitRepository functionUnitRepo = Mockito.mock(FunctionUnitRepository.class);
        TableDefinitionRepository tableDefRepo = Mockito.mock(TableDefinitionRepository.class);
        FormDefinitionRepository formDefRepo = Mockito.mock(FormDefinitionRepository.class);

        ProcessDesignComponent component = new ProcessDesignComponentImpl(
                processDefRepo, functionUnitRepo, tableDefRepo, formDefRepo);

        if (scenario.tableExists) {
            when(tableDefRepo.findByIdWithFields(scenario.subTableId))
                    .thenReturn(Optional.of(scenario.table));
        } else {
            when(tableDefRepo.findByIdWithFields(scenario.subTableId))
                    .thenReturn(Optional.empty());
        }

        String bpmnXml = generateBpmnXml(scenario.subTableId, scenario.assigneeField);
        ValidationResult result = component.validateMultiInstance(bpmnXml, scenario.functionUnitId);

        boolean shouldPass = scenario.tableExists && scenario.tableBelongsToFunctionUnit
                && scenario.tableTypeIsSub && scenario.assigneeFieldExists;

        assertThat(result.isValid()).isEqualTo(shouldPass);
    }

    private static class ValidationScenario {
        final Long subTableId;
        final Long functionUnitId;
        final String assigneeField;
        final boolean tableExists;
        final boolean tableBelongsToFunctionUnit;
        final boolean tableTypeIsSub;
        final boolean assigneeFieldExists;
        final TableDefinition table;

        ValidationScenario(Long subTableId, Long functionUnitId, String assigneeField,
                boolean tableExists, boolean tableBelongsToFunctionUnit,
                boolean tableTypeIsSub, boolean assigneeFieldExists) {
            this.subTableId = subTableId;
            this.functionUnitId = functionUnitId;
            this.assigneeField = assigneeField;
            this.tableExists = tableExists;
            this.tableBelongsToFunctionUnit = tableBelongsToFunctionUnit;
            this.tableTypeIsSub = tableTypeIsSub;
            this.assigneeFieldExists = assigneeFieldExists;
            this.table = tableExists ? createTable(subTableId,
                    tableBelongsToFunctionUnit ? functionUnitId : functionUnitId + 999,
                    tableTypeIsSub ? TableType.SUB : TableType.MAIN,
                    assigneeFieldExists ? assigneeField : "different_field") : null;
        }

        private static TableDefinition createTable(Long tableId, Long functionUnitId,
                TableType tableType, String fieldName) {
            FunctionUnit functionUnit = FunctionUnit.builder()
                    .id(functionUnitId).name("function_unit_" + functionUnitId).build();

            TableDefinition table = TableDefinition.builder()
                    .id(tableId).functionUnit(functionUnit).tableName("table_" + tableId)
                    .tableDisplayName("Table " + tableId).tableType(tableType)
                    .fieldDefinitions(new ArrayList<>()).build();

            table.getFieldDefinitions().add(FieldDefinition.builder()
                    .id(1L).tableDefinition(table).fieldName("id")
                    .dataType(DataType.BIGINT).nullable(false)
                    .isPrimaryKey(true).sortOrder(0).build());

            table.getFieldDefinitions().add(FieldDefinition.builder()
                    .id(2L).tableDefinition(table).fieldName(fieldName)
                    .dataType(DataType.VARCHAR).length(64).nullable(true)
                    .sortOrder(1).build());

            return table;
        }
    }

    private String generateBpmnXml(Long subTableId, String assigneeField) {
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:flowable="http://flowable.org/bpmn"
                              xmlns:custom="http://custom.namespace">
              <bpmn:process id="testProcess">
                <bpmn:subProcess id="MultiInstance_SubTable_%d" name="Multi-Instance SubProcess">
                  <bpmn:multiInstanceLoopCharacteristics isSequential="false">
                    <bpmn:extensionElements>
                      <flowable:collection>multiInstance_test_collection</flowable:collection>
                      <flowable:elementVariable>currentItem</flowable:elementVariable>
                    </bpmn:extensionElements>
                  </bpmn:multiInstanceLoopCharacteristics>
                  <bpmn:startEvent id="MI_Start_%d" />
                  <bpmn:userTask id="MI_UserTask_%d" name="Test Task">
                    <bpmn:extensionElements>
                      <custom:properties>
                        <custom:property name="assigneeType" value="ELEMENT_VARIABLE" />
                        <custom:property name="subTableId" value="%d" />
                        <custom:property name="subTableName" value="test_table" />
                        <custom:property name="assigneeField" value="%s" />
                        <custom:property name="rowIdVariable" value="currentItem.rowId" />
                      </custom:properties>
                    </bpmn:extensionElements>
                  </bpmn:userTask>
                  <bpmn:endEvent id="MI_End_%d" />
                </bpmn:subProcess>
              </bpmn:process>
            </bpmn:definitions>
            """, subTableId, subTableId, subTableId, subTableId, assigneeField, subTableId);
    }

    @Provide
    Arbitrary<ValidationScenario> validationScenarios() {
        Arbitrary<Long> subTableIds = Arbitraries.longs().between(1L, 1000L);
        Arbitrary<Long> functionUnitIds = Arbitraries.longs().between(1L, 100L);
        Arbitrary<String> assigneeFields = Arbitraries.of(
                "assignee_id", "approver_id", "reviewer_id", "handler_id", "owner_id");
        Arbitrary<Boolean> booleans = Arbitraries.of(true, false);

        return Combinators.combine(subTableIds, functionUnitIds, assigneeFields,
                booleans, booleans, booleans, booleans).as(ValidationScenario::new);
    }
}
