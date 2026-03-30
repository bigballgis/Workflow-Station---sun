package com.portal.component;

import com.portal.dto.ChangeHistoryContext;
import com.portal.dto.ChangeHistoryRecord;
import com.portal.dto.SubTableChange;
import com.portal.entity.ChangeHistory;
import com.portal.enums.ChangeType;
import com.portal.repository.ChangeHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 变更历史组件
 * 记录字段级变更历史，采用"尽力而为"策略，失败不阻断主流程
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChangeHistoryComponent {

    private final ChangeHistoryRepository changeHistoryRepository;

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

        return entities.stream()
                .map(this::toRecord)
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

    private ChangeHistoryRecord toRecord(ChangeHistory entity) {
        return ChangeHistoryRecord.builder()
                .id(entity.getId())
                .processInstanceId(entity.getProcessInstanceId())
                .taskInstanceId(entity.getTaskInstanceId())
                .stageId(entity.getStageId())
                .userId(entity.getUserId())
                .timestamp(entity.getTimestamp())
                .fieldName(entity.getFieldName())
                .oldValue(entity.getOldValue())
                .newValue(entity.getNewValue())
                .changeType(entity.getChangeType().name())
                .subTableName(entity.getSubTableName())
                .rowIdentifier(entity.getRowIdentifier())
                .concurrent(Boolean.TRUE.equals(entity.getIsConcurrent()))
                .build();
    }
}
