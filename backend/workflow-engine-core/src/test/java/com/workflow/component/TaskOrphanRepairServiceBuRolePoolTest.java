package com.workflow.component;

import com.workflow.client.AdminCenterClient;
import com.workflow.client.PortalAssignmentEventClient;
import com.workflow.repository.EngineActorIdentities;
import com.workflow.repository.ExtendedTaskInfoRepository;
import com.workflow.repository.FlowableRuntimeTaskQueries;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
    @Mock
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;
    @Mock
    private FlowableRuntimeTaskQueries runtimeTaskQueries;
    @Mock
    private PortalAssignmentEventClient portalAssignmentEventClient;

    private TaskOrphanRepairService service;

    @BeforeEach
    void setUp() {
        ClaimPoolMembershipSync sync = new ClaimPoolMembershipSync(taskService, adminCenterClient,
                extendedTaskInfoRepository,
                new ClaimPoolTaskLocator(taskService, bpmnActionParser, extendedTaskInfoRepository, runtimeTaskQueries,
                        new PoolCodes(adminCenterClient)),
                portalAssignmentEventClient, mock(TaskHandover.class), mock(EngineActorIdentities.class));
        service = new TaskOrphanRepairService();
        ReflectionTestUtils.setField(service, "taskService", taskService);
        ReflectionTestUtils.setField(service, "runtimeService", runtimeService);
        ReflectionTestUtils.setField(service, "repositoryService", repositoryService);
        ReflectionTestUtils.setField(service, "adminCenterClient", adminCenterClient);
        ReflectionTestUtils.setField(service, "bpmnActionParser", bpmnActionParser);
        ReflectionTestUtils.setField(service, "claimPoolMembershipSync", sync);
    }

    @Test
    @DisplayName("sole-member BU_ROLE restore adds a candidate, never setAssignee")
    void soleMemberAddsCandidateNotAssignee() {
        Task task = buRoleTask("t-sole");
        when(taskService.getIdentityLinksForTask("t-sole")).thenReturn(List.of());
        when(adminCenterClient.getUsersByBusinessUnitAndRole("bu-1", "role-1")).thenReturn(List.of("123456"));

        service.restoreBuRoleClaimPool(task);

        verify(taskService).addCandidateUser("t-sole", "123456");
        verify(taskService, never()).setAssignee(anyString(), anyString());
    }

    @Test
    @DisplayName("Admin Center unreachable: the release stands and the pool waits for the periodic resync")
    void releaseStandsWhenAdminCenterIsDown() {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("t-down");
        when(task.getProcessDefinitionId()).thenReturn("pd");
        when(task.getTaskDefinitionKey()).thenReturn("step");
        TaskQuery query = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskId("t-down")).thenReturn(query);
        when(query.singleResult()).thenReturn(task);
        when(bpmnActionParser.getUserTaskExtensionPropertyValue("pd", "step", "assigneeType")).thenReturn("BU_ROLE");
        when(bpmnActionParser.getUserTaskExtensionPropertyValue("pd", "step", "roleId")).thenReturn("role-1");
        when(bpmnActionParser.getUserTaskExtensionPropertyValue("pd", "step", "businessUnitId")).thenReturn("bu-1");
        when(bpmnActionParser.getUserTaskExtensionPropertyValue("pd", "step", "assigneeValue")).thenReturn(null);
        when(bpmnActionParser.getUserTaskExtensionPropertyValue("pd", "step", "roleIds")).thenReturn(null);
        when(adminCenterClient.getBusinessUnitCodeById("bu-1"))
                .thenThrow(new com.workflow.exception.AdminCenterUnavailableException("down", null));

        org.assertj.core.api.Assertions.assertThatCode(() -> service.restoreBuRoleClaimPool(task))
                .doesNotThrowAnyException();
        verify(taskService, never()).addCandidateUser(anyString(), anyString());
    }

    @Test
    @DisplayName("an existing pool follows the current holders: newcomers added, former holders removed (WP8b-2)")
    void existingPoolFollowsCurrentHolders() {
        Task task = buRoleTask("t-pool");
        IdentityLink former = candidate("former");
        when(taskService.getIdentityLinksForTask("t-pool")).thenReturn(List.of(former));
        when(adminCenterClient.getUsersByBusinessUnitAndRole("bu-1", "role-1")).thenReturn(List.of("newcomer"));

        service.restoreBuRoleClaimPool(task);

        verify(taskService).deleteCandidateUser("t-pool", "former");
        verify(taskService).addCandidateUser("t-pool", "newcomer");
        verify(taskService, never()).setAssignee(anyString(), anyString());
    }

    @Test
    @DisplayName("an MI role row with a single holder joins the candidate pool, never setAssignee (D-40)")
    void soleMiRoleHolderJoinsThePool() {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("t-mi");
        when(task.getProcessDefinitionId()).thenReturn("pd");
        when(task.getTaskDefinitionKey()).thenReturn("row");
        when(task.getExecutionId()).thenReturn("ex-1");
        TaskQuery query = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskUnassigned()).thenReturn(query);
        when(query.orderByTaskCreateTime()).thenReturn(query);
        when(query.desc()).thenReturn(query);
        when(query.listPage(0, 10)).thenReturn(List.of(task));
        when(bpmnActionParser.getUserTaskExtensionPropertyValue("pd", "row", "assigneeType")).thenReturn("ELEMENT_VARIABLE");
        when(bpmnActionParser.getUserTaskExtensionPropertyValue("pd", "row", "assigneeMode")).thenReturn("role");
        when(bpmnActionParser.getUserTaskExtensionPropertyValue("pd", "row", "roleField")).thenReturn("roleCode");
        when(bpmnActionParser.getUserTaskExtensionPropertyValue("pd", "row", "buField")).thenReturn("buCode");
        when(runtimeService.getVariable("ex-1", "currentItem")).thenReturn(java.util.Map.of("roleCode", "MGR", "buCode", "FIN"));
        when(adminCenterClient.isEligibleRole("FIN", "MGR")).thenReturn(true);
        when(adminCenterClient.getUsersByBusinessUnitAndRole("FIN", "MGR")).thenReturn(List.of("only-holder"));

        service.repairOrphanMultiInstanceTasks(10);

        verify(taskService).addCandidateUser("t-mi", "only-holder");
        verify(taskService, never()).setAssignee(anyString(), anyString());
    }

    private Task buRoleTask(String id) {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn(id);
        when(task.getProcessDefinitionId()).thenReturn("pd");
        when(task.getTaskDefinitionKey()).thenReturn("step");
        TaskQuery query = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskId(id)).thenReturn(query);
        when(query.singleResult()).thenReturn(task);
        when(bpmnActionParser.getUserTaskExtensionPropertyValue("pd", "step", "assigneeType")).thenReturn("BU_ROLE");
        when(bpmnActionParser.getUserTaskExtensionPropertyValue("pd", "step", "roleId")).thenReturn("role-1");
        when(bpmnActionParser.getUserTaskExtensionPropertyValue("pd", "step", "roleIds")).thenReturn(null);
        when(bpmnActionParser.getUserTaskExtensionPropertyValue("pd", "step", "businessUnitId")).thenReturn("bu-1");
        when(bpmnActionParser.getUserTaskExtensionPropertyValue("pd", "step", "assigneeValue")).thenReturn(null);
        when(adminCenterClient.isEligibleRole("bu-1", "role-1")).thenReturn(true);
        return task;
    }

    private static IdentityLink candidate(String userId) {
        IdentityLink link = mock(IdentityLink.class);
        when(link.getType()).thenReturn("candidate");
        when(link.getUserId()).thenReturn(userId);
        return link;
    }
}
