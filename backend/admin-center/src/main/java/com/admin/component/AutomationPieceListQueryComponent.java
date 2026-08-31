package com.admin.component;

import com.admin.dto.list.AdminListPage;
import com.admin.dto.request.AutomationPieceListQueryRequest;
import com.admin.dto.response.AutomationPieceSummary;
import com.admin.list.AutomationPieceColumnSpec;

import com.admin.list.ListQuerySupport;
import com.admin.service.AutomationPieceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;
import com.platform.common.list.ListFilterSql;

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


        PageNames pageNames = loadPageNames(filterSql, where.toString(), params, request);
        List<AutomationPieceSummary> rows = toRows(pageNames.names());
        ListQuerySupport.logIfSlow(log, LIST_KEY, request.page(), request.size(), total, started);
        return new AdminListPage<>(AutomationPieceColumnSpec.columns(), rows,
                request.page(), request.size(), total);
    }

    private PageNames loadPageNames(ListFilterSql filterSql, String where, List<Object> params,
                                    AutomationPieceListQueryRequest request) {
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.size());
        pageParams.add(request.page() * request.size());
        String orderBy = filterSql.orderBy(request.sortField(), request.sortDirection());
        String sql = "SELECT pm.name" + where + orderBy + " LIMIT ? OFFSET ?";
        ResultSetExtractor<PageNames> extractor = rs -> {
            List<String> names = new ArrayList<>();
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
            return new PageNames(names);
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


    private record PageNames(List<String> names) {
    }
}
