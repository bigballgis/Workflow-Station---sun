package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.ChangeHistoryContext;
import com.portal.dto.ChangeHistoryRecord;
import com.portal.dto.SubTableChange;
import com.portal.entity.ChangeHistory;
import com.portal.entity.ProcessInstance;
import com.portal.enums.ChangeType;
import com.portal.repository.ChangeHistoryRepository;
import com.portal.repository.ProcessInstanceRepository;
import com.platform.security.entity.User;
import com.platform.security.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Change History component
 * Records field-level change history with a "best-effort" strategy; failures do
 * not block the main flow.
 */
@Slf4j
@Component
public class ChangeHistoryComponent {

    private final ChangeHistoryRepository changeHistoryRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final UserRepository userRepository;
    private final WorkflowEngineClient workflowEngineClient;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    /**
     * Isolated commits for history writes — failures roll back only this slice
     * (never outer txn).
     */
    private final TransactionTemplate requiresNewTx;

    public ChangeHistoryComponent(
            ChangeHistoryRepository changeHistoryRepository,
            ProcessInstanceRepository processInstanceRepository,
            UserRepository userRepository,
            WorkflowEngineClient workflowEngineClient,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.changeHistoryRepository = changeHistoryRepository;
        this.processInstanceRepository = processInstanceRepository;
        this.userRepository = userRepository;
        this.workflowEngineClient = workflowEngineClient;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx = tt;
    }

    /**
     * Internal/engine variable names that should never appear in user-visible
     * change history.
     */
    private static final Set<String> INTERNAL_FIELD_BLACKLIST = Set.of(
            "__subTables__", "subTableName", "foreignKey", "assigneeField",
            "mainRecordId", "activeBusinessUnitId", "activeRoleId",
            "requestItemsHasHighValue", "totalPrice", "maxItemPrice", "itemCount",
            "initiator", "participant_assigner_user_id",
            "id", "currentUserId");
    /**
     * Fallback row-id field names when the canonical {@code id} column is absent.
     */
    private static final String[] ROW_ID_FALLBACK_FIELDS = { "row_id", "rowId", "rowID", "id_idw", "_rowKey",
            "rowKey" };
    private static final Set<String> SUB_TABLE_ROW_METADATA_FIELDS = Set.of(
            "id", "row_id", "rowid", "rowkey", "id_idw", "_rowkey",
            "created_at", "created_by", "updated_at", "updated_by", "case_row_id",
            "task_current_node", "sub_task_current_node", "task_status", "sub_task_status");

    /**
     * Record field changes.
     * Compare oldValues and newValues, and create a ChangeHistory record for each
     * changed field.
     */
    public void recordFieldChanges(ChangeHistoryContext context,
            Map<String, Object> oldValues,
            Map<String, Object> newValues) {
        Instant now = Instant.now();
        List<ChangeHistory> records = new ArrayList<>();

        for (Map.Entry<String, Object> entry : newValues.entrySet()) {
            String fieldName = entry.getKey();
            if (isInternalField(fieldName)) {
                continue;
            }
            Object newValue = entry.getValue();
            Object oldValue = oldValues.get(fieldName);

            if (!Objects.equals(oldValue, newValue)) {
                ChangeHistory record = ChangeHistory.builder()
                        .processInstanceId(context.getProcessInstanceId())
                        .taskInstanceId(context.getTaskInstanceId())
                        .stageId(context.getStageId())
                        .userId(context.getUserId())
                        .timestamp(now)
                        .fieldName(fieldName)
                        .oldValue(toDisplayString(oldValue))
                        .newValue(toDisplayString(newValue))
                        .changeType(ChangeType.FIELD_UPDATE)
                        .build();
                records.add(record);
            }
        }

        if (records.isEmpty()) {
            return;
        }

        requiresNewTx.executeWithoutResult(status -> {
            try {
                changeHistoryRepository.saveAll(records);
                log.debug("Recorded {} field change(s) for process {}", records.size(), context.getProcessInstanceId());
            } catch (Exception e) {
                log.warn("Failed to record field changes for process {}: {}",
                        context.getProcessInstanceId(), e.getMessage());
                status.setRollbackOnly();
            }
        });
    }

