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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 变更历史组件
 * 记录字段级变更历史，采用"尽力而为"策略，失败不阻断主流程
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChangeHistoryComponent {

    private final ChangeHistoryRepository changeHistoryRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final UserRepository userRepository;
    private final WorkflowEngineClient workflowEngineClient;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

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
     * 记录字段变更
     * 比较 oldValues 和 newValues，为每个变更的字段创建一条 ChangeHistory 记录
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFieldChanges(ChangeHistoryContext context,
                                   Map<String, Object> oldValues,
                                   Map<String, Object> newValues) {
        try {
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
                            .oldValue(oldValue != null ? oldValue.toString() : null)
                            .newValue(newValue != null ? newValue.toString() : null)
                            .changeType(ChangeType.FIELD_UPDATE)
                            .build();
                    records.add(record);
                }
            }

            if (!records.isEmpty()) {
                changeHistoryRepository.saveAll(records);
                log.debug("Recorded {} field change(s) for process {}", records.size(), context.getProcessInstanceId());
            }
        } catch (Exception e) {
            log.warn("Failed to record field changes for process {}: {}",
                    context.getProcessInstanceId(), e.getMessage());
        }
    }

    private static boolean isInternalField(String fieldName) {
        if (fieldName == null) return true;
        if (INTERNAL_FIELD_BLACKLIST.contains(fieldName)) return true;
        return fieldName.startsWith("_snapshot_") || fieldName.startsWith("__");
    }

    /**
     * 记录子表变更
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSubTableChanges(ChangeHistoryContext context,
                                      String subTableName,
                                      List<SubTableChange> changes) {
        try {
            Instant now = Instant.now();
            List<ChangeHistory> records = new ArrayList<>();

            for (SubTableChange change : changes) {
                ChangeType changeType = mapSubTableChangeType(change.getChangeType());

                ChangeHistory record = ChangeHistory.builder()
                        .processInstanceId(context.getProcessInstanceId())
                        .taskInstanceId(context.getTaskInstanceId())
                        .stageId(context.getStageId())
                        .userId(context.getUserId())
                        .timestamp(now)
                        .fieldName(subTableName)
                        .oldValue(change.getOldValues() != null ? change.getOldValues().toString() : null)
                        .newValue(change.getNewValues() != null ? change.getNewValues().toString() : null)
                        .changeType(changeType)
                        .subTableName(subTableName)
                        .rowIdentifier(change.getRowIdentifier())
                        .build();
                records.add(record);
            }

            if (!records.isEmpty()) {
                changeHistoryRepository.saveAll(records);
                log.debug("Recorded {} sub-table change(s) for process {}, table {}",
                        records.size(), context.getProcessInstanceId(), subTableName);
            }
        } catch (Exception e) {
            log.warn("Failed to record sub-table changes for process {}, table {}: {}",
                    context.getProcessInstanceId(), subTableName, e.getMessage());
        }
    }

    /**
     * 查询变更历史
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
     * 记录并发修改警告
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordConcurrentModificationWarning(String processInstanceId,
                                                     String fieldName,
                                                     String userId1,
                                                     String userId2) {
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
        }
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
}
