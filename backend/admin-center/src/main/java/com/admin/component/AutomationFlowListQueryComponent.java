package com.admin.component;

import com.admin.dto.list.AdminListPage;
import com.admin.dto.request.AutomationFlowListQueryRequest;
import com.admin.dto.response.AutomationFlowSummary;
import com.admin.list.AutomationFlowColumnSpec;

import com.admin.list.ListQuerySupport;
import com.admin.service.AutomationFlowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;
import com.platform.common.list.ListFilterSql;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Automation Flows list: COUNT(*) and the page share toolbar keyword plus column
 * filters. Readiness uses the same DRAFT/ENABLED/DISABLED CASE as the UI.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationFlowListQueryComponent {

    static final String LIST_KEY = "admin-automation-flows";

    private static final String FROM_JOIN = """
             FROM flow f
             JOIN LATERAL (SELECT "displayName", valid, updated FROM flow_version v
                           WHERE v."flowId" = f.id ORDER BY v.created DESC LIMIT 1) fv ON true
             JOIN project p ON p.id = f."projectId"
             LEFT JOIN "user" u ON u.id = f."ownerId"
             LEFT JOIN user_identity ui ON ui.id = u."identityId"
             WHERE 1=1
            """;

    private final JdbcTemplate jdbcTemplate;
    private final AutomationFlowService automationFlowService;

    public AdminListPage<AutomationFlowSummary> query(AutomationFlowListQueryRequest request) {
        long started = System.nanoTime();
        ListFilterSql filterSql = AutomationFlowColumnSpec.sql();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(FROM_JOIN);
        appendKeyword(where, params, request.keyword());
        where.append(filterSql.whereClause(request.filters(), params));

        ResultSetExtractor<Long> countExtractor = rs -> rs.next() ? rs.getLong(1) : 0L;
        long total = ListQuerySupport.requireCount(
                ListQuerySupport.query(jdbcTemplate, "SELECT COUNT(*)" + where, params, countExtractor),
                LIST_KEY);


        PageIds pageIds = loadPageIds(filterSql, where.toString(), params, request);
        List<AutomationFlowSummary> rows = toRows(pageIds.ids());
        ListQuerySupport.logIfSlow(log, LIST_KEY, request.page(), request.size(), total, started);
        return new AdminListPage<>(AutomationFlowColumnSpec.columns(), rows,
                request.page(), request.size(), total);
    }

    private PageIds loadPageIds(ListFilterSql filterSql, String where, List<Object> params,
                                AutomationFlowListQueryRequest request) {
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.size());
        pageParams.add(request.page() * request.size());
        String orderBy = filterSql.orderBy(request.sortField(), request.sortDirection());
        String sql = "SELECT f.id" + where + orderBy + " LIMIT ? OFFSET ?";
        ResultSetExtractor<PageIds> extractor = rs -> {
            List<String> ids = new ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getString("id"));
            }
            return new PageIds(ids);
        };
        return ListQuerySupport.query(jdbcTemplate, sql, pageParams, extractor);
    }

    private List<AutomationFlowSummary> toRows(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, AutomationFlowSummary> byId = automationFlowService.findFlowsByIds(ids).stream()
                .collect(Collectors.toMap(AutomationFlowSummary::getId, Function.identity()));
        List<AutomationFlowSummary> ordered = new ArrayList<>(ids.size());
        for (String id : ids) {
            AutomationFlowSummary row = byId.get(id);
            if (row == null) {
                throw new IllegalStateException("automation-flow page referenced missing flow " + id);
            }
            ordered.add(row);
        }
        return ordered;
    }


    private static void appendKeyword(StringBuilder where, List<Object> params, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String like = "%" + ListFilterSql.escapeLike(keyword.trim()) + "%";
        where.append(" AND (fv.\"displayName\" ILIKE ? OR f.id ILIKE ?")
                .append(" OR f.metadata->>'hermesFlowKey' ILIKE ? OR p.\"displayName\" ILIKE ?)");
        params.add(like);
        params.add(like);
        params.add(like);
        params.add(like);
    }


    private record PageIds(List<String> ids) {
    }
}
