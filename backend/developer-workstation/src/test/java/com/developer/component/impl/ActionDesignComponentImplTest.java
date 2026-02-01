package com.developer.component.impl;

import com.developer.entity.ActionDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.enums.ActionType;
import com.developer.exception.BusinessException;
import com.developer.repository.ActionDefinitionRepository;
import com.developer.repository.FunctionUnitRepository;
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
 * 测试 ActionDesignComponentImpl 的删除保护功能
 * 
 * 验证属性 4: 删除保护 - 被引用的动作
 * 验证属性 5: 删除成功 - 未被引用的动作
 */
@ExtendWith(MockitoExtension.class)
class ActionDesignComponentImplTest {
    
    @Mock
    private ActionDefinitionRepository actionDefinitionRepository;
    
    @Mock
    private FunctionUnitRepository functionUnitRepository;
    
    @InjectMocks
    private ActionDesignComponentImpl actionDesignComponent;
    
    @BeforeEach
    void setUp() {
        // 测试前准备
    }
    
    /**
     * 测试用例 1: 删除被流程引用的动作时抛出 BusinessException
     * 验证属性 4: 删除保护 - 被引用的动作
     */
    @Test
    void testDelete_WhenActionInUse_ShouldThrowException() {
        // Given: 动作被流程引用
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(1L)
                .name("Test Function")
                .build();
        
        ProcessDefinition processDefinition = ProcessDefinition.builder()
                .id(1L)
                .functionUnit(functionUnit)
                .bpmnXml("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<definitions>" +
                        "<userTask id=\"task1\" name=\"TestTask\">" +
                        "<extensionElements>" +
                        "<actionProperty id=\"action\" value=\"TestAction\"/>" +
                        "</extensionElements>" +
                        "</userTask>" +
                        "</definitions>")
                .build();
        
        functionUnit.setProcessDefinition(processDefinition);
        
        ActionDefinition action = ActionDefinition.builder()
                .id(1L)
                .actionName("TestAction")
                .actionType(ActionType.APPROVE)
                .functionUnit(functionUnit)
                .build();
        
        when(actionDefinitionRepository.findById(1L)).thenReturn(Optional.of(action));
        
        // When & Then: 删除应抛出异常
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            actionDesignComponent.delete(1L);
        });
        
        assertEquals("ACTION_IN_USE", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("无法删除动作：该动作正在被流程定义使用"));
        verify(actionDefinitionRepository, never()).delete(any(ActionDefinition.class));
    }
    
    /**
     * 测试用例 2: 删除未被引用的动作成功
     * 验证属性 5: 删除成功 - 未被引用的动作
     */
    @Test
    void testDelete_WhenActionNotInUse_ShouldSucceed() {
        // Given: 动作未被引用
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(1L)
                .name("Test Function")
                .build();
        
        ProcessDefinition processDefinition = ProcessDefinition.builder()
                .id(1L)
                .functionUnit(functionUnit)
                .bpmnXml("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<definitions>" +
                        "<userTask id=\"task1\" name=\"TestTask\">" +
                        "<extensionElements>" +
                        "<actionProperty id=\"action\" value=\"OtherAction\"/>" +
                        "</extensionElements>" +
                        "</userTask>" +
                        "</definitions>")
                .build();
        
        functionUnit.setProcessDefinition(processDefinition);
        
        ActionDefinition action = ActionDefinition.builder()
                .id(1L)
                .actionName("TestAction")
                .actionType(ActionType.APPROVE)
                .functionUnit(functionUnit)
                .build();
        
        when(actionDefinitionRepository.findById(1L)).thenReturn(Optional.of(action));
        
        // When: 删除动作
        assertDoesNotThrow(() -> {
            actionDesignComponent.delete(1L);
        });
        
        // Then: 动作应被删除
        verify(actionDefinitionRepository).delete(action);
    }
    
    /**
     * 测试用例 3: 删除动作时没有流程定义
     * 验证属性 5: 删除成功 - 未被引用的动作
     */
    @Test
    void testDelete_WhenNoProcessDefinition_ShouldSucceed() {
        // Given: 功能单元没有流程定义
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(1L)
                .name("Test Function")
                .processDefinition(null)
                .build();
        
        ActionDefinition action = ActionDefinition.builder()
                .id(1L)
                .actionName("TestAction")
                .actionType(ActionType.APPROVE)
                .functionUnit(functionUnit)
                .build();
        
        when(actionDefinitionRepository.findById(1L)).thenReturn(Optional.of(action));
        
        // When: 删除动作
        assertDoesNotThrow(() -> {
            actionDesignComponent.delete(1L);
        });
        
        // Then: 动作应被删除
        verify(actionDefinitionRepository).delete(action);
    }
    
    /**
     * 测试用例 4: 删除动作时 BPMN XML 为空
     * 验证属性 5: 删除成功 - 未被引用的动作
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
        
        ActionDefinition action = ActionDefinition.builder()
                .id(1L)
                .actionName("TestAction")
                .actionType(ActionType.APPROVE)
                .functionUnit(functionUnit)
                .build();
        
        when(actionDefinitionRepository.findById(1L)).thenReturn(Optional.of(action));
        
        // When: 删除动作
        assertDoesNotThrow(() -> {
            actionDesignComponent.delete(1L);
        });
        
        // Then: 动作应被删除
        verify(actionDefinitionRepository).delete(action);
    }
    
    /**
     * 测试用例 5: 删除未被引用的动作成功 - 完整测试
     * 验证属性 5: 删除成功 - 未被引用的动作
     * 任务 5.2: 为 ActionDesignComponentImpl 编写删除成功测试
     */
    @Test
    void testDelete_ActionNotInUse_ShouldDeleteSuccessfully() {
        // Given: 动作存在但未被流程引用
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(1L)
                .name("Test Function")
                .build();
        
        ProcessDefinition processDefinition = ProcessDefinition.builder()
                .id(1L)
                .functionUnit(functionUnit)
                .bpmnXml("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<definitions>" +
                        "<userTask id=\"task1\" name=\"TestTask\">" +
                        "<extensionElements>" +
                        "<actionProperty id=\"action\" value=\"DifferentAction\"/>" +
                        "</extensionElements>" +
                        "</userTask>" +
                        "</definitions>")
                .build();
        
        functionUnit.setProcessDefinition(processDefinition);
        
        ActionDefinition action = ActionDefinition.builder()
                .id(1L)
                .actionName("TestAction")
                .actionType(ActionType.APPROVE)
                .functionUnit(functionUnit)
                .build();
        
        // Mock repository calls
        when(actionDefinitionRepository.findById(1L)).thenReturn(Optional.of(action));
        
        // When: 删除动作
        assertDoesNotThrow(() -> {
            actionDesignComponent.delete(1L);
        });
        
        // Then: 验证动作已从数据库删除
        verify(actionDefinitionRepository, times(2)).findById(1L); // Called in getById() and checkActionDependencies()
        verify(actionDefinitionRepository, times(1)).delete(action);
    }
}