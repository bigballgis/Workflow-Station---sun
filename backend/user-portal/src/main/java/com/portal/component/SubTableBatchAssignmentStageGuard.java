package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.SubTableBatchChange;
import com.portal.exception.PortalException;
import com.portal.util.WorkflowEnginePayloadHelper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Rejects initial batch-assignment configuration after its frozen BPMN target
 * task has started.  This deliberately asks workflow-engine for the actual
 * process state: a missing row task id or a client supplied status is not
 * evidence that a sub-task has not yet been generated.
 */
final class SubTableBatchAssignmentStageGuard {

    private SubTableBatchAssignmentStageGuard() {
    }

    record Submission(String stageId, String processId, Map<String, Object> variables,
                      Map<String, Object> form, List<SubTableBatchChange> changes, Map<String, Object> canonical) { }

    static void validate(String activeTaskDefinitionKey, String processInstanceId,
                         Map<String, Object> instanceVariables, Map<String, Object> formDefinition,
                         List<SubTableBatchChange> changes, ObjectMapper objectMapper,
                         WorkflowEngineClient workflowEngineClient) {
        validate(new Submission(activeTaskDefinitionKey, processInstanceId, instanceVariables, formDefinition, changes, null),
                objectMapper, workflowEngineClient);
    }

    static void validate(Submission submission, ObjectMapper objectMapper, WorkflowEngineClient workflowEngineClient) {
        var changes = submission.changes();
        boolean declared = changes != null && changes.stream().anyMatch(SubTableBatchAssignmentStageGuard::isAssign);
        boolean enhanced = submission.variables() != null && submission.variables().containsKey(SubTableWriteIsolation.BATCH_RUNTIME_FIELD);
        if (!declared && (!enhanced || submission.canonical() == null || !submission.canonical().containsKey("__subTables__"))) return;

        Object sourceId = submission.form() == null ? null : submission.form().get("sourceId");
        Map<String, Object> controls = SubTableBatchRuntimeSnapshot.projectForForm(
                submission.variables(), sourceId, objectMapper);
        Set<String> targets = new LinkedHashSet<>();
        if (changes != null) for (SubTableBatchChange change : changes) {
            if (isAssign(change)) targets.add(targetTaskDefinitionKey(change.getBatchControlId(), change.getBindingId(), controls));
        }
        for (var control : SubTableBatchAssignmentDifferences.changedControls(submission.variables(), controls, submission.canonical())) {
            targets.add(targetTaskDefinitionKey(control.id(), control.bindingId(), controls));
        }
        if (targets.isEmpty()) return;
        if (targets.contains(submission.stageId()) || !startedTargets(submission.stageId(), submission.processId(), targets,
                workflowEngineClient, false).isEmpty()) throw forbidden("Batch assignment target task has already started");
    }

    /** UI eligibility is a projection only; persistence always repeats the authoritative stage check. */
    static Map<String, Object> project(Map<String, Object> controls, String stageId, String processId,
                                       WorkflowEngineClient engine) {
        Map<String, String> controlTargets = new LinkedHashMap<>();
        for (var entry : controls.entrySet()) {
            Map<String, Object> control = map(entry.getValue());
            if (Boolean.TRUE.equals(map(control.get("capabilities")).get("allowBatchAssign"))
                    && !Boolean.FALSE.equals(map(control.get("originalCrud")).get("allowEdit"))) {
                controlTargets.put(entry.getKey(), targetTaskDefinitionKey(entry.getKey(), text(control.get("bindingId")), controls));
            }
        }
        if (controlTargets.isEmpty()) return controls;
        Set<String> started = startedTargets(stageId, processId, new LinkedHashSet<>(controlTargets.values()), engine, true);
        Map<String, Object> projected = new LinkedHashMap<>(controls);
        controlTargets.forEach((id, target) -> {
            if (!started.contains(target)) return;
            Map<String, Object> control = new LinkedHashMap<>(map(controls.get(id)));
            Map<String, Object> flags = new LinkedHashMap<>(map(control.get("capabilities")));
            flags.put("allowBatchAssign", false);
            control.put("capabilities", flags);
            projected.put(id, control);
        });
        return projected;
    }

