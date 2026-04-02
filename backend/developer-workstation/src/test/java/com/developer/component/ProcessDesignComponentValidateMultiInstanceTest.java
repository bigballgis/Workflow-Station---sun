package com.developer.component;

import com.developer.component.impl.ProcessDesignComponentImpl;
import com.developer.dto.ValidationResult;
import com.developer.entity.FieldDefinition;
import com.developer.entity.FormDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.entity.TableDefinition;
import com.developer.enums.DataType;
import com.developer.enums.FormType;
import com.developer.enums.TableType;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.ProcessDefinitionRepository;
import com.developer.repository.TableDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 测试 ProcessDesignComponent.validateMultiInstance() 方法
 * 
 * **Validates: Requirements 2.2, 2.3, 8.1, 8.2, 8.3**
 * 
 * 需求 2.2: 验证 subTableId 属于当前 FunctionUnit 且 table_type=SUB
 * 需求 2.3: 验证 assigneeField 存在于子表的 FieldDefinition 列表中
 * 需求 8.1: 验证 collection 变量名格式合法（字母、数字、下划线）
 * 需求 8.2: 验证子流程内部至少包含一个 userTask
 * 需求 8.3: 验证配置完整性（collection、elementVariable）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProcessDesignComponent - validateMultiInstance")
class ProcessDesignComponentValidateMultiInstanceTest {

    @Mock
    private ProcessDefinitionRepository processDefinitionRepository;
    
    @Mock
    private FunctionUnitRepository functionUnitRepository;
    
    @Mock
    private TableDefinitionRepository tableDefinitionRepository;
    
    @Mock
    private FormDefinitionRepository formDefinitionRepository;
    
    private ProcessDesignComponent processDesignComponent;
    
    private static final Long FUNCTION_UNIT_ID = 1L;
    private static final Long SUB_TABLE_ID = 100L;
    private static final Long FORM_ID = 200L;
    
    @BeforeEach
    void setUp() {
        processDesignComponent = new ProcessDesignComponentImpl(
            processDefinitionRepository,
            functionUnitRepository,
            tableDefinitionRepository,
            formDefinitionRepository
        );
    }
    
    @Test
    @DisplayName("Should pass validation for valid multi-instance configuration")
    void shouldPassValidationForValidConfiguration() {
        // Given: 有效的多实例子流程 BPMN XML
        String bpmnXml = createValidMultiInstanceBpmn();
        
        // Mock: 子表存在且属于当前 FunctionUnit
        TableDefinition subTable = createValidSubTable();
        when(tableDefinitionRepository.findByIdWithFields(SUB_TABLE_ID))
            .thenReturn(Optional.of(subTable));
        
        // Mock: 表单存在且属于当前 FunctionUnit
        FormDefinition form = createValidForm();
        when(formDefinitionRepository.findById(FORM_ID))
            .thenReturn(Optional.of(form));
        
        // When: 验证多实例配置
        ValidationResult result = processDesignComponent.validateMultiInstance(bpmnXml, FUNCTION_UNIT_ID);
        
        // Then: 验证通过
        assertThat(result.isValid())
            .as("Validation should pass for valid configuration")
            .isTrue();
        
        assertThat(result.getErrors())
            .as("Should have no errors")
            .isEmpty();
    }
    
    @Test
    @DisplayName("Should fail validation when collection variable name is invalid")
    void shouldFailValidationForInvalidCollectionVariableName() {
        // Given: collection 变量名包含非法字符
        String bpmnXml = createBpmnWithInvalidCollectionVariable();
        
        // When: 验证多实例配置
        ValidationResult result = processDesignComponent.validateMultiInstance(bpmnXml, FUNCTION_UNIT_ID);
        
        // Then: 验证失败
        assertThat(result.isValid())
            .as("Validation should fail for invalid collection variable name")
            .isFalse();
        
        assertThat(result.getErrors())
            .as("Should have INVALID_COLLECTION_VARIABLE error")
            .anyMatch(error -> error.getCode().equals("INVALID_COLLECTION_VARIABLE"));
    }
    