    private static boolean isInternalField(String fieldName) {
        if (fieldName == null)
            return true;
        if (INTERNAL_FIELD_BLACKLIST.contains(fieldName))
            return true;
        return fieldName.startsWith("_snapshot_") || fieldName.startsWith("__");
    }

    /**
     * Record sub-table changes with a pre-normalized table name (bypasses
     * normalization
     * so that numeric binding IDs can be persisted when text-key aliases are
     * absent).
     * Used by task completion to record consolidated change history.
     */
    public void recordSubTableChangesWithName(ChangeHistoryContext context,
            String normalizedName,
            List<SubTableChange> changes) {
        if (normalizedName == null || normalizedName.isBlank()) {
            return;
        }
        recordNormalizedSubTableChanges(context, normalizedName, changes);
    }

    /**
     * Record sub-table changes.
     * <p>
     * Normalizes sub-table names: skips pure-numeric binding-ID keys and lowercases
     * to merge case variants
     * (e.g. "People" and "people" are treated as the same table). Per-call
     * deduplication prevents the same
     * field change from being recorded multiple times when the same sub-table data
     * is submitted under several
     * key aliases in {@code __subTables__}.
     */
    public void recordSubTableChanges(ChangeHistoryContext context,
            String subTableName,
            List<SubTableChange> changes) {
        // Normalize: skip pure-numeric binding-ID keys; lowercase to merge
        // "People"/"people"
        String normalizedName = normalizeSubTableNameForHistory(subTableName);
        if (normalizedName == null) {
            return;
        }
        recordNormalizedSubTableChanges(context, normalizedName, changes);
    }

    /**
     * Persists one physical audit record for each changed sub-table field. This
     * keeps the
     * audit table atomic and prevents a JSON object containing several field
     * changes from
     * being stored in a single history row.
     */
    private void recordNormalizedSubTableChanges(ChangeHistoryContext context,
            String normalizedName,
            List<SubTableChange> changes) {
        Instant now = Instant.now();
        List<ChangeHistory> records = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SubTableChange change : changes) {
            ChangeType changeType = mapSubTableChangeType(change.getChangeType());
            for (String fieldName : changedSubTableFieldNames(change)) {
                String dedupKey = context.getProcessInstanceId() + "|" + normalizedName + "|"
                        + change.getChangeType() + "|" + change.getRowIdentifier() + "|" + fieldName;
                if (!seen.add(dedupKey)) {
                    log.debug("Skipping duplicate sub-table field change record (in-batch): {}", dedupKey);
                    continue;
                }
                String oldVal = toDisplayString(valueForField(change.getOldValues(), fieldName));
                String newVal = toDisplayString(valueForField(change.getNewValues(), fieldName));
                ChangeHistory lastRecord = changeHistoryRepository
                        .findTopByProcessInstanceIdAndSubTableNameAndRowIdentifierAndFieldNameAndChangeTypeOrderByTimestampDesc(
                                context.getProcessInstanceId(), normalizedName, change.getRowIdentifier(),
                                fieldName, changeType);
                if (lastRecord != null
                        && Objects.equals(lastRecord.getOldValue(), oldVal)
                        && Objects.equals(lastRecord.getNewValue(), newVal)) {
                    log.debug(
                            "Skipping duplicate sub-table field change record (cross-save): process={}, table={}, row={}, field={}, type={}",
                            context.getProcessInstanceId(), normalizedName, change.getRowIdentifier(), fieldName,
                            changeType);
                    continue;
                }
                records.add(ChangeHistory.builder()
                        .processInstanceId(context.getProcessInstanceId())
                        .taskInstanceId(context.getTaskInstanceId())
                        .stageId(context.getStageId())
                        .userId(context.getUserId())
                        .timestamp(now)
                        .fieldName(fieldName)
                        .oldValue(oldVal)
                        .newValue(newVal)
                        .changeType(changeType)
                        .subTableName(normalizedName)
                        .rowIdentifier(change.getRowIdentifier())
                        .build());
            }
        }

