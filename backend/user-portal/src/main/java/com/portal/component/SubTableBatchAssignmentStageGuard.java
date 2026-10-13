package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.SubTableBatchChange;
import com.portal.exception.PortalException;
import com.portal.util.WorkflowEnginePayloadHelper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Rejects initial batch-assignment configuration after its frozen BPMN target
 * task has started.  This deliberately asks workflow-engine for the actual
 * process state: a missing row task id or a client supplied status is not
 * evidence that a sub-task has not yet been generated.
 */
final class SubTableBatchAssignmentStageGuard {

    private SubTableBatchAssignmentStageGuard() {
    }

    static void validate(String activeTaskDefinitionKey, String processInstanceId,
                         Map<String, Object> instanceVariables, Map<String, Object> formDefinition,
                         List<SubTableBatchChange> changes, ObjectMapper objectMapper,
                         WorkflowEngineClient workflowEngineClient) {
        if (changes == null || changes.stream().noneMatch(SubTableBatchAssignmentStageGuard::isAssign)) return;
        if (workflowEngineClient == null) {
            throw forbidden("Batch assignment stage cannot be verified");
        }

        Object sourceId = formDefinition == null ? null : formDefinition.get("sourceId");
        Map<String, Object> controls = SubTableBatchRuntimeSnapshot.projectForForm(
                instanceVariables, sourceId, objectMapper);
        List<Map<String, Object>> tasks = null;
        boolean taskStateLoaded = false;
        for (SubTableBatchChange change : changes) {
            if (!isAssign(change)) continue;
            String targetTaskDefinitionKey = targetTaskDefinitionKey(change, controls);
            if (targetTaskDefinitionKey.equals(activeTaskDefinitionKey)) {
                throw forbidden("Batch assignment target task has already started");
            }
            if (!taskStateLoaded) {
                Optional<Map<String, Object>> payload = workflowEngineClient.getProcessInstanceTasks(processInstanceId);
                tasks = payload.map(WorkflowEnginePayloadHelper::taskListFromPayload).orElse(null);
                if (tasks == null) {
                    throw forbidden("Batch assignment stage cannot be verified");
                }
                taskStateLoaded = true;
            }
            boolean targetStarted = tasks.stream().filter(java.util.Objects::nonNull)
                    .anyMatch(task -> targetTaskDefinitionKey.equals(text(task.get("taskDefinitionKey"))));
            if (targetStarted) {
                throw forbidden("Batch assignment target task has already started");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static String targetTaskDefinitionKey(SubTableBatchChange change, Map<String, Object> controls) {
        if (change == null || blank(change.getBatchControlId()) || blank(change.getBindingId())) {
            throw forbidden("Batch assignment is missing its frozen control identity");
        }
        Object rawControl = controls.get(change.getBatchControlId());
        if (!(rawControl instanceof Map<?, ?> raw)) {
            throw forbidden("Batch assignment control is not available for this task form");
        }
        Map<String, Object> control = (Map<String, Object>) raw;
        if (!change.getBindingId().trim().equals(text(control.get("bindingId")))) {
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
