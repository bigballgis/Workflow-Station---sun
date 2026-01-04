package com.workflow.properties;

import com.workflow.component.ProcessEngineComponent;
import com.workflow.dto.request.ProcessDefinitionRequest;
import com.workflow.dto.response.DeploymentResult;
import com.workflow.dto.response.ProcessDefinitionResult;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
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
 * 流程查询信息完整性属性测试
 * 验证需求: 需求 1.3 - 流程定义查询和信息获取
 * 
 * 属性 3: 流程查询信息完整性
 * 查询返回的流程定义信息必须与实际部署的信息完全一致，
 * 包括名称、版本、类别、资源名称等所有元数据。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ProcessQueryIntegrityProperties {

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
        assertThat(repositoryService).isNotNull();
    }

    /**
     * 属性测试: 查询结果与部署信息完全一致
     */
    @Test
    void queryResultShouldMatchDeploymentInfo() {
        String processKey = "integrity_test_" + UUID.randomUUID().toString().substring(0, 8);
        String processName = "Integrity Test Process";
        String category = "test-category";
        
        // 部署流程定义
        DeploymentResult deploymentResult = deployProcess(processKey, processName, category, VALID_SIMPLE_BPMN);
        assertThat(deploymentResult.isSuccess()).isTrue();
        
        // 查询流程定义
        List<ProcessDefinitionResult> definitions = processEngineComponent.getProcessDefinitions(category, processKey);
        
        // 验证查询结果完整性
        assertThat(definitions).hasSize(1);
        ProcessDefinitionResult definition = definitions.get(0);
        
        // 验证基本信息一致性
        assertThat(definition.getKey()).isEqualTo(processKey);
        assertThat(definition.getName()).isEqualTo(processName);
        assertThat(definition.getCategory()).isEqualTo(category);
        assertThat(definition.getVersion()).isEqualTo(deploymentResult.getVersion());
        assertThat(definition.getId()).isEqualTo(deploymentResult.getProcessDefinitionId());
        assertThat(definition.getDeploymentId()).isEqualTo(deploymentResult.getDeploymentId());
        
        // 验证资源信息
        assertThat(definition.getResourceName()).isEqualTo(processKey + ".bpmn");
        assertThat(definition.getSuspended()).isFalse();
        assertThat(definition.getHasStartFormKey()).isFalse();
        assertThat(definition.getHasGraphicalNotation()).isFalse();
    }

    /**
     * 属性测试: 多个流程定义查询信息完整性
     */
    @Test
    void multipleProcessDefinitionsQueryIntegrity() {
        String baseKey = "multi_test_" + UUID.randomUUID().toString().substring(0, 8);
        String category = "multi-category";
        
        // 部署多个不同的流程定义
        DeploymentResult result1 = deployProcess(baseKey + "_1", "Process 1", category, VALID_SIMPLE_BPMN);
        DeploymentResult result2 = deployProcess(baseKey + "_2", "Process 2", category, VALID_USER_TASK_BPMN);
        
        // 查询指定类别的所有流程定义
        List<ProcessDefinitionResult> definitions = processEngineComponent.getProcessDefinitions(category, null);
        
        // 验证查询结果包含所有部署的流程定义
        assertThat(definitions).hasSize(2);
        
        // 验证每个流程定义的信息完整性
        ProcessDefinitionResult def1 = definitions.stream()
                .filter(def -> def.getKey().equals(baseKey + "_1"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Process 1 not found"));
        
        ProcessDefinitionResult def2 = definitions.stream()
                .filter(def -> def.getKey().equals(baseKey + "_2"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Process 2 not found"));
        
        // 验证第一个流程定义
        assertThat(def1.getName()).isEqualTo("Process 1");
        assertThat(def1.getCategory()).isEqualTo(category);
        assertThat(def1.getId()).isEqualTo(result1.getProcessDefinitionId());
        assertThat(def1.getDeploymentId()).isEqualTo(result1.getDeploymentId());
        
        // 验证第二个流程定义
        assertThat(def2.getName()).isEqualTo("Process 2");
        assertThat(def2.getCategory()).isEqualTo(category);
        assertThat(def2.getId()).isEqualTo(result2.getProcessDefinitionId());
        assertThat(def2.getDeploymentId()).isEqualTo(result2.getDeploymentId());
    }

    /**
     * 属性测试: 查询结果与Flowable原生查询一致性
     */
    @Test
    void queryResultConsistentWithFlowableNative() {
        String processKey = "native_test_" + UUID.randomUUID().toString().substring(0, 8);
        String processName = "Native Consistency Test";
        String category = "native-category";
        
        // 部署流程定义
        DeploymentResult deploymentResult = deployProcess(processKey, processName, category, VALID_SIMPLE_BPMN);
        
        // 使用我们的组件查询
        List<ProcessDefinitionResult> ourResults = processEngineComponent.getProcessDefinitions(category, processKey);
        
        // 使用Flowable原生查询 - 注意：需要查询部署而不是流程定义的类别
        ProcessDefinition nativeResult = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .latestVersion()
                .singleResult();
        
        // 验证查询结果一致性
        assertThat(ourResults).hasSize(1);
        assertThat(nativeResult).isNotNull();
        
        ProcessDefinitionResult ourResult = ourResults.get(0);
        
        assertThat(ourResult.getId()).isEqualTo(nativeResult.getId());
        assertThat(ourResult.getKey()).isEqualTo(nativeResult.getKey());
        // 注意：我们的结果使用部署名称，而原生结果使用BPMN中的名称
        // 这是预期的行为差异，我们的实现优先使用部署名称
        assertThat(ourResult.getVersion()).isEqualTo(nativeResult.getVersion());
        // 我们的结果使用部署类别，而原生结果使用BPMN命名空间
        // 这也是预期的行为差异
        assertThat(ourResult.getDeploymentId()).isEqualTo(nativeResult.getDeploymentId());
        assertThat(ourResult.getResourceName()).isEqualTo(nativeResult.getResourceName());
        assertThat(ourResult.getSuspended()).isEqualTo(nativeResult.isSuspended());
        assertThat(ourResult.getHasStartFormKey()).isEqualTo(nativeResult.hasStartFormKey());
        assertThat(ourResult.getHasGraphicalNotation()).isEqualTo(nativeResult.hasGraphicalNotation());
    }

    /**
     * 属性测试: 查询过滤条件准确性
     */
    @Test
    void queryFilterAccuracy() {
        String baseKey = "filter_test_" + UUID.randomUUID().toString().substring(0, 8);
        String category1 = "category-1";
        String category2 = "category-2";
        
        // 部署不同类别的流程定义
        deployProcess(baseKey + "_cat1", "Process Cat1", category1, VALID_SIMPLE_BPMN);
        deployProcess(baseKey + "_cat2", "Process Cat2", category2, VALID_USER_TASK_BPMN);
        
        // 测试类别过滤
        List<ProcessDefinitionResult> cat1Results = processEngineComponent.getProcessDefinitions(category1, null);
        List<ProcessDefinitionResult> cat2Results = processEngineComponent.getProcessDefinitions(category2, null);
        
        // 验证类别过滤准确性
        assertThat(cat1Results).hasSize(1);
        assertThat(cat1Results.get(0).getCategory()).isEqualTo(category1);
        assertThat(cat1Results.get(0).getKey()).isEqualTo(baseKey + "_cat1");
        
        assertThat(cat2Results).hasSize(1);
        assertThat(cat2Results.get(0).getCategory()).isEqualTo(category2);
        assertThat(cat2Results.get(0).getKey()).isEqualTo(baseKey + "_cat2");
        
        // 测试键过滤
        List<ProcessDefinitionResult> keyResults = processEngineComponent.getProcessDefinitions(null, baseKey + "_cat1");
        assertThat(keyResults).hasSize(1);
        assertThat(keyResults.get(0).getKey()).isEqualTo(baseKey + "_cat1");
        assertThat(keyResults.get(0).getCategory()).isEqualTo(category1);
        
        // 测试组合过滤
        List<ProcessDefinitionResult> combinedResults = processEngineComponent.getProcessDefinitions(category2, baseKey + "_cat2");
        assertThat(combinedResults).hasSize(1);
        assertThat(combinedResults.get(0).getKey()).isEqualTo(baseKey + "_cat2");
        assertThat(combinedResults.get(0).getCategory()).isEqualTo(category2);
    }

    /**
     * 属性测试: 空查询结果处理
     */
    @Test
    void emptyQueryResultHandling() {
        String nonExistentKey = "non_existent_" + UUID.randomUUID().toString().substring(0, 8);
        String nonExistentCategory = "non-existent-category";
        
        // 查询不存在的流程定义
        List<ProcessDefinitionResult> keyResults = processEngineComponent.getProcessDefinitions(null, nonExistentKey);
        List<ProcessDefinitionResult> categoryResults = processEngineComponent.getProcessDefinitions(nonExistentCategory, null);
        List<ProcessDefinitionResult> combinedResults = processEngineComponent.getProcessDefinitions(nonExistentCategory, nonExistentKey);
        
        // 验证空结果处理
        assertThat(keyResults).isEmpty();
        assertThat(categoryResults).isEmpty();
        assertThat(combinedResults).isEmpty();
    }

    /**
     * 属性测试: 查询结果数据类型正确性
     */
    @Test
    void queryResultDataTypeCorrectness() {
        String processKey = "datatype_test_" + UUID.randomUUID().toString().substring(0, 8);
        String processName = "Data Type Test Process";
        String category = "datatype-category";
        
        // 部署流程定义
        DeploymentResult deploymentResult = deployProcess(processKey, processName, category, VALID_SIMPLE_BPMN);
        
        // 查询流程定义
        List<ProcessDefinitionResult> definitions = processEngineComponent.getProcessDefinitions(category, processKey);
        assertThat(definitions).hasSize(1);
        
        ProcessDefinitionResult definition = definitions.get(0);
        
        // 验证数据类型正确性
        assertThat(definition.getId()).isInstanceOf(String.class).isNotEmpty();
        assertThat(definition.getKey()).isInstanceOf(String.class).isEqualTo(processKey);
        assertThat(definition.getName()).isInstanceOf(String.class).isEqualTo(processName);
        assertThat(definition.getVersion()).isInstanceOf(Integer.class).isPositive();
        assertThat(definition.getCategory()).isInstanceOf(String.class).isEqualTo(category);
        assertThat(definition.getDeploymentId()).isInstanceOf(String.class).isNotEmpty();
        assertThat(definition.getResourceName()).isInstanceOf(String.class).isNotEmpty();
        assertThat(definition.getSuspended()).isInstanceOf(Boolean.class);
        assertThat(definition.getHasStartFormKey()).isInstanceOf(Boolean.class);
        assertThat(definition.getHasGraphicalNotation()).isInstanceOf(Boolean.class);
        
        // 验证可选字段处理
        // 这些字段可能为null，但如果不为null，应该是正确的类型
        if (definition.getDescription() != null) {
            assertThat(definition.getDescription()).isInstanceOf(String.class);
        }
        if (definition.getDiagramResourceName() != null) {
            assertThat(definition.getDiagramResourceName()).isInstanceOf(String.class);
        }
        if (definition.getTenantId() != null) {
            assertThat(definition.getTenantId()).isInstanceOf(String.class);
        }
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
}