package com.workflow.properties;

import com.workflow.component.ProcessEngineComponent;
import com.workflow.dto.request.ProcessDefinitionRequest;
import com.workflow.dto.request.StartProcessRequest;
import com.workflow.dto.request.ProcessInstanceQueryRequest;
import com.workflow.dto.response.DeploymentResult;
import com.workflow.dto.response.ProcessInstanceResult;
import com.workflow.dto.response.ProcessInstanceQueryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * 流程实例创建一致性属性测试
 * 验证需求: 需求 2.1 - 流程实例启动和管理
 * 
 * 属性 5: 流程实例创建一致性
 * 对于任何有效的流程定义和启动参数，系统应该能够成功创建流程实例，
 * 并且创建的流程实例应该与启动参数保持一致。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ProcessInstanceCreationConsistencyProperties {

    @Autowired
    private ProcessEngineComponent processEngineComponent;

    private static final String VALID_SIMPLE_BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xmlns:flowable="http://flowable.org/bpmn"
                     targetNamespace="http://www.flowable.org/processdef"
                     xsi:schemaLocation="http://www.omg.org/spec/BPMN/20100524/MODEL http://www.omg.org/spec/BPMN/20100524/BPMN20.xsd">
          <process id="%s" name="Test Process" isExecutable="true">
            <startEvent id="startEvent" name="Start"/>
            <sequenceFlow id="flow1" sourceRef="startEvent" targetRef="endEvent"/>
            <endEvent id="endEvent" name="End"/>
          </process>
        </definitions>
        """;

    @BeforeEach
    void setUp() {
        assertThat(processEngineComponent).isNotNull();
    }

    /**
     * 属性测试: 流程实例创建后应该能够通过查询找到
     */
    @ParameterizedTest
    @ValueSource(strings = {"simpleProcess1", "simpleProcess2", "simpleProcess3"})
    void createdProcessInstanceShouldBeQueryable(String processKeySuffix) {
        // Given: 部署一个有效的流程定义
        String processKey = "test_process_" + processKeySuffix + "_" + UUID.randomUUID().toString().substring(0, 8);
        String businessKey = "business_" + processKeySuffix + "_" + System.currentTimeMillis();
        String processName = "Test Process " + processKey;
        
        ProcessDefinitionRequest deployRequest = new ProcessDefinitionRequest();
        deployRequest.setName(processName);
        deployRequest.setKey(processKey);
        deployRequest.setCategory("test");
        deployRequest.setBpmnXml(String.format(VALID_SIMPLE_BPMN, processKey));
        
        DeploymentResult deployResult = processEngineComponent.deployProcess(deployRequest);
        assertThat(deployResult.isSuccess()).isTrue();
        
        // When: 启动流程实例
        StartProcessRequest startRequest = new StartProcessRequest();
        startRequest.setProcessDefinitionKey(processKey);
        startRequest.setBusinessKey(businessKey);
        startRequest.setStartUserId("testUser");
        
        ProcessInstanceResult startResult = processEngineComponent.startProcess(startRequest);
        
        // Then: 启动应该成功
        assertThat(startResult.isSuccess()).isTrue();
        assertThat(startResult.getProcessInstanceId()).isNotNull();
        assertThat(startResult.getProcessDefinitionKey()).isEqualTo(processKey);
        assertThat(startResult.getBusinessKey()).isEqualTo(businessKey);
        
        // And: 应该能够通过查询找到创建的流程实例
        ProcessInstanceQueryRequest queryRequest = new ProcessInstanceQueryRequest();
        queryRequest.setProcessInstanceId(startResult.getProcessInstanceId());
        
        ProcessInstanceQueryResult queryResult = processEngineComponent.queryProcessInstances(queryRequest);
        
        assertThat(queryResult.getTotalCount()).isEqualTo(1);
        assertThat(queryResult.getProcessInstances()).hasSize(1);
        
        ProcessInstanceQueryResult.ProcessInstanceInfo instanceInfo = queryResult.getProcessInstances().get(0);
        assertThat(instanceInfo.getProcessInstanceId()).isEqualTo(startResult.getProcessInstanceId());
        assertThat(instanceInfo.getProcessDefinitionKey()).isEqualTo(processKey);
        assertThat(instanceInfo.getBusinessKey()).isEqualTo(businessKey);
        assertThat(instanceInfo.getStartUserId()).isEqualTo("testUser");
        assertThat(instanceInfo.getState()).isEqualTo("completed");
        assertThat(instanceInfo.isSuspended()).isFalse();
        assertThat(instanceInfo.isEnded()).isTrue();
    }

    /**
     * 属性测试: 流程实例创建时的变量应该正确保存
     */
    @Test
    void processInstanceVariablesShouldBePersisted() {
        // Given: 部署一个有效的流程定义
        String processKey = "test_process_variables_" + UUID.randomUUID().toString().substring(0, 8);
        String processName = "Test Process " + processKey;
        
        ProcessDefinitionRequest deployRequest = new ProcessDefinitionRequest();
        deployRequest.setName(processName);
        deployRequest.setKey(processKey);
        deployRequest.setCategory("test");
        deployRequest.setBpmnXml(String.format(VALID_SIMPLE_BPMN, processKey));
        
        DeploymentResult deployResult = processEngineComponent.deployProcess(deployRequest);
        assertThat(deployResult.isSuccess()).isTrue();
        
        // When: 启动流程实例并设置变量
        Map<String, Object> variables = new HashMap<>();
        variables.put("testString", "hello world");
        variables.put("testNumber", 42);
        variables.put("testBoolean", true);
        
        StartProcessRequest startRequest = new StartProcessRequest();
        startRequest.setProcessDefinitionKey(processKey);
        startRequest.setBusinessKey("test-business-key");
        startRequest.setStartUserId("testUser");
        startRequest.setVariables(variables);
        
        ProcessInstanceResult startResult = processEngineComponent.startProcess(startRequest);
        
        // Then: 启动应该成功
        assertThat(startResult.isSuccess()).isTrue();
        assertThat(startResult.getVariables()).isEqualTo(variables);
        
        // And: 查询流程实例时应该能获取到正确的变量
        ProcessInstanceQueryRequest queryRequest = new ProcessInstanceQueryRequest();
        queryRequest.setProcessInstanceId(startResult.getProcessInstanceId());
        
        ProcessInstanceQueryResult queryResult = processEngineComponent.queryProcessInstances(queryRequest);
        
        assertThat(queryResult.getTotalCount()).isEqualTo(1);
        ProcessInstanceQueryResult.ProcessInstanceInfo instanceInfo = queryResult.getProcessInstances().get(0);
        
        // 验证变量是否正确保存
        Map<String, Object> savedVariables = instanceInfo.getVariables();
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            assertThat(savedVariables).containsEntry(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 属性测试: 多个流程实例应该能够独立创建和管理
     */
    @Test
    void multipleProcessInstancesShouldBeIndependent() {
        // Given: 部署一个有效的流程定义
        String processKey = "test_process_multiple_" + UUID.randomUUID().toString().substring(0, 8);
        String processName = "Test Process " + processKey;
        
        ProcessDefinitionRequest deployRequest = new ProcessDefinitionRequest();
        deployRequest.setName(processName);
        deployRequest.setKey(processKey);
        deployRequest.setCategory("test");
        deployRequest.setBpmnXml(String.format(VALID_SIMPLE_BPMN, processKey));
        
        DeploymentResult deployResult = processEngineComponent.deployProcess(deployRequest);
        assertThat(deployResult.isSuccess()).isTrue();
        
        // When: 创建多个流程实例
        StartProcessRequest startRequest1 = new StartProcessRequest();
        startRequest1.setProcessDefinitionKey(processKey);
        startRequest1.setBusinessKey("business-key-1");
        startRequest1.setStartUserId("user1");
        
        StartProcessRequest startRequest2 = new StartProcessRequest();
        startRequest2.setProcessDefinitionKey(processKey);
        startRequest2.setBusinessKey("business-key-2");
        startRequest2.setStartUserId("user2");
        
        ProcessInstanceResult result1 = processEngineComponent.startProcess(startRequest1);
        ProcessInstanceResult result2 = processEngineComponent.startProcess(startRequest2);
        
        // Then: 两个实例都应该成功创建
        assertThat(result1.isSuccess()).isTrue();
        assertThat(result2.isSuccess()).isTrue();
        
        // And: 两个实例应该有不同的ID
        assertThat(result1.getProcessInstanceId()).isNotEqualTo(result2.getProcessInstanceId());
        
        // And: 查询应该能找到两个独立的实例
        ProcessInstanceQueryRequest queryRequest = new ProcessInstanceQueryRequest();
        queryRequest.setProcessDefinitionKey(processKey);
        
        ProcessInstanceQueryResult queryResult = processEngineComponent.queryProcessInstances(queryRequest);
        
        assertThat(queryResult.getTotalCount()).isEqualTo(2);
        assertThat(queryResult.getProcessInstances()).hasSize(2);
        
        // 验证两个实例的独立性
        ProcessInstanceQueryResult.ProcessInstanceInfo instance1 = queryResult.getProcessInstances().stream()
                .filter(info -> info.getBusinessKey().equals("business-key-1"))
                .findFirst()
                .orElseThrow();
        ProcessInstanceQueryResult.ProcessInstanceInfo instance2 = queryResult.getProcessInstances().stream()
                .filter(info -> info.getBusinessKey().equals("business-key-2"))
                .findFirst()
                .orElseThrow();
        
        assertThat(instance1.getStartUserId()).isEqualTo("user1");
        assertThat(instance2.getStartUserId()).isEqualTo("user2");
        assertThat(instance1.getProcessInstanceId()).isNotEqualTo(instance2.getProcessInstanceId());
    }

    /**
     * 属性测试: 流程实例查询应该支持各种过滤条件
     */
    @Test
    void processInstanceQueryShouldSupportFiltering() {
        // Given: 部署一个有效的流程定义
        String processKey = "test_process_filtering_" + UUID.randomUUID().toString().substring(0, 8);
        String processName = "Test Process " + processKey;
        
        ProcessDefinitionRequest deployRequest = new ProcessDefinitionRequest();
        deployRequest.setName(processName);
        deployRequest.setKey(processKey);
        deployRequest.setCategory("test");
        deployRequest.setBpmnXml(String.format(VALID_SIMPLE_BPMN, processKey));
        
        DeploymentResult deployResult = processEngineComponent.deployProcess(deployRequest);
        assertThat(deployResult.isSuccess()).isTrue();
        
        // When: 创建流程实例
        StartProcessRequest startRequest = new StartProcessRequest();
        startRequest.setProcessDefinitionKey(processKey);
        startRequest.setBusinessKey("filter-test-key");
        startRequest.setStartUserId("filterUser");
        
        ProcessInstanceResult startResult = processEngineComponent.startProcess(startRequest);
        assertThat(startResult.isSuccess()).isTrue();
        
        // Then: 按流程定义键查询应该找到实例
        ProcessInstanceQueryRequest queryByKey = new ProcessInstanceQueryRequest();
        queryByKey.setProcessDefinitionKey(processKey);
        
        ProcessInstanceQueryResult resultByKey = processEngineComponent.queryProcessInstances(queryByKey);
        assertThat(resultByKey.getTotalCount()).isGreaterThanOrEqualTo(1);
        
        // And: 按业务键查询应该找到实例
        ProcessInstanceQueryRequest queryByBusinessKey = new ProcessInstanceQueryRequest();
        queryByBusinessKey.setBusinessKey("filter-test-key");
        
        ProcessInstanceQueryResult resultByBusinessKey = processEngineComponent.queryProcessInstances(queryByBusinessKey);
        assertThat(resultByBusinessKey.getTotalCount()).isEqualTo(1);
        assertThat(resultByBusinessKey.getProcessInstances().get(0).getBusinessKey()).isEqualTo("filter-test-key");
        
        // And: 按启动用户查询应该找到实例
        ProcessInstanceQueryRequest queryByUser = new ProcessInstanceQueryRequest();
        queryByUser.setStartUserId("filterUser");
        
        ProcessInstanceQueryResult resultByUser = processEngineComponent.queryProcessInstances(queryByUser);
        assertThat(resultByUser.getTotalCount()).isGreaterThanOrEqualTo(1);
        
        // And: 按不存在的条件查询应该返回空结果
        ProcessInstanceQueryRequest queryNonExistent = new ProcessInstanceQueryRequest();
        queryNonExistent.setBusinessKey("non-existent-key");
        
        ProcessInstanceQueryResult resultNonExistent = processEngineComponent.queryProcessInstances(queryNonExistent);
        assertThat(resultNonExistent.getTotalCount()).isEqualTo(0);
    }
}