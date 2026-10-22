package com.portal.component;

import com.portal.entity.ChangeHistory;

import java.util.List;
import java.util.Set;

/**
 * Multi-instance To Do hides other participants on {@code miParticipantRow}
 * tables. Shared sub-tables keep their own row ids.
 */
final class ChangeHistoryMiRowFilter {

    private ChangeHistoryMiRowFilter() {
    }

    /**
     * @param miCollectionTables history names of the tables the request's release declares as MI
     *                           participant rows (not the live designer's current declaration)
     */
    static List<ChangeHistory> retainForMiTask(
            List<ChangeHistory> entities,
            String rowIdentifier,
            Set<String> miCollectionTables) {
        if (rowIdentifier == null || rowIdentifier.isBlank()) {
            return entities;
        }
        return entities.stream()
                .filter(entity -> keepHistoryRowForMiTask(entity, rowIdentifier, miCollectionTables))
                .toList();
    }

    static boolean keepHistoryRowForMiTask(
            ChangeHistory entity,
            String rowIdentifier,
            Set<String> miCollectionTables) {
        if (entity.getRowIdentifier() == null || rowIdentifier.equals(entity.getRowIdentifier())) {
            return true;
        }
        if (miCollectionTables.isEmpty()) {
            return false;
        }
        String table = ChangeHistoryComponent.normalizeSubTableNameForHistory(entity.getSubTableName());
        return table == null || !miCollectionTables.contains(table);
    }
}
