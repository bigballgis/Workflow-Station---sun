package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.controller.TaskController;
import com.portal.dto.TaskCompleteRequest;
import com.portal.exception.PortalException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TaskCompletionObservationTest {
    private final WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
    private final TaskProcessComponent processor = mock(TaskProcessComponent.class, CALLS_REAL_METHODS);
    private final ActionPostEmailComponent email = mock(ActionPostEmailComponent.class);
    private final TaskController controller = new TaskController(null, processor, null, null, null, email, null, null, null);
    private final SubTableBatchInitialMiOutcome.Plan plan = new SubTableBatchInitialMiOutcome.Plan(
            "synthetic-instance", "synthetic-definition", List.of(new SubTableBatchInitialMiOutcome.Expected("configured-node", true,
                    "synthetic_table", List.of(Map.of("synthetic_key", "one")), List.of())));

    TaskCompletionObservationTest() {
        ReflectionTestUtils.setField(processor, "workflowEngineClient", engine);
        when(email.runWithPostEmail(any(), org.mockito.ArgumentMatchers.<Supplier<SubTableBatchInitialMiOutcome.Plan>>any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
    }
    private TaskCompleteRequest request() {
        return TaskCompleteRequest.builder().taskId("forged-path-id").action("APPROVE").build();
    }
    @Test void controllerReturnsSuccessfulActionWithObservationAndDoesNotRetryOnQueryFailure() {
        doReturn(plan).when(processor).completeTaskWithObservationPlan(any(), eq("synthetic-user"), nullable(String.class));
        when(engine.getProcessInstanceTasks("synthetic-instance")).thenThrow(new IllegalStateException("synthetic-private-error"));
        var request = request();
        var result = controller.completeTask("synthetic-task", "synthetic-user", request);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isNotNull();
        assertThat(request.getTaskId()).isEqualTo("synthetic-task");
        assertThat(result.getData().batchAssignmentVerification().status()).isEqualTo("UNCONFIRMED");
        assertThat(result.getData().toString()).doesNotContain("synthetic-private-error");
        var order = inOrder(processor, engine);
        order.verify(processor).completeTaskWithObservationPlan(request, "synthetic-user", null);
        order.verify(processor).observeCompletion(plan);
        order.verify(engine).getProcessInstanceTasks("synthetic-instance");
        verify(processor, never()).completeTask(any(), any(), any());
        verifyNoMoreInteractions(engine);
    }
    @Test void legacyActionHasNoNewResponseDataOrEngineObservation() {
        doReturn(null).when(processor).completeTaskWithObservationPlan(any(), eq("synthetic-user"), nullable(String.class));
        assertThat(controller.completeTask("synthetic-task", "synthetic-user", request()).getData()).isNull();
        verifyNoInteractions(engine);
    }
    @Test void rejectedWriteCannotRunPostCompletionObservation() {
        doThrow(new PortalException("409", "Synthetic preflight rejection")).when(processor)
                .completeTaskWithObservationPlan(any(), eq("synthetic-user"), nullable(String.class));
        assertThatThrownBy(() -> controller.completeTask("synthetic-task", "synthetic-user", request()))
                .isInstanceOf(PortalException.class);
        verify(processor, never()).observeCompletion(any());
        verifyNoInteractions(engine);
    }
    @Test void newCompletionEntryRetainsItsTransactionAndObserverDoesNotOwnOne() throws Exception {
        assertThat(TaskProcessComponent.class.getMethod("completeTaskWithObservationPlan", TaskCompleteRequest.class, String.class, String.class)
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class)).isTrue();
        assertThat(TaskProcessComponent.class.getMethod("observeCompletion", SubTableBatchInitialMiOutcome.Plan.class)
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class)).isFalse();
    }

    @Test void springProxyCommitsBeforeTheControllerObservesTheEngine() {
        var database = new org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder().generateUniqueName(true)
                .setType(org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType.H2).build();
        try {
            var jdbc = new org.springframework.jdbc.core.JdbcTemplate(database);
            jdbc.execute("CREATE TABLE synthetic_completion(id INTEGER PRIMARY KEY)");
            var observed = new java.util.concurrent.atomic.AtomicBoolean();
            doAnswer(invocation -> {
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                jdbc.update("INSERT INTO synthetic_completion VALUES(1)");
                return plan;
            }).when(processor).completeTaskWithObservationPlan(any(), eq("synthetic-user"), nullable(String.class));
            doAnswer(invocation -> {
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM synthetic_completion", Integer.class)).isEqualTo(1);
                observed.set(true);
                return invocation.callRealMethod();
            }).when(processor).observeCompletion(plan);
            when(engine.getProcessInstanceTasks("synthetic-instance")).thenReturn(Optional.empty());
            var factory = new org.springframework.aop.framework.ProxyFactory(processor);
            factory.setProxyTargetClass(true);
            factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(
                    new org.springframework.jdbc.datasource.DataSourceTransactionManager(database),
                    new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
            var proxied = (TaskProcessComponent) factory.getProxy();
            var controller = new TaskController(null, proxied, null, null, null, email, null, null, null);
            var response = controller.completeTask("synthetic-task", "synthetic-user", request());
            assertThat(observed).isTrue();
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().batchAssignmentVerification().status()).isEqualTo("UNCONFIRMED");
        } finally {
            database.shutdown();
        }
    }
}