    private static Set<String> startedTargets(String stageId, String processId, Set<String> targets,
                                               WorkflowEngineClient engine, boolean readProjection) {
        Set<String> started = new LinkedHashSet<>();
        if (targets.contains(stageId)) started.add(stageId);
        if (started.containsAll(targets)) return started;
        if (engine == null || blank(stageId) || blank(processId)) throw forbidden("Batch assignment stage cannot be verified");
        Optional<Map<String, Object>> payload = engine.getProcessInstanceTasks(processId);
        List<Map<String, Object>> tasks = payload.map(WorkflowEnginePayloadHelper::taskListFromPayload).orElse(null);
        if (tasks == null) throw forbidden("Batch assignment stage cannot be verified");
        validateTaskIdentities(tasks, processId, "taskDefinitionKey");
        List<Map<String, Object>> history = engine.getTaskHistory(processId)
                .orElseThrow(() -> forbidden("Batch assignment history cannot be verified"));
        validateTaskIdentities(history, processId, "activityId");
        boolean hostRecorded = history.stream().anyMatch(task -> stageId.equals(task.get("activityId"))
                && task.containsKey("endTime") && task.get("endTime") == null
                && task.containsKey("deleteReason") && task.get("deleteReason") == null);
        if (!hostRecorded) {
            boolean endedHost = history.stream().anyMatch(task -> stageId.equals(task.get("activityId"))
                    && task.containsKey("endTime") && task.containsKey("deleteReason")
                    && (task.get("endTime") != null || task.get("deleteReason") != null));
            if (readProjection && endedHost) return targets;
            throw forbidden("Batch assignment host history cannot be verified");
        }
        for (String target : targets) {
            if (tasks.stream().anyMatch(task -> target.equals(task.get("taskDefinitionKey")))
                    || history.stream().anyMatch(task -> target.equals(task.get("activityId")))) started.add(target);
        }
        return started;
    }

    private static void validateTaskIdentities(List<?> tasks, String processInstanceId, String nodeField) {
        for (Object raw : tasks) {
            if (!(raw instanceof Map<?, ?> task) || blank(text(task.get(nodeField))) || !processInstanceId.equals(task.get("processInstanceId"))) {
                throw forbidden("Batch assignment task identity cannot be verified");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static String targetTaskDefinitionKey(String controlId, String bindingId, Map<String, Object> controls) {
        if (blank(controlId) || blank(bindingId)) {
            throw forbidden("Batch assignment is missing its frozen control identity");
        }
        Object rawControl = controls.get(controlId);
        if (!(rawControl instanceof Map<?, ?> raw)) {
            throw forbidden("Batch assignment control is not available for this task form");
        }
        Map<String, Object> control = (Map<String, Object>) raw;
        if (!bindingId.trim().equals(text(control.get("bindingId")))) {
            throw forbidden("Batch assignment control does not belong to the submitted binding");
        }
        Map<String, Object> capabilities = map(control.get("capabilities"));
        Map<String, Object> originalCrud = map(control.get("originalCrud"));
        if (!Boolean.TRUE.equals(capabilities.get("allowBatchAssign"))
                || Boolean.FALSE.equals(originalCrud.get("allowEdit"))) {
            throw forbidden("Batch assignment is not enabled for this control");
        }
        String target = text(map(control.get("assignmentConfig")).get("taskDefinitionKey"));
        if (blank(target)) {
            throw forbidden("Batch assignment has no frozen BPMN task target");
        }
        return target;
    }

    private static boolean isAssign(SubTableBatchChange change) {
        return change != null && "ASSIGN".equals(change.getOperation());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
    }

    private static String text(Object value) {
        return value instanceof String text ? text.trim() : "";
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static PortalException forbidden(String message) {
        return new PortalException("403", message);
    }
}
