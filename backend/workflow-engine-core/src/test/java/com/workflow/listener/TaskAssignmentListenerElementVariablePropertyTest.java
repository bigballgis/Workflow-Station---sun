package com.workflow.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import com.workflow.repository.ExtendedTaskInfoRepository;
import net.jqwik.api.*;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.event.impl.FlowableEntityEventImpl;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * TaskAssignmentListener ELEMENT_VARIABLE 分配类型属性测试
 * 
 * Property 6: 多实例子任务创建与分配正确性
 * 
 * For any 多实例子任务创建事件，TaskAssignmentListener 应将任务分配给 elementVariable 中指定的处理人，
 * 并创建 ExtendedTaskInfo 记录（assignment_type=USER，assignment_target=处理人 ID，
 * extended_properties 包含 subTableRowId）。
 * 
 * **Validates: Requirements 4.2, 4.3, 4.4**
 */
public class TaskAssignmentListenerElementVariablePropertyTest {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Property 6: 多实例子任务创建与分配正确性
     * 
     * 随机生成 elementVariable 内容，验证 ExtendedTaskInfo 记录正确
     */
    @Property(tries = 100)
    @Label("Feature: multi-instance-task-dispatch, Property 6: 多实例子任务创建与分配正确性")
    void taskAssignmentListenerCreatesCorrectExtendedTaskInfoForElementVariable(
            @ForAll("elementVariableScenarios") ElementVariableScenario scenario) throws Exception {
        
        // Given: 创建 mock 对象
        TaskService taskService = mock(TaskService.class);
        RuntimeService runtimeService = mock(RuntimeService.class);
        RepositoryService repositoryService = mock(RepositoryService.class);
        ExtendedTaskInfoRepository extendedTaskInfoRepository = mock(ExtendedTaskInfoRepository.class);
        
        TaskAssignmentListener listener = new TaskAssignmentListener();
        
        // 使用反射注入依赖
        injectField(listener, "taskService", taskService);
        injectField(listener, "runtimeService", runtimeService);
        injectField(listener, "repositoryService", repositoryService);
        injectField(listener, "extendedTaskInfoRepository", extendedTaskInfoRepository);
        
        // 创建 mock TaskEntity
        TaskEntity task = mock(TaskEntity.class);
        when(task.getId()).thenReturn(scenario.taskId);
        when(task.getProcessInstanceId()).thenReturn(scenario.processInstanceId);
        when(task.getProcessDefinitionId()).thenReturn(scenario.processDefinitionId);
        when(task.getTaskDefinitionKey()).thenReturn(scenario.taskDefinitionKey);
        when(task.getName()).thenReturn(scenario.taskName);
        when(task.getAssignee()).thenReturn(null); // 任务未分配
        when(task.getExecutionId()).thenReturn(scenario.executionId);
        
        // 创建 BPMN 模型，包含 ELEMENT_VARIABLE 扩展属性
        BpmnModel bpmnModel = createBpmnModelWithElementVariable(
            scenario.taskDefinitionKey,
            scenario.subTableId,
            scenario.subTableName
        );
        when(repositoryService.getBpmnModel(scenario.processDefinitionId)).thenReturn(bpmnModel);
        
        // 设置 currentItem 变量
        when(runtimeService.getVariable(scenario.executionId, "currentItem"))
            .thenReturn(scenario.currentItem);
        
        // 创建 TASK_CREATED 事件
        FlowableEntityEventImpl event = mock(FlowableEntityEventImpl.class);
        when(event.getType()).thenReturn(FlowableEngineEventType.TASK_CREATED);
        when(event.getEntity()).thenReturn(task);
        
        // When: 触发事件
        listener.onEvent(event);
        
        // Then: 验证任务分配
        verify(taskService).setAssignee(scenario.taskId, scenario.expectedAssigneeId);
        
        // 验证 ExtendedTaskInfo 创建
        ArgumentCaptor<ExtendedTaskInfo> captor = ArgumentCaptor.forClass(ExtendedTaskInfo.class);
        verify(extendedTaskInfoRepository).save(captor.capture());
        
        ExtendedTaskInfo savedInfo = captor.getValue();
        
        // 验证 1: 基本字段正确
        assertThat(savedInfo.getTaskId()).isEqualTo(scenario.taskId);
        assertThat(savedInfo.getProcessInstanceId()).isEqualTo(scenario.processInstanceId);
        assertThat(savedInfo.getProcessDefinitionId()).isEqualTo(scenario.processDefinitionId);
        assertThat(savedInfo.getTaskDefinitionKey()).isEqualTo(scenario.taskDefinitionKey);
        assertThat(savedInfo.getTaskName()).isEqualTo(scenario.taskName);
        
        // 验证 2: assignment_type = USER
        assertThat(savedInfo.getAssignmentType()).isEqualTo(AssignmentType.USER);
        
        // 验证 3: assignment_target = 处理人 ID
        assertThat(savedInfo.getAssignmentTarget()).isEqualTo(scenario.expectedAssigneeId);
        
        // 验证 4: status = ASSIGNED
        assertThat(savedInfo.getStatus()).isEqualTo("ASSIGNED");
        
        // 验证 5: extended_properties 包含正确的多实例元数据
        String extendedPropertiesJson = savedInfo.getExtendedProperties();
        assertThat(extendedPropertiesJson).isNotNull();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> extendedProps = objectMapper.readValue(extendedPropertiesJson, Map.class);
        
        // 验证 multiInstance 标记
        assertThat(extendedProps.get("multiInstance")).isEqualTo(true);
        
        // 验证 subTableRowId
        assertThat(extendedProps.get("subTableRowId"))
            .isEqualTo(scenario.expectedRowId.intValue()); // JSON 反序列化为 Integer
        
        // 验证 subTableRowVersion
        assertThat(extendedProps.get("subTableRowVersion"))
            .isEqualTo(scenario.expectedRowVersion.intValue());
        
        // 验证 subTableId
        assertThat(extendedProps.get("subTableId")).isEqualTo(scenario.subTableId);
        
        // 验证 subTableName
        assertThat(extendedProps.get("subTableName")).isEqualTo(scenario.subTableName);
    }
    
