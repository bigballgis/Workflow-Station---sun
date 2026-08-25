package com.admin.bi.component;

import com.admin.bi.dto.response.RbacMappingResponse;
import com.admin.bi.dto.response.SupersetRoleResponse;
import com.admin.bi.entity.BiRbacMapping;
import com.admin.bi.entity.BiSupersetRole;
import com.admin.bi.repository.BiRbacMappingRepository;
import com.admin.bi.repository.BiSupersetRoleRepository;
import com.admin.dto.list.AdminListGroup;
import com.admin.dto.list.AdminListPage;
import com.admin.dto.request.BiRbacListQueryRequest;
import com.admin.list.BiRbacColumnSpec;
import com.admin.list.ListFilterSql;
import com.admin.list.ListQuerySupport;
import com.admin.repository.RoleRepository;
import com.platform.security.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * BI RBAC mapping list: one row per mapped active sys_role. COUNT(*), page and
 * group counts share toolbar roleName/roleType plus column filters.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BiRbacListQueryComponent {

    static final String LIST_KEY = "admin-bi-rbac";

    private final JdbcTemplate jdbcTemplate;
    private final RoleRepository roleRepository;
    private final BiRbacMappingRepository mappingRepository;
    private final BiSupersetRoleRepository supersetRoleRepository;

    public AdminListPage<RbacMappingResponse> query(BiRbacListQueryRequest request) {
        long started = System.nanoTime();
        ListFilterSql filterSql = BiRbacColumnSpec.sql();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(
                " FROM sys_roles r WHERE r.status = 'ACTIVE'"
                        + " AND EXISTS (SELECT 1 FROM bi_rbac_mapping m WHERE m.sys_role_id = r.id)");
        appendRoleName(where, params, request.roleName());
        appendRoleType(where, params, request.roleType());
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
            throw new IllegalStateException("GROUP BY returned no groups for a non-empty bi-rbac list");
        }

        PageIds pageIds = loadPageIds(filterSql, where.toString(), params, request, groupExpression);
        List<RbacMappingResponse> rows = toRows(pageIds.ids());
        applyGroupedValues(rows, request.groupBy(), pageIds.groupedValues());
        ListQuerySupport.logIfSlow(log, LIST_KEY, request.page(), request.size(), total, started);
        return new AdminListPage<>(BiRbacColumnSpec.columns(), rows, groups,
                request.page(), request.size(), total);
    }

    private PageIds loadPageIds(ListFilterSql filterSql, String where, List<Object> params,
                                BiRbacListQueryRequest request, String groupExpression) {
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

    private List<RbacMappingResponse> toRows(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, Role> byId = roleRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Role::getId, Function.identity()));
        Map<String, List<BiRbacMapping>> mappingsByRole = mappingRepository.findBySysRoleIdIn(ids).stream()
                .collect(Collectors.groupingBy(BiRbacMapping::getSysRoleId));
        List<Integer> supersetIds = mappingsByRole.values().stream()
                .flatMap(List::stream)
                .map(BiRbacMapping::getSupersetRoleId)
                .distinct()
                .toList();
        Map<Integer, BiSupersetRole> supersetById = supersetIds.isEmpty()
                ? Map.of()
                : supersetRoleRepository.findBySupersetRoleIdIn(supersetIds).stream()
                .collect(Collectors.toMap(BiSupersetRole::getSupersetRoleId, Function.identity()));

        List<RbacMappingResponse> rows = new ArrayList<>(ids.size());
        for (String id : ids) {
            Role role = byId.get(id);
            if (role == null) {
                throw new IllegalStateException("bi-rbac page referenced missing role " + id);
            }
            rows.add(toResponse(role, mappingsByRole.getOrDefault(id, List.of()), supersetById));
        }
        return rows;
    }

    private static RbacMappingResponse toResponse(Role role, List<BiRbacMapping> mappings,
                                                  Map<Integer, BiSupersetRole> supersetById) {
        List<SupersetRoleResponse> supersetRoles = mappings.stream()
                .map(m -> supersetById.get(m.getSupersetRoleId()))
                .filter(Objects::nonNull)
                .map(BiRbacListQueryComponent::toSupersetRoleResponse)
                .collect(Collectors.toList());
        LocalDateTime lastUpdated = mappings.stream()
                .map(BiRbacMapping::getCreatedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        return RbacMappingResponse.builder()
                .sysRoleId(role.getId())
                .sysRoleName(role.getName())
                .sysRoleCode(role.getCode())
                .sysRoleType(role.getType())
                .supersetRoles(supersetRoles)
                .lastUpdatedAt(lastUpdated)
                .build();
    }

    private static SupersetRoleResponse toSupersetRoleResponse(BiSupersetRole entity) {
        return SupersetRoleResponse.builder()
                .id(entity.getId())
                .supersetRoleId(entity.getSupersetRoleId())
                .name(entity.getName())
                .status(entity.getStatus())
                .lastSyncedAt(entity.getLastSyncedAt())
                .build();
    }

    private static void applyGroupedValues(List<RbacMappingResponse> rows, String groupBy,
                                           List<String> groupedValues) {
        if (groupBy == null || groupBy.isBlank()) {
            return;
        }
        if (rows.size() != groupedValues.size()) {
            throw new IllegalStateException("grouped values and page rows are different lengths");
        }
        for (int i = 0; i < rows.size(); i++) {
            String label = groupedValues.get(i) == null ? "" : groupedValues.get(i);
            if (!"sysRoleType".equals(groupBy)) {
                throw new IllegalStateException("grouped field was not selected: " + groupBy);
            }
            rows.get(i).setSysRoleType(label);
        }
    }

    private static void appendRoleName(StringBuilder where, List<Object> params, String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return;
        }
        where.append(" AND r.name ILIKE ?");
        params.add("%" + ListFilterSql.escapeLike(roleName.trim()) + "%");
    }

    private static void appendRoleType(StringBuilder where, List<Object> params, String roleType) {
        if (roleType == null || roleType.isBlank()) {
            return;
        }
        where.append(" AND r.type = ?");
        params.add(roleType.trim());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record PageIds(List<String> ids, List<String> groupedValues) {
    }
}
