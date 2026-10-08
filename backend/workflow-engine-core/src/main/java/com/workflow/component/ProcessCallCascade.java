package com.workflow.component;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Terminates the other half of a caller/callee pair when one half is terminated.
 *
 * <p>Parent and child Function Unit processes are bound together: whichever side ends
 * abnormally, the other follows. Flowable gives us only the happy path — a child that
 * <em>completes</em> lets the parent move on — so an abnormally terminated child would
 * otherwise leave the parent parked on its call activity forever.
 *
 * <h2>Two deliberate departures from {@link MultiInstanceCanceller}</h2>
 * <ol>
 *   <li><b>No activity-id string matching.</b> That class finds its targets with
 *       {@code activityId.contains("MultiInstance")}, which silently no-ops on any Function
 *       Unit that does not happen to name its activity that way. Here the relation is a real
 *       one held by the engine ({@code superProcessInstanceId}), so it is queried, not guessed.</li>
 *   <li><b>Failures are not swallowed.</b> That class catches everything to avoid disturbing
 *       the main cancellation. A parent left RUNNING after its child was terminated is not a
 *       cosmetic problem — it is a request nobody can finish or withdraw — so failures
 *       propagate to the caller.</li>
 * </ol>
 *
 * <h2>Re-entrancy</h2>
 * The binding is bidirectional, so a naive implementation loops: terminating the parent
 * cascades to the child, whose termination cascades back to the parent. Every cascade-initiated
 * deletion is therefore tagged with {@link #CASCADE_DELETE_REASON_PREFIX}, and a deletion
 * carrying that tag does not cascade <em>upwards</em> again.
 *
 * <p>The guard applies to the upward direction only. Descending is safe by construction — each
 * step moves to an instance the previous one called, and call cycles are rejected at design
 * time — and guarding it too would stop the walk at the first child, leaving grandchildren
 * running.
 */
@Slf4j
@Component
public class ProcessCallCascade {

    /**
     * Marks a deletion as already being part of a cascade. Flowable stores the delete reason on
     * the historic instance, so this both breaks the loop and leaves an audit trail explaining
     * why an instance the user never touched was terminated.
     */
    public static final String CASCADE_DELETE_REASON_PREFIX = "CASCADED_FROM_LINKED_PROCESS:";

    @Autowired
    private RuntimeService runtimeService;

    /**
     * Terminates every child process instance started by the given parent.
     *
     * <p>Call immediately before deleting the parent, so the children are gone by the time the
     * parent's own call activities are torn down.
     *
     * @param parentProcessInstanceId the parent being terminated
     * @param reason                  why the parent is ending, propagated to the children
     * @return how many child instances were terminated
     */
    public int cascadeToChildren(String parentProcessInstanceId, String reason) {
        if (parentProcessInstanceId == null || parentProcessInstanceId.isBlank()) {
            return 0;
        }

        // Note: no cascade-tag guard here. Walking *downwards* can never come back to where it
        // started — each step moves to a called instance, and the designer rejects call cycles —
        // so descending into an already-cascading subtree is exactly what must happen for
        // grandchildren to be terminated. Only the upward direction needs the loop break.
        List<ProcessInstance> children = runtimeService.createProcessInstanceQuery()
                .superProcessInstanceId(parentProcessInstanceId)
                .list();
        if (children.isEmpty()) {
            return 0;
        }

        String cascadeReason = cascadeReason(parentProcessInstanceId, reason);
        int terminated = 0;
        for (ProcessInstance child : children) {
            // Recurse first: a child may itself be a caller.
            terminated += cascadeToChildren(child.getId(), cascadeReason);
            runtimeService.deleteProcessInstance(child.getId(), cascadeReason);
            terminated++;
            log.info("Cascaded termination from parent {} to child process instance {}",
                    parentProcessInstanceId, child.getId());
        }
        return terminated;
    }

    /**
     * Terminates the parent that called the given child.
     *
     * <p>Used when a child ends abnormally (rejected, withdrawn, cancelled) and the parent must
     * not be left parked on its call activity.
     *
     * @param childProcessInstanceId the child that is ending
     * @param reason                 why the child is ending, propagated to the parent
     * @return the terminated parent's id, or {@code null} when this instance has no caller
     */
    public String cascadeToParent(String childProcessInstanceId, String reason) {
        if (childProcessInstanceId == null || childProcessInstanceId.isBlank()) {
            return null;
        }
        if (isCascadeReason(reason)) {
            return null;
        }

        String parentId = findParentProcessInstanceId(childProcessInstanceId);
        if (parentId == null) {
            return null;
        }

        String cascadeReason = cascadeReason(childProcessInstanceId, reason);
        // Siblings started by the same parent must go too, otherwise they would outlive it.
        cascadeToChildrenExcept(parentId, childProcessInstanceId, cascadeReason);
        runtimeService.deleteProcessInstance(parentId, cascadeReason);
        log.info("Cascaded termination from child {} to parent process instance {}",
                childProcessInstanceId, parentId);
        return parentId;
    }

    /**
     * The process instance that called this one, or {@code null} when it was started directly.
     *
     * <p>Flowable is the source of truth for this relation; the mirrored column on
     * {@code up_process_instance} exists only to save the portal a round trip.
     */
    public String findParentProcessInstanceId(String processInstanceId) {
        ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (instance == null) {
            return null;
        }
        String superExecutionId = instance.getSuperExecutionId();
        if (superExecutionId == null || superExecutionId.isBlank()) {
            return null;
        }
        Execution superExecution = runtimeService.createExecutionQuery()
                .executionId(superExecutionId)
                .singleResult();
        return superExecution != null ? superExecution.getProcessInstanceId() : null;
    }

    /**
     * The BPMN element id of the call activity that started this instance, or {@code null}.
     *
     * <p>This is what groups siblings when one call activity is multi-instance.
     */
    public String findCallActivityId(String processInstanceId) {
        ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (instance == null || instance.getSuperExecutionId() == null) {
            return null;
        }
        Execution superExecution = runtimeService.createExecutionQuery()
                .executionId(instance.getSuperExecutionId())
                .singleResult();
        return superExecution != null ? superExecution.getActivityId() : null;
    }

    private void cascadeToChildrenExcept(String parentProcessInstanceId, String excludedChildId,
                                         String cascadeReason) {
        List<ProcessInstance> siblings = new ArrayList<>(runtimeService.createProcessInstanceQuery()
                .superProcessInstanceId(parentProcessInstanceId)
                .list());
        for (ProcessInstance sibling : siblings) {
            if (sibling.getId().equals(excludedChildId)) {
                continue;
            }
            runtimeService.deleteProcessInstance(sibling.getId(), cascadeReason);
            log.info("Cascaded termination to sibling process instance {} of parent {}",
                    sibling.getId(), parentProcessInstanceId);
        }
    }

    private boolean isCascadeReason(String reason) {
        return reason != null && reason.startsWith(CASCADE_DELETE_REASON_PREFIX);
    }

    private String cascadeReason(String originProcessInstanceId, String reason) {
        String detail = (reason == null || reason.isBlank()) ? "terminated" : reason;
        return CASCADE_DELETE_REASON_PREFIX + originProcessInstanceId + " (" + detail + ")";
    }
}
