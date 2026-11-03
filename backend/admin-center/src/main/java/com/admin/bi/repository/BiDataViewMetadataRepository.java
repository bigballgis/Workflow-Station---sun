package com.admin.bi.repository;

import com.admin.bi.dto.response.DataViewAssignmentResponse;
import com.admin.bi.dto.response.DataViewOptionResponse;
import com.admin.exception.AdminBusinessException;
import com.platform.common.functionunit.CatalogSnapshotReader;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Admin release directory only. Old designer bindings retain labels but cannot authorize anything. */
@Repository
@RequiredArgsConstructor
public class BiDataViewMetadataRepository {
    private final JdbcTemplate jdbcTemplate;
    private final CatalogSnapshotReader snapshots;

    public void requirePublishedView(Long functionUnitId, Long tableId, Long viewId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ac_current_data_views "
                + "WHERE view_id=? AND function_unit_id=? AND table_id=?", Integer.class, viewId,functionUnitId,tableId);
        if (count == null || count != 1) throw new AdminBusinessException("DATA_VIEW_NOT_AVAILABLE",
                "The published View does not belong to this Function Unit and table");
    }

    public void requireAvailableView(Long viewId) {
        Integer count=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ac_current_data_views WHERE view_id=?",Integer.class,viewId);
        if(count==null || count!=1) throw new AdminBusinessException("DATA_VIEW_NOT_AVAILABLE","Published View unavailable");
    }

    public Map<String,Object> targetSnapshot(Long viewId) {
        var rows = jdbcTemplate.queryForList("SELECT jsonb_build_object('functionUnitCode',function_unit_code,"
                + "'functionUnitName',function_unit_name,'tableName',table_name,'tableDisplayName',table_display_name,"
                + "'tableType',table_type,'viewName',view_name)::text FROM ac_current_data_views WHERE view_id=?",String.class,viewId);
        if (rows.size()!=1) throw new AdminBusinessException("DATA_VIEW_NOT_AVAILABLE","Published View unavailable");
        return snapshots.parse(rows.get(0));
    }

    public List<DataViewOptionResponse> listViews(Long functionUnitId, Long tableId) {
        return jdbcTemplate.query("SELECT view_id,view_name FROM ac_current_data_views "
                + "WHERE function_unit_id=? AND table_id=? ORDER BY view_name,view_id",
                (rs,n) -> new DataViewOptionResponse(rs.getLong(1),rs.getString(2)),functionUnitId,tableId);
    }

    public List<DataViewAssignmentResponse> responses(List<String> ids) {
        if (ids.isEmpty()) return List.of();
        String placeholders = String.join(",",Collections.nCopies(ids.size(),"?"));
        return jdbcTemplate.query("""
                SELECT a.*, d.dashboard_title,
                       COALESCE(v.function_unit_id,a.function_unit_id) AS resolved_fu_id,
                       COALESCE(v.table_id,a.table_id) AS resolved_table_id,
                       COALESCE(v.function_unit_code,a.legacy_target->>'functionUnitCode') AS fu_code,
                       COALESCE(v.function_unit_name,a.legacy_target->>'functionUnitName') AS fu_name,
                       COALESCE(v.table_name,a.legacy_target->>'tableName') AS table_name,
                       COALESCE(v.table_display_name,a.legacy_target->>'tableDisplayName') AS table_display_name,
                       COALESCE(v.table_type,a.legacy_target->>'tableType') AS table_type,
                       COALESCE(v.view_name,a.legacy_target->>'viewName') AS view_name,
                       v.view_id IS NOT NULL AS view_available
                FROM bi_data_view_assignment a JOIN bi_dashboard_registry d ON d.id=a.dashboard_id
                LEFT JOIN ac_current_data_views v ON a.runtime_target=TRUE AND v.view_id=a.view_id
                WHERE a.id IN (
                """ + placeholders + ") ORDER BY a.created_at,a.id",(rs,n) -> DataViewAssignmentResponse.builder()
                .id(rs.getString("id")).dashboardId(rs.getString("dashboard_id"))
                .dashboardTitle(rs.getString("dashboard_title")).functionUnitId(rs.getLong("resolved_fu_id"))
                .functionUnitCode(rs.getString("fu_code")).functionUnitName(rs.getString("fu_name"))
                .tableId(rs.getLong("resolved_table_id")).tableName(rs.getString("table_name"))
                .tableDisplayName(rs.getString("table_display_name")).tableType(rs.getString("table_type"))
                .viewId(rs.getObject("view_id",Long.class)).viewName(rs.getString("view_name"))
                .viewAvailable(rs.getBoolean("view_available")).enabled(rs.getBoolean("enabled"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime()).build(),ids.toArray());
    }
}