    /**
     * Property 6 边界情况：currentItem 为 null 时不应分配任务
     */
    @Property(tries = 50)
    @Label("Feature: multi-instance-task-dispatch, Property 6: currentItem 为 null 时不分配")
    void shouldNotAssignWhenCurrentItemIsNull(
            @ForAll("taskScenarios") TaskScenario scenario) {
        
        // Given: 创建 mock 对象
        TaskService taskService = mock(TaskService.class);
        RuntimeService runtimeService = mock(RuntimeService.class);
        RepositoryService repositoryService = mock(RepositoryService.class);
        ExtendedTaskInfoRepository extendedTaskInfoRepository = mock(ExtendedTaskInfoRepository.class);
        
        TaskAssignmentListener listener = new TaskAssignmentListener();
        
        injectField(listener, "taskService", taskService);
        injectField(listener, "runtimeService", runtimeService);
        injectField(listener, "repositoryService", repositoryService);
        injectField(listener, "extendedTaskInfoRepository", extendedTaskInfoRepository);
        
        // 创建 mock TaskEntity
        TaskEntity task = mock(TaskEntity.class);
        when(task.getId()).thenReturn(scenario.taskId);
        when(task.getProcessInstanceId()).thenReturn(scenario.processInstanceId);
        when(task.getProcessDefinitionId()).thenReturn(scenario.processDefinitionId);
        when(task.getTaskDefinitionKey()).thenReturn(scenario.taskDefinitionKey);
        when(task.getName()).thenReturn(scenario.taskName);
        when(task.getAssignee()).thenReturn(null);
        when(task.getExecutionId()).thenReturn(scenario.executionId);
        
        // 创建 BPMN 模型
        BpmnModel bpmnModel = createBpmnModelWithElementVariable(
            scenario.taskDefinitionKey, "45", "fu_participants");
        when(repositoryService.getBpmnModel(scenario.processDefinitionId)).thenReturn(bpmnModel);
        
        // currentItem 为 null
        when(runtimeService.getVariable(scenario.executionId, "currentItem")).thenReturn(null);
        
        // 创建事件
        FlowableEntityEventImpl event = mock(FlowableEntityEventImpl.class);
        when(event.getType()).thenReturn(FlowableEngineEventType.TASK_CREATED);
        when(event.getEntity()).thenReturn(task);
        
        // When: 触发事件
        listener.onEvent(event);
        
        // Then: 不应分配任务
        verify(taskService, never()).setAssignee(any(), any());
        verify(extendedTaskInfoRepository, never()).save(any());
    }
    
