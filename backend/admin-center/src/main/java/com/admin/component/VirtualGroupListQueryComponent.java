package com.admin.component;

import com.admin.dto.list.AdminListGroup;
import com.admin.dto.list.AdminListPage;
import com.admin.dto.request.VirtualGroupListQueryRequest;
import com.admin.dto.response.VirtualGroupInfo;
import com.admin.enums.VirtualGroupType;
import com.admin.list.ListFilterSql;
import com.admin.list.ListQuerySupport;
import com.admin.list.VirtualGroupColumnSpec;
import com.admin.repository.RoleRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.repository.VirtualGroupRoleRepository;
import com.platform.security.entity.Role;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.entity.VirtualGroupRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Virtual Group list: COUNT(*) and the page share the tab type, toolbar keyword,
 * and column filters. Member counts and role bindings are batched on the page.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VirtualGroupListQueryComponent {

    static final String LIST_KEY = "admin-virtual-groups";

    private static final String FROM_JOIN = """
             FROM sys_virtual_groups vg
             LEFT JOIN sys_virtual_group_roles vgr ON vgr.virtual_group_id = vg.id
             LEFT JOIN sys_roles r ON r.id = vgr.role_id
             WHERE 1=1
            """;

    private final JdbcTemplate jdbcTemplate;
    private final VirtualGroupRepository virtualGroupRepository;
    private final VirtualGroupRoleRepository virtualGroupRoleRepository;
    private final RoleRepository roleRepository;

    public AdminListPage<VirtualGroupInfo> query(VirtualGroupListQueryRequest request) {
        long started = System.nanoTime();
        ListFilterSql filterSql = VirtualGroupColumnSpec.sql();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(FROM_JOIN);
        appendType(where, params, request.type());
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
            throw new IllegalStateException("GROUP BY returned no groups for a non-empty virtual-group list");
        }

        PageIds pageIds = loadPageIds(filterSql, where.toString(), params, request, groupExpression);
        List<VirtualGroupInfo> rows = toRows(pageIds.ids());
        applyGroupedValues(rows, request.groupBy(), pageIds.groupedValues());
        ListQuerySupport.logIfSlow(log, LIST_KEY, request.page(), request.size(), total, started);
        return new AdminListPage<>(VirtualGroupColumnSpec.columns(), rows, groups,
                request.page(), request.size(), total);
    }

    private PageIds loadPageIds(ListFilterSql filterSql, String where, List<Object> params,
                                VirtualGroupListQueryRequest request, String groupExpression) {
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.size());
        pageParams.add(request.page() * request.size());
        String orderBy = groupExpression == null
                ? filterSql.orderBy(request.sortField(), request.sortDirection())
                : filterSql.orderByGrouped(groupExpression, request.sortField(), request.sortDirection());
        String groupedSelect = groupExpression == null ? "" : ", " + groupExpression + " AS grouped_value";
        String sql = "SELECT vg.id" + groupedSelect + where + orderBy + " LIMIT ? OFFSET ?";
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

    private List<VirtualGroupInfo> toRows(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, VirtualGroup> byId = virtualGroupRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(VirtualGroup::getId, Function.identity()));
        Map<String, Integer> memberCounts = loadMemberCounts(ids);
        Map<String, Role> roleBindings = loadRoleBindings(ids);
        List<VirtualGroupInfo> ordered = new ArrayList<>(ids.size());
        for (String id : ids) {
            VirtualGroup entity = byId.get(id);
            if (entity == null) {
                throw new IllegalStateException("virtual-group page referenced missing group " + id);
            }
            VirtualGroupInfo row = VirtualGroupInfo.fromEntity(entity);
            row.setMemberCount(memberCounts.getOrDefault(id, 0));
            Role bound = roleBindings.get(id);
            if (bound != null) {
                row.setBoundRoleId(bound.getId());
                row.setBoundRoleName(bound.getName());
                row.setBoundRoleCode(bound.getCode());
                row.setBoundRoleType(bound.getType());
            }
            ordered.add(row);
        }
        return ordered;
    }

    private Map<String, Integer> loadMemberCounts(List<String> ids) {
        String placeholders = String.join(",", ids.stream().map(n -> "?").toList());
        String sql = "SELECT group_id, COUNT(*) AS member_count FROM sys_virtual_group_members"
                + " WHERE group_id IN (" + placeholders + ") GROUP BY group_id";
        ResultSetExtractor<Map<String, Integer>> extractor = rs -> {
            Map<String, Integer> counts = new HashMap<>();
            while (rs.next()) {
                counts.put(rs.getString("group_id"), rs.getInt("member_count"));
            }
            return counts;
        };
        return ListQuerySupport.query(jdbcTemplate, sql, new ArrayList<>(ids), extractor);
    }

    private Map<String, Role> loadRoleBindings(List<String> ids) {
        List<VirtualGroupRole> bindings = virtualGroupRoleRepository.findByVirtualGroupIdIn(ids);
        if (bindings.isEmpty()) {
            return Map.of();
        }
        List<String> roleIds = bindings.stream().map(VirtualGroupRole::getRoleId).distinct().toList();
        Map<String, Role> roleMap = roleRepository.findAllById(roleIds).stream()
                .collect(Collectors.toMap(Role::getId, Function.identity()));
        Map<String, Role> byGroup = new HashMap<>();
        for (VirtualGroupRole binding : bindings) {
            Role role = roleMap.get(binding.getRoleId());
            if (role != null) {
                byGroup.putIfAbsent(binding.getVirtualGroupId(), role);
            }
        }
        return byGroup;
    }

    private static void applyGroupedValues(List<VirtualGroupInfo> rows, String groupBy,
                                           List<String> groupedValues) {
        if (groupBy == null || groupBy.isBlank()) {
            return;
        }
        if (rows.size() != groupedValues.size()) {
            throw new IllegalStateException("grouped values and page rows are different lengths");
        }
        for (int i = 0; i < rows.size(); i++) {
            String label = groupedValues.get(i) == null ? "" : groupedValues.get(i);
            VirtualGroupInfo row = rows.get(i);
            switch (groupBy) {
                case "type" -> row.setType(label.isBlank() ? null : VirtualGroupType.valueOf(label));
                case "boundRoleType" -> row.setBoundRoleType(label);
                case "status" -> row.setStatus(label);
                default -> throw new IllegalStateException("grouped field was not selected: " + groupBy);
            }
        }
    }

    private static void appendType(StringBuilder where, List<Object> params, String type) {
        if (type == null || type.isBlank()) {
            return;
        }
        VirtualGroupType.valueOf(type.trim());
        where.append(" AND vg.type = ?");
        params.add(type.trim());
    }

    private static void appendKeyword(StringBuilder where, List<Object> params, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String like = "%" + ListFilterSql.escapeLike(keyword.trim()) + "%";
        where.append(" AND (vg.name ILIKE ? OR vg.code ILIKE ? OR vg.ad_group ILIKE ?")
                .append(" OR r.name ILIKE ? OR vg.display_name ILIKE ?)");
        params.add(like);
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
