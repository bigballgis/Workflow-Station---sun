package com.portal.component;

import com.platform.common.list.ListColumnFilter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.jdbc.SubTableRowIdentity;
import com.portal.entity.ProcessInstance;
import com.portal.exception.PortalException;
import com.portal.util.ListFilterSql;
import com.portal.util.SqlFragment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.stereotype.Component;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads one page of a SUB Main Table View, where a row is a sub-table row rather than a process
 * instance.
 *
 * <p>The rows live in {@code variables -> '__subTables__' -> <bindingKey>}, so one instance
 * expands into many and the paging has to act on the expansion: the page is a LIMIT/OFFSET over
 * the expanded rows and the total counts them, not the instances they came from.
 *
     * <p>The query is two layers, and cannot be one. The inner layer expands and de-duplicates,
     * which forces its ORDER BY to lead with the de-duplication key; the outer layer applies the
     * filters, the user's sort and the paging, whose order is whatever the user asked for.
     * Collapsing them would make those two orders fight.
 *
 * <p>De-duplication is by instance plus the row's own identity and deliberately not by which
 * binding the row was found under: binding a sub-table into several forms is normal, and the same
 * row then appears under each binding. Keying on the binding would show one row several times.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MainTableViewSubRowQueryComponent {

    /** Identity of the expanded row, from the shared key list — see {@link SubTableRowIdentity}. */
    private static final String ROW_IDENTITY = SubTableRowIdentity.sqlIdentityExpression("expanded.elem");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * @param bindingKeys    {@code __subTables__} keys this view's table is bound under
     * @param designerFilter the view's own filter, compiled against the expanded row
     * @param involvement    predicate restricting rows to the ones the user is involved in, or null
     */
    public record Query(
            Long viewId,
            String functionUnitCode,
            List<String> bindingKeys,
            ListFilterSql sql,
            SqlFragment designerFilter,
            List<ListColumnFilter> filters,
            String sortField,
            String sortDirection,
            String search,
            List<String> searchable,
            MainTableViewInvolvementScope.Predicate involvement,
            int page,
            int size) {
    }

    /** One expanded row: the sub-table row itself plus the instance that carries it. */
    public record Row(ProcessInstance instance, Map<String, Object> subRow) {
    }

    public record Page(List<Row> rows, long total) {
    }

    public Page query(Query query) {
        List<Object> params = new ArrayList<>();
        String inner = expandedRows(query, params);

        StringBuilder where = new StringBuilder(" WHERE TRUE");
        where.append(query.designerFilter().sql());
        params.addAll(query.designerFilter().params());
        where.append(query.sql().searchClause(query.search(), query.searchable(), params));
        where.append(query.sql().whereClause(query.filters(), params));

        Long total = queryOne("SELECT COUNT(*) FROM " + inner + " pi" + where,
                params, rs -> rs.next() ? rs.getLong(1) : 0L);

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(query.size());
        pageParams.add(query.page() * query.size());
        String orderBy = query.sql().orderBy(query.sortField(), query.sortDirection());

        List<Row> rows = queryOne(
                "SELECT * FROM " + inner + " pi" + where + orderBy + " LIMIT ? OFFSET ?",
                pageParams, rs -> readRows(rs, query.viewId()));

        return new Page(rows, total != null ? total : 0L);
    }

    /**
     * The inner layer: every sub-table row of every instance of this function unit, once.
     *
     * <p>Row visibility is decided here rather than outside, because it is a property of the
     * instance and not of the row: settling it before the expansion means an instance the user
     * may not see is never expanded at all.
     *
     * <p>There is no {@code COALESCE(..., '[]')} around the array, on purpose. An instance that
     * never filled this sub-table simply has no elements — {@code jsonb_array_elements} is strict
     * and yields nothing for a missing key, which is the same answer. Defaulting would only hide
     * the other case, where the slice holds something that is not an array at all; that is broken
     * data and should fail loudly.
     */
    private String expandedRows(Query query, List<Object> outParams) {
        if (query.bindingKeys().isEmpty()) {
            throw new IllegalArgumentException("A sub-table view must be bound to at least one form");
        }
        StringBuilder lateral = new StringBuilder();
        for (String bindingKey : query.bindingKeys()) {
            if (!lateral.isEmpty()) {
                lateral.append(" UNION ALL ");
            }
            lateral.append("SELECT ?::text AS slice_key, e.elem, e.ord")
                    .append(" FROM jsonb_array_elements(pi.variables->'__subTables__'->?::text)")
                    .append(" WITH ORDINALITY AS e(elem, ord)");
            outParams.add(bindingKey);
            outParams.add(bindingKey);
        }
        outParams.add(query.functionUnitCode());

        StringBuilder visible = new StringBuilder();
        if (query.involvement() != null) {
            visible.append(query.involvement().sql());
            outParams.addAll(query.involvement().params());
        }

        return "(SELECT DISTINCT ON (pi.id, " + ROW_IDENTITY + ")"
                + " pi.id, pi.status, pi.start_time, pi.start_user_id, pi.start_user_name,"
                + " pi.current_node, pi.variables::text AS variables,"
                + " expanded.elem AS sub_elem, expanded.slice_key, expanded.ord,"
                + " " + ROW_IDENTITY + " AS row_identity"
                + " FROM up_process_instance pi"
                + " CROSS JOIN LATERAL (" + lateral + ") expanded"
                + " WHERE pi.function_unit_code = ?" + visible
                + " ORDER BY pi.id, " + ROW_IDENTITY + ", pi.start_time DESC NULLS LAST)";
    }

    private List<Row> readRows(ResultSet rs, Long viewId) throws SQLException {
        List<Row> rows = new ArrayList<>();
        while (rs.next()) {
            String identity = rs.getString("row_identity");
            if (identity == null || identity.isBlank()) {
                // Merging identity-less rows by content would silently drop duplicates and
                // understate the total. The row is reported by location only — its values may be
                // personal data and have no place in a log or an error body.
                throw new PortalException("500", "Sub-table row without identity in view " + viewId
                        + ", process instance " + rs.getString("id")
                        + ", slice " + rs.getString("slice_key")
                        + ", position " + rs.getLong("ord")
                        + ". DISTINCT ON treats missing identities as equal, so other rows in this"
                        + " view may be affected too — check the whole slice, not just this row.");
            }
            ProcessInstance instance = new ProcessInstance();
            instance.setId(rs.getString("id"));
            instance.setStatus(rs.getString("status"));
            Timestamp startTime = rs.getTimestamp("start_time");
            instance.setStartTime(startTime != null ? startTime.toLocalDateTime() : null);
            instance.setStartUserId(rs.getString("start_user_id"));
            instance.setStartUserName(rs.getString("start_user_name"));
            instance.setCurrentNode(rs.getString("current_node"));
            instance.setVariables(readJson(rs.getString("variables")));
            rows.add(new Row(instance, readJson(rs.getString("sub_elem"))));
        }
        return rows;
    }

    /** Binds a {@code String[]} as a SQL array so the visible-instance list stays one parameter. */
    private <T> T queryOne(String sql, List<Object> params,
                           org.springframework.jdbc.core.ResultSetExtractor<T> extractor) {
        PreparedStatementCreator creator = connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            int index = 1;
            for (Object param : params) {
                if (param instanceof String[] array) {
                    statement.setArray(index++, connection.createArrayOf("text", array));
                } else {
                    statement.setObject(index++, param);
                }
            }
            return statement;
        };
        return jdbcTemplate.query(creator, extractor);
    }

    private Map<String, Object> readJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Stored JSON is not readable", e);
        }
    }
}