    @Test
    @DisplayName("Should fail validation when subProcess has no userTask")
    void shouldFailValidationWhenNoUserTask() {
        // Given: 子流程内没有 userTask
        String bpmnXml = createBpmnWithoutUserTask();
        
        // When: 验证多实例配置
        ValidationResult result = processDesignComponent.validateMultiInstance(bpmnXml, FUNCTION_UNIT_ID);
        
        // Then: 验证失败
        assertThat(result.isValid())
            .as("Validation should fail when subProcess has no userTask")
            .isFalse();
        
        assertThat(result.getErrors())
            .as("Should have MISSING_USER_TASK error")
            .anyMatch(error -> error.getCode().equals("MISSING_USER_TASK"));
    }
    
    @Test
    @DisplayName("Should fail validation when subTable does not belong to FunctionUnit")
    void shouldFailValidationWhenSubTableWrongFunctionUnit() {
        // Given: 子表属于其他 FunctionUnit
        String bpmnXml = createValidMultiInstanceBpmn();
        
        TableDefinition subTable = createSubTableWithDifferentFunctionUnit();
        when(tableDefinitionRepository.findByIdWithFields(SUB_TABLE_ID))
            .thenReturn(Optional.of(subTable));
        
        // When: 验证多实例配置
        ValidationResult result = processDesignComponent.validateMultiInstance(bpmnXml, FUNCTION_UNIT_ID);
        
        // Then: 验证失败
        assertThat(result.isValid())
            .as("Validation should fail when subTable belongs to different FunctionUnit")
            .isFalse();
        
        assertThat(result.getErrors())
            .as("Should have SUBTABLE_WRONG_FUNCTION_UNIT error")
            .anyMatch(error -> error.getCode().equals("SUBTABLE_WRONG_FUNCTION_UNIT"));
    }
    
    @Test
    @DisplayName("Should fail validation when table type is not SUB")
    void shouldFailValidationWhenTableTypeNotSub() {
        // Given: 表类型不是 SUB
        String bpmnXml = createValidMultiInstanceBpmn();
        
        TableDefinition mainTable = createMainTable();
        when(tableDefinitionRepository.findByIdWithFields(SUB_TABLE_ID))
            .thenReturn(Optional.of(mainTable));
        
        // When: 验证多实例配置
        ValidationResult result = processDesignComponent.validateMultiInstance(bpmnXml, FUNCTION_UNIT_ID);
        
        // Then: 验证失败
        assertThat(result.isValid())
            .as("Validation should fail when table type is not SUB")
            .isFalse();
        
        assertThat(result.getErrors())
            .as("Should have INVALID_TABLE_TYPE error")
            .anyMatch(error -> error.getCode().equals("INVALID_TABLE_TYPE"));
    }
    
    @Test
    @DisplayName("Should fail validation when assigneeField does not exist in subTable")
    void shouldFailValidationWhenAssigneeFieldNotFound() {
        // Given: assigneeField 不存在于子表中
        String bpmnXml = createBpmnWithNonExistentAssigneeField();
        
        TableDefinition subTable = createValidSubTable();
        when(tableDefinitionRepository.findByIdWithFields(SUB_TABLE_ID))
            .thenReturn(Optional.of(subTable));
        
        // When: 验证多实例配置
        ValidationResult result = processDesignComponent.validateMultiInstance(bpmnXml, FUNCTION_UNIT_ID);
        
        // Then: 验证失败
        assertThat(result.isValid())
            .as("Validation should fail when assigneeField does not exist")
            .isFalse();
        
        assertThat(result.getErrors())
            .as("Should have ASSIGNEE_FIELD_NOT_FOUND error")
            .anyMatch(error -> error.getCode().equals("ASSIGNEE_FIELD_NOT_FOUND"));
    }
    
