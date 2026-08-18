package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.dto.ListColumnFilter;
import com.portal.entity.ProcessInstance;
import com.portal.util.ListFilterSql;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads one page of a MAIN Main Table View straight from the database.
 *
 * <p>One process instance is one row here, so the page is a {@code LIMIT}/{@code OFFSET} on
 * {@code up_process_instance} and the total is a {@code COUNT(*)} over the same predicate. Row
 * visibility, the designer's filter, the user's column filters, the keyword search and the sort
 * are all part of that predicate, which is what makes the count and the page agree: reading a
 * capped batch and filtering it in memory — the shape this replaces — reported the size of the
 * batch, so the last page was wrong whenever the view held more rows than the cap.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MainTableViewRowQueryComponent {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * @param involvement predicate restricting rows to the ones the user is involved in, or null
     *                    for views open to everyone who can see the view
     * @param searchable  fields the keyword search covers
     */
    public record Query(
            String functionUnitCode,
            ListFilterSql sql,
            List<ListColumnFilter> filters,
            String sortField,
            String sortDirection,
            String groupBy,
            String search,
            List<String> searchable,
            MainTableViewInvolvementScope.Predicate involvement,
            int page,
            int size) {
    }

    /** One group of the whole result set, not of the page — the count is what the header shows. */
    public record Group(String label, long count) {
    }

    public record Page(List<ProcessInstance> instances, long total, List<Group> groups) {
    }

    public Page query(Query query) {
        List<Object> params = new ArrayList<>();
        params.add(query.functionUnitCode());
        StringBuilder where = new StringBuilder(" WHERE pi.function_unit_code = ?");
        if (query.involvement() != null) {
            where.append(query.involvement().sql());
            params.addAll(query.involvement().params());
        }
        where.append(query.sql().searchClause(query.search(), query.searchable(), params));
        where.append(query.sql().whereClause(query.filters(), params));

        Long total = queryOne("SELECT COUNT(*) FROM up_process_instance pi" + where,
                params, rs -> rs.next() ? rs.getLong(1) : 0L);

        String groupExpression = query.groupBy() == null || query.groupBy().isBlank()
                ? null
                : query.sql().groupByExpression(query.groupBy());
        List<Group> groups = groupExpression == null
                ? List.of()
                : groupsOf(groupExpression, where.toString(), params);

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(query.size());
        pageParams.add(query.page() * query.size());
        String orderBy = groupExpression == null
                ? query.sql().orderBy(query.sortField(), query.sortDirection())
                : query.sql().orderByGrouped(groupExpression, query.sortField(), query.sortDirection());
        String sql = "SELECT pi.id, pi.status, pi.start_time, pi.start_user_id, pi.start_user_name,"
                + " pi.current_node, pi.variables::text AS variables"
                + " FROM up_process_instance pi" + where
                + orderBy
                + " LIMIT ? OFFSET ?";

        List<ProcessInstance> instances = queryOne(sql, pageParams, rs -> {
            List<ProcessInstance> rows = new ArrayList<>();
            while (rs.next()) {
                ProcessInstance instance = new ProcessInstance();
                instance.setId(rs.getString("id"));
                instance.setStatus(rs.getString("status"));
                Timestamp startTime = rs.getTimestamp("start_time");
                instance.setStartTime(startTime != null ? startTime.toLocalDateTime() : null);
                instance.setStartUserId(rs.getString("start_user_id"));
                instance.setStartUserName(rs.getString("start_user_name"));
                instance.setCurrentNode(rs.getString("current_node"));
                instance.setVariables(readVariables(rs.getString("variables")));
                rows.add(instance);
            }
            return rows;
        });

        return new Page(instances, total != null ? total : 0L, groups);
    }

    /**
     * Counts every group of the whole result set. The page only carries the rows of the groups it
     * happens to cover, so a header count computed from those rows would shrink at page
     * boundaries; this counts over the same predicate the page is drawn from.
     */
    private List<Group> groupsOf(String groupExpression, String where, List<Object> params) {
        String sql = "SELECT " + groupExpression + " AS group_label, COUNT(*) AS group_count"
                + " FROM up_process_instance pi" + where
                + " GROUP BY " + groupExpression
                + " ORDER BY " + groupExpression + " ASC NULLS LAST";
        return queryOne(sql, params, rs -> {
            List<Group> groups = new ArrayList<>();
            while (rs.next()) {
                groups.add(new Group(rs.getString("group_label"), rs.getLong("group_count")));
            }
            return groups;
        });
    }

    /**
     * Runs one statement, binding a {@code String[]} as a SQL array so the visible-instance list
     * travels as a single parameter instead of one placeholder per id.
     */
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

    private Map<String, Object> readVariables(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Process variables are not readable JSON", e);
        }
    }
}