        if (records.isEmpty()) {
            return;
        }

        requiresNewTx.executeWithoutResult(status -> {
            try {
                changeHistoryRepository.saveAll(records);
                log.debug("Recorded {} sub-table field change(s) for process {}, table {}",
                        records.size(), context.getProcessInstanceId(), normalizedName);
            } catch (Exception e) {
                log.warn("Failed to record sub-table changes for process {}, table {}: {}",
                        context.getProcessInstanceId(), normalizedName, e.getMessage());
                status.setRollbackOnly();
            }
        });
    }

    private static Set<String> changedSubTableFieldNames(SubTableChange change) {
        Set<String> fieldNames = new TreeSet<>();
        if (change.getOldValues() != null) {
            fieldNames.addAll(change.getOldValues().keySet());
        }
        if (change.getNewValues() != null) {
            fieldNames.addAll(change.getNewValues().keySet());
        }
        fieldNames.removeIf(ChangeHistoryComponent::isSubTableRowMetadataField);
        return fieldNames;
    }

    private static Object valueForField(Map<String, Object> values, String fieldName) {
        return values == null ? null : values.get(fieldName);
    }

    /**
     * Query change history, optionally filtered to a specific sub-table row.
     */
    public List<ChangeHistoryRecord> getChangeHistory(String processInstanceId, String rowIdentifier) {
        List<ChangeHistory> entities = changeHistoryRepository
                .findByProcessInstanceIdOrderByTimestampAsc(processInstanceId);
        // Filter out internal fields that were recorded before the blacklist was in
        // place
        entities = entities.stream()
                .filter(e -> !isInternalField(e.getFieldName()))
                .toList();

        // When a row identifier is given (e.g. from a multi-instance Todo task),
        // keep only records for that specific row plus top-level field changes.
        if (rowIdentifier != null && !rowIdentifier.isBlank()) {
            entities = entities.stream()
                    .filter(e -> e.getRowIdentifier() == null
                            || rowIdentifier.equals(e.getRowIdentifier()))
                    .toList();
        }
        
        Map<String, String> userDisplayById = resolveUserDisplayNames(entities);
        StageNameMaps stageNames = resolveStageNames(processInstanceId);
        HistoryFieldMetadata fieldMetadata = resolveHistoryFieldMetadata(processInstanceId);
        return entities.stream()
                .flatMap(e -> toRecords(e, userDisplayById, stageNames, fieldMetadata).stream())
                .filter(record -> fieldMetadata.isUserVisible(record))
                .toList();
    }

    /**
     * Query change history, optionally filtered by task ID.
     * For multi-instance sub-tasks, resolves the specific row identifier from task
     * variables.
     */
    public List<ChangeHistoryRecord> getChangeHistory(String processInstanceId, String rowIdentifier, String taskId) {
        String resolvedRowId = rowIdentifier;
        if ((resolvedRowId == null || resolvedRowId.isBlank()) && taskId != null && !taskId.isBlank()) {
            resolvedRowId = resolveRowIdFromTask(taskId);
        }
        return getChangeHistory(processInstanceId, resolvedRowId);
    }

    /** Query change history for the whole process (no row filter). */
    public List<ChangeHistoryRecord> getChangeHistory(String processInstanceId) {
        return getChangeHistory(processInstanceId, null, null);
    }

    /**
     * Resolve the row identifier from a multi-instance task's variables via the
     * workflow engine.
     */
    private String resolveRowIdFromTask(String taskId) {
        try {
            return workflowEngineClient.getTaskById(taskId)
                    .map(task -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> variables = (Map<String, Object>) task.get("variables");
                        if (variables != null) {
                            Object currentItem = variables.get("_currentItem");
                            if (currentItem instanceof Map<?, ?> item) {
                                Object rowId = item.get("rowId");
                                return rowId != null ? rowId.toString() : null;
                            }
                        }
                        return null;
                    })
                    .orElse(null);
        } catch (Exception e) {
            log.debug("Could not resolve rowId from task {}: {}", taskId, e.getMessage());
        }
        return null;
    }

    /**
     * Record concurrent modification warning.
     */
    public void recordConcurrentModificationWarning(String processInstanceId,
            String fieldName,
            String userId1,
            String userId2) {
        requiresNewTx.executeWithoutResult(status -> {
            try {
                ChangeHistory record = ChangeHistory.builder()
                        .processInstanceId(processInstanceId)
                        .userId(userId2)
                        .timestamp(Instant.now())
                        .fieldName(fieldName)
                        .changeType(ChangeType.FIELD_UPDATE)
                        .isConcurrent(true)
                        .build();
                changeHistoryRepository.save(record);
                log.warn("Concurrent modification detected on process {}, field {}, users: {} and {}",
                        processInstanceId, fieldName, userId1, userId2);
            } catch (Exception e) {
                log.warn("Failed to record concurrent modification warning for process {}: {}",
                        processInstanceId, e.getMessage());
                status.setRollbackOnly();
            }
        });
    }

    private ChangeType mapSubTableChangeType(String changeType) {
        return switch (changeType) {
            case "ROW_ADD" -> ChangeType.SUB_TABLE_ROW_ADD;
            case "ROW_UPDATE" -> ChangeType.SUB_TABLE_ROW_UPDATE;
            case "ROW_DELETE" -> ChangeType.SUB_TABLE_ROW_DELETE;
            default -> ChangeType.SUB_TABLE_ROW_UPDATE;
        };
    }

    /**
     * Splits legacy sub-table JSON payloads into field-level response rows. New
     * writes are
     * already atomic; this compatibility path keeps historical records readable in
     * the same
     * one-field-per-row table without mutating audit evidence.
     */
    private List<ChangeHistoryRecord> toRecords(ChangeHistory entity,
            Map<String, String> userDisplayById,
            StageNameMaps stageNames,
            HistoryFieldMetadata fieldMetadata) {
        if (!isLegacySubTablePayload(entity)) {
            return List.of(toRecord(entity, userDisplayById, stageNames, fieldMetadata,
                    entity.getFieldName(), entity.getOldValue(), entity.getNewValue()));
        }
        Map<String, Object> oldFields = parseJsonObject(entity.getOldValue());
        Map<String, Object> newFields = parseJsonObject(entity.getNewValue());
        Set<String> fieldNames = new TreeSet<>();
        fieldNames.addAll(oldFields.keySet());
        fieldNames.addAll(newFields.keySet());
        fieldNames.removeIf(ChangeHistoryComponent::isSubTableRowMetadataField);
        if (fieldNames.isEmpty()) {
            return List.of(toRecord(entity, userDisplayById, stageNames, fieldMetadata,
                    entity.getFieldName(), entity.getOldValue(), entity.getNewValue()));
        }
        return fieldNames.stream()
                .map(fieldName -> toRecord(entity, userDisplayById, stageNames, fieldMetadata, fieldName,
                        toDisplayString(oldFields.get(fieldName)), toDisplayString(newFields.get(fieldName))))
                .toList();
    }

    /**
     * The prior writer stored an entire sub-table row payload as JSON and used the
     * table
     * name as {@code fieldName}. New audit rows retain the actual field name, so
     * this guard
     * prevents structured values (such as a selector object) from being expanded
     * incorrectly.
     */
    private static boolean isLegacySubTablePayload(ChangeHistory entity) {
        return entity.getSubTableName() != null
                && !entity.getSubTableName().isBlank()
                && entity.getSubTableName().equals(entity.getFieldName());
    }

    private Map<String, Object> parseJsonObject(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private ChangeHistoryRecord toRecord(ChangeHistory entity,
            Map<String, String> userDisplayById,
            StageNameMaps stageNames,
            HistoryFieldMetadata fieldMetadata,
            String fieldName,
            String oldValue,
            String newValue) {
        String userName = userDisplayById.get(entity.getUserId());
        String stageName = null;
        if (entity.getTaskInstanceId() != null && !entity.getTaskInstanceId().isBlank()) {
            stageName = stageNames.taskInstanceIdToName().get(entity.getTaskInstanceId());
        }
        if (stageName == null && entity.getStageId() != null && !entity.getStageId().isBlank()) {
            stageName = stageNames.taskDefinitionKeyToName().get(entity.getStageId());
        }
        FieldMetadata field = fieldMetadata.resolve(entity.getSubTableName(), fieldName);
        String fieldLabel = field != null ? field.label() : null;
        Integer fieldOrder = field != null ? field.order() : null;
        return ChangeHistoryRecord.builder()
                .id(entity.getId())
                .processInstanceId(entity.getProcessInstanceId())
                .taskInstanceId(entity.getTaskInstanceId())
                .stageId(entity.getStageId())
                .stageName(stageName)
                .userId(entity.getUserId())
                .userName(userName)
                .timestamp(entity.getTimestamp())
                .fieldName(fieldName)
                .fieldLabel(fieldLabel)
                .fieldOrder(fieldOrder)
                .oldValue(displayValueForKnownUser(oldValue, userDisplayById))
                .newValue(displayValueForKnownUser(newValue, userDisplayById))
                .changeType(entity.getChangeType().name())
                .subTableName(entity.getSubTableName())
                .rowIdentifier(entity.getRowIdentifier())
                .concurrent(Boolean.TRUE.equals(entity.getIsConcurrent()))
                .build();
    }

    private Map<String, String> resolveUserDisplayNames(List<ChangeHistory> entities) {
        Set<String> ids = entities.stream()
                .flatMap(entity -> java.util.stream.Stream.of(
                        entity.getUserId(), entity.getOldValue(), entity.getNewValue()))
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(this::isPotentialUserId)
                .collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new HashMap<>();
        for (User u : userRepository.findAllById(ids)) {
            if (u != null && u.getId() != null) {
                out.put(u.getId(), displayNameForUser(u));
            }
        }
        return out;
    }

    private boolean isPotentialUserId(String value) {
        return !value.isEmpty() && value.length() <= 64 && !value.startsWith("{") && !value.startsWith("[");
    }

    private static String displayValueForKnownUser(String value, Map<String, String> userDisplayById) {
        if (value == null || userDisplayById == null) {
            return value;
        }
        return userDisplayById.getOrDefault(value, value);
    }

    private static String displayNameForUser(User u) {
        if (u.getFullName() != null && !u.getFullName().isBlank()) {
            return u.getFullName().trim();
        }
        if (u.getDisplayName() != null && !u.getDisplayName().isBlank()) {
            return u.getDisplayName().trim();
        }
        return u.getUsername();
    }

    private StageNameMaps resolveStageNames(String processInstanceId) {
        Map<String, String> byTaskId = new HashMap<>();
        Map<String, String> byDefKey = new HashMap<>();
        try {
            workflowEngineClient.getTaskHistory(processInstanceId).ifPresent(tasks -> {
                for (Map<String, Object> task : tasks) {
                    String name = stringOrNull(task.get("name"));
                    if (name == null || name.isBlank()) {
                        continue;
                    }
                    String taskInstanceId = stringOrNull(task.get("id"));
                    if (taskInstanceId != null && !taskInstanceId.isBlank()) {
                        byTaskId.putIfAbsent(taskInstanceId, name.trim());
                    }
                    String defKey = stringOrNull(task.get("activityId"));
                    if (defKey != null && !defKey.isBlank()) {
                        byDefKey.putIfAbsent(defKey, name.trim());
                    }
                }
            });
        } catch (Exception e) {
            log.debug("Could not enrich change history stage names for {}: {}", processInstanceId, e.getMessage());
        }
        return new StageNameMaps(byTaskId, byDefKey);
    }

    private static String stringOrNull(Object o) {
        return o == null ? null : o.toString();
    }

    private record StageNameMaps(Map<String, String> taskInstanceIdToName,
            Map<String, String> taskDefinitionKeyToName) {
    }

    /**
     * Builds the audit display contract from the form definition and the
     * relation-table
     * field definitions belonging to the process function unit. This deliberately
     * uses
     * configured metadata rather than inferring relationships from field-name
     * conventions.
     */
    private HistoryFieldMetadata resolveHistoryFieldMetadata(String processInstanceId) {
        Map<String, FieldMetadata> topLevelFields = new HashMap<>();
        Map<String, String> labels = resolveFieldLabels(processInstanceId);
        Map<String, Integer> orders = resolveFieldOrders(processInstanceId);
        Set<String> formFields = new HashSet<>();
        formFields.addAll(labels.keySet());
        formFields.addAll(orders.keySet());
        for (String fieldName : formFields) {
            topLevelFields.put(fieldName, new FieldMetadata(labels.get(fieldName), orders.get(fieldName)));
        }
        Map<String, Map<String, FieldMetadata>> subTableFields = new HashMap<>();
        try {
            ProcessInstance processInstance = processInstanceRepository.findById(processInstanceId).orElse(null);
            if (processInstance == null || processInstance.getProcessDefinitionKey() == null) {
                return new HistoryFieldMetadata(topLevelFields, subTableFields);
            }
            List<SubTableFieldMetadataRow> rows = jdbcTemplate.query(
                    """
                            SELECT td.table_name, fd.field_name,
                                   COALESCE(NULLIF(BTRIM(fd.display_name), ''), fd.field_name) AS display_name,
                                   fd.sort_order
                            FROM dw_table_definitions td
                            INNER JOIN dw_field_definitions fd ON fd.table_id = td.id
                            INNER JOIN dw_function_units fu ON fu.id = td.function_unit_id
                            WHERE fu.code = ? AND td.table_type = 'SUB'
                            """,
                    (rs, rowNum) -> new SubTableFieldMetadataRow(
                            rs.getString("table_name"),
                            rs.getString("field_name"),
                            rs.getString("display_name"),
                            rs.getInt("sort_order")),
                    processInstance.getProcessDefinitionKey().trim());
            for (SubTableFieldMetadataRow row : rows) {
                String tableName = normalizeSubTableNameForHistory(row.tableName());
                if (tableName == null || row.fieldName() == null || row.fieldName().isBlank()) {
                    continue;
                }
                subTableFields.computeIfAbsent(tableName, ignored -> new HashMap<>())
                        .putIfAbsent(row.fieldName(), new FieldMetadata(row.displayName(), row.sortOrder()));
            }
        } catch (Exception e) {
            log.debug("Could not resolve configured sub-table fields for {}: {}", processInstanceId, e.getMessage());
        }
        return new HistoryFieldMetadata(topLevelFields, subTableFields);
    }

    private record FieldMetadata(String label, Integer order) {
    }

    private record SubTableFieldMetadataRow(String tableName, String fieldName,
            String displayName, int sortOrder) {
    }

    private record HistoryFieldMetadata(Map<String, FieldMetadata> topLevelFields,
            Map<String, Map<String, FieldMetadata>> subTableFields) {
        FieldMetadata resolve(String subTableName, String fieldName) {
            if (fieldName == null || fieldName.isBlank()) {
                return null;
            }
            String normalizedTableName = normalizeSubTableNameForHistory(subTableName);
            if (normalizedTableName != null) {
                Map<String, FieldMetadata> tableFields = subTableFields.get(normalizedTableName);
                if (tableFields != null) {
                    return tableFields.get(fieldName);
                }
            }
            return topLevelFields.get(fieldName);
        }

        boolean isUserVisible(ChangeHistoryRecord record) {
            String fieldName = record.getFieldName();
            if (fieldName == null || fieldName.isBlank()) {
                return false;
            }
            String normalizedTableName = normalizeSubTableNameForHistory(record.getSubTableName());
            if (normalizedTableName != null) {
                Map<String, FieldMetadata> tableFields = subTableFields.get(normalizedTableName);
                return tableFields == null || tableFields.containsKey(fieldName);
            }
            return topLevelFields.isEmpty() || topLevelFields.containsKey(fieldName);
        }
    }

    /**
     * Resolve field labels from the PROCESS form configJson.
     * Extracts field → title mappings from the form designer rule array,
     * including sub-form fields.
     */
    private Map<String, String> resolveFieldLabels(String processInstanceId) {
        Map<String, String> labels = new HashMap<>();
        try {
            ProcessInstance pi = processInstanceRepository.findById(processInstanceId).orElse(null);
            if (pi == null || pi.getProcessDefinitionKey() == null) {
                return labels;
            }
            String processDefKey = pi.getProcessDefinitionKey();

            List<String> configJsonStrings = jdbcTemplate.query(
                    """
                            SELECT fd.config_json::text AS config_json
                            FROM dw_form_definitions fd
                            INNER JOIN dw_function_units fu ON fu.id = fd.function_unit_id
                            WHERE fu.code = ?
                            """,
                    (rs, rowNum) -> rs.getString("config_json"),
                    processDefKey.trim());

            for (String raw : configJsonStrings) {
                if (raw == null || raw.isBlank())
                    continue;
                try {
                    Map<String, Object> config = objectMapper.readValue(raw, new TypeReference<>() {
                    });
                    extractFieldLabelsFromConfig(config, labels, null);
                } catch (Exception e) {
                    log.debug("Could not parse configJson for field labels: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.debug("Could not resolve field labels for {}: {}", processInstanceId, e.getMessage());
        }
        return labels;
    }

    private Map<String, Integer> resolveFieldOrders(String processInstanceId) {
        Map<String, Integer> orders = new HashMap<>();
        try {
            ProcessInstance pi = processInstanceRepository.findById(processInstanceId).orElse(null);
            if (pi == null || pi.getProcessDefinitionKey() == null) {
                return orders;
            }
            String processDefKey = pi.getProcessDefinitionKey();
            List<String> configJsonStrings = jdbcTemplate.query(
                    """
                            SELECT fd.config_json::text AS config_json
                            FROM dw_form_definitions fd
                            INNER JOIN dw_function_units fu ON fu.id = fd.function_unit_id
                            WHERE fu.code = ?
                            """,
                    (rs, rowNum) -> rs.getString("config_json"),
                    processDefKey.trim());
            int[] counter = { 0 };
            for (String raw : configJsonStrings) {
                if (raw == null || raw.isBlank())
                    continue;
                try {
                    Map<String, Object> config = objectMapper.readValue(raw, new TypeReference<>() {
                    });
                    extractFieldLabelsFromConfig(config, null, new FieldMetaCollector(orders, counter));
                } catch (Exception e) {
                    log.debug("Could not parse configJson for field orders: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.debug("Could not resolve field orders for {}: {}", processInstanceId, e.getMessage());
        }
        return orders;
    }

    private record FieldMetaCollector(Map<String, Integer> fieldOrders, int[] counter) {
        void accept(String fieldName) {
            fieldOrders.putIfAbsent(fieldName, counter[0]++);
        }
    }

    @SuppressWarnings("unchecked")
    private void extractFieldLabelsFromConfig(Map<String, Object> config,
            Map<String, String> labels,
            FieldMetaCollector collector) {
        Object rule = config.get("rule");
        if (rule instanceof List<?> rules) {
            for (Object item : rules) {
                if (item instanceof Map<?, ?> ruleItem) {
                    Object field = ruleItem.get("field");
                    Object title = ruleItem.get("title");
                    if (field instanceof String f && !f.isBlank()) {
                        if (title instanceof String t && !t.isBlank() && labels != null) {
                            labels.putIfAbsent(f, t);
                        }
                        if (collector != null) {
                            collector.accept(f);
                        }
                    }
                }
            }
        }
        Object subForms = config.get("subForms");
        if (subForms instanceof Map<?, ?> subMap) {
            for (Object subConfig : subMap.values()) {
                if (subConfig instanceof Map<?, ?> sc) {
                    extractFieldLabelsFromConfig((Map<String, Object>) sc, labels, collector);
                }
            }
        }
    }

    // ==================== display helpers ====================

    /**
     * Converts a value to a user-friendly display string for change history.
     * Maps and Lists are serialized as JSON; scalars use {@code toString()}.
     */
    private String toDisplayString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        // Maps, Lists, and other complex objects → compact JSON
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return value.toString();
        }
    }

    /**
     * Normalizes a sub-table name from {@code __subTables__} for change history
     * recording.
     * Returns {@code null} when the key should be skipped (pure-numeric binding
     * IDs).
     * Otherwise returns the trimmed, lowercased name so that "People" and "people"
     * merge.
     */
    static String normalizeSubTableNameForHistory(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        String trimmed = rawName.trim();
        // Skip pure-numeric keys in the general path — these are internal binding IDs
        // that
        // duplicate text-key aliases. Callers that need numeric-key support must bypass
        // this.
        if (trimmed.matches("\\d+")) {
            return null;
        }
        return trimmed.toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    @SuppressWarnings("unchecked")
    static Map<String, List<Map<String, Object>>> normalizeSubTableRowsByHistoryName(Object subTablesObj) {
        if (!(subTablesObj instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, List<Map<String, Object>>> rowsByName = new LinkedHashMap<>();
        Set<String> seenRowIds = new HashSet<>();
        // Sort keys so old and new snapshots assign the same row to the same
        // normalized-name group regardless of HashMap iteration order.
        List<Map.Entry<?, ?>> sortedEntries = new ArrayList<>(rawMap.entrySet());
        sortedEntries.sort((a, b) -> {
            String ka = a.getKey() != null ? a.getKey().toString() : "";
            String kb = b.getKey() != null ? b.getKey().toString() : "";
            return ka.compareTo(kb);
        });
        for (Map.Entry<?, ?> entry : sortedEntries) {
            String normalizedName = normalizeSubTableNameForHistory(stringOrNull(entry.getKey()));
            if (normalizedName == null) {
                continue;
            }
            if (!(entry.getValue() instanceof List<?> rawRows)) {
                continue;
            }
            List<Map<String, Object>> rows = rowsByName.computeIfAbsent(normalizedName, ignored -> new ArrayList<>());
            for (Object rawRow : rawRows) {
                if (rawRow instanceof Map<?, ?> row) {
                    Map<String, Object> typedRow = (Map<String, Object>) row;
                    // Cross-key dedup: the same row can appear under multiple
                    // key aliases (binding ID, table name, normalized name).
                    // Keep only the first occurrence so diff output doesn't
                    // contain mirror records for each alias.
                    String rowId = resolveRowIdentifier(typedRow);
                    if (rowId != null && !seenRowIds.add(rowId)) {
                        continue;
                    }
                    rows.add(typedRow);
                }
            }
        }
        return rowsByName;
    }

    /**
     * Resolves a human-readable row identifier from a sub-table row map.
     * Tries {@code id}, then common fallback fields ({@code id_idw}, {@code rowId},
     * …),
     * falling back to the first non-internal key–value pair.
     *
     * @param row the sub-table row map (never null)
     * @return a displayable row identifier, or {@code null}
     */
    public static String resolveRowIdentifier(Map<String, Object> row) {
        // 1. Canonical "id"
        Object id = row.get("id");
        if (id != null) {
            return String.valueOf(id);
        }
        // 2. Known fallback fields
        for (String field : ROW_ID_FALLBACK_FIELDS) {
            Object v = row.get(field);
            if (v != null) {
                return String.valueOf(v);
            }
        }
        // 3. First non-internal key-value pair as a last resort
        for (Map.Entry<String, Object> e : row.entrySet()) {
            String k = e.getKey();
            if (k == null || k.startsWith("_") || isInternalField(k)) {
                continue;
            }
            Object v = e.getValue();
            if (v != null) {
                return String.valueOf(v);
            }
        }
        return null;
    }

    static boolean isSubTableRowMetadataField(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return true;
        }
        return SUB_TABLE_ROW_METADATA_FIELDS.contains(fieldName.trim().toLowerCase());
    }
}
