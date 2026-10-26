package com.admin.bi.repository;

import com.admin.bi.dto.response.DataViewAssignmentResponse;
import com.admin.bi.dto.response.DataViewOptionResponse;
import com.admin.exception.AdminBusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

/** Reads concrete published view identities and batch assignment display metadata. */
@Repository
@RequiredArgsConstructor
public class BiDataViewMetadataRepository {
    private final JdbcTemplate jdbcTemplate;

    public void requirePublishedView(Long functionUnitId, Long tableId, Long viewId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM dw_main_table_view_configs v
                JOIN dw_table_definitions t ON t.id = v.main_table_id AND t.function_unit_id = v.function_unit_id
                WHERE v.id = ? AND v.function_unit_id = ? AND v.main_table_id = ?
                  AND v.status = 'PUBLISHED' AND t.table_type IN ('MAIN', 'SUB')
                """, Integer.class, viewId, functionUnitId, tableId);
        if (count == null || count != 1) {
            throw new AdminBusinessException("DATA_VIEW_NOT_AVAILABLE",
                    "The selected published View does not belong to this Function Unit and table");
        }
    }

    public List<DataViewOptionResponse> listViews(Long functionUnitId, Long tableId) {
        return jdbcTemplate.query("""
                SELECT v.id, v.view_name FROM dw_main_table_view_configs v
                JOIN dw_table_definitions t ON t.id = v.main_table_id AND t.function_unit_id = v.function_unit_id
                WHERE v.function_unit_id = ? AND v.main_table_id = ? AND v.status = 'PUBLISHED'
                  AND t.table_type IN ('MAIN', 'SUB') ORDER BY v.view_name, v.id
                """, (rs, row) -> new DataViewOptionResponse(rs.getLong("id"), rs.getString("view_name")),
                functionUnitId, tableId);
    }

    public List<DataViewAssignmentResponse> responses(List<String> ids) {
        if (ids.isEmpty()) return List.of();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        return jdbcTemplate.query("""
                SELECT a.*, d.dashboard_title, fu.code AS fu_code,
                       fu.name AS fu_name,
                       t.table_name, t.table_display_name, t.table_type, v.view_name,
                       COALESCE(v.status = 'PUBLISHED', FALSE) AS view_available
                FROM bi_data_view_assignment a
                JOIN bi_dashboard_registry d ON d.id = a.dashboard_id
                JOIN dw_function_units fu ON fu.id = a.function_unit_id
                LEFT JOIN dw_table_definitions t ON t.id = a.table_id AND t.function_unit_id = a.function_unit_id
                LEFT JOIN dw_main_table_view_configs v ON v.id = a.view_id
                       AND v.function_unit_id = a.function_unit_id AND v.main_table_id = a.table_id
                WHERE a.id IN (
                """ + placeholders + ") ORDER BY a.created_at, a.id", (rs, row) -> DataViewAssignmentResponse.builder()
                .id(rs.getString("id")).dashboardId(rs.getString("dashboard_id"))
                .dashboardTitle(rs.getString("dashboard_title"))
                .functionUnitId(rs.getLong("function_unit_id")).functionUnitCode(rs.getString("fu_code"))
                .functionUnitName(rs.getString("fu_name")).tableId(rs.getLong("table_id"))
                .tableName(rs.getString("table_name")).tableDisplayName(rs.getString("table_display_name"))
                .tableType(rs.getString("table_type")).viewId(rs.getObject("view_id", Long.class))
                .viewName(rs.getString("view_name")).viewAvailable(rs.getBoolean("view_available"))
                .enabled(rs.getBoolean("enabled")).createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime()).build(), ids.toArray());
    }
}
