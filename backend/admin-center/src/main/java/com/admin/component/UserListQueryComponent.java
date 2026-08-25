package com.admin.component;

import com.admin.dto.list.AdminListGroup;
import com.admin.dto.list.AdminListPage;
import com.admin.dto.request.UserListQueryRequest;
import com.admin.dto.response.UserInfo;
import com.admin.list.ListFilterSql;
import com.admin.list.ListQuerySupport;
import com.admin.list.UserColumnSpec;
import com.admin.repository.UserRepository;
import com.platform.security.entity.User;
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
 * User list: {@code COUNT(*)}, the page and group counts share one predicate (soft-delete,
 * toolbar keyword/status, column filters). Outer alias is {@code su}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserListQueryComponent {

    static final String LIST_KEY = "admin-users";

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final UserManagerComponent userManager;

    public AdminListPage<UserInfo> query(UserListQueryRequest request) {
        long started = System.nanoTime();
        ListFilterSql filterSql = UserColumnSpec.sql();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(
                " FROM sys_users su WHERE (su.deleted = false OR su.deleted IS NULL)");
        appendStatus(where, params, request.status());
        appendKeyword(where, params, request.keyword());
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
            throw new IllegalStateException("GROUP BY returned no groups for a non-empty user list");
        }

        PageIds pageIds = loadPageIds(filterSql, where.toString(), params, request, groupExpression);
        List<UserInfo> rows = toRows(pageIds.ids());
        applyGroupedValues(rows, request.groupBy(), pageIds.groupedValues());
        ListQuerySupport.logIfSlow(log, LIST_KEY, request.page(), request.size(), total, started);
        return new AdminListPage<>(UserColumnSpec.columns(), rows, groups,
                request.page(), request.size(), total);
    }

    private PageIds loadPageIds(ListFilterSql filterSql, String where, List<Object> params,
                                UserListQueryRequest request, String groupExpression) {
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.size());
        pageParams.add(request.page() * request.size());
        String orderBy = groupExpression == null
                ? filterSql.orderBy(request.sortField(), request.sortDirection())
                : filterSql.orderByGrouped(groupExpression, request.sortField(), request.sortDirection());
        String groupedSelect = groupExpression == null ? "" : ", " + groupExpression + " AS grouped_value";
        String sql = "SELECT su.id" + groupedSelect + where + orderBy + " LIMIT ? OFFSET ?";
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

    private List<UserInfo> toRows(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, User> byId = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<User> ordered = new ArrayList<>(ids.size());
        for (String id : ids) {
            User user = byId.get(id);
            if (user == null) {
                throw new IllegalStateException("user list page referenced missing user " + id);
            }
            ordered.add(user);
        }
        return userManager.toUserInfos(ordered);
    }

    private static void applyGroupedValues(List<UserInfo> rows, String groupBy,
                                           List<String> groupedValues) {
        if (groupBy == null || groupBy.isBlank()) {
            return;
        }
        if (rows.size() != groupedValues.size()) {
            throw new IllegalStateException("grouped values and page rows are different lengths");
        }
        for (int i = 0; i < rows.size(); i++) {
            String label = groupedValues.get(i) == null ? "" : groupedValues.get(i);
            UserInfo row = rows.get(i);
            switch (groupBy) {
                case "status" -> row.setStatus(label.isBlank()
                        ? null
                        : com.platform.security.model.UserStatus.fromString(label));
                case "entityManagerName" -> row.setEntityManagerName(label);
                case "functionManagerName" -> row.setFunctionManagerName(label);
                default -> throw new IllegalStateException("grouped field was not selected: " + groupBy);
            }
        }
    }

    private static void appendStatus(StringBuilder where, List<Object> params, String status) {
        if (status == null || status.isBlank()) {
            return;
        }
        where.append(" AND su.status = ?");
        params.add(com.platform.security.model.UserStatus.fromString(status).name());
    }

    private static void appendKeyword(StringBuilder where, List<Object> params, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String like = "%" + ListFilterSql.escapeLike(keyword.trim()) + "%";
        where.append(" AND (su.username ILIKE ? OR su.full_name ILIKE ?")
                .append(" OR su.display_name ILIKE ? OR su.email ILIKE ?)");
        params.add(like);
        params.add(like);
        params.add(like);
        params.add(like);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record PageIds(List<String> ids, List<String> groupedValues) {
    }
}
