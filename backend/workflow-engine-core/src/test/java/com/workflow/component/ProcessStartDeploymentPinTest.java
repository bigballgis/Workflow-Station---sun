package com.workflow.component;

import com.workflow.dto.request.StartProcessRequest;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessStartDeploymentPinTest {
    private final ProcessEngineComponent component = new ProcessEngineComponent();
    private final RepositoryService repository = mock(RepositoryService.class);
    private final RuntimeService runtime = mock(RuntimeService.class);
    private final ProcessDefinition definition = mock(ProcessDefinition.class);
    private final ProcessInstance instance = mock(ProcessInstance.class);
    private com.workflow.component.impl.ProcessStartComponentImpl starter;

    private StartProcessRequest request(String expected) {
        var query = mock(ProcessDefinitionQuery.class, RETURNS_SELF);
        when(repository.createProcessDefinitionQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(definition);
        when(definition.getId()).thenReturn("synthetic:2:current");
        when(definition.getKey()).thenReturn("synthetic");
        when(instance.getId()).thenReturn("synthetic-instance");
        ReflectionTestUtils.setField(component, "repositoryService", repository);
        ReflectionTestUtils.setField(component, "runtimeService", runtime);
        var status = mock(ProcessInstanceStatusReader.class);
        when(status.getProcessInstanceStatus("synthetic-instance")).thenReturn(Map.of("nextTaskName", "Case Submission"));
        ReflectionTestUtils.setField(component, "processInstanceStatusReader", status);
        var manager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(org.springframework.transaction.TransactionStatus.class));
        starter = new com.workflow.component.impl.ProcessStartComponentImpl(repository, runtime, mock(ProcessDeploymentManager.class),
                mock(com.workflow.assignment.AssignmentRootStarter.class), manager);
        ReflectionTestUtils.setField(component, "processStartComponent", starter);
        var request = new StartProcessRequest();
        request.setProcessDefinitionKey("synthetic"); request.setExpectedProcessDefinitionId(expected);
        request.setBusinessKey("synthetic-business"); request.setVariables(Map.of("synthetic", true));
        return request;
    }

    @Test void matchingPinStartsExactlyTheValidatedIdInsteadOfResolvingLatestAgain() {
        var request = request("synthetic:2:current");
        when(runtime.startProcessInstanceById("synthetic:2:current", request.getBusinessKey(), request.getVariables())).thenReturn(instance);
        when(runtime.startProcessInstanceByKey("synthetic", request.getBusinessKey(), request.getVariables())).thenReturn(instance);
        var result = component.startProcess(request);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getNextTaskName()).isEqualTo("Case Submission");
        verify(runtime).startProcessInstanceById("synthetic:2:current", request.getBusinessKey(), request.getVariables());
        verify(runtime, never()).startProcessInstanceByKey(anyString(), anyString(), anyMap());
    }

    @Test void olderExactDefinitionIsValidEvenAfterAnotherVersionIsLatest() {
        var request = request("synthetic:1:old");
        when(definition.getId()).thenReturn("synthetic:1:old");
        when(runtime.startProcessInstanceById("synthetic:1:old", request.getBusinessKey(), request.getVariables())).thenReturn(instance);
        assertThat(component.startProcess(request).isSuccess()).isTrue();
        verify(runtime).startProcessInstanceById("synthetic:1:old", request.getBusinessKey(), request.getVariables());
        verify(runtime, never()).startProcessInstanceByKey(anyString(), anyString(), anyMap());
    }

    @Test void definitionFromAnotherKeyCannotBeStarted() {
        var request = request("synthetic:2:current");
        when(definition.getKey()).thenReturn("another-process");
        assertThatThrownBy(() -> component.startProcess(request)).hasMessageContaining("VERSION_DEFINITION_MISMATCH");
        verifyNoInteractions(runtime);
    }

    @Test void legacyRequestKeepsTheExistingKeyBasedStartContract() {
        var request = request(null);
        when(runtime.startProcessInstanceByKey("synthetic", request.getBusinessKey(), request.getVariables())).thenReturn(instance);
        assertThat(component.startProcess(request).isSuccess()).isTrue();
        verify(runtime).startProcessInstanceByKey("synthetic", request.getBusinessKey(), request.getVariables());
    }

    @Test void authenticatedInitiatorAlsoStartsByTheVerifiedIdAndClearsThreadIdentity() {
        var request = request("synthetic:2:current"); request.setStartUserId("synthetic-user");
        ReflectionTestUtils.setField(starter, "i18nService", mock(com.platform.common.i18n.I18nService.class));
        ReflectionTestUtils.setField(starter, "notificationDispatchHelper", mock(com.platform.messaging.support.NotificationDispatchHelper.class));
        when(runtime.startProcessInstanceById("synthetic:2:current", request.getBusinessKey(), request.getVariables())).thenReturn(instance);
        assertThat(component.startProcess(request).isSuccess()).isTrue();
        verify(runtime).startProcessInstanceById("synthetic:2:current", request.getBusinessKey(), request.getVariables());
        verify(runtime, never()).startProcessInstanceByKey(anyString(), anyString(), anyMap());
        assertThat(org.flowable.common.engine.impl.identity.Authentication.getAuthenticatedUserId()).isNull();
    }
}
