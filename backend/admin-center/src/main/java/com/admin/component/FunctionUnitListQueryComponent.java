package com.admin.component;

import com.admin.dto.list.AdminListGroup;
import com.admin.dto.list.AdminListPage;
import com.admin.dto.request.FunctionUnitListQueryRequest;
import com.admin.dto.response.FunctionUnitInfo;
import com.admin.entity.FunctionUnit;
import com.admin.enums.FunctionUnitStatus;
import com.admin.list.FunctionUnitColumnSpec;
import com.admin.list.ListFilterSql;
import com.admin.list.ListQuerySupport;
import com.admin.repository.FunctionUnitRepository;
import com.admin.service.UserReferenceResolver;
import com.platform.common.list.ListColumnMeta;
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
 * Function Unit list and archive: DISTINCT ON (code) picks the highest version first,
 * then COUNT(*) / groups / LIMIT share the keyword + column-filter predicate.
 * Grouping writes {@link FunctionUnitInfo} only.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FunctionUnitListQueryComponent {

    static final String LIST_KEY = "admin-function-units";
    static final String ARCHIVE_KEY = "admin-function-units-archived";

    private final JdbcTemplate jdbcTemplate;
    private final FunctionUnitRepository functionUnitRepository;
    private final UserReferenceResolver userReferenceResolver;

    public AdminListPage<FunctionUnitInfo> queryList(FunctionUnitListQueryRequest request) {
        return query(request, false);
    }

    public AdminListPage<FunctionUnitInfo> queryArchived(FunctionUnitListQueryRequest request) {
        return query(request, true);
    }

    private AdminListPage<FunctionUnitInfo> query(FunctionUnitListQueryRequest request, boolean archived) {
        long started = System.nanoTime();
        String listKey = archived ? ARCHIVE_KEY : LIST_KEY;
        List<ListColumnMeta> columns = archived
                ? FunctionUnitColumnSpec.archiveColumns()
                : FunctionUnitColumnSpec.columns();
        ListFilterSql filterSql = archived
                ? FunctionUnitColumnSpec.archiveSql()
                : FunctionUnitColumnSpec.sql();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(FunctionUnitColumnSpec.latestFrom(archived));
        appendKeyword(where, params, request.keyword());
        where.append(filterSql.whereClause(request.filters(), params));

        ResultSetExtractor<Long> countExtractor = rs -> rs.next() ? rs.getLong(1) : 0L;
        long total = ListQuerySupport.requireCount(
                ListQuerySupport.query(jdbcTemplate, "SELECT COUNT(*)" + where, params, countExtractor),
                listKey);

        String groupExpression = blankToNull(request.groupBy()) == null
                ? null
                : filterSql.groupByExpression(request.groupBy());
        List<AdminListGroup> groups = groupExpression == null
                ? List.of()
                : ListQuerySupport.groupsOf(jdbcTemplate, groupExpression, where.toString(), params);
        if (groupExpression != null && total > 0 && groups.isEmpty()) {
            throw new IllegalStateException("GROUP BY returned no groups for a non-empty function-unit list");
        }

        PageIds pageIds = loadPageIds(filterSql, where.toString(), params, request, groupExpression);
        List<FunctionUnitInfo> rows = toRows(pageIds.ids(), archived);
        applyGroupedValues(rows, request.groupBy(), pageIds.groupedValues(), archived);
        ListQuerySupport.logIfSlow(log, listKey, request.page(), request.size(), total, started);
        return new AdminListPage<>(columns, rows, groups, request.page(), request.size(), total);
    }

    private PageIds loadPageIds(ListFilterSql filterSql, String where, List<Object> params,
                                FunctionUnitListQueryRequest request, String groupExpression) {
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.size());
        pageParams.add(request.page() * request.size());
        String orderBy = groupExpression == null
                ? filterSql.orderBy(request.sortField(), request.sortDirection())
                : filterSql.orderByGrouped(groupExpression, request.sortField(), request.sortDirection());
        String groupedSelect = groupExpression == null ? "" : ", " + groupExpression + " AS grouped_value";
        String sql = "SELECT fu.id" + groupedSelect + where + orderBy + " LIMIT ? OFFSET ?";
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

    private List<FunctionUnitInfo> toRows(List<String> ids, boolean archived) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, FunctionUnit> byId = functionUnitRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(FunctionUnit::getId, Function.identity()));
        List<FunctionUnitInfo> ordered = new ArrayList<>(ids.size());
        for (String id : ids) {
            FunctionUnit entity = byId.get(id);
            if (entity == null) {
                throw new IllegalStateException("function-unit page referenced missing unit " + id);
            }
            ordered.add(FunctionUnitInfo.fromEntity(entity));
        }
        if (archived) {
            enrichUpdatedBy(ordered);
        }
        return ordered;
    }

    private void enrichUpdatedBy(List<FunctionUnitInfo> rows) {
        var cache = userReferenceResolver.resolveUsernames(
                rows.stream().map(FunctionUnitInfo::getUpdatedBy).toList());
        for (FunctionUnitInfo row : rows) {
            if (row.getUpdatedBy() != null) {
                row.setUpdatedBy(userReferenceResolver.resolveWithCache(row.getUpdatedBy(), cache));
            }
        }
    }

    private static void applyGroupedValues(List<FunctionUnitInfo> rows, String groupBy,
                                           List<String> groupedValues, boolean archived) {
        if (groupBy == null || groupBy.isBlank()) {
            return;
        }
        if (rows.size() != groupedValues.size()) {
            throw new IllegalStateException("grouped values and page rows are different lengths");
        }
        for (int i = 0; i < rows.size(); i++) {
            String label = groupedValues.get(i) == null ? "" : groupedValues.get(i);
            FunctionUnitInfo row = rows.get(i);
            switch (groupBy) {
                case "status" -> row.setStatus(label.isBlank() ? null : FunctionUnitStatus.valueOf(label));
                case "enabled" -> row.setEnabled("true".equalsIgnoreCase(label));
                case "updatedBy" -> {
                    if (!archived) {
                        throw new IllegalStateException("grouped field was not selected: " + groupBy);
                    }
                    row.setUpdatedBy(label);
                }
                default -> throw new IllegalStateException("grouped field was not selected: " + groupBy);
            }
        }
    }

    static void appendKeyword(StringBuilder where, List<Object> params, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String like = "%" + ListFilterSql.escapeLike(keyword.trim()) + "%";
        where.append(" AND (fu.name ILIKE ? OR fu.code ILIKE ? OR COALESCE(fu.description, '') ILIKE ?)");
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
