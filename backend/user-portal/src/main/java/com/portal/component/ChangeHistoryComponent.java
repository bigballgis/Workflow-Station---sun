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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Change History component
 * Records field-level change history with a "best-effort" strategy; failures do not block the main flow.
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

    /** Isolated commits for history writes — failures roll back only this slice (never outer txn). */
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
     * Internal/engine variable names that should never appear in user-visible change history.
     */
    private static final Set<String> INTERNAL_FIELD_BLACKLIST = Set.of(
            "__subTables__", "subTableName", "foreignKey", "assigneeField",
            "mainRecordId", "activeBusinessUnitId", "activeRoleId",
            "requestItemsHasHighValue", "totalPrice", "maxItemPrice", "itemCount",
            "initiator", "participant_assigner_user_id"
    );

    /**
     * Per-thread dedup set to prevent duplicate sub-table change records when the same data is
     * submitted under multiple key aliases (binding ID, table name, normalized name) in
     * {@code __subTables__}. Keys follow the pattern
     * {@code processInstanceId|subTableName|changeType|rowIdentifier}.
     * Evicted when the set grows beyond 500 entries.
     */
    private final ThreadLocal<Set<String>> dedupSeenKeys = ThreadLocal.withInitial(HashSet::new);

    /** Fallback row-id field names when the canonical {@code id} column is absent. */
    private static final String[] ROW_ID_FALLBACK_FIELDS = {"id_idw", "rowId", "_rowKey", "rowKey"};

    /**
     * Record field changes.
     * Compare oldValues and newValues, and create a ChangeHistory record for each changed field.
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
        if (fieldName == null) return true;
        if (INTERNAL_FIELD_BLACKLIST.contains(fieldName)) return true;
        return fieldName.startsWith("_snapshot_") || fieldName.startsWith("__");
    }

    /**
     * Record sub-table changes.
     * <p>
     * Normalizes sub-table names: skips pure-numeric binding-ID keys and lowercases to merge case variants
     * (e.g. "People" and "people" are treated as the same table). A per-thread dedup set prevents the same
     * row change from being recorded multiple times when the same sub-table data is submitted under several
     * key aliases in {@code __subTables__}.
     */
    public void recordSubTableChanges(ChangeHistoryContext context,
                                      String subTableName,
                                      List<SubTableChange> changes) {
        // Normalize: skip pure-numeric binding-ID keys; lowercase to merge "People"/"people"
        String normalizedName = normalizeSubTableNameForHistory(subTableName);
        if (normalizedName == null) {
            return;
        }

        Instant now = Instant.now();
        List<ChangeHistory> records = new ArrayList<>();
        Set<String> seen = dedupSeenKeys.get();

        for (SubTableChange change : changes) {
            ChangeType changeType = mapSubTableChangeType(change.getChangeType());

            // Dedup: same (processInstanceId + normalized name + changeType + rowId) → skip
            String dedupKey = context.getProcessInstanceId() + "|" + normalizedName + "|"
                    + change.getChangeType() + "|" + change.getRowIdentifier();
            if (!seen.add(dedupKey)) {
                log.debug("Skipping duplicate sub-table change record: {}", dedupKey);
                continue;
            }

            ChangeHistory record = ChangeHistory.builder()
                    .processInstanceId(context.getProcessInstanceId())
                    .taskInstanceId(context.getTaskInstanceId())
                    .stageId(context.getStageId())
                    .userId(context.getUserId())
                    .timestamp(now)
                    .fieldName(normalizedName)
                    .oldValue(toDisplayString(change.getOldValues()))
                    .newValue(toDisplayString(change.getNewValues()))
                    .changeType(changeType)
                    .subTableName(normalizedName)
                    .rowIdentifier(change.getRowIdentifier())
                    .build();
            records.add(record);
        }

        // Safety: evict dedup set if it grows too large (unlikely for a single request)
        if (seen.size() > 500) {
            seen.clear();
        }

        if (records.isEmpty()) {
            return;
        }

        requiresNewTx.executeWithoutResult(status -> {
            try {
                changeHistoryRepository.saveAll(records);
                log.debug("Recorded {} sub-table change(s) for process {}, table {}",
                        records.size(), context.getProcessInstanceId(), subTableName);
            } catch (Exception e) {
                log.warn("Failed to record sub-table changes for process {}, table {}: {}",
                        context.getProcessInstanceId(), subTableName, e.getMessage());
                status.setRollbackOnly();
            }
        });
    }

    /**
     * Query change history.
     */
    public List<ChangeHistoryRecord> getChangeHistory(String processInstanceId) {
        List<ChangeHistory> entities = changeHistoryRepository
                .findByProcessInstanceIdOrderByTimestampAsc(processInstanceId);

        // Filter out internal fields that were recorded before the blacklist was in place
        entities = entities.stream()
                .filter(e -> !isInternalField(e.getFieldName()))
                .toList();

        Map<String, String> userDisplayById = resolveUserDisplayNames(entities);
        StageNameMaps stageNames = resolveStageNames(processInstanceId);
        Map<String, String> fieldLabels = resolveFieldLabels(processInstanceId);

        return entities.stream()
                .map(e -> toRecord(e, userDisplayById, stageNames, fieldLabels))
                .toList();
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

    private ChangeHistoryRecord toRecord(ChangeHistory entity,
                                         Map<String, String> userDisplayById,
                                         StageNameMaps stageNames,
                                         Map<String, String> fieldLabels) {
        String userName = userDisplayById.get(entity.getUserId());
        String stageName = null;
        if (entity.getTaskInstanceId() != null && !entity.getTaskInstanceId().isBlank()) {
            stageName = stageNames.taskInstanceIdToName().get(entity.getTaskInstanceId());
        }
        if (stageName == null && entity.getStageId() != null && !entity.getStageId().isBlank()) {
            stageName = stageNames.taskDefinitionKeyToName().get(entity.getStageId());
        }

        String fieldLabel = fieldLabels.get(entity.getFieldName());

        return ChangeHistoryRecord.builder()
                .id(entity.getId())
                .processInstanceId(entity.getProcessInstanceId())
                .taskInstanceId(entity.getTaskInstanceId())
                .stageId(entity.getStageId())
                .stageName(stageName)
                .userId(entity.getUserId())
                .userName(userName)
                .timestamp(entity.getTimestamp())
                .fieldName(entity.getFieldName())
                .fieldLabel(fieldLabel)
                .oldValue(entity.getOldValue())
                .newValue(entity.getNewValue())
                .changeType(entity.getChangeType().name())
                .subTableName(entity.getSubTableName())
                .rowIdentifier(entity.getRowIdentifier())
                .concurrent(Boolean.TRUE.equals(entity.getIsConcurrent()))
                .build();
    }

    private Map<String, String> resolveUserDisplayNames(List<ChangeHistory> entities) {
        Set<String> ids = entities.stream()
                .map(ChangeHistory::getUserId)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
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
                if (raw == null || raw.isBlank()) continue;
                try {
                    Map<String, Object> config = objectMapper.readValue(raw, new TypeReference<>() {});
                    extractFieldLabelsFromConfig(config, labels);
                } catch (Exception e) {
                    log.debug("Could not parse configJson for field labels: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.debug("Could not resolve field labels for {}: {}", processInstanceId, e.getMessage());
        }
        return labels;
    }

    @SuppressWarnings("unchecked")
    private void extractFieldLabelsFromConfig(Map<String, Object> config, Map<String, String> labels) {
        Object rule = config.get("rule");
        if (rule instanceof List<?> rules) {
            for (Object item : rules) {
                if (item instanceof Map<?, ?> ruleItem) {
                    Object field = ruleItem.get("field");
                    Object title = ruleItem.get("title");
                    if (field instanceof String f && title instanceof String t && !f.isBlank() && !t.isBlank()) {
                        labels.putIfAbsent(f, t);
                    }
                }
            }
        }
        Object subForms = config.get("subForms");
        if (subForms instanceof Map<?, ?> subMap) {
            for (Object subConfig : subMap.values()) {
                if (subConfig instanceof Map<?, ?> sc) {
                    extractFieldLabelsFromConfig((Map<String, Object>) sc, labels);
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
     * Normalizes a sub-table name from {@code __subTables__} for change history recording.
     * Returns {@code null} when the key should be skipped (pure-numeric binding IDs).
     * Otherwise returns the trimmed, lowercased name so that "People" and "people" merge.
     */
    static String normalizeSubTableNameForHistory(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        String trimmed = rawName.trim();
        // Skip pure-numeric keys — these are internal binding IDs, not user-visible table names
        if (trimmed.matches("\\d+")) {
            return null;
        }
        return trimmed.toLowerCase();
    }

    /**
     * Resolves a human-readable row identifier from a sub-table row map.
     * Tries {@code id}, then common fallback fields ({@code id_idw}, {@code rowId}, …),
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
}
