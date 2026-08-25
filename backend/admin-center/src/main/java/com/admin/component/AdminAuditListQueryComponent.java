package com.admin.component;

import com.admin.audit.AuditActorResolver;
import com.admin.dto.list.AdminListGroup;
import com.admin.dto.list.AdminListPage;
import com.admin.dto.request.AdminAuditListQueryRequest;
import com.admin.entity.AuditLog;
import com.admin.list.AdminAuditColumnSpec;
import com.admin.list.ListFilterSql;
import com.admin.list.ListQuerySupport;
import com.admin.repository.AuditLogRepository;
import com.admin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Admin Center audit list: COUNT(*), page and group counts share the toolbar predicate
 * plus column filters. Outer alias is {@code al}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuditListQueryComponent {

    static final String LIST_KEY = "admin-audit";

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AdminListPage<AuditLog> query(AdminAuditListQueryRequest request) {
        long started = System.nanoTime();
        ListFilterSql filterSql = AdminAuditColumnSpec.sql();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" FROM admin_audit_logs al WHERE 1=1");
        appendToolbar(where, params, request);
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
            throw new IllegalStateException("GROUP BY returned no groups for a non-empty admin-audit result");
        }

        PageIds pageIds = loadPageIds(filterSql, where.toString(), params, request, groupExpression);
        List<AuditLog> rows = toRows(pageIds.ids());
        ListQuerySupport.logIfSlow(log, LIST_KEY, request.page(), request.size(), total, started);
        return new AdminListPage<>(AdminAuditColumnSpec.columns(), rows, groups,
                request.page(), request.size(), total);
    }

    private PageIds loadPageIds(ListFilterSql filterSql, String where, List<Object> params,
                                AdminAuditListQueryRequest request, String groupExpression) {
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.size());
        pageParams.add(request.page() * request.size());
        String orderBy = groupExpression == null
                ? filterSql.orderBy(request.sortField(), request.sortDirection())
                : filterSql.orderByGrouped(groupExpression, request.sortField(), request.sortDirection());
        String groupedSelect = groupExpression == null ? "" : ", " + groupExpression + " AS grouped_value";
        String sql = "SELECT al.id" + groupedSelect + where + orderBy + " LIMIT ? OFFSET ?";
        ResultSetExtractor<PageIds> extractor = rs -> {
            List<String> ids = new ArrayList<>();
            List<String> grouped = new ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getString("id"));
                grouped.add(groupExpression == null ? null : rs.getString("grouped_value"));
            }
            return new PageIds(ids, grouped);
        };
        return ListQuerySupport.query(jdbcTemplate, sql, pageParams, extractor);
    }

    private List<AuditLog> toRows(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, AuditLog> byId = auditLogRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(AuditLog::getId, Function.identity()));
        List<AuditLog> ordered = new ArrayList<>(ids.size());
        for (String id : ids) {
            AuditLog log = byId.get(id);
            if (log == null) {
                throw new IllegalStateException("admin-audit page referenced missing log " + id);
            }
            ordered.add(log);
        }
        AuditActorResolver.enrichOperatorUsernames(ordered, userRepository);
        return ordered;
    }

    private static void appendToolbar(StringBuilder where, List<Object> params,
                                      AdminAuditListQueryRequest request) {
        if (notBlank(request.action())) {
            where.append(" AND al.action = ?");
            params.add(request.action());
        }
        if (notBlank(request.resourceType())) {
            where.append(" AND al.resource_type = ?");
            params.add(request.resourceType());
        }
        if (notBlank(request.username())) {
            where.append(" AND al.user_name = ?");
            params.add(request.username());
        }
        if (notBlank(request.result())) {
            where.append(" AND al.success = ?");
            params.add("SUCCESS".equalsIgnoreCase(request.result()));
        }
        if (notBlank(request.ipAddress())) {
            where.append(" AND al.ip_address = ?");
            params.add(request.ipAddress().trim());
        }
        if (notBlank(request.resourceId())) {
            where.append(" AND al.resource_id = ?");
            params.add(request.resourceId());
        }
        if (notBlank(request.startTime())) {
            where.append(" AND al.timestamp >= ?");
            params.add(Timestamp.from(Instant.parse(request.startTime())));
        }
        if (notBlank(request.endTime())) {
            where.append(" AND al.timestamp <= ?");
            params.add(Timestamp.from(Instant.parse(request.endTime())));
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record PageIds(List<String> ids, List<String> groupedValues) {
    }
}
