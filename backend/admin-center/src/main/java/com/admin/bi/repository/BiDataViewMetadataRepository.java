package com.admin.bi.repository;

import com.admin.bi.dto.response.DataViewAssignmentResponse;
import com.admin.component.FunctionUnitLifecycleComponent;
import com.admin.entity.FunctionUnit;
import com.admin.exception.AdminBusinessException;
import com.platform.common.functionunit.CatalogSnapshotReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Published View metadata for BI Data View Assignments, read from released data only: a unit's active release (the
 * one new requests start on) and its published content. A View is named by its function unit code and View name.
 */
@Repository
public class BiDataViewMetadataRepository {

    /** A published View of an active release, with the unit's own MAIN / SUB table it lists. */
    public record ReleasedView(String catalogId, String functionUnitCode, String functionUnitName, String viewName,
                               String tableName, String tableDisplayName, String tableType,
                               List<Map<String, Object>> accessRules) {
    }

    /** SQL joining an assignment row {@code a} to its unit's active release {@code fu}, View {@code rv}, table {@code rt}. */
    public record ReleaseJoin(String sql, List<Object> params) {
    }

    private final JdbcTemplate jdbcTemplate;
    private final FunctionUnitLifecycleComponent lifecycle;
    private final CatalogSnapshotReader snapshots;

    public BiDataViewMetadataRepository(JdbcTemplate jdbcTemplate, FunctionUnitLifecycleComponent lifecycle,
                                        CatalogSnapshotReader snapshots) {
        this.jdbcTemplate = jdbcTemplate;
        this.lifecycle = lifecycle;
        this.snapshots = snapshots;
    }

    public List<FunctionUnit> activeReleases() {
        return lifecycle.listLatestDeployedFunctionUnits();
    }

    /** The unit's published View of this name in its active release; empty when there is none. */
    public Optional<ReleasedView> publishedView(String functionUnitCode, String viewName) {
        if (functionUnitCode == null || viewName == null) {
            return Optional.empty();
        }
        return lifecycle.getActiveCatalogForPortalStart(functionUnitCode)
                .flatMap(unit -> snapshots.publishedView(unit.getId(), viewName).flatMap(view -> released(unit, view)));
    }

    public ReleasedView requirePublishedView(String functionUnitCode, String viewName) {
        return publishedView(functionUnitCode, viewName).orElseThrow(() -> new AdminBusinessException(
                "DATA_VIEW_NOT_AVAILABLE", "The selected View is not published in the function unit's active release"));
    }

    /** Every published View of a release that lists one of the unit's own MAIN / SUB tables. */
    public List<ReleasedView> publishedViews(FunctionUnit release) {
        List<ReleasedView> views = new ArrayList<>();
        for (Map<String, Object> view : snapshots.publishedViews(release.getId())) {
            released(release, view).ifPresent(views::add);
        }
        return views;
    }

    private Optional<ReleasedView> released(FunctionUnit release, Map<String, Object> view) {
        return snapshots.dataTable(release.getId(), stringOf(view.get("mainTableName")))
                .filter(table -> "MAIN".equals(table.get("tableType")) || "SUB".equals(table.get("tableType")))
                .map(table -> {
                    String tableName = stringOf(table.get("tableName"));
                    String display = stringOf(table.get("tableDisplayName"));
                    return new ReleasedView(release.getId(), release.getCode(), release.getName(),
                            stringOf(view.get("viewName")), tableName,
                            display == null || display.isBlank() ? tableName : display,
                            stringOf(table.get("tableType")),
                            view.get("accessRules") == null ? List.of()
                                    : CatalogSnapshotReader.objects(view.get("accessRules")));
                });
    }

    public Optional<String> findFunctionUnitCodeOfAssignment(String assignmentId) {
        return jdbcTemplate.queryForList(
                "SELECT function_unit_code FROM bi_data_view_assignment WHERE id = ? AND function_unit_code IS NOT NULL",
                String.class, assignmentId).stream().findFirst();
    }