    /**
     * Property 6 边界情况：assigneeId 无效时不应创建 ExtendedTaskInfo
     */
    @Property(tries = 50)
    @Label("Feature: multi-instance-task-dispatch, Property 6: assigneeId 无效时不创建 ExtendedTaskInfo")
    void shouldNotCreateExtendedTaskInfoWhenAssigneeIdIsInvalid(
            @ForAll("elementVariableScenarios") ElementVariableScenario scenario) {
        
        // Given: 创建 mock 对象
        TaskService taskService = mock(TaskService.class);
        RuntimeService runtimeService = mock(RuntimeService.class);
        RepositoryService repositoryService = mock(RepositoryService.class);
        ExtendedTaskInfoRepository extendedTaskInfoRepository = mock(ExtendedTaskInfoRepository.class);
        
        TaskAssignmentListener listener = new TaskAssignmentListener();
        
        injectField(listener, "taskService", taskService);
        injectField(listener, "runtimeService", runtimeService);
        injectField(listener, "repositoryService", repositoryService);
        injectField(listener, "extendedTaskInfoRepository", extendedTaskInfoRepository);
        
        // 创建 mock TaskEntity
        TaskEntity task = mock(TaskEntity.class);
        when(task.getId()).thenReturn(scenario.taskId);
        when(task.getProcessInstanceId()).thenReturn(scenario.processInstanceId);
        when(task.getProcessDefinitionId()).thenReturn(scenario.processDefinitionId);
        when(task.getTaskDefinitionKey()).thenReturn(scenario.taskDefinitionKey);
        when(task.getName()).thenReturn(scenario.taskName);
        when(task.getAssignee()).thenReturn(null);
        when(task.getExecutionId()).thenReturn(scenario.executionId);
        
        // 创建 BPMN 模型
        BpmnModel bpmnModel = createBpmnModelWithElementVariable(
            scenario.taskDefinitionKey,
            scenario.subTableId,
            scenario.subTableName
        );
        when(repositoryService.getBpmnModel(scenario.processDefinitionId)).thenReturn(bpmnModel);
        
        // 设置 currentItem 变量
        when(runtimeService.getVariable(scenario.executionId, "currentItem"))
            .thenReturn(scenario.currentItem);
        
        // 模拟 setAssignee 失败（用户不存在/已禁用）
        doThrow(new RuntimeException("User not found or disabled"))
            .when(taskService).setAssignee(scenario.taskId, scenario.expectedAssigneeId);
        
        // 创建事件
        FlowableEntityEventImpl event = mock(FlowableEntityEventImpl.class);
        when(event.getType()).thenReturn(FlowableEngineEventType.TASK_CREATED);
        when(event.getEntity()).thenReturn(task);
        
        // When: 触发事件
        listener.onEvent(event);
        
        // Then: 尝试分配任务但失败
        verify(taskService).setAssignee(scenario.taskId, scenario.expectedAssigneeId);
        
        // 不应创建 ExtendedTaskInfo（因为分配失败）
        verify(extendedTaskInfoRepository, never()).save(any());
    }
    
    // ==================== 数据生成器 ====================
    
    /**
     * 元素变量场景
     */
    private static class ElementVariableScenario {
        final String taskId;
        final String processInstanceId;
        final String processDefinitionId;
        final String taskDefinitionKey;
        final String taskName;
        final String executionId;
        final String subTableId;
        final String subTableName;
        final Map<String, Object> currentItem;
        final String expectedAssigneeId;
        final Long expectedRowId;
        final Long expectedRowVersion;
        
        ElementVariableScenario(
                String taskId,
                String processInstanceId,
                String processDefinitionId,
                String taskDefinitionKey,
                String taskName,
                String executionId,
                String subTableId,
                String subTableName,
                String assigneeId,
                Long rowId,
                Long rowVersion) {
            
            this.taskId = taskId;
            this.processInstanceId = processInstanceId;
            this.processDefinitionId = processDefinitionId;
            this.taskDefinitionKey = taskDefinitionKey;
            this.taskName = taskName;
            this.executionId = executionId;
            this.subTableId = subTableId;
            this.subTableName = subTableName;
            this.expectedAssigneeId = assigneeId;
            this.expectedRowId = rowId;
            this.expectedRowVersion = rowVersion;
            
            // 构建 currentItem Map
            this.currentItem = new HashMap<>();
            this.currentItem.put("assigneeId", assigneeId);
            this.currentItem.put("rowId", rowId);
            this.currentItem.put("rowVersion", rowVersion);
        }
    }
    