    @Test
    @DisplayName("Should fail validation when formId does not belong to FunctionUnit")
    void shouldFailValidationWhenFormWrongFunctionUnit() {
        // Given: 表单属于其他 FunctionUnit
        String bpmnXml = createValidMultiInstanceBpmn();
        
        TableDefinition subTable = createValidSubTable();
        when(tableDefinitionRepository.findByIdWithFields(SUB_TABLE_ID))
            .thenReturn(Optional.of(subTable));
        
        FormDefinition form = createFormWithDifferentFunctionUnit();
        when(formDefinitionRepository.findById(FORM_ID))
            .thenReturn(Optional.of(form));
        
        // When: 验证多实例配置
        ValidationResult result = processDesignComponent.validateMultiInstance(bpmnXml, FUNCTION_UNIT_ID);
        
        // Then: 验证失败
        assertThat(result.isValid())
            .as("Validation should fail when form belongs to different FunctionUnit")
            .isFalse();
        
        assertThat(result.getErrors())
            .as("Should have FORM_WRONG_FUNCTION_UNIT error")
            .anyMatch(error -> error.getCode().equals("FORM_WRONG_FUNCTION_UNIT"));
    }
    
    @Test
    @DisplayName("Should fail validation when subTable not found")
    void shouldFailValidationWhenSubTableNotFound() {
        // Given: 子表不存在
        String bpmnXml = createValidMultiInstanceBpmn();
        
        when(tableDefinitionRepository.findByIdWithFields(SUB_TABLE_ID))
            .thenReturn(Optional.empty());
        
        // When: 验证多实例配置
        ValidationResult result = processDesignComponent.validateMultiInstance(bpmnXml, FUNCTION_UNIT_ID);
        
        // Then: 验证失败
        assertThat(result.isValid())
            .as("Validation should fail when subTable not found")
            .isFalse();
        
        assertThat(result.getErrors())
            .as("Should have SUBTABLE_NOT_FOUND error")
            .anyMatch(error -> error.getCode().equals("SUBTABLE_NOT_FOUND"));
    }
    
    @Test
    @DisplayName("Should fail validation when collection variable is missing")
    void shouldFailValidationWhenCollectionVariableMissing() {
        // Given: 缺少 collection 变量配置
        String bpmnXml = createBpmnWithoutCollectionVariable();
        
        // When: 验证多实例配置
        ValidationResult result = processDesignComponent.validateMultiInstance(bpmnXml, FUNCTION_UNIT_ID);
        
        // Then: 验证失败
        assertThat(result.isValid())
            .as("Validation should fail when collection variable is missing")
            .isFalse();
        
        assertThat(result.getErrors())
            .as("Should have MISSING_COLLECTION_VARIABLE error")
            .anyMatch(error -> error.getCode().equals("MISSING_COLLECTION_VARIABLE"));
    }
    
    // NOTE: Test for missing elementVariable is not implemented because
    // the validateMultiInstance() method in ProcessDesignComponentImpl
    // does not currently validate elementVariable presence.
    // This is a gap in the implementation of task 3.1.
    // The test should be added once the implementation is updated to include
    // elementVariable validation as required by requirement 8.3.
    
    // Helper methods to create test data
    
    private String createValidMultiInstanceBpmn() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:flowable="http://flowable.org/bpmn"
                              xmlns:custom="http://custom.namespace">
              <bpmn:process id="testProcess">
                <bpmn:subProcess id="MultiInstance_SubTable_100" name="Multi-Instance SubProcess">
                  <bpmn:multiInstanceLoopCharacteristics isSequential="false">
                    <bpmn:extensionElements>
                      <flowable:collection>multiInstance_participants_collection</flowable:collection>
                      <flowable:elementVariable>currentItem</flowable:elementVariable>
                    </bpmn:extensionElements>
                  </bpmn:multiInstanceLoopCharacteristics>
                  
                  <bpmn:startEvent id="MI_Start_100" />
                  
                  <bpmn:userTask id="MI_UserTask_100" name="Fill Information">
                    <bpmn:extensionElements>
                      <custom:properties>
                        <custom:property name="assigneeType" value="ELEMENT_VARIABLE" />
                        <custom:property name="subTableId" value="100" />
                        <custom:property name="subTableName" value="participants" />
                        <custom:property name="assigneeField" value="assignee_id" />
                        <custom:property name="rowIdVariable" value="currentItem.rowId" />
                        <custom:property name="formId" value="200" />
                      </custom:properties>
                    </bpmn:extensionElements>
                  </bpmn:userTask>
                  