    /**
     * The same resolution as {@link #publishedView} in SQL, for list filters and ordering: the unit's active release
     * {@code fu} (none when it has no deployed, enabled version), its published View {@code rv} of the assignment's
     * name and the MAIN / SUB table {@code rt} that View lists.
     */
    public ReleaseJoin releaseJoin() {
        List<Object> params = new ArrayList<>();
        List<String> activeIds = activeReleases().stream().map(FunctionUnit::getId).toList();
        String active = activeIds.isEmpty() ? "1 = 0"
                : "fu.id IN (" + String.join(",", Collections.nCopies(activeIds.size(), "?")) + ")";
        params.addAll(activeIds);
        return new ReleaseJoin("""
                 LEFT JOIN sys_function_units fu ON fu.code = a.function_unit_code AND %s
                 LEFT JOIN LATERAL (
                     SELECT v.value AS view
                     FROM sys_function_unit_contents c
                     CROSS JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(c.content_data::jsonb) = 'array'
                         THEN c.content_data::jsonb ELSE '[]'::jsonb END) AS v(value)
                     WHERE c.function_unit_id = fu.id AND c.content_type = 'MAIN_TABLE_VIEW'
                       AND v.value ->> 'status' = 'PUBLISHED' AND v.value ->> 'viewName' = a.view_name
                     LIMIT 1) rv ON TRUE
                 LEFT JOIN LATERAL (
                     SELECT c.content_data::jsonb AS t
                     FROM sys_function_unit_contents c
                     WHERE c.function_unit_id = fu.id AND c.content_type = 'DATA_TABLE'
                       AND c.content_data::jsonb ->> 'tableType' IN ('MAIN', 'SUB')
                       AND lower(c.content_data::jsonb ->> 'tableName') = lower(rv.view ->> 'mainTableName')
                     LIMIT 1) rt ON TRUE
                """.formatted(active), params);
    }

    public List<DataViewAssignmentResponse> responses(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        ReleaseJoin join = releaseJoin();
        List<Object> params = new ArrayList<>(join.params());
        params.addAll(ids);
        String sql = """
                SELECT a.id, a.dashboard_id, d.dashboard_title, a.function_unit_code,
                       COALESCE(fu.name, a.function_unit_code) AS function_unit_name,
                       rt.t ->> 'tableName' AS table_name,
                       COALESCE(NULLIF(rt.t ->> 'tableDisplayName', ''), rt.t ->> 'tableName') AS table_display_name,
                       rt.t ->> 'tableType' AS table_type, a.view_name, rt.t IS NOT NULL AS view_available,
                       a.enabled, a.created_at, a.updated_at
                FROM bi_data_view_assignment a
                JOIN bi_dashboard_registry d ON d.id = a.dashboard_id
                """ + join.sql() + " WHERE a.id IN (" + String.join(",", Collections.nCopies(ids.size(), "?")) + ")";
        return jdbcTemplate.query(sql, (rs, rowNum) -> DataViewAssignmentResponse.builder()
                .id(rs.getString("id"))
                .dashboardId(rs.getString("dashboard_id"))
                .dashboardTitle(rs.getString("dashboard_title"))
                .functionUnitCode(rs.getString("function_unit_code"))
                .functionUnitName(rs.getString("function_unit_name"))
                .tableName(rs.getString("table_name"))
                .tableDisplayName(rs.getString("table_display_name"))
                .tableType(rs.getString("table_type"))
                .viewName(rs.getString("view_name"))
                .viewAvailable(rs.getBoolean("view_available"))
                .enabled(rs.getBoolean("enabled"))
                .createdAt(toLocal(rs.getTimestamp("created_at")))
                .updatedAt(toLocal(rs.getTimestamp("updated_at")))
                .build(), params.toArray());
    }

    private static java.time.LocalDateTime toLocal(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
