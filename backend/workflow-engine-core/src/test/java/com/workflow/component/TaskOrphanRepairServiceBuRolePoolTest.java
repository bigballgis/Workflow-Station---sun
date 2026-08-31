package com.workflow.component;

import com.workflow.client.AdminCenterClient;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaskOrphanRepairService — BU Role pool restore")
class TaskOrphanRepairServiceBuRolePoolTest {

    @Mock
    private TaskService taskService;
    @Mock
    private RuntimeService runtimeService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private AdminCenterClient adminCenterClient;
    @Mock
    private BpmnActionParser bpmnActionParser;

    private TaskOrphanRepairService service;

    @BeforeEach
    void setUp() {
        service = new TaskOrphanRepairService();
        ReflectionTestUtils.setField(service, "taskService", taskService);
        ReflectionTestUtils.setField(service, "runtimeService", runtimeService);
        ReflectionTestUtils.setField(service, "repositoryService", repositoryService);
        ReflectionTestUtils.setField(service, "adminCenterClient", adminCenterClient);
        ReflectionTestUtils.setField(service, "bpmnActionParser", bpmnActionParser);
    }

    @Test
    @DisplayName("sole-member BU_ROLE restore adds a candidate, never setAssignee")
    void soleMemberAddsCandidateNotAssignee() {
        Task task = org.mockito.Mockito.mock(Task.class);
        when(task.getId()).thenReturn("t-sole");
        when(task.getProcessDefinitionId()).thenReturn("pd");
        when(task.getTaskDefinitionKey()).thenReturn("step");
        when(taskService.getIdentityLinksForTask("t-sole")).thenReturn(List.of());
        when(bpmnActionParser.getUserTaskExtensionPropertyValue("pd", "step", "assigneeType"))
                .thenReturn("BU_ROLE");
        when(bpmnActionParser.getUserTaskExtensionPropertyValue("pd", "step", "roleId"))
                .thenReturn("role-1");
        when(bpmnActionParser.getUserTaskExtensionPropertyValue("pd", "step", "roleIds"))
                .thenReturn(null);
        when(bpmnActionParser.getUserTaskExtensionPropertyValue("pd", "step", "businessUnitId"))
                .thenReturn("bu-1");
        when(adminCenterClient.isEligibleRole("bu-1", "role-1")).thenReturn(true);
        when(adminCenterClient.getUsersByBusinessUnitAndRole("bu-1", "role-1"))
                .thenReturn(List.of("123456"));

        service.restoreBuRoleClaimPool(task);

        verify(taskService).addCandidateUser("t-sole", "123456");
        verify(taskService, never()).setAssignee(anyString(), anyString());
    }

    @Test
    @DisplayName("existing candidate links are not rewritten")
    void existingCandidatesAreLeftAlone() {
        Task task = org.mockito.Mockito.mock(Task.class);
        when(task.getId()).thenReturn("t-pool");
        IdentityLink link = org.mockito.Mockito.mock(IdentityLink.class);
        when(link.getType()).thenReturn("candidate");
        when(link.getUserId()).thenReturn("u1");
        when(taskService.getIdentityLinksForTask("t-pool")).thenReturn(List.of(link));

        service.restoreBuRoleClaimPool(task);

        verify(taskService, never()).addCandidateUser(any(), any());
        verify(taskService, never()).setAssignee(any(), any());
        verify(adminCenterClient, never()).getUsersByBusinessUnitAndRole(any(), any());
    }
}
