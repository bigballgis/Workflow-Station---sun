package com.portal.component;

import com.portal.dto.TaskFormSnapshot;
import com.portal.util.SystemAuditFieldFiller;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Task Form 字段/快照纯函数工具协作类。
 * 无状态、单一职责：字段子集抽取、可编辑字段过滤、并发修改探测、快照与 Map 互转、字段权限解析。
 * 行为与拆分前 {@link TaskFormComponent} 中的对应方法逐字一致。
 */
@Component
public class TaskFormFieldMapper {

    /**
     * Filters read-only fields, keeping editable fields only.
     * When fieldPermissions is empty, accepts all non-audit fields (backward compatible).
     * Platform audit columns are never accepted from the client.
     * {@code __subTables__} passes through unfiltered here — callers needing sub-table
     * field-level enforcement additionally apply {@link ChangeHistorySubmissionFilter}'s
     * binding-aware, alias-safe sub-table filtering (see {@code TaskFormComponent#submitTaskForm}).
     *
     * <p><b>An absent key means EDITABLE, not READONLY.</b> The Form Designer persists
     * {@code field_permissions} sparsely: {@code applyTaskFieldPermissionsFromTableFields} writes a
     * key only when {@code taskFieldPermissionForField} classifies the field as READONLY (audit
     * column, read-only FK, auto PK, computed), and both the designer
     * ({@code useFormSave.getFieldPermission}: {@code perms?.[key] || 'EDITABLE'}) and the portal
     * renderer treat a missing key as editable. Requiring an exact {@code "EDITABLE"} match here
     * inverted that contract and silently dropped every ordinary field.
     *
     * <p>The bug hid behind the empty-map short-circuit above: a form with NO read-only field saves
     * a literal {@code {}} and passes everything, so only forms that configure at least one
     * read-only field were affected — those have a non-empty map in which no key is ever the string
     * {@code "EDITABLE"}, so every scalar was discarded. Measured on FU {@code atm-20260623-gaevus}
     * form 321 ("Transaction Request Form - Transaction Assignment"): 21 permission entries, all
     * READONLY, zero EDITABLE — {@code case_status} was absent and therefore never persisted, while
     * the request still returned 200 and the UI reported success.
     *
     * <p>Explicit READONLY is still rejected, and audit keys are stripped unconditionally below, so
     * this widens what is accepted only to fields the designer itself considers editable.
     */
    public Map<String, Object> filterEditableFields(Map<String, Object> formData,
                                                    Map<String, String> fieldPermissions) {
        if (formData == null || formData.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, Object> accepted = new HashMap<>();
        if (fieldPermissions == null || fieldPermissions.isEmpty()) {
            accepted.putAll(formData);
        } else {
            // Built with an explicit loop rather than Collectors.toMap: that collector routes
            // through HashMap.merge, which throws NullPointerException on a null VALUE. Clearing
            // an optional field in the UI submits exactly that (e.g. Case Status emptied ->
            // {"case_status": null}), and such a field must be persisted as null, not rejected.
            // The empty-permissions branch above never had this problem (HashMap's copy
            // constructor accepts nulls), so the two branches used to disagree on null handling.
            for (Map.Entry<String, Object> entry : formData.entrySet()) {
                String key = entry.getKey();
                if ("__subTables__".equals(key) || !"READONLY".equals(fieldPermissions.get(key))) {
                    accepted.put(key, entry.getValue());
                }
            }
        }
        SystemAuditFieldFiller.stripClientAuditKeys(accepted);
        return accepted;
    }

    /**
     * Extracts a field subset from full process variables.
     */
    public Map<String, Object> extractFieldSubset(Map<String, Object> allVariables,
                                                  Set<String> fieldNames) {
        if (fieldNames == null || fieldNames.isEmpty()) {
            return new HashMap<>(allVariables);
        }

        Map<String, Object> subset = new HashMap<>();
        for (String fieldName : fieldNames) {
            if (allVariables.containsKey(fieldName)) {
                subset.put(fieldName, allVariables.get(fieldName));
            }
            // Owner / Lookup store a companion "<field>__display". Permissions only list
            // the main field, so the label must be copied or the widget falls back to the raw id.
            if (fieldName != null && !fieldName.endsWith("__display")) {
                String displayKey = fieldName + "__display";
                if (allVariables.containsKey(displayKey)) {
                    subset.put(displayKey, allVariables.get(displayKey));
                }
            }
        }
        return subset;
    }

    /**
     * Counts fields that differ between snapshot and live values.
     */
    public int countSnapshotDiffs(Map<String, Object> snapshotValues, Map<String, Object> liveValues) {
        if (snapshotValues == null || liveValues == null) {
            return 0;
        }

        int diffCount = 0;
        for (Map.Entry<String, Object> entry : snapshotValues.entrySet()) {
            Object snapshotVal = entry.getValue();
            Object liveVal = liveValues.get(entry.getKey());
            if (!Objects.equals(snapshotVal, liveVal)) {
                diffCount++;
            }
        }
        return diffCount;
    }

    /**
     * Converts snapshot DTO to Map for storage in process variables.
     */
    public Map<String, Object> snapshotToMap(TaskFormSnapshot snapshot) {
        Map<String, Object> map = new HashMap<>();
        map.put("taskId", snapshot.getTaskId());
        map.put("taskDefinitionKey", snapshot.getTaskDefinitionKey());
        map.put("assignee", snapshot.getAssignee());
        map.put("completedAt", snapshot.getCompletedAt() != null
                ? snapshot.getCompletedAt().toString() : null);
        map.put("fieldValues", snapshot.getFieldValues());
        return map;
    }

    /**
     * Restores snapshot DTO from Map (read from process variables).
     */
    @SuppressWarnings("unchecked")
    public TaskFormSnapshot mapToSnapshot(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        return TaskFormSnapshot.builder()
                .taskId((String) map.get("taskId"))
                .taskDefinitionKey((String) map.get("taskDefinitionKey"))
                .assignee((String) map.get("assignee"))
                .completedAt(map.get("completedAt") != null
                        ? Instant.parse((String) map.get("completedAt")) : null)
                .fieldValues(map.get("fieldValues") instanceof Map
                        ? (Map<String, Object>) map.get("fieldValues") : Collections.emptyMap())
                .build();
    }

    /**
     * Detects concurrent edits by comparing baseline values to current process variables.
     * When current value != baseline, another user changed the field during editing.
     *
     * @param baselineValues field snapshot from client load (may be null)
     * @param currentVariables current process variables
     * @param submittedFieldNames field names in this submit
     * @return field names modified concurrently
     */
    public Set<String> detectConcurrentModifications(Map<String, Object> baselineValues,
                                                     Map<String, Object> currentVariables,
                                                     Set<String> submittedFieldNames) {
        Set<String> concurrentFields = new java.util.HashSet<>();

        if (baselineValues == null || baselineValues.isEmpty()) {
            return concurrentFields;
        }

        for (String fieldName : submittedFieldNames) {
            if (baselineValues.containsKey(fieldName)) {
                Object baselineVal = baselineValues.get(fieldName);
                Object currentVal = currentVariables.get(fieldName);
                if (!Objects.equals(baselineVal, currentVal)) {
                    concurrentFields.add(fieldName);
                }
            }
        }

        return concurrentFields;
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> extractFieldPermissions(Map<String, Object> formDefinition) {
        Object fp = formDefinition.get("fieldPermissions");
        if (fp instanceof Map) {
            Map<String, Object> raw = (Map<String, Object>) fp;
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                result.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "READONLY");
            }
            return result;
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> extractMapField(Map<String, Object> source, String fieldName) {
        Object value = source.get(fieldName);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    public TaskFormSnapshot extractSnapshot(Map<String, Object> allVariables, String snapshotKey) {
        Object snapshotObj = allVariables.get(snapshotKey);
        if (snapshotObj instanceof Map) {
            return mapToSnapshot((Map<String, Object>) snapshotObj);
        }
        return null;
    }
}