                  <bpmn:endEvent id="MI_End_100" />
                </bpmn:subProcess>
              </bpmn:process>
            </bpmn:definitions>
            """;
    }
    
    private String createBpmnWithInvalidCollectionVariable() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:flowable="http://flowable.org/bpmn"
                              xmlns:custom="http://custom.namespace">
              <bpmn:process id="testProcess">
                <bpmn:subProcess id="MultiInstance_SubTable_100">
                  <bpmn:multiInstanceLoopCharacteristics isSequential="false">
                    <bpmn:extensionElements>
                      <flowable:collection>invalid-collection-name</flowable:collection>
                      <flowable:elementVariable>currentItem</flowable:elementVariable>
                    </bpmn:extensionElements>
                  </bpmn:multiInstanceLoopCharacteristics>
                  
                  <bpmn:userTask id="MI_UserTask_100" name="Fill Information">
                    <bpmn:extensionElements>
                      <custom:properties>
                        <custom:property name="assigneeType" value="ELEMENT_VARIABLE" />
                        <custom:property name="subTableId" value="100" />
                        <custom:property name="assigneeField" value="assignee_id" />
                      </custom:properties>
                    </bpmn:extensionElements>
                  </bpmn:userTask>
                </bpmn:subProcess>
              </bpmn:process>
            </bpmn:definitions>
            """;
    }
    
    private String createBpmnWithoutUserTask() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:flowable="http://flowable.org/bpmn">
              <bpmn:process id="testProcess">
                <bpmn:subProcess id="MultiInstance_SubTable_100">
                  <bpmn:multiInstanceLoopCharacteristics isSequential="false">
                    <bpmn:extensionElements>
                      <flowable:collection>multiInstance_participants_collection</flowable:collection>
                      <flowable:elementVariable>currentItem</flowable:elementVariable>
                    </bpmn:extensionElements>
                  </bpmn:multiInstanceLoopCharacteristics>
                  
                  <bpmn:startEvent id="MI_Start_100" />
                  <bpmn:endEvent id="MI_End_100" />
                </bpmn:subProcess>
              </bpmn:process>
            </bpmn:definitions>
            """;
    }
    
    private String createBpmnWithNonExistentAssigneeField() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:flowable="http://flowable.org/bpmn"
                              xmlns:custom="http://custom.namespace">
              <bpmn:process id="testProcess">
                <bpmn:subProcess id="MultiInstance_SubTable_100">
                  <bpmn:multiInstanceLoopCharacteristics isSequential="false">
                    <bpmn:extensionElements>
                      <flowable:collection>multiInstance_participants_collection</flowable:collection>
                      <flowable:elementVariable>currentItem</flowable:elementVariable>
                    </bpmn:extensionElements>
                  </bpmn:multiInstanceLoopCharacteristics>
                  
                  <bpmn:userTask id="MI_UserTask_100" name="Fill Information">
                    <bpmn:extensionElements>
                      <custom:properties>
                        <custom:property name="assigneeType" value="ELEMENT_VARIABLE" />
                        <custom:property name="subTableId" value="100" />
                        <custom:property name="assigneeField" value="non_existent_field" />
                      </custom:properties>
                    </bpmn:extensionElements>
                  </bpmn:userTask>
                </bpmn:subProcess>
              </bpmn:process>
            </bpmn:definitions>
            """;
    }
    
    private String createBpmnWithoutCollectionVariable() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:flowable="http://flowable.org/bpmn"
                              xmlns:custom="http://custom.namespace">
              <bpmn:process id="testProcess">
                <bpmn:subProcess id="MultiInstance_SubTable_100">
                  <bpmn:multiInstanceLoopCharacteristics isSequential="false">
                    <bpmn:extensionElements>
                      <flowable:elementVariable>currentItem</flowable:elementVariable>
                    </bpmn:extensionElements>
                  </bpmn:multiInstanceLoopCharacteristics>
                  
                  <bpmn:userTask id="MI_UserTask_100" name="Fill Information">
                    <bpmn:extensionElements>
                      <custom:properties>
                        <custom:property name="assigneeType" value="ELEMENT_VARIABLE" />
                        <custom:property name="subTableId" value="100" />
                        <custom:property name="assigneeField" value="assignee_id" />
                      </custom:properties>
                    </bpmn:extensionElements>
                  </bpmn:userTask>
                </bpmn:subProcess>
              </bpmn:process>
            </bpmn:definitions>
            """;
    }
    
    private TableDefinition createValidSubTable() {
        FunctionUnit functionUnit = FunctionUnit.builder()
            .id(FUNCTION_UNIT_ID)
            .name("test_function_unit")
            .build();
        
        TableDefinition table = TableDefinition.builder()
            .id(SUB_TABLE_ID)
            .functionUnit(functionUnit)
            .tableName("participants")
            .tableDisplayName("Participants")
            .tableType(TableType.SUB)
            .fieldDefinitions(new ArrayList<>())
            .build();
        
        // 添加字段定义
        List<FieldDefinition> fields = new ArrayList<>();
        
        fields.add(FieldDefinition.builder()
            .id(1L)
            .tableDefinition(table)
            .fieldName("id")
            .dataType(DataType.BIGINT)
            .nullable(false)
            .isPrimaryKey(true)
            .sortOrder(0)
            .build());
        
        fields.add(FieldDefinition.builder()
            .id(2L)
            .tableDefinition(table)
            .fieldName("name")
            .dataType(DataType.VARCHAR)
            .length(100)
            .nullable(false)
            .sortOrder(1)
            .build());
        
        fields.add(FieldDefinition.builder()
            .id(3L)
            .tableDefinition(table)
            .fieldName("assignee_id")
            .dataType(DataType.VARCHAR)
            .length(64)
            .nullable(true)
            .sortOrder(2)
            .build());
        
        table.getFieldDefinitions().addAll(fields);
        
        return table;
    }
    
    private TableDefinition createSubTableWithDifferentFunctionUnit() {
        FunctionUnit differentFunctionUnit = FunctionUnit.builder()
            .id(999L)  // Different FunctionUnit ID
            .name("different_function_unit")
            .build();
        
        TableDefinition table = createValidSubTable();
        table.setFunctionUnit(differentFunctionUnit);
        
        return table;
    }
    
    private TableDefinition createMainTable() {
        FunctionUnit functionUnit = FunctionUnit.builder()
            .id(FUNCTION_UNIT_ID)
            .name("test_function_unit")
            .build();
        
        TableDefinition table = TableDefinition.builder()
            .id(SUB_TABLE_ID)
            .functionUnit(functionUnit)
            .tableName("meetings")
            .tableDisplayName("Meetings")
            .tableType(TableType.MAIN)  // MAIN type instead of SUB
            .fieldDefinitions(new ArrayList<>())
            .build();
        
        return table;
    }
    
    private FormDefinition createValidForm() {
        FunctionUnit functionUnit = FunctionUnit.builder()
            .id(FUNCTION_UNIT_ID)
            .name("test_function_unit")
            .build();
        
        return FormDefinition.builder()
            .id(FORM_ID)
            .functionUnit(functionUnit)
            .formName("participant_form")
            .formType(FormType.TASK)
            .configJson(new HashMap<>())
            .build();
    }
    
    private FormDefinition createFormWithDifferentFunctionUnit() {
        FunctionUnit differentFunctionUnit = FunctionUnit.builder()
            .id(999L)  // Different FunctionUnit ID
            .name("different_function_unit")
            .build();
        
        return FormDefinition.builder()
            .id(FORM_ID)
            .functionUnit(differentFunctionUnit)
            .formName("participant_form")
            .formType(FormType.TASK)
            .configJson(new HashMap<>())
            .build();
    }
}
