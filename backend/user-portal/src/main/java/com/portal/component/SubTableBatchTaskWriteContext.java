package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.exception.PortalException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Server-owned scope for enhanced task writes, including Owner updates with no batch/table marker. */
final class SubTableBatchTaskWriteContext {
    private SubTableBatchTaskWriteContext() { }

    record Request(String taskId, String processId, String stageId,
                   Map<String, Object> formData, Map<String, Object> baseline) { }

    static Map<String, Object> resolve(Request request, WorkflowEngineClient engine) {
        return resolve(request,engine,false);
    }

    static Map<String, Object> resolve(Request request, WorkflowEngineClient engine,boolean protectedAssignment) {
        Map<String, Object> submitted = request.formData();
        if (submitted==null || !protectedAssignment && (request.baseline() == null
                || !request.baseline().containsKey(SubTableWriteIsolation.BATCH_RUNTIME_FIELD))) return submitted;
        Map<String, Object> detail = engine.requireTaskWriteContext(request.taskId());
        if (detail == null || !(detail.get("writeContext") instanceof Map<?, ?> context)
                || !request.taskId().equals(context.get("taskId")) || !request.processId().equals(context.get("processInstanceId"))
                || !request.stageId().equals(context.get("taskDefinitionKey"))
                || !(context.get("processDefinitionId") instanceof String definition) || definition.isBlank()
                || !(context.get("multiInstance") instanceof Boolean mi)) throw invalid();
        Map<String, Object> result = new LinkedHashMap<>(submitted);
        result.remove("_currentItem");
        result.remove("currentItem");
        if (mi) {
            if (!(context.get("currentItem") instanceof Map<?, ?> item) || !(item.get("rowKey") instanceof Map<?, ?> key)
                    || key.isEmpty() || key.entrySet().stream().anyMatch(entry -> !(entry.getKey() instanceof String name)
                    || name.isBlank() || !(entry.getValue() instanceof String || entry.getValue() instanceof Number || entry.getValue() instanceof Boolean)
                    || String.valueOf(entry.getValue()).isBlank())) throw invalid();
            result.put("_currentItem", Map.of("rowKey", new LinkedHashMap<>(key)));
        }
        return result;
    }

    private static PortalException invalid() {
        return new PortalException("409", "Active task write scope is missing or does not match the current task");
    }

    static void applyCurrentItem(Map<String, Object> authority, Map<String, Object> target, boolean enhanced) {
        Map<String, Object> item = OwnerFieldComponent.taskScopedCurrentItem(authority);
        if (enhanced) OwnerFieldComponent.stripProcessWideCurrentItem(target);
        if (item != null) target.put("_currentItem", item);
    }
}
