package com.workflow.properties;

import com.workflow.component.ProcessEngineComponent;
import com.workflow.dto.request.ProcessDefinitionRequest;
import com.workflow.dto.response.DeploymentResult;
import com.workflow.exception.WorkflowValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * BPMN文件验证完整性属性测试
 * 验证需求: 需求 1.1 - BPMN流程定义管理
 * 
 * 属性 1: BPMN文件验证完整性
 * 对于任何有效的BPMN 2.0文件，系统应该能够成功解析和部署；
 * 对于任何无效的BPMN文件，系统应该拒绝部署并提供明确的错误信息。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class BpmnFileValidationProperties {

    @Autowired
    private ProcessEngineComponent processEngineComponent;

    private static final String VALID_SIMPLE_BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xmlns:flowable="http://flowable.org/bpmn"
                     targetNamespace="http://www.flowable.org/processdef"
                     xsi:schemaLocation="http://www.omg.org/spec/BPMN/20100524/MODEL http://www.omg.org/spec/BPMN/20100524/BPMN20.xsd">
          <process id="simpleProcess" name="Simple Process" isExecutable="true">
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
          <process id="userTaskProcess" name="User Task Process" isExecutable="true">
            <startEvent id="startEvent" name="Start"/>
            <sequenceFlow id="flow1" sourceRef="startEvent" targetRef="userTask"/>
            <userTask id="userTask" name="User Task" flowable:assignee="user1"/>
            <sequenceFlow id="flow2" sourceRef="userTask" targetRef="endEvent"/>
            <endEvent id="endEvent" name="End"/>
          </process>
        </definitions>
        """;

    private static final String VALID_GATEWAY_BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xmlns:flowable="http://flowable.org/bpmn"
                     targetNamespace="http://www.flowable.org/processdef"
                     xsi:schemaLocation="http://www.omg.org/spec/BPMN/20100524/MODEL http://www.omg.org/spec/BPMN/20100524/BPMN20.xsd">
          <process id="gatewayProcess" name="Gateway Process" isExecutable="true">
            <startEvent id="startEvent" name="Start"/>
            <sequenceFlow id="flow1" sourceRef="startEvent" targetRef="gateway"/>
            <exclusiveGateway id="gateway" name="Decision"/>
            <sequenceFlow id="flow2" sourceRef="gateway" targetRef="task1">
              <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
            </sequenceFlow>
            <sequenceFlow id="flow3" sourceRef="gateway" targetRef="task2">
              <conditionExpression xsi:type="tFormalExpression">${approved == false}</conditionExpression>
            </sequenceFlow>
            <userTask id="task1" name="Approved Task"/>
            <userTask id="task2" name="Rejected Task"/>
            <sequenceFlow id="flow4" sourceRef="task1" targetRef="endEvent"/>
            <sequenceFlow id="flow5" sourceRef="task2" targetRef="endEvent"/>
            <endEvent id="endEvent" name="End"/>
          </process>
        </definitions>
        """;

    @BeforeEach
    void setUp() {
        // 确保每个测试都有干净的状态
        assertThat(processEngineComponent).isNotNull();
    }

    /**
     * 属性测试: 有效的BPMN文件应该成功部署
     */
    @ParameterizedTest
    @ValueSource(strings = {"simpleProcess", "userTaskProcess", "gatewayProcess"})
    void validBpmnFilesShouldDeploySuccessfully(String processType) {
        // Given: 创建有效的流程定义请求
        String processKey = "test_" + processType + "_" + UUID.randomUUID().toString().substring(0, 8);
        String processName = "Test " + processType + " Process";
        
        ProcessDefinitionRequest request = new ProcessDefinitionRequest();
        request.setName(processName);
        request.setKey(processKey);
        request.setCategory("test");
        request.setBpmnXml(getBpmnContent(processType, processKey));
        
        // When: 部署流程定义
        DeploymentResult result = processEngineComponent.deployProcess(request);
        
        // Then: 部署应该成功
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getDeploymentId()).isNotNull();
        assertThat(result.getProcessDefinitionId()).isNotNull();
        assertThat(result.getProcessDefinitionKey()).isEqualTo(processKey);
        assertThat(result.getMessage()).contains("成功");
    }

    /**
     * 属性测试: 无效的BPMN文件应该被拒绝
     */
    @ParameterizedTest
    @ValueSource(strings = {"invalidXml", "incompleteBpmn", "invalidFlow", "nonXml"})
    void invalidBpmnFilesShouldBeRejected(String invalidType) {
        // Given: 创建无效的流程定义请求
        String processKey = "invalid_" + invalidType + "_" + UUID.randomUUID().toString().substring(0, 8);
        String processName = "Invalid " + invalidType + " Process";
        
        ProcessDefinitionRequest request = new ProcessDefinitionRequest();
        request.setName(processName);
        request.setKey(processKey);
        request.setCategory("test");
        request.setBpmnXml(getInvalidBpmnContent(invalidType));
        
        // When & Then: 部署应该失败并抛出验证异常
        assertThatThrownBy(() -> processEngineComponent.deployProcess(request))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("BPMN文件格式验证失败");
    }

    /**
     * 属性测试: 纯空白字符的BPMN内容应该被拒绝
     */
    @Test
    void whitespaceOnlyBpmnContentShouldBeRejected() {
        // Given: 创建纯空白字符的流程定义请求
        String processKey = "whitespace_test_" + UUID.randomUUID().toString().substring(0, 8);
        String processName = "Whitespace Test Process";
        
        ProcessDefinitionRequest request = new ProcessDefinitionRequest();
        request.setName(processName);
        request.setKey(processKey);
        request.setCategory("test");
        request.setBpmnXml("   \n\t   ");
        
        // When & Then: 部署应该失败并抛出验证异常
        // 纯空白字符应该被基本内容验证捕获，而不是格式验证
        assertThatThrownBy(() -> processEngineComponent.deployProcess(request))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("BPMN内容不能为空");
    }

    /**
     * 属性测试: 空或null的BPMN内容应该被拒绝
     */
    @Test
    void emptyOrNullBpmnContentShouldBeRejected() {
        String processKey = "empty_test_" + UUID.randomUUID().toString().substring(0, 8);
        String processName = "Empty Test Process";
        
        // Test null content
        ProcessDefinitionRequest requestWithNull = new ProcessDefinitionRequest();
        requestWithNull.setName(processName);
        requestWithNull.setKey(processKey);
        requestWithNull.setCategory("test");
        requestWithNull.setBpmnXml(null);
        
        assertThatThrownBy(() -> processEngineComponent.deployProcess(requestWithNull))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("BPMN内容不能为空");
        
        // Test empty content
        ProcessDefinitionRequest requestWithEmpty = new ProcessDefinitionRequest();
        requestWithEmpty.setName(processName + "_empty");
        requestWithEmpty.setKey(processKey + "_empty");
        requestWithEmpty.setCategory("test");
        requestWithEmpty.setBpmnXml("");
        
        assertThatThrownBy(() -> processEngineComponent.deployProcess(requestWithEmpty))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("BPMN内容不能为空");
    }

    /**
     * 属性测试: 重复的流程定义键应该创建新版本
     */
    @Test
    void duplicateProcessKeyShouldCreateNewVersion() {
        String processKey = "duplicate_test_" + UUID.randomUUID().toString().substring(0, 8);
        String processName = "Duplicate Test Process";
        
        // Given: 创建第一个流程定义
        ProcessDefinitionRequest request1 = new ProcessDefinitionRequest();
        request1.setName(processName + "_v1");
        request1.setKey(processKey);
        request1.setCategory("test");
        request1.setBpmnXml(getBpmnContent("simpleProcess", processKey));
        
        // When: 部署第一个版本
        DeploymentResult result1 = processEngineComponent.deployProcess(request1);
        
        // Then: 第一个版本应该成功
        assertThat(result1.isSuccess()).isTrue();
        assertThat(result1.getVersion()).isEqualTo(1);
        
        // Given: 创建第二个流程定义（相同的key）
        ProcessDefinitionRequest request2 = new ProcessDefinitionRequest();
        request2.setName(processName + "_v2");
        request2.setKey(processKey);
        request2.setCategory("test");
        request2.setBpmnXml(getBpmnContent("userTaskProcess", processKey));
        
        // When: 部署第二个版本
        DeploymentResult result2 = processEngineComponent.deployProcess(request2);
        
        // Then: 第二个版本应该成功，版本号递增
        assertThat(result2.isSuccess()).isTrue();
        assertThat(result2.getVersion()).isEqualTo(2);
        assertThat(result2.getProcessDefinitionKey()).isEqualTo(processKey);
    }

    // 辅助方法：获取有效的BPMN内容
    private String getBpmnContent(String processType, String processKey) {
        return switch (processType) {
            case "simpleProcess" -> VALID_SIMPLE_BPMN.replace("simpleProcess", processKey);
            case "userTaskProcess" -> VALID_USER_TASK_BPMN.replace("userTaskProcess", processKey);
            case "gatewayProcess" -> VALID_GATEWAY_BPMN.replace("gatewayProcess", processKey);
            default -> VALID_SIMPLE_BPMN.replace("simpleProcess", processKey);
        };
    }

    // 辅助方法：获取无效的BPMN内容
    private String getInvalidBpmnContent(String invalidType) {
        return switch (invalidType) {
            case "invalidXml" -> "<?xml version='1.0'?><invalid>not bpmn</invalid>";
            case "incompleteBpmn" -> """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <process id="incomplete"/>
                </definitions>
                """;
            case "invalidFlow" -> """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             targetNamespace="http://www.flowable.org/processdef">
                  <process id="invalidFlow" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="nonExistentTask"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """;
            case "nonXml" -> "This is not XML at all";
            case "whitespace" -> "   \n\t   ";
            default -> "invalid content";
        };
    }
}