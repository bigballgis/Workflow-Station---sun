package com.workflow.component;

import com.workflow.dto.response.ActivityInfo;
import com.workflow.dto.response.GatewayEvaluationResult;
import com.workflow.dto.response.ParallelGatewayResult;
import com.workflow.exception.WorkflowBusinessException;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.ParallelGateway;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 网关执行逻辑单元测试
 * 测试排他网关条件表达式执行、并行网关分支创建和合并、异常情况处理
 */
@ExtendWith(MockitoExtension.class)
class GatewayExecutionTest {

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private RepositoryService repositoryService;

    @InjectMocks
    private ProcessEngineComponent processEngineComponent;

    private String processInstanceId;
    private String gatewayId;
    private String processDefinitionId;

    @BeforeEach
    void setUp() {
        processInstanceId = "process-instance-123";
        gatewayId = "gateway-001";
        processDefinitionId = "process-def-123";
    }

    @Test
    void testEvaluateExclusiveGateway_Success() {
        // Given
        ProcessInstance processInstance = mock(ProcessInstance.class);
        when(processInstance.getProcessDefinitionId()).thenReturn(processDefinitionId);
        
        when(runtimeService.createProcessInstanceQuery())
            .thenReturn(mock(org.flowable.engine.runtime.ProcessInstanceQuery.class));
        when(runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId))
            .thenReturn(mock(org.flowable.engine.runtime.ProcessInstanceQuery.class));
        when(runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult())
            .thenReturn(processInstance);
        when(runtimeService.getVariables(processInstanceId)).thenReturn(new HashMap<>());
        
        // Mock BPMN model and gateway
        BpmnModel bpmnModel = mock(BpmnModel.class);
        ExclusiveGateway exclusiveGateway = mock(ExclusiveGateway.class);
        when(repositoryService.getBpmnModel(processDefinitionId)).thenReturn(bpmnModel);
        when(bpmnModel.getFlowElement(gatewayId)).thenReturn(exclusiveGateway);
        when(exclusiveGateway.getName()).thenReturn("Test Gateway");
        when(exclusiveGateway.getOutgoingFlows()).thenReturn(new ArrayList<>());

        // When
        GatewayEvaluationResult result = processEngineComponent.evaluateExclusiveGateway(
            processInstanceId, gatewayId
        );

