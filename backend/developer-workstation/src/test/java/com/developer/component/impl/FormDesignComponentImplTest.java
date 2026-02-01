package com.developer.component.impl;

import com.developer.entity.FormDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.exception.BusinessException;
import com.developer.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 测试 FormDesignComponentImpl 的删除保护功能
 * 
 * 验证属性 2: 删除保护 - 被引用的表单
 * 验证属性 3: 删除成功 - 未被引用的表单
 */
@ExtendWith(MockitoExtension.class)
class FormDesignComponentImplTest {
    
    @Mock
    private FormDefinitionRepository formDefinitionRepository;
    
    @Mock
    private FunctionUnitRepository functionUnitRepository;
    
    @Mock
    private TableDefinitionRepository tableDefinitionRepository;
    
    @Mock
    private FormTableBindingRepository formTableBindingRepository;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @InjectMocks
    private FormDesignComponentImpl formDesignComponent;
    
    @BeforeEach
    void setUp() {
        // 测试前准备
    }
    
    /**
     * 测试用例 1: 删除被流程引用的表单时抛出 BusinessException
     * 验证属性 2: 删除保护 - 被引用的表单
     */
    @Test
    void testDelete_WhenFormInUse_ShouldThrowException() {
        // Given: 表单被流程引用
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(1L)
                .name("Test Function")
                .build();
        
        ProcessDefinition processDefinition = ProcessDefinition.builder()
                .id(1L)
                .functionUnit(functionUnit)
                .bpmnXml("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<definitions>" +
                        "<userTask id=\"task1\" name=\"TestForm\">" +
                        "<extensionElements>" +
                        "<formProperty id=\"form\" value=\"TestForm\"/>" +
                        "</extensionElements>" +
                        "</userTask>" +
                        "</definitions>")
                .build();
        
        functionUnit.setProcessDefinition(processDefinition);
        
        FormDefinition form = FormDefinition.builder()
                .id(1L)
                .formName("TestForm")
                .functionUnit(functionUnit)
                .build();
        
        when(formDefinitionRepository.findByIdWithBindings(1L)).thenReturn(Optional.of(form));
        when(formDefinitionRepository.findById(1L)).thenReturn(Optional.of(form));
        
        // When & Then: 删除应抛出异常
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            formDesignComponent.delete(1L);
        });
        
        assertEquals("FORM_IN_USE", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("无法删除表单：该表单正在被流程定义使用"));
        verify(formDefinitionRepository, never()).delete(any(FormDefinition.class));
    }
    
    /**
     * 测试用例 2: 删除未被引用的表单成功
     * 验证属性 3: 删除成功 - 未被引用的表单
     */
    @Test
    void testDelete_WhenFormNotInUse_ShouldSucceed() {
        // Given: 表单未被引用
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(1L)
                .name("Test Function")
                .build();
        
        ProcessDefinition processDefinition = ProcessDefinition.builder()
                .id(1L)
                .functionUnit(functionUnit)
                .bpmnXml("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<definitions>" +
                        "<userTask id=\"task1\" name=\"OtherForm\">" +
                        "</userTask>" +
                        "</definitions>")
                .build();
        
        functionUnit.setProcessDefinition(processDefinition);
        
        FormDefinition form = FormDefinition.builder()
                .id(1L)
                .formName("TestForm")
                .functionUnit(functionUnit)
                .build();
        
        when(formDefinitionRepository.findByIdWithBindings(1L)).thenReturn(Optional.of(form));
        when(formDefinitionRepository.findById(1L)).thenReturn(Optional.of(form));
        
        // When: 删除表单
        assertDoesNotThrow(() -> {
            formDesignComponent.delete(1L);
        });
        
        // Then: 表单应被删除
        verify(formDefinitionRepository).delete(form);
    }
    
    /**
     * 测试用例 3: 删除表单时没有流程定义
     * 验证属性 3: 删除成功 - 未被引用的表单
     */
    @Test
    void testDelete_WhenNoProcessDefinition_ShouldSucceed() {
        // Given: 功能单元没有流程定义
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(1L)
                .name("Test Function")
                .processDefinition(null)
                .build();
        
        FormDefinition form = FormDefinition.builder()
                .id(1L)
                .formName("TestForm")
                .functionUnit(functionUnit)
                .build();
        
        when(formDefinitionRepository.findByIdWithBindings(1L)).thenReturn(Optional.of(form));
        when(formDefinitionRepository.findById(1L)).thenReturn(Optional.of(form));
        
        // When: 删除表单
        assertDoesNotThrow(() -> {
            formDesignComponent.delete(1L);
        });
        
        // Then: 表单应被删除
        verify(formDefinitionRepository).delete(form);
    }
    
    /**
     * 测试用例 4: 删除表单时 BPMN XML 为空
     * 验证属性 3: 删除成功 - 未被引用的表单
     */
    @Test
    void testDelete_WhenBpmnXmlIsNull_ShouldSucceed() {
        // Given: 流程定义的 BPMN XML 为空
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(1L)
                .name("Test Function")
                .build();
        
        ProcessDefinition processDefinition = ProcessDefinition.builder()
                .id(1L)
                .functionUnit(functionUnit)
                .bpmnXml(null)
                .build();
        
        functionUnit.setProcessDefinition(processDefinition);
        
        FormDefinition form = FormDefinition.builder()
                .id(1L)
                .formName("TestForm")
                .functionUnit(functionUnit)
                .build();
        
        when(formDefinitionRepository.findByIdWithBindings(1L)).thenReturn(Optional.of(form));
        when(formDefinitionRepository.findById(1L)).thenReturn(Optional.of(form));
        
        // When: 删除表单
        assertDoesNotThrow(() -> {
            formDesignComponent.delete(1L);
        });
        
        // Then: 表单应被删除
        verify(formDefinitionRepository).delete(form);
    }
}