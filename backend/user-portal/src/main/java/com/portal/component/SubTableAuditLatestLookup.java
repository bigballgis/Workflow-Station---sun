package com.portal.component;

import com.portal.dto.SubTableChange;
import com.portal.repository.ChangeHistoryRepository;
import com.portal.repository.ChangeHistoryRepository.AuditCell;
import com.portal.repository.ChangeHistoryRepository.AuditCellSnapshot;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Request-local audit lookup, bounded by row count and limited to the cells the existing diff will compare. */
final class SubTableAuditLatestLookup {
    private SubTableAuditLatestLookup() { }

    static Map<AuditCell, AuditCellSnapshot> load(ChangeHistoryRepository repository, String processId,
                                             String tableName, List<SubTableChange> changes) {
        Map<String, Set<AuditCell>> cellsByRow = new LinkedHashMap<>();
        for (SubTableChange change : changes) {
            Set<AuditCell> cells = cellsByRow.computeIfAbsent(change.getRowIdentifier(), key -> new LinkedHashSet<>());
            for (String field : ChangeHistoryComponent.changedSubTableFieldNames(change)) {
                cells.add(new AuditCell(change.getRowIdentifier(), field,
                        ChangeHistoryComponent.mapSubTableChangeType(change.getChangeType())));
            }
        }
        List<Set<AuditCell>> groups = List.copyOf(cellsByRow.values());
        Map<AuditCell, AuditCellSnapshot> latest = new LinkedHashMap<>();
        // Ordinary imports can exceed the workspace's 200-row limit; retain bounded requests.
        for (int offset = 0; offset < groups.size(); offset += 200) {
            List<AuditCell> cells = groups.subList(offset, Math.min(offset + 200, groups.size()))
                    .stream().flatMap(Set::stream).toList();
            for (AuditCellSnapshot record : repository.findLatestAuditCells(processId, tableName, cells)) {
                latest.put(record.cell(), record);
            }
        }
        return latest;
    }
}