    /**
     * 任务场景（用于边界测试）
     */
    private static class TaskScenario {
        final String taskId;
        final String processInstanceId;
        final String processDefinitionId;
        final String taskDefinitionKey;
        final String taskName;
        final String executionId;
        
        TaskScenario(
                String taskId,
                String processInstanceId,
                String processDefinitionId,
                String taskDefinitionKey,
                String taskName,
                String executionId) {
            
            this.taskId = taskId;
            this.processInstanceId = processInstanceId;
            this.processDefinitionId = processDefinitionId;
            this.taskDefinitionKey = taskDefinitionKey;
            this.taskName = taskName;
            this.executionId = executionId;
        }
    }
    
    /**
     * 生成元素变量场景
     */
    @Provide
    Arbitrary<ElementVariableScenario> elementVariableScenarios() {
        // 任务 ID
        Arbitrary<String> taskIds = Arbitraries.strings()
            .withCharRange('a', 'z')
            .numeric()
            .ofMinLength(8)
            .ofMaxLength(16)
            .map(s -> "task-" + s);
        
        // 流程实例 ID
        Arbitrary<String> processInstanceIds = Arbitraries.strings()
            .withCharRange('a', 'z')
            .numeric()
            .ofMinLength(10)
            .ofMaxLength(20)
            .map(s -> "proc-" + s);
        
        // 流程定义 ID
        Arbitrary<String> processDefinitionIds = Arbitraries.strings()
            .withCharRange('a', 'z')
            .numeric()
            .ofMinLength(10)
            .ofMaxLength(20)
            .map(s -> "procdef-" + s);
        
        // 任务定义键
        Arbitrary<String> taskDefinitionKeys = Arbitraries.of(
            "Task_FillInfo", "Task_Review", "Task_Approve", 
            "Task_Submit", "Task_Process", "Task_Complete"
        );
        
        // 任务名称
        Arbitrary<String> taskNames = Arbitraries.of(
            "填写参会信息", "审核信息", "批准申请",
            "提交资料", "处理任务", "完成工作"
        );
        
        // 执行 ID
        Arbitrary<String> executionIds = Arbitraries.strings()
            .withCharRange('a', 'z')
            .numeric()
            .ofMinLength(10)
            .ofMaxLength(20)
            .map(s -> "exec-" + s);
        
        // 子表 ID
        Arbitrary<String> subTableIds = Arbitraries.integers()
            .between(1, 1000)
            .map(String::valueOf);
        
        // 子表名称
        Arbitrary<String> subTableNames = Arbitraries.of(
            "fu_participants", "fu_approvers", "fu_reviewers",
            "fu_items", "fu_details", "fu_members"
        );
        
        // 处理人 ID
        Arbitrary<String> assigneeIds = Arbitraries.strings()
            .withCharRange('a', 'z')
            .numeric()
            .ofMinLength(5)
            .ofMaxLength(10)
            .map(s -> "user-" + s);
        
        // 行 ID
        Arbitrary<Long> rowIds = Arbitraries.longs().between(1L, 100000L);
        
        // 行版本号
        Arbitrary<Long> rowVersions = Arbitraries.longs().between(1L, 100L);
        
        // 使用嵌套组合（jqwik 最多支持 8 个参数）
        // 第一组：任务相关的 8 个参数
        return Combinators.combine(
            taskIds,
            processInstanceIds,
            processDefinitionIds,
            taskDefinitionKeys,
            taskNames,
            executionIds,
            subTableIds,
            subTableNames
        ).flatAs((taskId, procInstId, procDefId, taskDefKey, taskName, execId, subTableId, subTableName) -> {
            // 第二组：剩余的 3 个参数
            return Combinators.combine(
                assigneeIds,
                rowIds,
                rowVersions
            ).as((assigneeId, rowId, rowVersion) -> 
                new ElementVariableScenario(
                    taskId, procInstId, procDefId, taskDefKey, taskName, execId,
                    subTableId, subTableName, assigneeId, rowId, rowVersion
                )
            );
        });
    }
    
