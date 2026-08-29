package com.portal.component;

import com.portal.dto.MyApplicationQueryRequest;
import com.portal.dto.PortalListPage;
import com.portal.dto.ProcessInstanceInfo;
import com.portal.util.AuditApplicationColumnSpec;

import com.portal.util.ListQuerySupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import com.platform.common.list.ListFilterSql;

import java.util.ArrayList;
import java.util.List;

/**
 * Portal Audit list: every request of one function unit. Callers must already
 * have established the audit grant — this answers "what is in it", not "who may look".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditApplicationListQueryComponent {

    static final String LIST_KEY = "fu-applications";

    private final JdbcTemplate jdbcTemplate;
    private final MyApplicationListQueryComponent myApplicationListQueryComponent;

    public PortalListPage<ProcessInstanceInfo> query(String functionUnitCode,
                                                     MyApplicationQueryRequest request) {
        if (functionUnitCode == null || functionUnitCode.isBlank()) {
            throw new IllegalArgumentException("functionUnitCode is required for fu-applications");
        }
        long started = System.nanoTime();
        ListFilterSql filterSql = AuditApplicationColumnSpec.sql();
        List<Object> params = new ArrayList<>();
        params.add(functionUnitCode);
        StringBuilder where = new StringBuilder(
                " FROM up_process_instance pi WHERE pi.function_unit_code = ?");
        appendStatus(where, params, request.status());
        where.append(AuditApplicationColumnSpec.textSearchClause(request.keyword(), params));
        where.append(filterSql.whereClause(request.filters(), params));

        long total = ListQuerySupport.requireCount(
                ListQuerySupport.query(jdbcTemplate, "SELECT COUNT(*)" + where, params,
                        rs -> rs.next() ? rs.getLong(1) : 0L),
                LIST_KEY);

        List<String> ids = loadPageIds(filterSql, where.toString(), params, request);
        long afterSql = System.nanoTime();
        List<ProcessInstanceInfo> rows = myApplicationListQueryComponent.toListRows(ids);
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        ListQuerySupport.logIfSlow(log, LIST_KEY, request.page(), request.size(), total, started);
        ListQuerySupport.logIfOverSla(log, LIST_KEY, request.page(), request.size(), total, elapsedMs,
                (afterSql - started) / 1_000_000L, elapsedMs - (afterSql - started) / 1_000_000L);
        return new PortalListPage<>(AuditApplicationColumnSpec.columns(), rows,
                request.page(), request.size(), total);
    }

    private List<String> loadPageIds(ListFilterSql filterSql, String where, List<Object> params,
                                     MyApplicationQueryRequest request) {
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.size());
        pageParams.add(request.page() * request.size());
        String orderBy = filterSql.orderBy(request.sortField(), request.sortDirection());
        String sql = "SELECT pi.id" + where + orderBy + " LIMIT ? OFFSET ?";
        return ListQuerySupport.query(jdbcTemplate, sql, pageParams, rs -> {
            List<String> ids = new ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getString("id"));
            }
            return ids;
        });
    }

    private static void appendStatus(StringBuilder where, List<Object> params, String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return;
        }
        where.append(" AND pi.status = ?");
        params.add(status);
    }
}
