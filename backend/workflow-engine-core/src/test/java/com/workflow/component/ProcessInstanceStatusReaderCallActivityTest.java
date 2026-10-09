package com.workflow.component;

import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Status of a process that is waiting on a Function Unit it called.
 *
 * <p>Such a process has no task of its own, so the status used to report no next task at all and
 * the calling request showed no current step or assignee for as long as the call ran.
 */
class ProcessInstanceStatusReaderCallActivityTest {

    /** Running instances: id → id of the instance that called it (null for top level). */
    private final Map<String, String> parentOf = new HashMap<>();
    /** Open tasks by process instance id. */
    private final Map<String, List<Task>> tasksOf = new HashMap<>();
    private final Map<String, String> finishedParentOf = new HashMap<>();
    private ProcessInstanceStatusReader reader;

    @BeforeEach
    void setUp() {
        RuntimeService runtimeService = mock(RuntimeService.class);
        TaskService taskService = mock(TaskService.class);
        HistoryService historyService = mock(HistoryService.class);

        when(runtimeService.createProcessInstanceQuery()).thenAnswer(inv -> instanceQuery());
        when(taskService.createTaskQuery()).thenAnswer(inv -> {
            TaskQuery query = mock(TaskQuery.class);
            String[] asked = new String[1];
            when(query.processInstanceId(anyString())).thenAnswer(a -> {
                asked[0] = a.getArgument(0);
                return query;
            });
            when(query.list()).thenAnswer(a -> tasksOf.getOrDefault(asked[0], List.of()));
            return query;
        });
        when(taskService.getIdentityLinksForTask(anyString())).thenReturn(List.of());
        when(historyService.createHistoricProcessInstanceQuery()).thenAnswer(inv -> {
            HistoricProcessInstanceQuery query = mock(HistoricProcessInstanceQuery.class);
            String[] asked = new String[1];
            when(query.processInstanceId(anyString())).thenAnswer(a -> {
                asked[0] = a.getArgument(0);
                return query;
            });
            when(query.singleResult()).thenAnswer(a -> {
                if (!finishedParentOf.containsKey(asked[0])) return null;
                HistoricProcessInstance hpi = mock(HistoricProcessInstance.class);
                when(hpi.getSuperProcessInstanceId()).thenReturn(finishedParentOf.get(asked[0]));
                return hpi;
            });
            return query;
        });

        // The finished branch also looks up the last activity; none is needed here.
        when(historyService.createHistoricActivityInstanceQuery()).thenAnswer(inv ->
                mock(org.flowable.engine.history.HistoricActivityInstanceQuery.class,
                        a -> a.getMethod().getReturnType().isInstance(a.getMock()) ? a.getMock()
                                : List.class.equals(a.getMethod().getReturnType()) ? List.of() : null));

        reader = new ProcessInstanceStatusReader();
        ReflectionTestUtils.setField(reader, "runtimeService", runtimeService);
        ReflectionTestUtils.setField(reader, "taskService", taskService);
        ReflectionTestUtils.setField(reader, "historyService", historyService);
    }

    private ProcessInstanceQuery instanceQuery() {
        ProcessInstanceQuery query = mock(ProcessInstanceQuery.class);
        String[] byId = new String[1];
        String[] bySuper = new String[1];
        String[] bySub = new String[1];
        when(query.processInstanceId(anyString())).thenAnswer(a -> { byId[0] = a.getArgument(0); return query; });
        when(query.superProcessInstanceId(anyString())).thenAnswer(a -> { bySuper[0] = a.getArgument(0); return query; });
        when(query.subProcessInstanceId(anyString())).thenAnswer(a -> { bySub[0] = a.getArgument(0); return query; });
        when(query.singleResult()).thenAnswer(a -> {
            if (byId[0] != null) return parentOf.containsKey(byId[0]) ? instance(byId[0]) : null;
            if (bySub[0] != null) {
                String parent = parentOf.get(bySub[0]);
                return parent != null ? instance(parent) : null;
            }
            return null;
        });
        when(query.list()).thenAnswer(a -> {
            List<ProcessInstance> out = new ArrayList<>();
            parentOf.forEach((id, parent) -> {
                if (bySuper[0] != null && bySuper[0].equals(parent)) out.add(instance(id));
            });
            return out;
        });
        return query;
    }

    private ProcessInstance instance(String id) {
        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getId()).thenReturn(id);
        when(pi.getSuperExecutionId()).thenReturn(parentOf.get(id) != null ? "exec-of-" + parentOf.get(id) : null);
        return pi;
    }

    private void task(String processInstanceId, String name, String assignee) {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-" + name);
        when(task.getName()).thenReturn(name);
        when(task.getAssignee()).thenReturn(assignee);
        when(task.getProcessInstanceId()).thenReturn(processInstanceId);
        tasksOf.computeIfAbsent(processInstanceId, k -> new ArrayList<>()).add(task);
    }

    @Test
    void reportsTheCalledUnitsTaskWhileTheCallerWaits() {
        parentOf.put("purchase", null);
        parentOf.put("vendor-check", "purchase");
        task("vendor-check", "Review Vendor", "user-dev");

        Map<String, Object> status = reader.getProcessInstanceStatus("purchase");

        assertThat(status.get("nextTaskName")).isEqualTo("Review Vendor");
        assertThat(status.get("nextAssignee")).isEqualTo("user-dev");
        assertThat(status.get("nextTaskProcessInstanceId")).isEqualTo("vendor-check");
        assertThat(status).doesNotContainKey("superProcessInstanceId");
    }

    @Test
    void followsNestedCalls() {
        parentOf.put("a", null);
        parentOf.put("b", "a");
        parentOf.put("c", "b");
        task("c", "Deep Step", "u1");

        assertThat(reader.getProcessInstanceStatus("a").get("nextTaskProcessInstanceId")).isEqualTo("c");
    }

    @Test
    void prefersTheProcessesOwnTask() {
        parentOf.put("purchase", null);
        parentOf.put("vendor-check", "purchase");
        task("purchase", "Approve Purchase", "boss");
        task("vendor-check", "Review Vendor", "user-dev");

        assertThat(reader.getProcessInstanceStatus("purchase").get("nextTaskName")).isEqualTo("Approve Purchase");
    }

    @Test
    void namesTheCallerOfARunningCalledUnit() {
        parentOf.put("purchase", null);
        parentOf.put("vendor-check", "purchase");
        task("vendor-check", "Review Vendor", "user-dev");

        assertThat(reader.getProcessInstanceStatus("vendor-check").get("superProcessInstanceId"))
                .isEqualTo("purchase");
    }

    /** Once the called unit finishes, the caller is what moves on — it has to be refreshed. */
    @Test
    void namesTheCallerOfAFinishedCalledUnit() {
        finishedParentOf.put("vendor-check", "purchase");

        Map<String, Object> status = reader.getProcessInstanceStatus("vendor-check");

        assertThat(status.get("completed")).isEqualTo(true);
        assertThat(status.get("superProcessInstanceId")).isEqualTo("purchase");
    }
}
