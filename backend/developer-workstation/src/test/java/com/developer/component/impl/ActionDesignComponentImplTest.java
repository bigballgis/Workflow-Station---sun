package com.developer.component.impl;

import com.developer.entity.ActionDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.enums.ActionType;
import com.developer.exception.DeveloperBusinessException;
import com.developer.repository.ActionDefinitionRepository;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.platform.common.i18n.I18nService;
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

    @Mock
    private FormDefinitionRepository formDefinitionRepository;

    @Mock
    private I18nService i18nService;

    @InjectMocks
    private ActionDesignComponentImpl actionDesignComponent;

    @BeforeEach
    void setUp() {
        // 为 i18n 提供 stub，避免 NPE
        lenient().when(i18nService.getMessage(anyString())).thenReturn("mock message");
        lenient().when(i18nService.getMessage(anyString(), any(Object[].class))).thenReturn("mock message with args");
    }

    /**
     * 测试用例 1: 删除被流程引用的动作时抛出 DeveloperBusinessException
     * 验证属性 4: 删除保护 - 被引用的动作
     *
     * <p>BPMN 中 custom:property name="actionIds" value="[1,2]" 引用了该动作的 ID，
     * DOM 解析后应检测到依赖并阻止删除。</p>
     */
    @Test
    void testDelete_WhenActionInUse_ShouldThrowException() {
        // Given: 动作 ID=1 被流程引用于 actionIds=[1,2]
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(1L)
                .name("Test Function")
                .build();

        ProcessDefinition processDefinition = ProcessDefinition.builder()
                .id(1L)
                .functionUnit(functionUnit)
                .bpmnXml("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"" +
                        " xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">" +
                        "<process id=\"testProcess\" name=\"Test\">" +
                        "<userTask id=\"task1\" name=\"TestTask\">" +
                        "<extensionElements>" +
                        "<custom:property name=\"actionIds\" value=\"[1,2]\"/>" +
                        "</extensionElements>" +
                        "</userTask>" +
                        "</process>" +
                        "</definitions>")
                .build();

        functionUnit.setProcessDefinition(processDefinition);

        ActionDefinition action = ActionDefinition.builder()
                .id(1L)
                .actionName("Approve")
                .actionType(ActionType.APPROVE)
                .functionUnit(functionUnit)
                .build();

        when(actionDefinitionRepository.findById(1L)).thenReturn(Optional.of(action));

        // When & Then: 删除应抛出异常
        DeveloperBusinessException exception = assertThrows(DeveloperBusinessException.class, () -> {
            actionDesignComponent.delete(1L);
        });

        assertEquals("ACTION_IN_USE", exception.getErrorCode());
        verify(actionDefinitionRepository, never()).delete(any(ActionDefinition.class));
    }

    /**
     * 测试用例 2: 删除未被引用的动作成功
     * 验证属性 5: 删除成功 - 未被引用的动作
     *
     * <p>BPMN 中 actionIds=[99] 不包含该动作的 ID=1。</p>
     */
    @Test
    void testDelete_WhenActionNotInUse_ShouldSucceed() {
        // Given: 动作 ID=1 未被引用（actionIds 引用的是 [99]）
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(1L)
                .name("Test Function")
                .build();

        ProcessDefinition processDefinition = ProcessDefinition.builder()
                .id(1L)
                .functionUnit(functionUnit)
                .bpmnXml("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">" +
                        "<process id=\"testProcess\" name=\"Test\">" +
                        "<userTask id=\"task1\" name=\"TestTask\">" +
                        "<extensionElements>" +
                        "<custom:property name=\"actionIds\" value=\"[99]\"/>" +
                        "</extensionElements>" +
                        "</userTask>" +
                        "</process>" +
                        "</definitions>")
                .build();

        functionUnit.setProcessDefinition(processDefinition);

        ActionDefinition action = ActionDefinition.builder()
                .id(1L)
                .actionName("Approve")
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
     */
    @Test
    void testDelete_ActionNotInUse_ShouldDeleteSuccessfully() {
        // Given: 动作 ID=5 存在但未被流程引用（actionIds=[1,2] 不包含 5）
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(1L)
                .name("Test Function")
                .build();

        ProcessDefinition processDefinition = ProcessDefinition.builder()
                .id(1L)
                .functionUnit(functionUnit)
                .bpmnXml("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">" +
                        "<process id=\"testProcess\" name=\"Test\">" +
                        "<userTask id=\"task1\" name=\"TestTask\">" +
                        "<extensionElements>" +
                        "<custom:property name=\"actionIds\" value=\"[1,2]\"/>" +
                        "</extensionElements>" +
                        "</userTask>" +
                        "</process>" +
                        "</definitions>")
                .build();

        functionUnit.setProcessDefinition(processDefinition);

        ActionDefinition action = ActionDefinition.builder()
                .id(5L)
                .actionName("CustomAction")
                .actionType(ActionType.API_CALL)
                .functionUnit(functionUnit)
                .build();

        when(actionDefinitionRepository.findById(5L)).thenReturn(Optional.of(action));

        // When: 删除动作
        assertDoesNotThrow(() -> {
            actionDesignComponent.delete(5L);
        });

        // Then: 验证动作已从数据库删除
        verify(actionDefinitionRepository, times(2)).findById(5L);
        verify(actionDefinitionRepository, times(1)).delete(action);
    }

    /**
     * 测试用例 6: 短数字动作名（如 "3"）不应因 BPMN XML 中包含数字 3 而误判为被引用
     *
     * 回归测试：修复 checkActionDependencies 中 contains(actionName) 全量
     * XML 子串匹配导致的假阳性。BPMN XML 中坐标/ID 含有数字 3，但
     * actionIds 并未引用此动作的 ID。
     */
    @Test
    void testDelete_ShortNumericActionName_ShouldNotCauseFalsePositive() {
        // Given: 动作 name="3", id=10，BPMN 中包含坐标和其他数字 3，但 actionIds 不包含 10
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(1L)
                .name("Multi-Instance Subtask Demo")
                .build();

        // 模拟真实的多实例子流程 BPMN XML，包含大量含数字 3 的坐标和属性
        ProcessDefinition processDefinition = ProcessDefinition.builder()
                .id(1L)
                .functionUnit(functionUnit)
                .bpmnXml("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"" +
                        " xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">" +
                        "<bpmn:process id=\"Process_1\" isExecutable=\"true\">" +
                        "  <bpmn:startEvent id=\"StartEvent_1\" name=\"Start\">" +
                        "    <bpmn:outgoing>Flow_123abc</bpmn:outgoing>" +
                        "  </bpmn:startEvent>" +
                        "  <bpmn:userTask id=\"Task_3Approval\" name=\"Multi-Instance Approval\">" +
                        "    <bpmn:incoming>Flow_123abc</bpmn:incoming>" +
                        "    <bpmn:outgoing>Flow_456def</bpmn:outgoing>" +
                        "    <bpmn:extensionElements>" +
                        "      <custom:property name=\"actionIds\" value=\"[1,2,4,5]\"/>" +
                        "      <custom:property name=\"subTableId\" value=\"13\"/>" +
                        "      <custom:property name=\"formId\" value=\"3\"/>" +
                        "    </bpmn:extensionElements>" +
                        "    <bpmn:multiInstanceLoopCharacteristics>" +
                        "      <bpmn:loopCardinality>3</bpmn:loopCardinality>" +
                        "      <bpmn:completionCondition>${nrOfCompletedInstances/nrOfInstances >= 0.3}</bpmn:completionCondition>" +
                        "    </bpmn:multiInstanceLoopCharacteristics>" +
                        "  </bpmn:userTask>" +
                        "  <bpmn:endEvent id=\"EndEvent_1\" name=\"End\">" +
                        "    <bpmn:incoming>Flow_456def</bpmn:incoming>" +
                        "  </bpmn:endEvent>" +
                        "  <bpmn:sequenceFlow id=\"Flow_123abc\" sourceRef=\"StartEvent_1\" targetRef=\"Task_3Approval\"/>" +
                        "  <bpmn:sequenceFlow id=\"Flow_456def\" sourceRef=\"Task_3Approval\" targetRef=\"EndEvent_1\"/>" +
                        "</bpmn:process>" +
                        "</definitions>")
                .build();

        functionUnit.setProcessDefinition(processDefinition);

        ActionDefinition action = ActionDefinition.builder()
                .id(10L)
                .actionName("3")
                .actionType(ActionType.API_CALL)
                .functionUnit(functionUnit)
                .build();

        when(actionDefinitionRepository.findById(10L)).thenReturn(Optional.of(action));

        // When: 删除 name="3" 的动作 — 旧实现会因 bpmnXml.contains("3") 抛出异常
        assertDoesNotThrow(() -> {
            actionDesignComponent.delete(10L);
        });

        // Then: 动作应被成功删除
        verify(actionDefinitionRepository).delete(action);
    }

    /**
     * 测试用例 7: globalActionIds 中引用了动作 ID 时也应阻止删除
     */
    @Test
    void testDelete_WhenActionInGlobalBinding_ShouldThrowException() {
        // Given: 动作 ID=3 被 globalActionIds 引用
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(1L)
                .name("Test Function")
                .build();

        ProcessDefinition processDefinition = ProcessDefinition.builder()
                .id(1L)
                .functionUnit(functionUnit)
                .bpmnXml("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">" +
                        "<process id=\"testProcess\" name=\"Test\">" +
                        "<extensionElements>" +
                        "<custom:property name=\"globalActionIds\" value=\"[3,5,7]\"/>" +
                        "</extensionElements>" +
                        "<userTask id=\"task1\" name=\"TestTask\"/>" +
                        "</process>" +
                        "</definitions>")
                .build();

        functionUnit.setProcessDefinition(processDefinition);

        ActionDefinition action = ActionDefinition.builder()
                .id(3L)
                .actionName("MyAction")
                .actionType(ActionType.APPROVE)
                .functionUnit(functionUnit)
                .build();

        when(actionDefinitionRepository.findById(3L)).thenReturn(Optional.of(action));

        // When & Then: 删除应抛出异常（被 globalActionIds 引用）
        DeveloperBusinessException exception = assertThrows(DeveloperBusinessException.class, () -> {
            actionDesignComponent.delete(3L);
        });

        assertEquals("ACTION_IN_USE", exception.getErrorCode());
        verify(actionDefinitionRepository, never()).delete(any(ActionDefinition.class));
    }
}