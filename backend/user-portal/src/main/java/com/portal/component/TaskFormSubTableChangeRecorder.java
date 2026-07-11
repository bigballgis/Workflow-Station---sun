package com.portal.component;

import com.portal.dto.ChangeHistoryContext;
import com.portal.dto.SubTableChange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Task Form 子表变更历史记录协作类。
 * 单一职责：对比新旧 __subTables__ 行集，识别 ROW_ADD / ROW_DELETE / ROW_UPDATE 并交由
 * {@link ChangeHistoryComponent} 落库。行为与拆分前 {@link TaskFormComponent} 中的对应方法逐字一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskFormSubTableChangeRecorder {

    private final ChangeHistoryComponent changeHistoryComponent;

    @SuppressWarnings("unchecked")
    public void recordSubTableChangeHistory(ChangeHistoryContext context,
                                            Object oldSubTablesObj,
                                            Object newSubTablesObj) {
        if (newSubTablesObj == null) {
            return;
        }
        try {
            Map<String, List<Map<String, Object>>> oldRowsByTable =
                    ChangeHistoryComponent.normalizeSubTableRowsByHistoryName(oldSubTablesObj);
            Map<String, List<Map<String, Object>>> newRowsByTable =
                    ChangeHistoryComponent.normalizeSubTableRowsByHistoryName(newSubTablesObj);
            for (Map.Entry<String, List<Map<String, Object>>> subTableEntry : newRowsByTable.entrySet()) {
                String subTableKey = subTableEntry.getKey();
                List<Map<String, Object>> newRows = subTableEntry.getValue();
                List<Map<String, Object>> oldRows = oldRowsByTable.getOrDefault(subTableKey, Collections.emptyList());
                List<SubTableChange> changes = computeSubTableRowChanges(oldRows, newRows);
                if (!changes.isEmpty()) {
                    changeHistoryComponent.recordSubTableChanges(
                            context, subTableKey, changes);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to record sub-table changes for process {}: {}",
                    context.getProcessInstanceId(), e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<SubTableChange> computeSubTableRowChanges(
            List<Map<String, Object>> oldRows,
            List<Map<String, Object>> newRows) {
        List<SubTableChange> changes = new ArrayList<>();
        // Build row lookup maps by row id (fallback: row_id, rowId, id_idw, _rowKey, rowKey, first non-internal value)
        Map<Object, Map<String, Object>> oldRowMap = new HashMap<>();
        for (Map<String, Object> row : oldRows) {
            Object rowId = ChangeHistoryComponent.resolveRowIdentifier(row);
            if (rowId != null) {
                oldRowMap.put(rowId, row);
            }
        }
        Map<Object, Map<String, Object>> newRowMap = new HashMap<>();
        for (Map<String, Object> row : newRows) {
            Object rowId = ChangeHistoryComponent.resolveRowIdentifier(row);
            if (rowId != null) {
                newRowMap.put(rowId, row);
            }
        }

        // Detect ROW_ADD (in new but not in old)
        for (Map.Entry<Object, Map<String, Object>> entry : newRowMap.entrySet()) {
            Object rowId = entry.getKey();
            if (!oldRowMap.containsKey(rowId)) {
                changes.add(SubTableChange.builder()
                        .changeType("ROW_ADD")
                        .rowIdentifier(String.valueOf(rowId))
                        .oldValues(null)
                        .newValues(entry.getValue())
                        .build());
            }
        }

        // Detect ROW_DELETE (in old but not in new)
        for (Map.Entry<Object, Map<String, Object>> entry : oldRowMap.entrySet()) {
            Object rowId = entry.getKey();
            if (!newRowMap.containsKey(rowId)) {
                changes.add(SubTableChange.builder()
                        .changeType("ROW_DELETE")
                        .rowIdentifier(String.valueOf(rowId))
                        .oldValues(entry.getValue())
                        .newValues(null)
                        .build());
            }
        }

        // Detect ROW_UPDATE (in both but field values differ)
        for (Map.Entry<Object, Map<String, Object>> entry : newRowMap.entrySet()) {
            Object rowId = entry.getKey();
            Map<String, Object> oldRow = oldRowMap.get(rowId);
            if (oldRow != null) {
                Map<String, Object> newRow = entry.getValue();
                Map<String, Object> changedFields = new HashMap<>();
                Map<String, Object> oldChangedFields = new HashMap<>();
                boolean hasChanges = false;
                // Compare business fields only; row identity and audit metadata are noisy for user-visible history.
                for (Map.Entry<String, Object> field : newRow.entrySet()) {
                    if (ChangeHistoryComponent.isSubTableRowMetadataField(field.getKey())) continue;
                    Object oldFieldVal = oldRow.get(field.getKey());
                    if (!Objects.equals(oldFieldVal, field.getValue())) {
                        changedFields.put(field.getKey(), field.getValue());
                        oldChangedFields.put(field.getKey(), oldFieldVal);
                        hasChanges = true;
                    }
                }
                if (hasChanges) {
                    changes.add(SubTableChange.builder()
                            .changeType("ROW_UPDATE")
                            .rowIdentifier(String.valueOf(rowId))
                            .oldValues(oldChangedFields)
                            .newValues(changedFields)
                            .build());
                }
            }
        }

        return changes;
    }

}