    /**
     * 生成任务场景
     */
    @Provide
    Arbitrary<TaskScenario> taskScenarios() {
        Arbitrary<String> taskIds = Arbitraries.strings()
            .withCharRange('a', 'z')
            .numeric()
            .ofMinLength(8)
            .ofMaxLength(16)
            .map(s -> "task-" + s);
        
        Arbitrary<String> processInstanceIds = Arbitraries.strings()
            .withCharRange('a', 'z')
            .numeric()
            .ofMinLength(10)
            .ofMaxLength(20)
            .map(s -> "proc-" + s);
        
        Arbitrary<String> processDefinitionIds = Arbitraries.strings()
            .withCharRange('a', 'z')
            .numeric()
            .ofMinLength(10)
            .ofMaxLength(20)
            .map(s -> "procdef-" + s);
        
        Arbitrary<String> taskDefinitionKeys = Arbitraries.of(
            "Task_FillInfo", "Task_Review", "Task_Approve"
        );
        
        Arbitrary<String> taskNames = Arbitraries.of(
            "填写参会信息", "审核信息", "批准申请"
        );
        
        Arbitrary<String> executionIds = Arbitraries.strings()
            .withCharRange('a', 'z')
            .numeric()
            .ofMinLength(10)
            .ofMaxLength(20)
            .map(s -> "exec-" + s);
        
        return Combinators.combine(
            taskIds,
            processInstanceIds,
            processDefinitionIds,
            taskDefinitionKeys,
            taskNames,
            executionIds
        ).as(TaskScenario::new);
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 使用反射注入字段
     */
    private void injectField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = TaskAssignmentListener.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject field: " + fieldName, e);
        }
    }
    
    /**
     * 创建包含 ELEMENT_VARIABLE 扩展属性的 BPMN 模型
     */
    private BpmnModel createBpmnModelWithElementVariable(
            String taskDefinitionKey,
            String subTableId,
            String subTableName) {
        
        BpmnModel bpmnModel = new BpmnModel();
        org.flowable.bpmn.model.Process process = new org.flowable.bpmn.model.Process();
        process.setId("Process_1");
        
        UserTask userTask = new UserTask();
        userTask.setId(taskDefinitionKey);
        userTask.setName("Test Task");
        
        // 创建扩展元素
        Map<String, List<ExtensionElement>> extensionElements = new HashMap<>();
        
        ExtensionElement propertiesElement = new ExtensionElement();
        propertiesElement.setName("properties");
        propertiesElement.setNamespace("http://custom.bpmn.io/schema");
        
        Map<String, List<ExtensionElement>> childElements = new HashMap<>();
        List<ExtensionElement> propertyElements = new ArrayList<>();
        
        // 添加 assigneeType 属性
        propertyElements.add(createProperty("assigneeType", "ELEMENT_VARIABLE"));
        
        // 添加 subTableId 属性
        if (subTableId != null) {
            propertyElements.add(createProperty("subTableId", subTableId));
        }
        
        // 添加 subTableName 属性
        if (subTableName != null) {
            propertyElements.add(createProperty("subTableName", subTableName));
        }
        
        childElements.put("property", propertyElements);
        propertiesElement.setChildElements(childElements);
        
        extensionElements.put("properties", Arrays.asList(propertiesElement));
        userTask.setExtensionElements(extensionElements);
        
        process.addFlowElement(userTask);
        bpmnModel.addProcess(process);
        
        return bpmnModel;
    }
    
    /**
     * 创建扩展属性元素
     */
    private ExtensionElement createProperty(String name, String value) {
        ExtensionElement property = new ExtensionElement();
        property.setName("property");
        property.setNamespace("http://custom.bpmn.io/schema");
        property.addAttribute(createAttribute("name", name));
        property.addAttribute(createAttribute("value", value));
        return property;
    }
    
    /**
     * 创建扩展属性
     */
    private ExtensionAttribute createAttribute(String name, String value) {
        ExtensionAttribute attr = new ExtensionAttribute();
        attr.setName(name);
        attr.setValue(value);
        return attr;
    }
}