        // Then
        assertNotNull(result);
        assertEquals(gatewayId, result.getGatewayId());
        assertEquals("Test Gateway", result.getGatewayName());
        assertEquals("ExclusiveGateway", result.getGatewayType());
        assertNotNull(result.getEvaluationTime());
    }

    @Test
    void testEvaluateExclusiveGateway_ProcessInstanceNotFound_ThrowsException() {
        // Given
        when(runtimeService.createProcessInstanceQuery())
            .thenReturn(mock(org.flowable.engine.runtime.ProcessInstanceQuery.class));
        when(runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId))
            .thenReturn(mock(org.flowable.engine.runtime.ProcessInstanceQuery.class));
        when(runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult())
            .thenReturn(null);

        // When & Then
        WorkflowBusinessException exception = assertThrows(
            WorkflowBusinessException.class,
            () -> processEngineComponent.evaluateExclusiveGateway(processInstanceId, gatewayId)
        );
        
        assertTrue(exception.getMessage().contains("流程实例不存在"));
    }

    @Test
    void testEvaluateExclusiveGateway_InvalidGateway_ThrowsException() {
        // Given
        ProcessInstance processInstance = mock(ProcessInstance.class);
        when(processInstance.getProcessDefinitionId()).thenReturn(processDefinitionId);
        
        when(runtimeService.createProcessInstanceQuery())
            .thenReturn(mock(org.flowable.engine.runtime.ProcessInstanceQuery.class));
        when(runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId))
            .thenReturn(mock(org.flowable.engine.runtime.ProcessInstanceQuery.class));
        when(runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult())
            .thenReturn(processInstance);

        // Mock BPMN model with non-gateway element
        BpmnModel bpmnModel = mock(BpmnModel.class);
        org.flowable.bpmn.model.UserTask userTask = mock(org.flowable.bpmn.model.UserTask.class);
        when(repositoryService.getBpmnModel(processDefinitionId)).thenReturn(bpmnModel);
        when(bpmnModel.getFlowElement(gatewayId)).thenReturn(userTask);

        // When & Then
        WorkflowBusinessException exception = assertThrows(
            WorkflowBusinessException.class,
            () -> processEngineComponent.evaluateExclusiveGateway(processInstanceId, gatewayId)
        );
        
        assertTrue(exception.getMessage().contains("不是排他网关"));
    }

    @Test
    void testHandleParallelGateway_Success() {
        // Given
        ProcessInstance processInstance = mock(ProcessInstance.class);
        when(processInstance.getProcessDefinitionId()).thenReturn(processDefinitionId);
        
        when(runtimeService.createProcessInstanceQuery())
            .thenReturn(mock(org.flowable.engine.runtime.ProcessInstanceQuery.class));
        when(runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId))
            .thenReturn(mock(org.flowable.engine.runtime.ProcessInstanceQuery.class));
        when(runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult())
            .thenReturn(processInstance);

        // Mock BPMN model and parallel gateway
        BpmnModel bpmnModel = mock(BpmnModel.class);
        ParallelGateway parallelGateway = mock(ParallelGateway.class);
        when(repositoryService.getBpmnModel(processDefinitionId)).thenReturn(bpmnModel);
        when(bpmnModel.getFlowElement(gatewayId)).thenReturn(parallelGateway);
        when(parallelGateway.getName()).thenReturn("Parallel Gateway");
        when(parallelGateway.getOutgoingFlows()).thenReturn(Arrays.asList(mock(SequenceFlow.class), mock(SequenceFlow.class)));
        when(parallelGateway.getIncomingFlows()).thenReturn(Arrays.asList(mock(SequenceFlow.class)));
        
        // Mock execution query
        when(runtimeService.createExecutionQuery())
            .thenReturn(mock(org.flowable.engine.runtime.ExecutionQuery.class));
        when(runtimeService.createExecutionQuery().processInstanceId(processInstanceId))
            .thenReturn(mock(org.flowable.engine.runtime.ExecutionQuery.class));
        when(runtimeService.createExecutionQuery().processInstanceId(processInstanceId).activityId(gatewayId))
            .thenReturn(mock(org.flowable.engine.runtime.ExecutionQuery.class));
        when(runtimeService.createExecutionQuery().processInstanceId(processInstanceId).activityId(gatewayId).list())
            .thenReturn(new ArrayList<>());

        // When
        ParallelGatewayResult result = processEngineComponent.handleParallelGateway(
            processInstanceId, gatewayId
        );

        // Then
        assertNotNull(result);
        assertEquals(gatewayId, result.getGatewayId());
        assertEquals("Parallel Gateway", result.getGatewayName());
        assertNotNull(result.getEvaluationTime());
    }

    @Test
    void testHandleParallelGateway_ProcessInstanceNotFound_ThrowsException() {
        // Given
        when(runtimeService.createProcessInstanceQuery())
            .thenReturn(mock(org.flowable.engine.runtime.ProcessInstanceQuery.class));
        when(runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId))
            .thenReturn(mock(org.flowable.engine.runtime.ProcessInstanceQuery.class));
        when(runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult())
            .thenReturn(null);

        // When & Then
        WorkflowBusinessException exception = assertThrows(
            WorkflowBusinessException.class,
            () -> processEngineComponent.handleParallelGateway(processInstanceId, gatewayId)
        );
        
        assertTrue(exception.getMessage().contains("流程实例不存在"));
    }

    @Test
    void testHandleParallelGateway_InvalidGateway_ThrowsException() {
        // Given
        ProcessInstance processInstance = mock(ProcessInstance.class);
        when(processInstance.getProcessDefinitionId()).thenReturn(processDefinitionId);
        
        when(runtimeService.createProcessInstanceQuery())
            .thenReturn(mock(org.flowable.engine.runtime.ProcessInstanceQuery.class));
        when(runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId))
            .thenReturn(mock(org.flowable.engine.runtime.ProcessInstanceQuery.class));
        when(runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult())
            .thenReturn(processInstance);

        // Mock BPMN model with non-parallel-gateway element
        BpmnModel bpmnModel = mock(BpmnModel.class);
        ExclusiveGateway exclusiveGateway = mock(ExclusiveGateway.class);
        when(repositoryService.getBpmnModel(processDefinitionId)).thenReturn(bpmnModel);
        when(bpmnModel.getFlowElement(gatewayId)).thenReturn(exclusiveGateway);

        // When & Then
        WorkflowBusinessException exception = assertThrows(
            WorkflowBusinessException.class,
            () -> processEngineComponent.handleParallelGateway(processInstanceId, gatewayId)
        );
        
        assertTrue(exception.getMessage().contains("不是并行网关"));
    }

    @Test
    void testGetCurrentActivities_Success() {
        // Given
        ProcessInstance processInstance = mock(ProcessInstance.class);
        when(processInstance.getProcessDefinitionId()).thenReturn(processDefinitionId);
        
        when(runtimeService.createProcessInstanceQuery())
            .thenReturn(mock(org.flowable.engine.runtime.ProcessInstanceQuery.class));
        when(runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId))
            .thenReturn(mock(org.flowable.engine.runtime.ProcessInstanceQuery.class));
        when(runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult())
            .thenReturn(processInstance);

        // Mock executions
        Execution execution1 = mock(Execution.class);
        when(execution1.getId()).thenReturn("exec1");
        when(execution1.getActivityId()).thenReturn("task1");
        when(execution1.isEnded()).thenReturn(false);
        
        when(runtimeService.createExecutionQuery())
            .thenReturn(mock(org.flowable.engine.runtime.ExecutionQuery.class));
        when(runtimeService.createExecutionQuery().processInstanceId(processInstanceId))
            .thenReturn(mock(org.flowable.engine.runtime.ExecutionQuery.class));
        when(runtimeService.createExecutionQuery().processInstanceId(processInstanceId).list())
            .thenReturn(Arrays.asList(execution1));

        // Mock BPMN model
        BpmnModel bpmnModel = mock(BpmnModel.class);
        org.flowable.bpmn.model.UserTask userTask = mock(org.flowable.bpmn.model.UserTask.class);
        when(repositoryService.getBpmnModel(processDefinitionId)).thenReturn(bpmnModel);
        when(bpmnModel.getFlowElement("task1")).thenReturn(userTask);
        when(userTask.getName()).thenReturn("User Task 1");

        // When
        List<ActivityInfo> result = processEngineComponent.getCurrentActivities(processInstanceId);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        ActivityInfo activityInfo = result.get(0);
        assertEquals("exec1", activityInfo.getExecutionId());
        assertEquals("task1", activityInfo.getActivityId());
        assertEquals("User Task 1", activityInfo.getActivityName());
        assertTrue(activityInfo.isActive());
    }

    @Test
    void testGetCurrentActivities_ProcessInstanceNotFound_ThrowsException() {
        // Given
        when(runtimeService.createProcessInstanceQuery())
            .thenReturn(mock(org.flowable.engine.runtime.ProcessInstanceQuery.class));
        when(runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId))
            .thenReturn(mock(org.flowable.engine.runtime.ProcessInstanceQuery.class));
        when(runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult())
            .thenReturn(null);

        // When & Then
        WorkflowBusinessException exception = assertThrows(
            WorkflowBusinessException.class,
            () -> processEngineComponent.getCurrentActivities(processInstanceId)
        );
        
        assertTrue(exception.getMessage().contains("流程实例不存在"));
    }
}