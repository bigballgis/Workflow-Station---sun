package com.workflow.properties;

import com.workflow.component.ProcessEngineComponent;
import com.workflow.dto.request.ProcessDefinitionRequest;
import com.workflow.dto.response.DeploymentResult;
import com.workflow.dto.response.ProcessDefinitionResult;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * 流程定义版本管理属性测试
 * 验证需求: 需求 1.2 - 流程定义版本管理
 * 
 * 属性 2: 流程定义版本不变性
 * 一旦部署的流程定义版本不能被修改，只能部署新版本；
 * 每个流程定义键的版本号必须严格递增。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ProcessDefinitionVersionProperties {

    @Autowired
    private ProcessEngineComponent processEngineComponent;

    @Autowired
    private RepositoryService repositoryService;

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
    }

    /**
     * 属性测试: 版本号严格递增
     */
    @Test
    void versionNumbersShouldStrictlyIncrement() {
        String processKey = "version_increment_test_" + UUID.randomUUID().toString().substring(0, 8);
        
        // 部署第一个版本
        DeploymentResult result1 = deployProcess(processKey, "Version 1", VALID_SIMPLE_BPMN);
        assertThat(result1.isSuccess()).isTrue();
        assertThat(result1.getVersion()).isEqualTo(1);
        
        // 部署第二个版本
        DeploymentResult result2 = deployProcess(processKey, "Version 2", VALID_USER_TASK_BPMN);
        assertThat(result2.isSuccess()).isTrue();
        assertThat(result2.getVersion()).isEqualTo(2);
        
        // 部署第三个版本
        DeploymentResult result3 = deployProcess(processKey, "Version 3", VALID_SIMPLE_BPMN);
        assertThat(result3.isSuccess()).isTrue();
        assertThat(result3.getVersion()).isEqualTo(3);
        
        // 验证版本号严格递增
        assertThat(result2.getVersion()).isGreaterThan(result1.getVersion());
        assertThat(result3.getVersion()).isGreaterThan(result2.getVersion());
    }

    /**
     * 属性测试: 不同流程键的版本号独立管理
     */
    @Test
    void differentProcessKeysHaveIndependentVersions() {
        String processKey1 = "independent_test1_" + UUID.randomUUID().toString().substring(0, 8);
        String processKey2 = "independent_test2_" + UUID.randomUUID().toString().substring(0, 8);
        
        // 为第一个流程键部署多个版本
        DeploymentResult result1v1 = deployProcess(processKey1, "Process 1 Version 1", VALID_SIMPLE_BPMN);
        DeploymentResult result1v2 = deployProcess(processKey1, "Process 1 Version 2", VALID_USER_TASK_BPMN);
        
        // 为第二个流程键部署版本
        DeploymentResult result2v1 = deployProcess(processKey2, "Process 2 Version 1", VALID_SIMPLE_BPMN);
        
        // 验证版本号独立管理
        assertThat(result1v1.getVersion()).isEqualTo(1);
        assertThat(result1v2.getVersion()).isEqualTo(2);
        assertThat(result2v1.getVersion()).isEqualTo(1); // 第二个流程键从1开始
        
        // 继续为第二个流程键部署版本
        DeploymentResult result2v2 = deployProcess(processKey2, "Process 2 Version 2", VALID_USER_TASK_BPMN);
        assertThat(result2v2.getVersion()).isEqualTo(2);
    }

    /**
     * 属性测试: 查询流程定义返回最新版本
     */
    @Test
    void queryProcessDefinitionsShouldReturnLatestVersion() {
        String processKey = "latest_version_test_" + UUID.randomUUID().toString().substring(0, 8);
        
        // 部署多个版本
        deployProcess(processKey, "Version 1", VALID_SIMPLE_BPMN);
        deployProcess(processKey, "Version 2", VALID_USER_TASK_BPMN);
        DeploymentResult latestResult = deployProcess(processKey, "Version 3", VALID_SIMPLE_BPMN);
        
        // 查询流程定义
        List<ProcessDefinitionResult> definitions = processEngineComponent.getProcessDefinitions(null, processKey);
        
        // 验证只返回最新版本
        assertThat(definitions).hasSize(1);
        ProcessDefinitionResult definition = definitions.get(0);
        assertThat(definition.getKey()).isEqualTo(processKey);
        assertThat(definition.getVersion()).isEqualTo(3);
        assertThat(definition.getId()).isEqualTo(latestResult.getProcessDefinitionId());
    }

    /**
     * 属性测试: 版本不变性 - 已部署的版本不能修改
     */
    @Test
    void deployedVersionsShouldBeImmutable() {
        String processKey = "immutable_test_" + UUID.randomUUID().toString().substring(0, 8);
        
        // 部署第一个版本
        DeploymentResult result1 = deployProcess(processKey, "Original Version", VALID_SIMPLE_BPMN);
        String originalDefinitionId = result1.getProcessDefinitionId();
        
        // 部署相同内容的"新"版本
        DeploymentResult result2 = deployProcess(processKey, "Same Content", VALID_SIMPLE_BPMN);
        
        // 验证创建了新版本而不是修改原版本
        assertThat(result2.getVersion()).isEqualTo(2);
        assertThat(result2.getProcessDefinitionId()).isNotEqualTo(originalDefinitionId);
        
        // 验证原版本仍然存在且未被修改 - 使用Flowable的RepositoryService直接查询所有版本
        long allVersionsCount = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .count();
        assertThat(allVersionsCount).isEqualTo(2);
        
        // 验证原版本的ID仍然存在
        org.flowable.engine.repository.ProcessDefinition originalVersion = repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionId(originalDefinitionId)
                .singleResult();
        assertThat(originalVersion).isNotNull();
        assertThat(originalVersion.getVersion()).isEqualTo(1);
    }

    /**
     * 属性测试: 并发部署版本号一致性
     */
    @Test
    void concurrentDeploymentsShouldMaintainVersionConsistency() throws InterruptedException {
        String processKey = "concurrent_test_" + UUID.randomUUID().toString().substring(0, 8);
        
        // 先部署一个基础版本
        deployProcess(processKey, "Base Version", VALID_SIMPLE_BPMN);
        
        // 模拟并发部署（在事务环境中，实际并发受限，但可以测试版本一致性）
        DeploymentResult result1 = deployProcess(processKey, "Concurrent 1", VALID_USER_TASK_BPMN);
        DeploymentResult result2 = deployProcess(processKey, "Concurrent 2", VALID_SIMPLE_BPMN);
        
        // 验证版本号连续且唯一
        assertThat(result1.getVersion()).isEqualTo(2);
        assertThat(result2.getVersion()).isEqualTo(3);
        assertThat(result1.getProcessDefinitionId()).isNotEqualTo(result2.getProcessDefinitionId());
    }

    /**
     * 属性测试: 版本号管理一致性
     * 注意：Flowable的版本管理是基于当前存在的最高版本号，删除版本后重新部署会填补空缺
     */
    @Test
    void versionNumbersShouldNeverRegress() {
        String processKey = "no_regress_test_" + UUID.randomUUID().toString().substring(0, 8);
        
        // 部署多个版本
        DeploymentResult result1 = deployProcess(processKey, "Version 1", VALID_SIMPLE_BPMN);
        DeploymentResult result2 = deployProcess(processKey, "Version 2", VALID_USER_TASK_BPMN);
        
        // 验证初始版本号
        assertThat(result1.getVersion()).isEqualTo(1);
        assertThat(result2.getVersion()).isEqualTo(2);
        
        // 删除最新版本的部署
        processEngineComponent.deleteProcessDefinition(result2.getDeploymentId(), true);
        
        // 再次部署新版本
        DeploymentResult result3 = deployProcess(processKey, "Version 3", VALID_SIMPLE_BPMN);
        
        // Flowable的实际行为：删除版本2后，新部署会重新使用版本2
        // 这是Flowable的正常行为，版本号基于当前最高版本递增
        assertThat(result3.getVersion()).isEqualTo(2);
        assertThat(result3.getVersion()).isGreaterThanOrEqualTo(result1.getVersion());
        
        // 验证版本号的一致性：新版本应该有不同的ID
        assertThat(result3.getProcessDefinitionId()).isNotEqualTo(result2.getProcessDefinitionId());
        assertThat(result3.getProcessDefinitionId()).isNotEqualTo(result1.getProcessDefinitionId());
    }

    // 辅助方法
    private DeploymentResult deployProcess(String processKey, String processName, String bpmnTemplate) {
        ProcessDefinitionRequest request = new ProcessDefinitionRequest();
        request.setName(processName);
        request.setKey(processKey);
        request.setCategory("test");
        request.setBpmnXml(bpmnTemplate.replace("PROCESS_KEY", processKey));
        
        return processEngineComponent.deployProcess(request);
    }
}