package com.admin.bi.component;

import com.admin.bi.dto.response.DashboardRegistryResponse;
import com.admin.bi.entity.BiDashboardRegistry;
import com.admin.bi.enums.DashboardStatus;
import com.admin.bi.repository.BiDashboardRegistryRepository;
import com.admin.dto.list.AdminListGroup;
import com.admin.dto.list.AdminListPage;
import com.admin.dto.request.BiDashboardListQueryRequest;
import com.admin.list.BiDashboardColumnSpec;
import com.admin.list.ListFilterSql;
import com.admin.list.ListQuerySupport;
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
 * BI Dashboard Registry list: COUNT(*), page and group counts share toolbar
 * title/tags/status plus column filters. Outer alias is {@code d}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BiDashboardListQueryComponent {

    static final String LIST_KEY = "admin-bi-dashboards";

    private final JdbcTemplate jdbcTemplate;
    private final BiDashboardRegistryRepository registryRepository;

    public AdminListPage<DashboardRegistryResponse> query(BiDashboardListQueryRequest request) {
        long started = System.nanoTime();
        ListFilterSql filterSql = BiDashboardColumnSpec.sql();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" FROM bi_dashboard_registry d WHERE 1=1");
        appendTitle(where, params, request.title());
        appendTags(where, params, request.tags());
        appendStatus(where, params, request.status());
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
            throw new IllegalStateException("GROUP BY returned no groups for a non-empty bi-dashboard list");
        }

        PageIds pageIds = loadPageIds(filterSql, where.toString(), params, request, groupExpression);
        List<DashboardRegistryResponse> rows = toRows(pageIds.ids());
        applyGroupedValues(rows, request.groupBy(), pageIds.groupedValues());
        ListQuerySupport.logIfSlow(log, LIST_KEY, request.page(), request.size(), total, started);
        return new AdminListPage<>(BiDashboardColumnSpec.columns(), rows, groups,
                request.page(), request.size(), total);
    }

    private PageIds loadPageIds(ListFilterSql filterSql, String where, List<Object> params,
                                BiDashboardListQueryRequest request, String groupExpression) {
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.size());
        pageParams.add(request.page() * request.size());
        String orderBy = groupExpression == null
                ? filterSql.orderBy(request.sortField(), request.sortDirection())
                : filterSql.orderByGrouped(groupExpression, request.sortField(), request.sortDirection());
        String groupedSelect = groupExpression == null ? "" : ", " + groupExpression + " AS grouped_value";
        String sql = "SELECT d.id" + groupedSelect + where + orderBy + " LIMIT ? OFFSET ?";
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

    private List<DashboardRegistryResponse> toRows(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, BiDashboardRegistry> byId = registryRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(BiDashboardRegistry::getId, Function.identity()));
        List<DashboardRegistryResponse> ordered = new ArrayList<>(ids.size());
        for (String id : ids) {
            BiDashboardRegistry entity = byId.get(id);
            if (entity == null) {
                throw new IllegalStateException("bi-dashboard page referenced missing dashboard " + id);
            }
            ordered.add(toResponse(entity));
        }
        return ordered;
    }

    private static DashboardRegistryResponse toResponse(BiDashboardRegistry entity) {
        return DashboardRegistryResponse.builder()
                .id(entity.getId())
                .dashboardTitle(entity.getDashboardTitle())
                .description(entity.getDescription())
                .embedId(entity.getEmbedId())
                .supersetDashboardUuid(entity.getSupersetDashboardUuid())
                .supersetDashboardId(entity.getSupersetDashboardId())
                .tags(entity.getTags())
                .isDefaultLanding(entity.getIsDefaultLanding())
                .status(entity.getStatus())
                .lastSyncedAt(entity.getLastSyncedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private static void applyGroupedValues(List<DashboardRegistryResponse> rows, String groupBy,
                                           List<String> groupedValues) {
        if (groupBy == null || groupBy.isBlank()) {
            return;
        }
        if (rows.size() != groupedValues.size()) {
            throw new IllegalStateException("grouped values and page rows are different lengths");
        }
        for (int i = 0; i < rows.size(); i++) {
            String label = groupedValues.get(i) == null ? "" : groupedValues.get(i);
            DashboardRegistryResponse row = rows.get(i);
            switch (groupBy) {
                case "status" -> row.setStatus(label.isBlank() ? null : DashboardStatus.valueOf(label));
                case "isDefaultLanding" -> row.setIsDefaultLanding("true".equalsIgnoreCase(label));
                default -> throw new IllegalStateException("grouped field was not selected: " + groupBy);
            }
        }
    }

    private static void appendTitle(StringBuilder where, List<Object> params, String title) {
        if (title == null || title.isBlank()) {
            return;
        }
        where.append(" AND d.dashboard_title ILIKE ?");
        params.add("%" + ListFilterSql.escapeLike(title.trim()) + "%");
    }

    private static void appendTags(StringBuilder where, List<Object> params, String tags) {
        if (tags == null || tags.isBlank()) {
            return;
        }
        where.append(" AND d.tags ILIKE ?");
        params.add("%" + ListFilterSql.escapeLike(tags.trim()) + "%");
    }

    private static void appendStatus(StringBuilder where, List<Object> params, String status) {
        if (status == null || status.isBlank()) {
            return;
        }
        where.append(" AND d.status = ?");
        params.add(DashboardStatus.valueOf(status.trim()).name());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record PageIds(List<String> ids, List<String> groupedValues) {
    }
}
