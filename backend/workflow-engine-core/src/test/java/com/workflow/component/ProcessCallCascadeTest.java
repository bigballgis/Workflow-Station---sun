package com.workflow.component;

import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ExecutionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Parent and child Function Unit processes are bound together, which makes the cascade
 * inherently bidirectional — and therefore inherently prone to looping. These tests pin
 * both the intended propagation and the loop break.
 *
 * <p>The Flowable query builders are hand-stubbed rather than deep-mocked: their fluent
 * methods return the query itself, and letting Mockito answer those dynamically made the
 * stubs nest and fail.
 */
class ProcessCallCascadeTest {

    private RuntimeService runtimeService;
    private ProcessCallCascade cascade;

    /** parent instance id -> children started by it. */
    private final Map<String, List<ProcessInstance>> childrenByParent = new HashMap<>();
    /** instance id -> super execution id (null when user-started). */
    private final Map<String, String> superExecutionByInstance = new HashMap<>();
    /** execution id -> {owning process instance id, activity id}. */
    private final Map<String, String[]> executions = new HashMap<>();

    @BeforeEach
    void setUp() {
        runtimeService = mock(RuntimeService.class);
        cascade = new ProcessCallCascade();
        ReflectionTestUtils.setField(cascade, "runtimeService", runtimeService);

        when(runtimeService.createProcessInstanceQuery()).thenAnswer(inv -> newProcessInstanceQuery());
        when(runtimeService.createExecutionQuery()).thenAnswer(inv -> newExecutionQuery());
    }

    /** A fresh query whose terminal operations read from the maps above. */
    private ProcessInstanceQuery newProcessInstanceQuery() {
        ProcessInstanceQuery query = mock(ProcessInstanceQuery.class);
        // Each fluent call records what was asked for and returns the same query object.
        String[] askedSuperParent = new String[1];
        String[] askedInstanceId = new String[1];

        when(query.superProcessInstanceId(anyString())).thenAnswer(inv -> {
            askedSuperParent[0] = inv.getArgument(0);
            return query;
        });
        when(query.processInstanceId(anyString())).thenAnswer(inv -> {
            askedInstanceId[0] = inv.getArgument(0);
            return query;
        });
        when(query.list()).thenAnswer(inv ->
                childrenByParent.getOrDefault(askedSuperParent[0], List.of()));
        when(query.singleResult()).thenAnswer(inv -> {
            String id = askedInstanceId[0];
            if (id == null) {
                return null;
            }
            return instance(id, superExecutionByInstance.get(id));
        });
        return query;
    }

    private ExecutionQuery newExecutionQuery() {
        ExecutionQuery query = mock(ExecutionQuery.class);
        String[] askedExecutionId = new String[1];

        when(query.executionId(anyString())).thenAnswer(inv -> {
            askedExecutionId[0] = inv.getArgument(0);
            return query;
        });
        when(query.singleResult()).thenAnswer(inv -> {
            String[] data = executions.get(askedExecutionId[0]);
            if (data == null) {
                return null;
            }
            Execution exec = mock(Execution.class);
            when(exec.getProcessInstanceId()).thenReturn(data[0]);
            when(exec.getActivityId()).thenReturn(data[1]);
            return exec;
        });
        return query;
    }

