package com.admin.component;

import com.admin.dto.list.AdminListPage;
import com.admin.dto.request.AutomationFlowRunListQueryRequest;
import com.admin.dto.response.AutomationFlowRunSummary;
import com.admin.list.AutomationFlowRunColumnSpec;
import com.admin.list.ListQuerySupport;
import com.admin.service.AutomationFlowRunService;
import com.admin.servicetask.ApWorkspaceSql;
import com.platform.common.list.ListFilterSql;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Automation Runs list: COUNT(*) and the page share toolbar keyword plus column filters.
 *
 * <p><b>可见集与 AP 自己的 Runs 页一致</b>：只算 {@code environment = 'PRODUCTION'} 且未归档
 * 的运行。AP 的 {@code GET /v1/flow-runs} 硬编码了这两条（builder 里的试跑是 TESTING），
 * 这页是把 DW 的 Run History 搬过来，可见集不能顺手变宽。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationFlowRunListQueryComponent {

    static final String LIST_KEY = "admin-automation-runs";

    private static final String FROM_HEAD = """
             FROM flow_run r
             JOIN flow f ON f.id = r."flowId"
             JOIN flow_version fv ON fv.id = r."flowVersionId"
             JOIN project p ON p.id = r."projectId"
            """;

    private static final String FROM_TAIL = """
             WHERE r.environment = 'PRODUCTION' AND r."archivedAt" IS NULL
            """;

    private final JdbcTemplate jdbcTemplate;
    private final AutomationFlowRunService automationFlowRunService;
    private final ApWorkspaceSql workspaceSql;

    public AdminListPage<AutomationFlowRunSummary> query(AutomationFlowRunListQueryRequest request) {
        long started = System.nanoTime();
        ListFilterSql filterSql = AutomationFlowRunColumnSpec.sql();
        // workspace join 的参数排在最前——join 在 where 之前，顺序错了就会串位。
        List<Object> params = new ArrayList<>(workspaceSql.joinParams());
        StringBuilder where = new StringBuilder(FROM_HEAD)
                .append(workspaceSql.joinClause())
                .append(FROM_TAIL);
        appendKeyword(where, params, request.keyword());
        where.append(filterSql.whereClause(request.filters(), params));

        ResultSetExtractor<Long> countExtractor = rs -> rs.next() ? rs.getLong(1) : 0L;
        long total = ListQuerySupport.requireCount(
                ListQuerySupport.query(jdbcTemplate, "SELECT COUNT(*)" + where, params, countExtractor),
                LIST_KEY);

        List<String> pageIds = loadPageIds(filterSql, where.toString(), params, request);
        List<AutomationFlowRunSummary> rows = toRows(pageIds);
        ListQuerySupport.logIfSlow(log, LIST_KEY, request.page(), request.size(), total, started);
        return new AdminListPage<>(AutomationFlowRunColumnSpec.columns(), rows,
                request.page(), request.size(), total);
    }

    private List<String> loadPageIds(ListFilterSql filterSql, String where, List<Object> params,
                                     AutomationFlowRunListQueryRequest request) {
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.size());
        pageParams.add(request.page() * request.size());
        String orderBy = filterSql.orderBy(request.sortField(), request.sortDirection());
        String sql = "SELECT r.id" + where + orderBy + " LIMIT ? OFFSET ?";
        ResultSetExtractor<List<String>> extractor = rs -> {
            List<String> ids = new ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getString("id"));
            }
            return ids;
        };
        return ListQuerySupport.query(jdbcTemplate, sql, pageParams, extractor);
    }

    private List<AutomationFlowRunSummary> toRows(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, AutomationFlowRunSummary> byId = automationFlowRunService.findRunsByIds(ids).stream()
                .collect(Collectors.toMap(AutomationFlowRunSummary::getId, Function.identity()));
        List<AutomationFlowRunSummary> ordered = new ArrayList<>(ids.size());
        for (String id : ids) {
            AutomationFlowRunSummary row = byId.get(id);
            if (row == null) {
                throw new IllegalStateException("automation-run page referenced missing run " + id);
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
        // workspace 名也可被关键字命中（列表按 workspace 展示，运维搜的就是它）
        where.append(" AND (fv.\"displayName\" ILIKE ? OR r.id ILIKE ? OR f.id ILIKE ?")
                .append(" OR f.metadata->>'hermesFlowKey' ILIKE ? OR ")
                .append(ApWorkspaceSql.LABEL_SQL).append(" ILIKE ?)");
        params.add(like);
        params.add(like);
        params.add(like);
        params.add(like);
        params.add(like);
    }
}
