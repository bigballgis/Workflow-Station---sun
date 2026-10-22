package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.audit.SystemAuditFields;
import com.platform.common.dto.PkGenerationConfig;
import com.platform.common.fk.PrimaryKeyAllocationService;
import com.platform.common.functionunit.CatalogRuntimeRows;
import com.platform.common.functionunit.CatalogSnapshotReader;
import com.platform.common.functionunit.RuntimeVersionContext;
import com.portal.dto.TaskInfo;
import com.portal.exception.PortalException;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.service.UserDisplayNameResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Both the action authority and its target metadata come from the request's release. */
@Component
@RequiredArgsConstructor
public class ActionFormPopupSubmitComponent {
    private final ObjectMapper mapper;
    private final ActionDefinitionLookupComponent actions;
    private final ProcessInstanceRepository instances;
    private final CatalogSnapshotReader snapshots;
    private final CatalogRuntimeRows rows;
    private final PrimaryKeyAllocationService primaryKeys;
    private final UserDisplayNameResolver displayNames;

    @Transactional
    public ActionTableRowRef submit(TaskInfo task, String actionId, Map<String, Object> formData, String userId) {
        return submitForProcessInstance(task.getProcessInstanceId(), actionId, formData, userId, task);
    }

    @Transactional
    public ActionTableRowRef submitForProcessInstance(
            String processInstanceId, String actionId, Map<String, Object> formData, String userId) {
        return submitForProcessInstance(processInstanceId, actionId, formData, userId, null);
    }

    private ActionTableRowRef submitForProcessInstance(
            String processInstanceId, String actionId, Map<String, Object> formData, String userId, TaskInfo task) {
        RuntimeVersionContext.rejectClientMetadata(formData);
        var instance = instances.findById(processInstanceId)
                .orElseThrow(() -> new PortalException("404", "Process instance not found"));
        String catalogId = ProcessVersionContext.of(instance).catalogId();
        var action = actions.resolvePinned(catalogId, List.of(actionId)).get(0);
        // Process-start callers authorize the initiator before entering this shared write path.
        boolean allowed = task == null || (task.getActions() != null && task.getActions().stream().anyMatch(a ->
                a.getActionId().equals(actionId) || a.getActionId().equals(action.getSourceId())));
        if (!allowed || !"FORM_POPUP".equals(action.getActionType())) {
            throw new PortalException("403", "Action is not available on this task");
        }
        Map<String, Object> config = snapshots.parse(action.getConfigJson());
        Map<String, Object> form = snapshots.form(catalogId, String.valueOf(config.get("formId")));
        var bindings = CatalogSnapshotReader.objects(form.get("tableBindings")).stream()
                .filter(b -> "ACTION".equals(b.get("bindingType"))).toList();
        if (bindings.size() != 1) throw CatalogSnapshotReader.invalid(catalogId);
        var binding = bindings.get(0);
        String sourceId = String.valueOf(binding.get("tableId"));
        Map<String, Object> table = snapshots.table(catalogId, sourceId);
        List<Map<String, Object>> fields = CatalogSnapshotReader.objects(table.get("fields"));
        Map<String, Object> data = new LinkedHashMap<>();
        Map<?, ?> permissions = form.get("fieldPermissions") instanceof Map<?, ?> map ? map : Map.of();
        var computed = new java.util.ArrayList<ComputedFieldRowEvaluation.FieldMeta>();
        for (Map<String, Object> field : fields) {
            String name = String.valueOf(field.get("fieldName"));
            Object permission = permissions.get(binding.get("bindingId") + ":" + name);
            if (permission == null) permission = permissions.get(name);
            if (!SystemAuditFields.isAuditField(name) && !Boolean.TRUE.equals(field.get("isComputed"))
                    && (permission == null || "EDITABLE".equals(permission))
                    && formData != null && formData.containsKey(name)) {
                Object value = formData.get(name);
                if (value instanceof Map<?, ?> selected) {
                    if (!selected.containsKey(name)) throw new PortalException("400", "Lookup value does not contain the configured field");
                    value = selected.get(name);
                }
                data.put(name, value);
            }
            if (Boolean.TRUE.equals(field.get("isComputed"))) {
                computed.add(ComputedFieldRowEvaluation.toMeta(name,
                        mapper.convertValue(field.get("computedField"), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { })));
            }
            if (Boolean.TRUE.equals(field.get("isPrimaryKey")) && data.get(name) == null) {
                PkGenerationConfig pk = field.get("pkGenerationJson") == null
                        ? PkGenerationConfig.builder().strategy("manual").build()
                        : mapper.convertValue(field.get("pkGenerationJson"), PkGenerationConfig.class);
                // Counters have no FK to DW definitions and survive rollback. Explicitly retain
                // this snapshot's namespace instead of auto-detecting a deleted DW table as RT.
                List<String> values = primaryKeys.allocate(Long.valueOf(sourceId), name, pk, 1, "", "dw_pk_sequences");
                if (values.isEmpty()) throw new PortalException("400", "Action primary key is required");
                data.put(name, values.get(0));
            }
        }
        Object fk = binding.get("foreignKeyField");
        if (fk instanceof String name) {
            Object requestId = instance.getVariables().get(RequestIdEnricher.REQUEST_ID_FIELD);
            if (requestId == null) throw new PortalException("409", "Action request identity is missing");
            data.put(name, requestId);
        }
        String actor = displayNames.resolve(userId);
        for (Map<String, Object> field : fields) {
            String name = String.valueOf(field.get("fieldName"));
            if (SystemAuditFields.CREATED_BY.equals(name) || SystemAuditFields.UPDATED_BY.equals(name)) data.put(name, actor);
        }
        ComputedFieldRowEvaluation.applyFields(ComputedFieldRowEvaluation.orderByDependency(computed), data,
                com.platform.common.computedfield.ComputedFieldContext.ofRow(data,
                        Map.of(String.valueOf(snapshots.mainTable(catalogId).get("tableName")), instance.getVariables())));
        return new ActionTableRowRef(rows.insert(catalogId, sourceId, instance.getId(), data, actor));
    }
}
