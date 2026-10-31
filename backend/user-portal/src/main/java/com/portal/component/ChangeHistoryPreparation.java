package com.portal.component;

import com.portal.dto.ChangeHistoryContext;
import com.portal.dto.SubTableChange;
import com.portal.entity.ChangeHistory;
import com.portal.enums.ChangeType;
import com.portal.repository.ChangeHistoryRepository;
import java.time.Instant;
import java.util.*;

/** Prepares immutable audit rows without persisting them or opening an independent transaction. */
final class ChangeHistoryPreparation {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(ChangeHistoryPreparation.class);
    private final ChangeHistoryComponent owner;
    private final ChangeHistoryRepository changeHistoryRepository;
    ChangeHistoryPreparation(ChangeHistoryComponent owner,ChangeHistoryRepository repository) { this.owner=owner; this.changeHistoryRepository=repository; }
    List<ChangeHistory> fields(ChangeHistoryContext context,Map<String,Object> oldValues,Map<String,Object> newValues) {
        Instant now = Instant.now();
        List<ChangeHistory> records = new ArrayList<>();
        for (Map.Entry<String, Object> entry : newValues.entrySet()) {
            String fieldName = entry.getKey();
            if (ChangeHistoryComponent.isInternalField(fieldName)) {
                continue;
            }
            Object newValue = entry.getValue();
            Object oldValue = oldValues.get(fieldName);
            if (!owner.semanticallyEqual(fieldName, oldValue, newValue)) {
                ChangeHistory record = ChangeHistory.builder()
                        .processInstanceId(context.getProcessInstanceId())
                        .taskInstanceId(context.getTaskInstanceId())
                        .stageId(context.getStageId())
                        .userId(context.getUserId())
                        .timestamp(now)
                        .batchId(context.getBatchId())
                        .batchTime(context.getBatchTime())
                        .fieldName(fieldName)
                        .oldValue(owner.toDisplayString(oldValue))
                        .newValue(owner.toDisplayString(newValue))
                        .changeType(ChangeType.FIELD_UPDATE)
                        .build();
                records.add(record);
            }
        }
        return records;
    }
    List<ChangeHistory> rows(ChangeHistoryContext context,String normalizedName,List<SubTableChange> changes) {
        Map<ChangeHistoryRepository.AuditCell, ChangeHistoryRepository.AuditCellSnapshot> latest = SubTableAuditLatestLookup.load(
                changeHistoryRepository, context.getProcessInstanceId(), normalizedName, changes);
        Instant now = Instant.now();
        List<ChangeHistory> records = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SubTableChange change : changes) {
            ChangeType changeType = ChangeHistoryComponent.mapSubTableChangeType(change.getChangeType());
            for (String fieldName : ChangeHistoryComponent.changedSubTableFieldNames(change)) {
                String dedupKey = context.getProcessInstanceId() + "|" + normalizedName + "|"
                        + change.getChangeType() + "|" + change.getRowIdentifier() + "|" + fieldName;
                if (!seen.add(dedupKey)) {
                    LOG.debug("Skipping duplicate sub-table field change record (in-batch): {}", dedupKey);
                    continue;
                }
                String oldVal = owner.toDisplayString(ChangeHistoryComponent.valueForField(change.getOldValues(), fieldName));
                String newVal = owner.toDisplayString(ChangeHistoryComponent.valueForField(change.getNewValues(), fieldName));
                if (owner.semanticallyEqual(fieldName,
                        ChangeHistoryComponent.valueForField(change.getOldValues(), fieldName),
                        ChangeHistoryComponent.valueForField(change.getNewValues(), fieldName))) {
                    continue;
                }
                ChangeHistoryRepository.AuditCellSnapshot lastRecord = latest.get(new ChangeHistoryRepository.AuditCell(change.getRowIdentifier(), fieldName, changeType));
                if (lastRecord != null
                        && Objects.equals(lastRecord.oldValue(), oldVal)
                        && Objects.equals(lastRecord.newValue(), newVal)) {
                    LOG.debug(
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
                        .batchId(context.getBatchId())
                        .batchTime(context.getBatchTime())
                        .fieldName(fieldName)
                        .oldValue(oldVal)
                        .newValue(newVal)
                        .changeType(changeType)
                        .subTableName(normalizedName)
                        .rowIdentifier(change.getRowIdentifier())
                        .build());
            }
        }
        return records;
    }
}
