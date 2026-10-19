package com.portal.repository;

import com.portal.entity.ChangeHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 变更历史 Repository
 */
@Repository
public interface ChangeHistoryRepository extends JpaRepository<ChangeHistory, Long>, JpaSpecificationExecutor<ChangeHistory> {

    List<ChangeHistory> findByProcessInstanceIdOrderByTimestampAsc(String processInstanceId);

    List<ChangeHistory> findByTaskInstanceId(String taskInstanceId);

    @Modifying
    @Query("DELETE FROM ChangeHistory c WHERE c.processInstanceId = :processInstanceId")
    void deleteByProcessInstanceId(@Param("processInstanceId") String processInstanceId);

    /** Batch delete for purge flows — one statement instead of one per instance id. */
    @Modifying
    @Query("DELETE FROM ChangeHistory c WHERE c.processInstanceId IN :processInstanceIds")
    void deleteByProcessInstanceIdIn(@Param("processInstanceIds") List<String> processInstanceIds);

    /** One bounded lookup for the changed rows, not one history query per edited cell. */
    @Query(value = """
            SELECT DISTINCT ON (row_identifier, field_name, change_type) * FROM up_change_history
            WHERE process_instance_id = :processId AND sub_table_name = :tableName
              AND (row_identifier IN (:rowIds) OR (:includeNull = true AND row_identifier IS NULL))
            ORDER BY row_identifier, field_name, change_type, timestamp DESC, id DESC
            """, nativeQuery = true)
    List<ChangeHistory> queryLatestSubTableChanges(@Param("processId") String processId,
            @Param("tableName") String tableName, @Param("rowIds") List<String> rowIds,
            @Param("includeNull") boolean includeNull);

    @Query(value = """
            SELECT DISTINCT ON (field_name, change_type) * FROM up_change_history
            WHERE process_instance_id = :processId AND sub_table_name = :tableName AND row_identifier IS NULL
            ORDER BY field_name, change_type, timestamp DESC, id DESC
            """, nativeQuery = true)
    List<ChangeHistory> queryLatestNullIdentityChanges(@Param("processId") String processId,
            @Param("tableName") String tableName);

    default List<ChangeHistory> findLatestSubTableChanges(String processId, String tableName,
            List<String> rowIds, boolean includeNull) {
        List<String> concrete = rowIds.stream().filter(java.util.Objects::nonNull).toList();
        // An all-null native IN list is bound as bytea by Hibernate, not varchar.
        if (concrete.isEmpty()) return includeNull ? queryLatestNullIdentityChanges(processId, tableName) : List.of();
        return queryLatestSubTableChanges(processId, tableName, concrete, includeNull);
    }

    /** Exact changed-cell keys let PostgreSQL seek one latest record instead of sorting all row history. */
    record AuditCell(String rowIdentifier, String fieldName, com.portal.enums.ChangeType changeType) { }
    record AuditCellSnapshot(AuditCell cell, String oldValue, String newValue) { }

    @Query(value = """
            SELECT latest.row_identifier, latest.field_name, latest.change_type, latest.old_value, latest.new_value
            FROM jsonb_to_recordset(CAST(:cellsJson AS jsonb))
                AS cells("rowIdentifier" text, "fieldName" text, "changeType" text)
            CROSS JOIN LATERAL (
                (SELECT * FROM up_change_history
                 WHERE process_instance_id = :processId AND sub_table_name = :tableName
                   AND row_identifier = cells."rowIdentifier"
                   AND field_name = cells."fieldName" AND change_type = cells."changeType"
                 ORDER BY timestamp DESC, id DESC LIMIT 1)
                UNION ALL
                (SELECT * FROM up_change_history
                 WHERE cells."rowIdentifier" IS NULL AND row_identifier IS NULL
                   AND process_instance_id = :processId AND sub_table_name = :tableName
                   AND field_name = cells."fieldName" AND change_type = cells."changeType"
                 ORDER BY timestamp DESC, id DESC LIMIT 1)
            ) latest
            """, nativeQuery = true)
    List<Object[]> queryLatestAuditCells(@Param("processId") String processId,
            @Param("tableName") String tableName, @Param("cellsJson") String cellsJson);

    default List<AuditCellSnapshot> findLatestAuditCells(String processId, String tableName, List<AuditCell> cells) {
        if (cells.isEmpty()) return List.of();
        return queryLatestAuditCells(processId, tableName,
                com.platform.common.util.JsonUtils.toJson(cells.stream().distinct().toList())).stream()
                .map(row -> new AuditCellSnapshot(new AuditCell((String) row[0], (String) row[1],
                        com.portal.enums.ChangeType.valueOf((String) row[2])), (String) row[3], (String) row[4]))
                .toList();
    }

    /**
     * Find all distinct function unit codes that have change history records,
     * resolved through the process instance link.
     */
    @Query("SELECT DISTINCT pi.functionUnitCode FROM ProcessInstance pi " +
           "WHERE pi.id IN (SELECT DISTINCT ch.processInstanceId FROM ChangeHistory ch) " +
           "AND pi.functionUnitCode IS NOT NULL")
    List<String> findDistinctFunctionUnitCodes();
}