    private static ProcessInstance instance(String id, String superExecutionId) {
        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getId()).thenReturn(id);
        when(pi.getSuperExecutionId()).thenReturn(superExecutionId);
        return pi;
    }

    /** Wires "child was started by parent's call activity". */
    private void declareChild(String parentId, String childId) {
        String executionId = "exec-" + childId;
        childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>())
                .add(instance(childId, executionId));
        superExecutionByInstance.put(childId, executionId);
        executions.put(executionId, new String[]{parentId, "Call_" + childId});
    }

    @Test
    void terminatesChildrenWhenParentIsTerminated() {
        declareChild("parent-1", "child-1");
        declareChild("parent-1", "child-2");

        int terminated = cascade.cascadeToChildren("parent-1", "user withdrew");

        assertThat(terminated).isEqualTo(2);
        verify(runtimeService).deleteProcessInstance(eq("child-1"), anyString());
        verify(runtimeService).deleteProcessInstance(eq("child-2"), anyString());
    }

    @Test
    void terminatesGrandchildrenToo() {
        declareChild("parent-1", "child-1");
        declareChild("child-1", "grandchild-1");

        int terminated = cascade.cascadeToChildren("parent-1", "user withdrew");

        assertThat(terminated).isEqualTo(2);
        verify(runtimeService).deleteProcessInstance(eq("grandchild-1"), anyString());
        verify(runtimeService).deleteProcessInstance(eq("child-1"), anyString());
    }

    @Test
    void terminatesParentWhenChildEndsAbnormally() {
        declareChild("parent-1", "child-1");

        String terminatedParent = cascade.cascadeToParent("child-1", "rejected");

        assertThat(terminatedParent).isEqualTo("parent-1");
        verify(runtimeService).deleteProcessInstance(eq("parent-1"), anyString());
    }

    @Test
    void terminatesSiblingsWhenOneChildEndsAbnormally() {
        declareChild("parent-1", "child-1");
        declareChild("parent-1", "sibling-1");

        cascade.cascadeToParent("child-1", "rejected");

        // The sibling must not outlive the parent that started it...
        verify(runtimeService).deleteProcessInstance(eq("sibling-1"), anyString());
        // ...while the triggering child is already being ended by its own caller.
        verify(runtimeService, never()).deleteProcessInstance(eq("child-1"), anyString());
    }

    /**
     * Without this guard, child -> parent -> child would recurse forever.
     *
     * <p>Only the upward direction is guarded: see
     * {@link #stillDescendsWhenAlreadyInsideACascade()} for why going down must not be.
     */
    @Test
    void doesNotCascadeUpwardsAgainWhenAlreadyInsideACascade() {
        declareChild("parent-1", "child-1");
        String alreadyCascading = ProcessCallCascade.CASCADE_DELETE_REASON_PREFIX + "origin (rejected)";

        String parent = cascade.cascadeToParent("child-1", alreadyCascading);

        assertThat(parent).isNull();
        verify(runtimeService, never()).deleteProcessInstance(anyString(), anyString());
    }

    /**
     * Descending is safe by construction and must keep going, otherwise a cascade that has
     * already started would stop at the first child and leave grandchildren running.
     */
    @Test
    void stillDescendsWhenAlreadyInsideACascade() {
        declareChild("parent-1", "child-1");
        String alreadyCascading = ProcessCallCascade.CASCADE_DELETE_REASON_PREFIX + "origin (rejected)";

        int terminated = cascade.cascadeToChildren("parent-1", alreadyCascading);

        assertThat(terminated).isEqualTo(1);
        verify(runtimeService).deleteProcessInstance(eq("child-1"), anyString());
    }

    @Test
    void cascadeReasonIsTaggedSoTheOtherDirectionStops() {
        declareChild("parent-1", "child-1");

        cascade.cascadeToChildren("parent-1", "user withdrew");

        verify(runtimeService).deleteProcessInstance(eq("child-1"),
                startsWith(ProcessCallCascade.CASCADE_DELETE_REASON_PREFIX));
    }

    @Test
    void doesNothingForInstanceWithoutChildren() {
        assertThat(cascade.cascadeToChildren("lonely-instance", "withdrawn")).isZero();
        verify(runtimeService, never()).deleteProcessInstance(anyString(), anyString());
    }

    @Test
    void returnsNullParentForUserStartedInstance() {
        superExecutionByInstance.put("standalone-1", null);

        assertThat(cascade.cascadeToParent("standalone-1", "withdrawn")).isNull();
        verify(runtimeService, never()).deleteProcessInstance(anyString(), anyString());
    }

    @Test
    void resolvesParentAndCallActivityIdFromEngine() {
        declareChild("parent-1", "child-1");

        assertThat(cascade.findParentProcessInstanceId("child-1")).isEqualTo("parent-1");
        assertThat(cascade.findCallActivityId("child-1")).isEqualTo("Call_child-1");
    }

    @Test
    void toleratesBlankIds() {
        assertThat(cascade.cascadeToChildren(null, "x")).isZero();
        assertThat(cascade.cascadeToChildren("  ", "x")).isZero();
        assertThat(cascade.cascadeToParent(null, "x")).isNull();
        assertThat(cascade.cascadeToParent("  ", "x")).isNull();
    }
}
