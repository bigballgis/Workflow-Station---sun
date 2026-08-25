package com.admin.component;

import com.admin.dto.list.AdminListGroup;
import com.admin.dto.list.AdminListPage;
import com.admin.dto.request.AutomationPieceListQueryRequest;
import com.admin.dto.response.AutomationPieceSummary;
import com.admin.list.AutomationPieceColumnSpec;
import com.admin.list.ListFilterSql;
import com.admin.list.ListQuerySupport;
import com.admin.service.AutomationPieceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Automation Pieces list: one row per package. COUNT(DISTINCT name) and the page
 * share toolbar keyword plus column filters. Inner {@code DISTINCT ON (name)}
 * keeps the newest stored version for filter/sort; the version switcher hydrates
 * every version of the paged packages.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationPieceListQueryComponent {

    static final String LIST_KEY = "admin-automation-pieces";

    private static final String LATEST_FROM = """
             FROM (
               SELECT DISTINCT ON (pm.name)
                 pm.id, pm.name, pm."displayName", pm.version, pm."pieceType",
                 pm.updated, pm.actions, pm.triggers
               FROM piece_metadata pm
               WHERE 1=1
            """;

    private final JdbcTemplate jdbcTemplate;
    private final AutomationPieceService automationPieceService;

    public AdminListPage<AutomationPieceSummary> query(AutomationPieceListQueryRequest request) {
        long started = System.nanoTime();
        ListFilterSql filterSql = AutomationPieceColumnSpec.sql();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(LATEST_FROM);
        appendKeyword(where, params, request.keyword());
        where.append(" ORDER BY pm.name, pm.version DESC) pm WHERE 1=1");
        where.append(filterSql.whereClause(request.filters(), params));

        ResultSetExtractor<Long> countExtractor = rs -> rs.next() ? rs.getLong(1) : 0L;
        long total = ListQuerySupport.requireCount(
                ListQuerySupport.query(jdbcTemplate, "SELECT COUNT(*)" + where, params, countExtractor),
                LIST_KEY);

        String groupExpression = blankToNull(request.groupBy()) == null
                ? null
                : filterSql.groupByExpression(request.groupBy());
        List<AdminListGroup> groups = groupExpression == null
                ? List.of()
                : ListQuerySupport.groupsOf(jdbcTemplate, groupExpression, where.toString(), params);
        if (groupExpression != null && total > 0 && groups.isEmpty()) {
            throw new IllegalStateException("GROUP BY returned no groups for a non-empty automation-piece list");
        }

        PageNames pageNames = loadPageNames(filterSql, where.toString(), params, request, groupExpression);
        List<AutomationPieceSummary> rows = toRows(pageNames.names());
        applyGroupedValues(rows, request.groupBy(), pageNames.groupedValues());
        ListQuerySupport.logIfSlow(log, LIST_KEY, request.page(), request.size(), total, started);
        return new AdminListPage<>(AutomationPieceColumnSpec.columns(), rows, groups,
                request.page(), request.size(), total);
    }

    private PageNames loadPageNames(ListFilterSql filterSql, String where, List<Object> params,
                                    AutomationPieceListQueryRequest request, String groupExpression) {
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.size());
        pageParams.add(request.page() * request.size());
        String orderBy = groupExpression == null
                ? filterSql.orderBy(request.sortField(), request.sortDirection())
                : filterSql.orderByGrouped(groupExpression, request.sortField(), request.sortDirection());
        String groupedSelect = groupExpression == null ? "" : ", " + groupExpression + " AS grouped_value";
        String sql = "SELECT pm.name" + groupedSelect + where + orderBy + " LIMIT ? OFFSET ?";
        ResultSetExtractor<PageNames> extractor = rs -> {
            List<String> names = new ArrayList<>();
            List<String> grouped = new ArrayList<>();
            while (rs.next()) {
                names.add(rs.getString("name"));
                grouped.add(groupExpression == null ? null : rs.getString("grouped_value"));
            }
            return new PageNames(names, grouped);
        };
        return ListQuerySupport.query(jdbcTemplate, sql, pageParams, extractor);
    }

    private List<AutomationPieceSummary> toRows(List<String> names) {
        if (names.isEmpty()) {
            return List.of();
        }
        Map<String, List<AutomationPieceSummary>> byName = automationPieceService.findPiecesByNames(names)
                .stream()
                .collect(Collectors.groupingBy(AutomationPieceSummary::getName));
        List<AutomationPieceSummary> rows = new ArrayList<>(names.size());
        for (String name : names) {
            List<AutomationPieceSummary> versions = byName.getOrDefault(name, List.of()).stream()
                    .sorted((a, b) -> compareVersionDesc(a.getVersion(), b.getVersion()))
                    .collect(Collectors.toList());
            if (versions.isEmpty()) {
                throw new IllegalStateException("automation-piece page referenced missing package " + name);
            }
            AutomationPieceSummary latest = versions.get(0);
            AutomationPieceSummary row = latest.toBuilder().versions(null).build();
            row.setVersions(versions);
            rows.add(row);
        }
        return rows;
    }

    private static void applyGroupedValues(List<AutomationPieceSummary> rows, String groupBy,
                                           List<String> groupedValues) {
        if (groupBy == null || groupBy.isBlank()) {
            return;
        }
        if (rows.size() != groupedValues.size()) {
            throw new IllegalStateException("grouped values and page rows are different lengths");
        }
        for (int i = 0; i < rows.size(); i++) {
            String label = groupedValues.get(i) == null ? "" : groupedValues.get(i);
            AutomationPieceSummary row = rows.get(i);
            switch (groupBy) {
                case "pieceType" -> row.setPieceType(label);
                case "disabled" -> row.setDisabled("true".equalsIgnoreCase(label));
                default -> throw new IllegalStateException("grouped field was not selected: " + groupBy);
            }
        }
    }

    private static void appendKeyword(StringBuilder where, List<Object> params, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String like = "%" + ListFilterSql.escapeLike(keyword.trim()) + "%";
        where.append(" AND (pm.name ILIKE ? OR pm.\"displayName\" ILIKE ?")
                .append(" OR pm.actions::text ILIKE ? OR pm.triggers::text ILIKE ?)");
        params.add(like);
        params.add(like);
        params.add(like);
        params.add(like);
    }

    /** Same ordering as the catalog page: newer semver first; illegal tokens fall back to text. */
    static int compareVersionDesc(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            Integer x = i < pa.length ? parseIntOrNull(pa[i]) : 0;
            Integer y = i < pb.length ? parseIntOrNull(pb[i]) : 0;
            if (x == null || y == null) {
                return b.compareTo(a);
            }
            if (!x.equals(y)) {
                return y - x;
            }
        }
        return 0;
    }

    private static Integer parseIntOrNull(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record PageNames(List<String> names, List<String> groupedValues) {
    }
}
