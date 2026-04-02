package com.workflow.component;

import com.workflow.dto.MultiInstanceCancelResult;
import com.workflow.dto.request.ProcessInstanceControlRequest;
import com.workflow.dto.response.ProcessInstanceControlResult;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * 测试 ProcessEngineComponent.controlProcessInstance() 的 terminate 分支
 * 验证在终止流程实例时，会先调用 MultiInstanceCanceller 取消多实例子任务
 */
@ExtendWith(MockitoExtension.class)
class ProcessEngineComponentTerminateMultiInstanceTest {

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private MultiInstanceCanceller multiInstanceCanceller;

    @InjectMocks
    private ProcessEngineComponent processEngineComponent;

    @Mock
    private ProcessInstanceQuery processInstanceQuery;

    @Mock
    private ProcessInstance processInstance;

    private static final String PROCESS_INSTANCE_ID = "test-process-instance-001";
    private static final String USER_ID = "test-user";
    private static final String REASON = "Test termination";

    @BeforeEach
    void setUp() {
        // Setup mock chain for process instance query (lenient for tests that don't use all mocks)
        lenient().when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
        lenient().when(processInstanceQuery.processInstanceId(anyString())).thenReturn(processInstanceQuery);
        lenient().when(processInstanceQuery.singleResult()).thenReturn(processInstance);
        lenient().when(processInstance.isEnded()).thenReturn(false);
    }

    @Test
    void testTerminateCallsMultiInstanceCancellerBeforeDelete() {
        // Given: 准备终止请求
        ProcessInstanceControlRequest request = new ProcessInstanceControlRequest();
        request.setProcessInstanceId(PROCESS_INSTANCE_ID);
        request.setAction("terminate");
        request.setUserId(USER_ID);
        request.setReason(REASON);

        // Mock MultiInstanceCanceller 返回成功结果
        MultiInstanceCancelResult cancelResult = MultiInstanceCancelResult.builder()
                .cancelledCount(3)
                .failedCount(0)
                .cancelledTasks(new ArrayList<>())
                .build();
        when(multiInstanceCanceller.cancelMultiInstanceTasks(PROCESS_INSTANCE_ID))
                .thenReturn(cancelResult);

        // When: 执行终止操作
        ProcessInstanceControlResult result = processEngineComponent.controlProcessInstance(request);

        // Then: 验证结果成功
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getCurrentState()).isEqualTo("terminated");

        // 验证调用顺序：先取消多实例子任务，再删除流程实例
        var inOrder = inOrder(multiInstanceCanceller, runtimeService);
        inOrder.verify(multiInstanceCanceller).cancelMultiInstanceTasks(PROCESS_INSTANCE_ID);
        inOrder.verify(runtimeService).deleteProcessInstance(PROCESS_INSTANCE_ID, REASON);
    }

    @Test
    void testTerminateContinuesEvenIfCancellerFails() {
        // Given: 准备终止请求
        ProcessInstanceControlRequest request = new ProcessInstanceControlRequest();
        request.setProcessInstanceId(PROCESS_INSTANCE_ID);
        request.setAction("terminate");
        request.setUserId(USER_ID);
        request.setReason(REASON);

        // Mock MultiInstanceCanceller 抛出异常
        when(multiInstanceCanceller.cancelMultiInstanceTasks(PROCESS_INSTANCE_ID))
                .thenThrow(new RuntimeException("Canceller failed"));

        // When: 执行终止操作
        ProcessInstanceControlResult result = processEngineComponent.controlProcessInstance(request);

        // Then: 验证终止操作仍然成功（不受取消失败影响）
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getCurrentState()).isEqualTo("terminated");

        // 验证仍然调用了删除流程实例
        verify(runtimeService).deleteProcessInstance(PROCESS_INSTANCE_ID, REASON);
    }

    @Test
    void testTerminateWithNoReason() {
        // Given: 准备终止请求（无原因）
        ProcessInstanceControlRequest request = new ProcessInstanceControlRequest();
        request.setProcessInstanceId(PROCESS_INSTANCE_ID);
        request.setAction("terminate");
        request.setUserId(USER_ID);
        request.setReason(null);

        // Mock MultiInstanceCanceller 返回成功结果
        MultiInstanceCancelResult cancelResult = MultiInstanceCancelResult.builder()
                .cancelledCount(0)
                .failedCount(0)
                .cancelledTasks(new ArrayList<>())
                .build();
        when(multiInstanceCanceller.cancelMultiInstanceTasks(PROCESS_INSTANCE_ID))
                .thenReturn(cancelResult);

        // When: 执行终止操作
        ProcessInstanceControlResult result = processEngineComponent.controlProcessInstance(request);

        // Then: 验证结果成功
        assertThat(result.isSuccess()).isTrue();

        // 验证使用默认原因
        verify(runtimeService).deleteProcessInstance(PROCESS_INSTANCE_ID, "Manually terminated");
    }

    @Test
    void testTerminateFailsIfProcessAlreadyEnded() {
        // Given: 流程实例已结束
        when(processInstance.isEnded()).thenReturn(true);

        ProcessInstanceControlRequest request = new ProcessInstanceControlRequest();
        request.setProcessInstanceId(PROCESS_INSTANCE_ID);
        request.setAction("terminate");
        request.setUserId(USER_ID);

        // When: 执行终止操作
        ProcessInstanceControlResult result = processEngineComponent.controlProcessInstance(request);

        // Then: 验证失败
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("already ended");

        // 验证不调用取消器和删除操作
        verify(multiInstanceCanceller, never()).cancelMultiInstanceTasks(anyString());
        verify(runtimeService, never()).deleteProcessInstance(anyString(), anyString());
    }

    @Test
    void testTerminateFailsIfProcessNotFound() {
        // Given: 流程实例不存在
        when(processInstanceQuery.singleResult()).thenReturn(null);

        ProcessInstanceControlRequest request = new ProcessInstanceControlRequest();
        request.setProcessInstanceId(PROCESS_INSTANCE_ID);
        request.setAction("terminate");
        request.setUserId(USER_ID);

        // When: 执行终止操作
        ProcessInstanceControlResult result = processEngineComponent.controlProcessInstance(request);

        // Then: 验证失败
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("does not exist");

        // 验证不调用取消器和删除操作
        verify(multiInstanceCanceller, never()).cancelMultiInstanceTasks(anyString());
        verify(runtimeService, never()).deleteProcessInstance(anyString(), anyString());
    }
}
