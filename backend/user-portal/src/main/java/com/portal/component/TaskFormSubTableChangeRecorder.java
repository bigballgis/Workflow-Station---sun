package com.portal.component;

import com.portal.dto.ChangeHistoryContext;
import com.portal.dto.SubTableChange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
                List<SubTableChange> changes = SubTableChangeHistoryDiff.compute(oldRows, newRows);
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
}

