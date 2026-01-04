package com.workflow.properties;

import com.workflow.component.ProcessEngineComponent;
import com.workflow.dto.request.ProcessDefinitionRequest;
import com.workflow.dto.request.StartProcessRequest;
import com.workflow.dto.response.DeploymentResult;
import com.workflow.dto.response.ProcessInstanceResult;
import com.workflow.exception.WorkflowValidationException;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * 运行实例删除保护属性测试
 * 验证需求: 需求 1.4 - 流程定义生命周期管理
 * 
 * 属性 4: 运行实例删除保护
 * 当流程定义存在运行中的流程实例时，不允许删除该流程定义，
 * 除非明确指定级联删除选项。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ProcessInstanceDeletionProtectionProperties {

    @Autowired
    private ProcessEngineComponent processEngineComponent;

    @Autowired
    private RuntimeService runtimeService;

    private static final String VALID_SIMPLE_BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xmlns:flowable="http://flowable.org/bpmn"
                     targetNamespace="http://www.flowable.org/processdef"
                     xsi:schemaLocation="http://www.omg.org/spec/BPMN/20100524/MODEL http://www.omg.org/spec/BPMN/20100524/BPMN20.xsd">
          <process id="PROCESS_KEY" name="Simple Process" isExecutable="true">
            <startEvent id="startEvent" name="Start"/>
            <sequenceFlow id="flow1" sourceRef="startEvent" targetRef="endEvent"/>
            <endEvent id="endEvent" name="End"/>
          </process>
        </definitions>
        """;

    private static final String VALID_USER_TASK_BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xmlns:flowable="http://flowable.org/bpmn"
                     targetNamespace="http://www.flowable.org/processdef"
                     xsi:schemaLocation="http://www.omg.org/spec/BPMN/20100524/MODEL http://www.omg.org/spec/BPMN/20100524/BPMN20.xsd">
          <process id="PROCESS_KEY" name="User Task Process" isExecutable="true">
            <startEvent id="startEvent" name="Start"/>
            <sequenceFlow id="flow1" sourceRef="startEvent" targetRef="userTask"/>
            <userTask id="userTask" name="User Task" flowable:assignee="user1"/>
            <sequenceFlow id="flow2" sourceRef="userTask" targetRef="endEvent"/>
            <endEvent id="endEvent" name="End"/>
          </process>
        </definitions>
        """;

    @BeforeEach
    void setUp() {
        assertThat(processEngineComponent).isNotNull();
        assertThat(runtimeService).isNotNull();
    }

    /**
     * 属性测试: 无运行实例时可以正常删除流程定义
     */
    @Test
    void shouldAllowDeletionWhenNoRunningInstances() {
        String processKey = "no_instances_" + UUID.randomUUID().toString().substring(0, 8);
        String processName = "No Instances Process";
        String category = "deletion-test";
        
        // 部署流程定义
        DeploymentResult deploymentResult = deployProcess(processKey, processName, category, VALID_SIMPLE_BPMN);
        assertThat(deploymentResult.isSuccess()).isTrue();
        
        // 验证可以正常删除（无运行实例）
        assertThatCode(() -> processEngineComponent.deleteProcessDefinition(deploymentResult.getDeploymentId(), false))
                .doesNotThrowAnyException();
    }

    /**
     * 属性测试: 有运行实例时不允许删除流程定义（非级联）
     */
    @Test
    void shouldPreventDeletionWhenRunningInstancesExist() {
        String processKey = "with_instances_" + UUID.randomUUID().toString().substring(0, 8);
        String processName = "With Instances Process";
        String category = "deletion-test";
        
        // 部署流程定义
        DeploymentResult deploymentResult = deployProcess(processKey, processName, category, VALID_USER_TASK_BPMN);
        assertThat(deploymentResult.isSuccess()).isTrue();
        
        // 启动流程实例
        ProcessInstanceResult instanceResult = startProcess(processKey, "business-key-1");
        assertThat(instanceResult.isSuccess()).isTrue();
        
        // 验证存在运行中的实例
        long runningCount = runtimeService.createProcessInstanceQuery()
                .deploymentId(deploymentResult.getDeploymentId())
                .count();
        assertThat(runningCount).isEqualTo(1);
        
        // 尝试删除流程定义（非级联），应该失败
        assertThatThrownBy(() -> processEngineComponent.deleteProcessDefinition(deploymentResult.getDeploymentId(), false))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("无法删除流程定义，存在")
                .hasMessageContaining("个运行中的流程实例");
    }

    /**
     * 属性测试: 有运行实例时允许级联删除流程定义
     */
    @Test
    void shouldAllowCascadeDeletionWhenRunningInstancesExist() {
        String processKey = "cascade_delete_" + UUID.randomUUID().toString().substring(0, 8);
        String processName = "Cascade Delete Process";
        String category = "deletion-test";
        
        // 部署流程定义
        DeploymentResult deploymentResult = deployProcess(processKey, processName, category, VALID_USER_TASK_BPMN);
        assertThat(deploymentResult.isSuccess()).isTrue();
        
        // 启动多个流程实例
        ProcessInstanceResult instance1 = startProcess(processKey, "business-key-1");
        ProcessInstanceResult instance2 = startProcess(processKey, "business-key-2");
        assertThat(instance1.isSuccess()).isTrue();
        assertThat(instance2.isSuccess()).isTrue();
        
        // 验证存在运行中的实例
        long runningCount = runtimeService.createProcessInstanceQuery()
                .deploymentId(deploymentResult.getDeploymentId())
                .count();
        assertThat(runningCount).isEqualTo(2);
        
        // 级联删除流程定义，应该成功
        assertThatCode(() -> processEngineComponent.deleteProcessDefinition(deploymentResult.getDeploymentId(), true))
                .doesNotThrowAnyException();
        
        // 验证流程实例也被删除
        long remainingCount = runtimeService.createProcessInstanceQuery()
                .deploymentId(deploymentResult.getDeploymentId())
                .count();
        assertThat(remainingCount).isEqualTo(0);
    }

    /**
     * 属性测试: 多个流程定义的删除保护
     */
    @Test
    void shouldProtectMultipleProcessDefinitions() {
        String baseKey = "multi_protect_" + UUID.randomUUID().toString().substring(0, 8);
        String category = "multi-protection-test";
        
        // 部署第一个流程定义并启动实例
        DeploymentResult deployment1 = deployProcess(baseKey + "_1", "Process 1", category, VALID_USER_TASK_BPMN);
        ProcessInstanceResult instance1 = startProcess(baseKey + "_1", "business-key-1");
        assertThat(deployment1.isSuccess()).isTrue();
        assertThat(instance1.isSuccess()).isTrue();
        
        // 部署第二个流程定义但不启动实例
        DeploymentResult deployment2 = deployProcess(baseKey + "_2", "Process 2", category, VALID_SIMPLE_BPMN);
        assertThat(deployment2.isSuccess()).isTrue();
        
        // 第一个流程定义有运行实例，不能删除
        assertThatThrownBy(() -> processEngineComponent.deleteProcessDefinition(deployment1.getDeploymentId(), false))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("无法删除流程定义，存在")
                .hasMessageContaining("个运行中的流程实例");
        
        // 第二个流程定义无运行实例，可以删除
        assertThatCode(() -> processEngineComponent.deleteProcessDefinition(deployment2.getDeploymentId(), false))
                .doesNotThrowAnyException();
    }

    /**
     * 属性测试: 已完成实例不影响删除
     */
    @Test
    void shouldAllowDeletionWhenOnlyCompletedInstancesExist() {
        String processKey = "completed_instances_" + UUID.randomUUID().toString().substring(0, 8);
        String processName = "Completed Instances Process";
        String category = "deletion-test";
        
        // 部署流程定义
        DeploymentResult deploymentResult = deployProcess(processKey, processName, category, VALID_SIMPLE_BPMN);
        assertThat(deploymentResult.isSuccess()).isTrue();
        
        // 启动并完成流程实例（简单流程会自动完成）
        ProcessInstanceResult instanceResult = startProcess(processKey, "business-key-1");
        assertThat(instanceResult.isSuccess()).isTrue();
        
        // 验证没有运行中的实例（简单流程已自动完成）
        long runningCount = runtimeService.createProcessInstanceQuery()
                .deploymentId(deploymentResult.getDeploymentId())
                .count();
        assertThat(runningCount).isEqualTo(0);
        
        // 应该可以正常删除
        assertThatCode(() -> processEngineComponent.deleteProcessDefinition(deploymentResult.getDeploymentId(), false))
                .doesNotThrowAnyException();
    }

    /**
     * 属性测试: 删除保护的错误信息准确性
     */
    @Test
    void shouldProvideAccurateErrorMessageForDeletionProtection() {
        String processKey = "error_message_" + UUID.randomUUID().toString().substring(0, 8);
        String processName = "Error Message Process";
        String category = "deletion-test";
        
        // 部署流程定义
        DeploymentResult deploymentResult = deployProcess(processKey, processName, category, VALID_USER_TASK_BPMN);
        assertThat(deploymentResult.isSuccess()).isTrue();
        
        // 启动3个流程实例
        for (int i = 1; i <= 3; i++) {
            ProcessInstanceResult instanceResult = startProcess(processKey, "business-key-" + i);
            assertThat(instanceResult.isSuccess()).isTrue();
        }
        
        // 验证错误信息包含准确的实例数量
        assertThatThrownBy(() -> processEngineComponent.deleteProcessDefinition(deploymentResult.getDeploymentId(), false))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("无法删除流程定义，存在 3 个运行中的流程实例");
    }

    /**
     * 属性测试: 删除不存在的部署ID
     */
    @Test
    void shouldHandleNonExistentDeploymentId() {
        String nonExistentDeploymentId = "non-existent-" + UUID.randomUUID().toString();
        
        // 尝试删除不存在的部署，应该抛出异常
        assertThatThrownBy(() -> processEngineComponent.deleteProcessDefinition(nonExistentDeploymentId, false))
                .hasMessageContaining("删除流程定义失败");
    }

    // 辅助方法
    private DeploymentResult deployProcess(String processKey, String processName, String category, String bpmnTemplate) {
        ProcessDefinitionRequest request = new ProcessDefinitionRequest();
        request.setName(processName);
        request.setKey(processKey);
        request.setCategory(category);
        request.setBpmnXml(bpmnTemplate.replace("PROCESS_KEY", processKey));
        
        return processEngineComponent.deployProcess(request);
    }

    private ProcessInstanceResult startProcess(String processKey, String businessKey) {
        StartProcessRequest request = new StartProcessRequest();
        request.setProcessDefinitionKey(processKey);
        request.setBusinessKey(businessKey);
        request.setVariables(new HashMap<>());
        
        return processEngineComponent.startProcess(request);
    }
}