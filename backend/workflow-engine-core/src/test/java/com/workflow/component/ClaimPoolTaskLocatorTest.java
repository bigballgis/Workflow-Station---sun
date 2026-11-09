package com.workflow.component;

import com.workflow.client.AdminCenterClient;
import com.workflow.component.ClaimPoolTaskLocator.PoolTask;
import com.workflow.dto.request.ClaimPoolResyncRequest.Pool;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.repository.ExtendedTaskInfoRepository;
import com.workflow.repository.FlowableRuntimeTaskQueries;
import com.workflow.repository.FlowableRuntimeTaskQueries.TaskNode;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimPoolTaskLocatorTest {

    @Mock
    private TaskService taskService;
    @Mock
    private BpmnActionParser bpmnActionParser;
    @Mock
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;
    @Mock
    private FlowableRuntimeTaskQueries runtimeTaskQueries;
    @Mock
    private AdminCenterClient adminCenterClient;

    private ClaimPoolTaskLocator locator;
    private final Map<String, String> bpmn = new HashMap<>();

    @BeforeEach
    void setUp() {
        locator = new ClaimPoolTaskLocator(taskService, bpmnActionParser, extendedTaskInfoRepository, runtimeTaskQueries,
                new PoolCodes(adminCenterClient));
        lenient().when(bpmnActionParser.getUserTaskExtensionPropertyValue(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> bpmn.get(inv.getArgument(0) + "|" + inv.getArgument(1) + "|" + inv.getArgument(2)));
    }

    @Test
    void aPushMatchesBpmnByCodeOrByIdAndAnswersInCodes() {
        node("pd-new", "approve", "BU_ROLE", "FIN", "MANAGER");
        node("pd-old", "approve", "BU_ROLE", "bu-uuid", "role-uuid");
        node("pd-other", "approve", "BU_ROLE", "HR", "MANAGER");
        Task newTask = tasksOfNode("pd-new", "approve", "t-new");
        Task oldTask = tasksOfNode("pd-old", "approve", "t-old");
        when(runtimeTaskQueries.distinctOpenTaskNodes()).thenReturn(List.of(
                new TaskNode("pd-new", "approve"), new TaskNode("pd-old", "approve"), new TaskNode("pd-other", "approve")));

        List<PoolTask> found = locator.of(List.of(new Pool("bu-uuid", "FIN", "role-uuid", "MANAGER")));

        assertThat(found).extracting(PoolTask::task).containsExactly(newTask, oldTask);
        assertThat(found).allSatisfy(p -> {
            assertThat(p.businessUnitCode()).isEqualTo("FIN");
            assertThat(p.roleCodes()).containsExactly("MANAGER");
        });
    }

    @Test
    void thePeriodicResyncTurnsIdsOfOlderDeploymentsIntoCodesToo() {
        node("pd-old", "approve", "BU_ROLE", "bu-uuid", "role-uuid");
        tasksOfNode("pd-old", "approve", "t-old");
        when(runtimeTaskQueries.distinctOpenTaskNodes()).thenReturn(List.of(new TaskNode("pd-old", "approve")));
        when(adminCenterClient.getBusinessUnitCodeById("bu-uuid")).thenReturn("FIN");
        when(adminCenterClient.getBuBoundedRoles()).thenReturn(List.of(Map.of("id", "role-uuid", "code", "MANAGER")));

        assertThat(locator.all()).singleElement().satisfies(p -> {
            assertThat(p.businessUnitCode()).isEqualTo("FIN");
            assertThat(p.roleCodes()).containsExactly("MANAGER");
        });
    }

    @Test
    void aBpmnPoolTaskCarriesItsExtendedRowOnEveryPath() {
        node("pd", "approve", "BU_ROLE", "FIN", "MANAGER");
        Task task = tasksOfNode("pd", "approve", "t-1");
        ExtendedTaskInfo row = ExtendedTaskInfo.builder().taskId("t-1").claimedBy("holder").build();
        when(extendedTaskInfoRepository.findByTaskIdInAndIsDeletedFalse(List.of("t-1"))).thenReturn(List.of(row));
        when(runtimeTaskQueries.distinctOpenTaskNodes()).thenReturn(List.of(new TaskNode("pd", "approve")));

        assertThat(locator.all()).singleElement().satisfies(p -> {
            assertThat(p.task()).isSameAs(task);
            assertThat(p.miRow()).isSameAs(row);
        });
    }

    @Test
    void manualAssignmentIsAPoolOnlyWhenNoUserWasChosen() {
        bpmn.put("pd|pick|assigneeType", "MANUAL_ASSIGN");
        Task byRole = mock(Task.class);
        Task byUser = mock(Task.class);
        stubTask(byRole, "t-role", "pd", "pick");
        stubTask(byUser, "t-user", "pd", "pick");
        TaskQuery query = nodeQuery("pd", "pick");
        when(query.list()).thenReturn(List.of(byRole, byUser));
        when(taskService.getVariables(eq("t-role"), anyCollection()))
                .thenReturn(Map.of("manualAssignBu_pick", "FIN", "manualAssignRole_pick", "MANAGER"));
        when(taskService.getVariables(eq("t-user"), anyCollection()))
                .thenReturn(Map.of("manualAssignee_pick", "u1"));
        when(runtimeTaskQueries.distinctOpenTaskNodes()).thenReturn(List.of(new TaskNode("pd", "pick")));

        List<PoolTask> found = locator.all();

        assertThat(found).extracting(p -> p.task().getId()).containsExactly("t-role");
    }

    @Test
    void miRowsAssignedByRoleArePools() {
        ExtendedTaskInfo row = ExtendedTaskInfo.builder().taskId("t-mi")
                .extendedProperties("{\"assigneeMode\":\"role\",\"businessUnitCode\":\"FIN\",\"roleCodes\":[\"MANAGER\"]}")
                .build();
        when(extendedTaskInfoRepository.findOpenMiRoleRows()).thenReturn(List.of(row));
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("t-mi");
        TaskQuery query = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskIds(anyCollection())).thenReturn(query);
        when(query.list()).thenReturn(List.of(task));

        List<PoolTask> found = locator.all();

        assertThat(found).singleElement().satisfies(p -> {
            assertThat(p.miRow()).isSameAs(row);
            assertThat(p.businessUnitCode()).isEqualTo("FIN");
        });
    }

    @Test
    void hierarchyRolePoolsAreNotResynced() {
        bpmn.put("pd|step|assigneeType", "HIERARCHY_ROLE");
        when(runtimeTaskQueries.distinctOpenTaskNodes()).thenReturn(List.of(new TaskNode("pd", "step")));

        assertThat(locator.all()).isEmpty();
    }

    private void node(String pd, String key, String type, String bu, String role) {
        bpmn.put(pd + "|" + key + "|assigneeType", type);
        bpmn.put(pd + "|" + key + "|businessUnitId", bu);
        bpmn.put(pd + "|" + key + "|roleIds", role);
    }

    private Task tasksOfNode(String pd, String key, String id) {
        Task task = mock(Task.class);
        lenient().when(task.getId()).thenReturn(id);
        TaskQuery query = nodeQuery(pd, key);
        when(query.list()).thenReturn(List.of(task));
        return task;
    }

    private final Map<String, TaskQuery> queriesByPd = new HashMap<>();

    private TaskQuery nodeQuery(String pd, String key) {
        TaskQuery root = queriesByPd.computeIfAbsent("root", k -> {
            TaskQuery q = mock(TaskQuery.class);
            lenient().when(taskService.createTaskQuery()).thenReturn(q);
            return q;
        });
        TaskQuery byPd = mock(TaskQuery.class);
        when(root.processDefinitionId(pd)).thenReturn(byPd);
        when(byPd.taskDefinitionKey(key)).thenReturn(byPd);
        return byPd;
    }

    private static void stubTask(Task task, String id, String pd, String key) {
        lenient().when(task.getId()).thenReturn(id);
        lenient().when(task.getProcessDefinitionId()).thenReturn(pd);
        lenient().when(task.getTaskDefinitionKey()).thenReturn(key);
    }
}
