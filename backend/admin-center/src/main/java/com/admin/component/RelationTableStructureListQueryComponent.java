package com.admin.component;

import com.admin.dto.list.AdminListGroup;
import com.admin.dto.list.RelationTableFuGroup;
import com.admin.dto.list.RelationTableStructureListPage;
import com.admin.dto.request.RelationTableStructureListQueryRequest;
import com.admin.dto.response.RelationTableResponse;
import com.admin.entity.FunctionUnit;
import com.admin.entity.RelationTableDefinition;
import com.admin.entity.RelationTableFunctionUnit;
import com.admin.list.ListFilterSql;
import com.admin.list.ListQuerySupport;
import com.admin.list.RelationTableStructureColumnSpec;
import com.admin.repository.RelationTableDefinitionRepository;
import com.platform.common.enums.RelationTableStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Relation Table Structure list: COUNT(*) and the page share the left-rail Function Unit
 * filter plus column filters. Sidebar groups are counted over the whole catalog so a
 * table linked to two units appears under both.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelationTableStructureListQueryComponent {

    static final String LIST_KEY = "admin-relation-table-structures";

    private static final String FROM = " FROM rt_table_definitions t WHERE 1=1 ";

    private final JdbcTemplate jdbcTemplate;
    private final RelationTableDefinitionRepository tableDefinitionRepository;
    private final RelationTableFunctionUnitResolver relationTableFunctionUnitResolver;

    @Transactional(readOnly = true)
    public RelationTableStructureListPage query(RelationTableStructureListQueryRequest request) {
        long started = System.nanoTime();
        ListFilterSql filterSql = RelationTableStructureColumnSpec.sql();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(FROM);
        appendFunctionUnit(where, params, request.functionUnitId());
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
            throw new IllegalStateException("GROUP BY returned no groups for a non-empty relation-table structure list");
        }

        PageIds pageIds = loadPageIds(filterSql, where.toString(), params, request, groupExpression);
        List<RelationTableResponse> rows = toRows(pageIds.ids());
        applyGroupedValues(rows, request.groupBy(), pageIds.groupedValues());
        List<RelationTableFuGroup> fuGroups = loadFunctionUnitGroups();
        ListQuerySupport.logIfSlow(log, LIST_KEY, request.page(), request.size(), total, started);
        return new RelationTableStructureListPage(RelationTableStructureColumnSpec.columns(), rows, groups,
                request.page(), request.size(), total, fuGroups);
    }

    private PageIds loadPageIds(ListFilterSql filterSql, String where, List<Object> params,
                                RelationTableStructureListQueryRequest request, String groupExpression) {
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.size());
        pageParams.add(request.page() * request.size());
        String orderBy = groupExpression == null
                ? filterSql.orderBy(request.sortField(), request.sortDirection())
                : filterSql.orderByGrouped(groupExpression, request.sortField(), request.sortDirection());
        String groupedSelect = groupExpression == null ? "" : ", " + groupExpression + " AS grouped_value";
        String sql = "SELECT t.id" + groupedSelect + where + orderBy + " LIMIT ? OFFSET ?";
        ResultSetExtractor<PageIds> extractor = rs -> {
            List<Long> ids = new ArrayList<>();
            List<String> grouped = new ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getLong("id"));
                grouped.add(groupExpression == null ? null : rs.getString("grouped_value"));
            }
            return new PageIds(ids, grouped);
        };
        return ListQuerySupport.query(jdbcTemplate, sql, pageParams, extractor);
    }

    private List<RelationTableResponse> toRows(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, RelationTableDefinition> byId = tableDefinitionRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(RelationTableDefinition::getId, Function.identity()));
        Map<Long, List<RelationTableFunctionUnit>> linksByTable =
                relationTableFunctionUnitResolver.loadLinksByTable(ids);
        Map<String, FunctionUnit> functionUnitsById =
                relationTableFunctionUnitResolver.loadFunctionUnitsById(linksByTable);
        List<RelationTableResponse> ordered = new ArrayList<>(ids.size());
        for (Long id : ids) {
            RelationTableDefinition entity = byId.get(id);
            if (entity == null) {
                throw new IllegalStateException("relation-table structure page referenced missing table " + id);
            }
            RelationTableResponse row = RelationTableResponse.fromEntity(entity);
            row.applyFunctionUnits(relationTableFunctionUnitResolver.resolve(linksByTable.get(id), functionUnitsById));
            ordered.add(row);
        }
        return ordered;
    }

    private List<RelationTableFuGroup> loadFunctionUnitGroups() {
        String commonSql = """
                SELECT COUNT(*) FROM rt_table_definitions t
                WHERE NOT EXISTS (
                    SELECT 1 FROM rt_table_function_units l WHERE l.relation_table_id = t.id)
                """;
        ResultSetExtractor<Long> countExtractor = rs -> rs.next() ? rs.getLong(1) : 0L;
        long commonCount = ListQuerySupport.requireCount(
                ListQuerySupport.query(jdbcTemplate, commonSql, List.of(), countExtractor), LIST_KEY);
        String fuSql = """
                SELECT l.function_unit_id AS fu_id, fu.name AS fu_name, fu.code AS fu_code, COUNT(*) AS group_count
                FROM rt_table_function_units l
                JOIN sys_function_units fu ON fu.id = l.function_unit_id
                GROUP BY l.function_unit_id, fu.name, fu.code
                ORDER BY fu.name ASC NULLS LAST, fu.code ASC
                """;
        ResultSetExtractor<List<RelationTableFuGroup>> extractor = rs -> {
            List<RelationTableFuGroup> groups = new ArrayList<>();
            if (commonCount > 0) {
                groups.add(new RelationTableFuGroup(
                        RelationTableStructureListQueryRequest.COMMON_KEY, null, commonCount));
            }
            while (rs.next()) {
                String name = rs.getString("fu_name");
                String code = rs.getString("fu_code");
                String label = (name == null || name.isBlank()) ? code : name;
                groups.add(new RelationTableFuGroup(rs.getString("fu_id"), label, rs.getLong("group_count")));
            }
            return groups;
        };
        return ListQuerySupport.query(jdbcTemplate, fuSql, List.of(), extractor);
    }

    private static void applyGroupedValues(List<RelationTableResponse> rows, String groupBy,
                                           List<String> groupedValues) {
        if (groupBy == null || groupBy.isBlank()) {
            return;
        }
        if (rows.size() != groupedValues.size()) {
            throw new IllegalStateException("grouped values and page rows are different lengths");
        }
        for (int i = 0; i < rows.size(); i++) {
            String label = groupedValues.get(i) == null ? "" : groupedValues.get(i);
            RelationTableResponse row = rows.get(i);
            switch (groupBy) {
                case "status" -> row.setStatus(label.isBlank() ? null : RelationTableStatus.fromCode(label));
                case "enabled" -> row.setEnabled("true".equalsIgnoreCase(label));
                case "portalVisible" -> row.setPortalVisible("true".equalsIgnoreCase(label));
                default -> throw new IllegalStateException("grouped field was not selected: " + groupBy);
            }
        }
    }

    private static void appendFunctionUnit(StringBuilder where, List<Object> params, String functionUnitId) {
        if (functionUnitId == null || functionUnitId.isBlank()) {
            return;
        }
        if (RelationTableStructureListQueryRequest.COMMON_KEY.equals(functionUnitId)) {
            where.append(" AND NOT EXISTS (SELECT 1 FROM rt_table_function_units l")
                    .append(" WHERE l.relation_table_id = t.id)");
            return;
        }
        where.append(" AND EXISTS (SELECT 1 FROM rt_table_function_units l")
                .append(" WHERE l.relation_table_id = t.id AND l.function_unit_id = ?)");
        params.add(functionUnitId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record PageIds(List<Long> ids, List<String> groupedValues) {
    }
}
