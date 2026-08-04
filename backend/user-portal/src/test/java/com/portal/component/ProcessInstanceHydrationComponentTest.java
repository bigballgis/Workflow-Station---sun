package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.entity.ProcessInstance;
import com.portal.exception.PortalException;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.service.UserDisplayNameResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessInstanceHydrationComponentTest {

    private static final String PI_ID = "19deadae-73d8-11f1-ba72-e6a5490c4ac4";

    @Mock
    private ProcessInstanceRepository processInstanceRepository;
    @Mock
    private WorkflowEngineClient workflowEngineClient;
    @Mock
    private UserDisplayNameResolver userDisplayNameResolver;
    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private ProcessInstanceHydrationComponent hydrationComponent;

    @Test
    void returnsLocalRowWhenPresent() {
        ProcessInstance local = ProcessInstance.builder().id(PI_ID).processDefinitionKey("Process_MCY").build();
        when(processInstanceRepository.findById(PI_ID)).thenReturn(Optional.of(local));

        ProcessInstance result = hydrationComponent.requireProcessInstance(PI_ID);

        assertThat(result).isSameAs(local);
        verify(workflowEngineClient, never()).getProcessInstance(any());
    }

    @Test
    void hydratesFromEngineWhenLocalMissing() {
        when(processInstanceRepository.findById(PI_ID)).thenReturn(Optional.empty());
        when(workflowEngineClient.getProcessInstance(PI_ID)).thenReturn(Optional.of(Map.of(
                "processInstanceId", PI_ID,
                "processDefinitionKey", "Process_MCY",
                "processDefinitionId", "Process_MCY:6:abc",
                "startUserId", "user-dev",
                "status", "active",
                "variables", Map.of("case_number", "123", "functionUnitId", "fu-1")
        )));
        when(workflowEngineClient.getProcessInstanceStatus(PI_ID)).thenReturn(Optional.of(Map.of(
                "nextTaskName", "Case Submission",
                "nextAssignee", "user-dev"
        )));
        when(userDisplayNameResolver.resolve("user-dev")).thenReturn("Developer Tester");
        when(processInstanceRepository.save(any(ProcessInstance.class))).thenAnswer(inv -> inv.getArgument(0));

        ProcessInstance result = hydrationComponent.requireProcessInstance(PI_ID);

        assertThat(result.getId()).isEqualTo(PI_ID);
        assertThat(result.getProcessDefinitionKey()).isEqualTo("Process_MCY");
        assertThat(result.getStatus()).isEqualTo("RUNNING");
        assertThat(result.getCurrentNode()).isEqualTo("Case Submission");
        assertThat(result.getVariables()).containsEntry("case_number", "123");
        verify(processInstanceRepository).save(any(ProcessInstance.class));
    }

    @Test
    void throwsWhenEngineHasNoInstance() {
        when(processInstanceRepository.findById(PI_ID)).thenReturn(Optional.empty());
        when(workflowEngineClient.getProcessInstance(PI_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hydrationComponent.requireProcessInstance(PI_ID))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining(PI_ID);
    }

    @Test
    void hydratesFromSnapshotWithoutCallingEngine() {
        when(processInstanceRepository.findById(PI_ID)).thenReturn(Optional.empty());
        when(userDisplayNameResolver.resolve("system")).thenReturn("system");
        when(processInstanceRepository.save(any(ProcessInstance.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> snapshot = Map.of(
                "processDefinitionKey", "test-20260803-xc0jmo",
                "businessKey", "email:msg-1",
                "startUserId", "system",
                "status", "RUNNING",
                "variables", Map.of("subject", "hello", "functionUnitId", "50030"));

        ProcessInstance result = hydrationComponent.requireProcessInstance(PI_ID, snapshot);

        assertThat(result.getId()).isEqualTo(PI_ID);
        assertThat(result.getProcessDefinitionKey()).isEqualTo("test-20260803-xc0jmo");
        assertThat(result.getVariables()).containsEntry("subject", "hello");
        verify(workflowEngineClient, never()).getProcessInstance(any());
        verify(processInstanceRepository).save(any(ProcessInstance.class));
    }
}
