package com.admin.component;

import com.admin.dto.list.AdminListGroup;
import com.admin.dto.list.AdminListPage;
import com.admin.dto.request.RoleListQueryRequest;
import com.admin.dto.response.RoleListItem;
import com.admin.enums.RoleType;
import com.admin.list.ListFilterSql;
import com.admin.list.ListQuerySupport;
import com.admin.list.RoleColumnSpec;
import com.admin.repository.RoleRepository;
import com.platform.security.entity.Role;
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
 * Role list: COUNT(*) and the page share the SYSTEM/CUSTOM tab predicate, optional
 * toolbar type, and column filters. Grouping writes to {@link RoleListItem} only.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleListQueryComponent {

    static final String LIST_KEY = "admin-roles";

    private static final String FROM = " FROM sys_roles r WHERE 1=1 ";

    private final JdbcTemplate jdbcTemplate;
    private final RoleRepository roleRepository;

    public AdminListPage<RoleListItem> query(RoleListQueryRequest request) {
        long started = System.nanoTime();
        ListFilterSql filterSql = RoleColumnSpec.sql();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(FROM);
        appendTab(where, params, request.tab());
        appendType(where, params, request.type());
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
            throw new IllegalStateException("GROUP BY returned no groups for a non-empty role list");
        }

        PageIds pageIds = loadPageIds(filterSql, where.toString(), params, request, groupExpression);
        List<RoleListItem> rows = toRows(pageIds.ids());
        applyGroupedValues(rows, request.groupBy(), pageIds.groupedValues());
        ListQuerySupport.logIfSlow(log, LIST_KEY, request.page(), request.size(), total, started);
        return new AdminListPage<>(RoleColumnSpec.columns(), rows, groups,
                request.page(), request.size(), total);
    }

    private PageIds loadPageIds(ListFilterSql filterSql, String where, List<Object> params,
                                RoleListQueryRequest request, String groupExpression) {
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.size());
        pageParams.add(request.page() * request.size());
        String orderBy = groupExpression == null
                ? filterSql.orderBy(request.sortField(), request.sortDirection())
                : filterSql.orderByGrouped(groupExpression, request.sortField(), request.sortDirection());
        String groupedSelect = groupExpression == null ? "" : ", " + groupExpression + " AS grouped_value";
        String sql = "SELECT r.id" + groupedSelect + where + orderBy + " LIMIT ? OFFSET ?";
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

    private List<RoleListItem> toRows(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, Role> byId = roleRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Role::getId, Function.identity()));
        List<RoleListItem> ordered = new ArrayList<>(ids.size());
        for (String id : ids) {
            Role entity = byId.get(id);
            if (entity == null) {
                throw new IllegalStateException("role page referenced missing role " + id);
            }
            ordered.add(RoleListItem.fromEntity(entity));
        }
        return ordered;
    }

    private static void applyGroupedValues(List<RoleListItem> rows, String groupBy,
                                           List<String> groupedValues) {
        if (groupBy == null || groupBy.isBlank()) {
            return;
        }
        if (rows.size() != groupedValues.size()) {
            throw new IllegalStateException("grouped values and page rows are different lengths");
        }
        for (int i = 0; i < rows.size(); i++) {
            String label = groupedValues.get(i) == null ? "" : groupedValues.get(i);
            RoleListItem row = rows.get(i);
            switch (groupBy) {
                case "type" -> row.setType(label);
                case "status" -> row.setStatus(label);
                case "isSystem" -> row.setIsSystem("true".equalsIgnoreCase(label));
                default -> throw new IllegalStateException("grouped field was not selected: " + groupBy);
            }
        }
    }

    static void appendTab(StringBuilder where, List<Object> params, String tab) {
        if (tab == null || tab.isBlank()) {
            throw new IllegalArgumentException("tab is required (SYSTEM or CUSTOM)");
        }
        String normalized = tab.trim();
        if ("SYSTEM".equals(normalized)) {
            where.append(" AND r.is_system = TRUE AND r.code IN (");
            where.append(RoleColumnSpec.SYSTEM_ROLE_LIST_CODES.stream().map(c -> "?").collect(Collectors.joining(",")));
            where.append(")");
            params.addAll(RoleColumnSpec.SYSTEM_ROLE_LIST_CODES);
            return;
        }
        if ("CUSTOM".equals(normalized)) {
            where.append(" AND r.is_system IS NOT TRUE AND r.type = ?");
            params.add("BU_BOUNDED");
            return;
        }
        throw new IllegalArgumentException("tab must be SYSTEM or CUSTOM");
    }

    private static void appendType(StringBuilder where, List<Object> params, String type) {
        if (type == null || type.isBlank()) {
            return;
        }
        RoleType.valueOf(type.trim());
        where.append(" AND r.type = ?");
        params.add(type.trim());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record PageIds(List<String> ids, List<String> groupedValues) {
    }
}
