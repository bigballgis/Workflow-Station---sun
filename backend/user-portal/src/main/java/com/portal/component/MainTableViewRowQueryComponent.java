package com.portal.component;

import com.platform.common.list.ListColumnFilter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.entity.ProcessInstance;
import com.portal.util.ListFilterSql;
import com.portal.util.SqlFragment;
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
     * @param designerFilter the view's own filter, compiled — it decides which rows the view is
     *                       about, so it belongs in the predicate the page and the total share
     * @param involvement    predicate restricting rows to the ones the user is involved in, or
     *                       null for views open to everyone who can see the view
     * @param searchable     fields the keyword search covers
     */
    public record Query(
            String functionUnitCode,
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

    public record Page(List<ProcessInstance> instances, long total) {
    }

    public Page query(Query query) {
        List<Object> params = new ArrayList<>();
        params.add(query.functionUnitCode());
        StringBuilder where = new StringBuilder(" WHERE pi.function_unit_code = ?");
        where.append(query.designerFilter().sql());
        params.addAll(query.designerFilter().params());
        if (query.involvement() != null) {
            where.append(query.involvement().sql());
            params.addAll(query.involvement().params());
        }
        where.append(query.sql().searchClause(query.search(), query.searchable(), params));
        where.append(query.sql().whereClause(query.filters(), params));

        Long total = queryOne("SELECT COUNT(*) FROM up_process_instance pi" + where,
                params, rs -> rs.next() ? rs.getLong(1) : 0L);

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(query.size());
        pageParams.add(query.page() * query.size());
        String orderBy = query.sql().orderBy(query.sortField(), query.sortDirection());
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

        return new Page(instances, total != null ? total : 0L);
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
