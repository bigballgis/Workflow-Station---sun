package com.admin.bi.component;

import com.admin.bi.dto.response.DashboardAssignmentResponse;
import com.admin.bi.entity.BiDashboardAssignment;
import com.admin.bi.entity.BiDashboardRegistry;
import com.admin.bi.enums.AssignmentTargetType;
import com.admin.bi.enums.LayoutMode;
import com.admin.bi.repository.BiDashboardAssignmentRepository;
import com.admin.bi.repository.BiDashboardRegistryRepository;
import com.admin.dto.list.AdminListGroup;
import com.admin.dto.list.AdminListPage;
import com.admin.dto.request.BiAssignmentListQueryRequest;
import com.admin.list.BiAssignmentColumnSpec;
import com.admin.list.ListFilterSql;
import com.admin.list.ListQuerySupport;
import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.RoleRepository;
import com.admin.repository.UserRepository;
import com.platform.security.entity.BusinessUnit;
import com.platform.security.entity.Role;
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
 * BI Dashboard Assignment list: COUNT(*), page and group counts share toolbar
 * targetType/dashboardTitle plus column filters. Outer alias is {@code a}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BiAssignmentListQueryComponent {

    static final String LIST_KEY = "admin-bi-assignments";

    private final JdbcTemplate jdbcTemplate;
    private final BiDashboardAssignmentRepository assignmentRepository;
    private final BiDashboardRegistryRepository registryRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BusinessUnitRepository businessUnitRepository;

    public AdminListPage<DashboardAssignmentResponse> query(BiAssignmentListQueryRequest request) {
        long started = System.nanoTime();
        ListFilterSql filterSql = BiAssignmentColumnSpec.sql();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(
                " FROM bi_dashboard_assignment a"
                        + " JOIN bi_dashboard_registry d ON d.id = a.dashboard_id"
                        + " WHERE 1=1");
        appendTargetType(where, params, request.targetType());
        appendDashboardTitle(where, params, request.dashboardTitle());
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
            throw new IllegalStateException("GROUP BY returned no groups for a non-empty bi-assignment list");
        }

        PageIds pageIds = loadPageIds(filterSql, where.toString(), params, request, groupExpression);
        List<DashboardAssignmentResponse> rows = toRows(pageIds.ids());
        applyGroupedValues(rows, request.groupBy(), pageIds.groupedValues());
        ListQuerySupport.logIfSlow(log, LIST_KEY, request.page(), request.size(), total, started);
        return new AdminListPage<>(BiAssignmentColumnSpec.columns(), rows, groups,
                request.page(), request.size(), total);
    }

    private PageIds loadPageIds(ListFilterSql filterSql, String where, List<Object> params,
                                BiAssignmentListQueryRequest request, String groupExpression) {
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.size());
        pageParams.add(request.page() * request.size());
        String orderBy = groupExpression == null
                ? filterSql.orderBy(request.sortField(), request.sortDirection())
                : filterSql.orderByGrouped(groupExpression, request.sortField(), request.sortDirection());
        String groupedSelect = groupExpression == null ? "" : ", " + groupExpression + " AS grouped_value";
        String sql = "SELECT a.id" + groupedSelect + where + orderBy + " LIMIT ? OFFSET ?";
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

    private List<DashboardAssignmentResponse> toRows(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, BiDashboardAssignment> byId = assignmentRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(BiDashboardAssignment::getId, Function.identity()));
        List<BiDashboardAssignment> ordered = new ArrayList<>(ids.size());
        for (String id : ids) {
            BiDashboardAssignment entity = byId.get(id);
            if (entity == null) {
                throw new IllegalStateException("bi-assignment page referenced missing assignment " + id);
            }
            ordered.add(entity);
        }
        TargetLookup lookup = loadTargets(ordered);
        List<DashboardAssignmentResponse> rows = new ArrayList<>(ordered.size());
        for (BiDashboardAssignment entity : ordered) {
            rows.add(toResponse(entity, lookup));
        }
        return rows;
    }

    private TargetLookup loadTargets(List<BiDashboardAssignment> rows) {
        List<String> dashboardIds = new ArrayList<>();
        List<String> userIds = new ArrayList<>();
        List<String> roleIds = new ArrayList<>();
        List<String> buIds = new ArrayList<>();
        for (BiDashboardAssignment row : rows) {
            dashboardIds.add(row.getDashboardId());
            switch (row.getTargetType()) {
                case USER -> userIds.add(row.getTargetId());
                case ROLE -> roleIds.add(row.getTargetId());
                case BUSINESS_UNIT -> buIds.add(row.getTargetId());
            }
        }
        Map<String, BiDashboardRegistry> dashboards = dashboardIds.isEmpty()
                ? Map.of()
                : registryRepository.findAllById(dashboardIds).stream()
                .collect(Collectors.toMap(BiDashboardRegistry::getId, Function.identity()));
        Map<String, User> users = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<String, Role> roles = roleIds.isEmpty()
                ? Map.of()
                : roleRepository.findAllById(roleIds).stream()
                .collect(Collectors.toMap(Role::getId, Function.identity()));
        Map<String, BusinessUnit> units = buIds.isEmpty()
                ? Map.of()
                : businessUnitRepository.findAllById(buIds).stream()
                .collect(Collectors.toMap(BusinessUnit::getId, Function.identity()));
        return new TargetLookup(dashboards, users, roles, units);
    }

    private static DashboardAssignmentResponse toResponse(BiDashboardAssignment entity, TargetLookup lookup) {
        BiDashboardRegistry dashboard = lookup.dashboards.get(entity.getDashboardId());
        if (dashboard == null) {
            throw new IllegalStateException(
                    "bi-assignment " + entity.getId() + " referenced missing dashboard " + entity.getDashboardId());
        }
        return DashboardAssignmentResponse.builder()
                .id(entity.getId())
                .dashboardId(entity.getDashboardId())
                .dashboardTitle(dashboard.getDashboardTitle())
                .targetType(entity.getTargetType())
                .targetId(entity.getTargetId())
                .targetName(lookup.nameOf(entity.getTargetType(), entity.getTargetId()))
                .layoutMode(entity.getLayoutMode())
                .displayOrder(entity.getDisplayOrder())
                .isDefault(entity.getIsDefault())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private static void applyGroupedValues(List<DashboardAssignmentResponse> rows, String groupBy,
                                           List<String> groupedValues) {
        if (groupBy == null || groupBy.isBlank()) {
            return;
        }
        if (rows.size() != groupedValues.size()) {
            throw new IllegalStateException("grouped values and page rows are different lengths");
        }
        for (int i = 0; i < rows.size(); i++) {
            String label = groupedValues.get(i) == null ? "" : groupedValues.get(i);
            DashboardAssignmentResponse row = rows.get(i);
            switch (groupBy) {
                case "targetType" -> row.setTargetType(
                        label.isBlank() ? null : AssignmentTargetType.valueOf(label));
                case "layoutMode" -> row.setLayoutMode(
                        label.isBlank() ? null : LayoutMode.valueOf(label));
                case "isDefault" -> row.setIsDefault("true".equalsIgnoreCase(label));
                default -> throw new IllegalStateException("grouped field was not selected: " + groupBy);
            }
        }
    }

    private static void appendTargetType(StringBuilder where, List<Object> params, String targetType) {
        if (targetType == null || targetType.isBlank()) {
            return;
        }
        where.append(" AND a.target_type = ?");
        params.add(AssignmentTargetType.valueOf(targetType.trim()).name());
    }

    private static void appendDashboardTitle(StringBuilder where, List<Object> params, String title) {
        if (title == null || title.isBlank()) {
            return;
        }
        where.append(" AND d.dashboard_title ILIKE ?");
        params.add("%" + ListFilterSql.escapeLike(title.trim()) + "%");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record PageIds(List<String> ids, List<String> groupedValues) {
    }

    private static final class TargetLookup {
        private final Map<String, BiDashboardRegistry> dashboards;
        private final Map<String, User> users;
        private final Map<String, Role> roles;
        private final Map<String, BusinessUnit> units;

        private TargetLookup(Map<String, BiDashboardRegistry> dashboards,
                             Map<String, User> users,
                             Map<String, Role> roles,
                             Map<String, BusinessUnit> units) {
            this.dashboards = dashboards;
            this.users = users;
            this.roles = roles;
            this.units = units;
        }

        private String nameOf(AssignmentTargetType type, String targetId) {
            return switch (type) {
                case USER -> {
                    User user = users.get(targetId);
                    if (user == null) {
                        yield null;
                    }
                    yield user.getDisplayName() != null ? user.getDisplayName() : user.getUsername();
                }
                case ROLE -> {
                    Role role = roles.get(targetId);
                    yield role == null ? null : role.getName();
                }
                case BUSINESS_UNIT -> {
                    BusinessUnit unit = units.get(targetId);
                    yield unit == null ? null : unit.getName();
                }
            };
        }
    }
}
