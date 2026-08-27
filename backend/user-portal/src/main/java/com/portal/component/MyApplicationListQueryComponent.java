package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.dto.MyApplicationQueryRequest;
import com.portal.dto.PortalListPage;
import com.portal.dto.ProcessInstanceInfo;
import com.portal.entity.ProcessInstance;
import com.portal.service.UserDisplayNameResolver;

import com.portal.util.ListQuerySupport;
import com.portal.util.MyApplicationColumnSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;
import com.platform.common.list.ListFilterSql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * My Requests list: one process instance is one row. Visibility is exact
 * ({@code start_user_id = current user}); the status tab ANDs with column filters.
 * {@code COUNT(*)}, the page shares that predicate.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MyApplicationListQueryComponent {

    static final String LIST_KEY = "my-applications";
    static final String LIST_PROJECTION_SQL = "SELECT pi.id, pi.process_definition_id, pi.process_definition_key,"
            + " pi.process_definition_name, pi.business_key, pi.start_user_id, pi.start_user_name,"
            + " pi.current_node, pi.current_assignee, pi.candidate_users, pi.status, pi.title,"
            + " pi.start_time, pi.end_time, pi.completed_at, pi.function_unit_catalog_id,"
            + " pi.function_unit_code, pi.function_unit_version_label,"
            + " (pi.variables - '__subTables__')::text AS variables"
            + " FROM up_process_instance pi WHERE pi.id IN (%s)";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ProcessApplicationQueryComponent processApplicationQueryComponent;
    private final RequestIdEnricher requestIdEnricher;
    private final UserDisplayNameResolver userDisplayNameResolver;

    public PortalListPage<ProcessInstanceInfo> query(String userId, MyApplicationQueryRequest request) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required for my-applications");
        }
        long started = System.nanoTime();
        ListFilterSql filterSql = MyApplicationColumnSpec.sql();
        List<Object> params = new ArrayList<>();
        params.add(userId);
        StringBuilder where = new StringBuilder(" FROM up_process_instance pi WHERE pi.start_user_id = ?");
        appendStatus(where, params, request.status());
        where.append(filterSql.whereClause(request.filters(), params));

        long total = ListQuerySupport.requireCount(
                ListQuerySupport.query(jdbcTemplate, "SELECT COUNT(*)" + where, params,
                        rs -> rs.next() ? rs.getLong(1) : 0L),
                LIST_KEY);


        PageIds pageIds = loadPageIds(filterSql, where.toString(), params, request);
        long afterSql = System.nanoTime();
        List<ProcessInstanceInfo> rows = toListRows(pageIds.ids());
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        ListQuerySupport.logIfSlow(log, LIST_KEY, request.page(), request.size(), total, started);
        ListQuerySupport.logIfOverSla(log, LIST_KEY, request.page(), request.size(), total, elapsedMs,
                (afterSql - started) / 1_000_000L, elapsedMs - (afterSql - started) / 1_000_000L);
        return new PortalListPage<>(MyApplicationColumnSpec.columns(), rows,
                request.page(), request.size(), total);
    }

    private PageIds loadPageIds(ListFilterSql filterSql, String where, List<Object> params,
                                MyApplicationQueryRequest request) {
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.size());
        pageParams.add(request.page() * request.size());
        String orderBy = filterSql.orderBy(request.sortField(), request.sortDirection());
        String sql = "SELECT pi.id" + where + orderBy + " LIMIT ? OFFSET ?";
        return ListQuerySupport.query(jdbcTemplate, sql, pageParams, rs -> {
            List<String> ids = new ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getString("id"));
            }
            return new PageIds(ids);
        });
    }

    List<ProcessInstanceInfo> toListRows(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, ProcessInstance> byId = new LinkedHashMap<>();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = LIST_PROJECTION_SQL.formatted(placeholders);
        ResultSetExtractor<Void> extractor = rs -> {
            while (rs.next()) {
                ProcessInstance instance = mapListRow(rs);
                byId.put(instance.getId(), instance);
            }
            return null;
        };
        jdbcTemplate.query(sql, extractor, ids.toArray());
        List<ProcessInstance> ordered = new ArrayList<>(ids.size());
        for (String id : ids) {
            ProcessInstance instance = byId.get(id);
            if (instance == null) {
                throw new IllegalStateException("my-applications page referenced missing instance " + id);
            }
            ordered.add(instance);
        }
        processApplicationQueryComponent.enrichRunningAssigneesFromEngine(ordered);

        Set<String> assigneeKeys = new LinkedHashSet<>();
        Set<String> functionUnitCodes = new LinkedHashSet<>();
        for (ProcessInstance instance : ordered) {
            assigneeKeys.addAll(userDisplayNameResolver.collectAssigneeUserKeys(
                    instance.getCurrentAssignee(), instance.getCandidateUsers()));
            if (instance.getFunctionUnitCode() != null) {
                functionUnitCodes.add(instance.getFunctionUnitCode());
            }
        }
        Map<String, String> userNameCache = userDisplayNameResolver.resolveBatch(assigneeKeys);
        RequestIdEnricher.SpecCache requestIdSpecs = requestIdEnricher.resolveSpecs(functionUnitCodes);
        Map<String, Map<String, String>> miNameMaps =
                processApplicationQueryComponent.buildMiNodeNameMaps(ordered);

        List<ProcessInstanceInfo> rows = new ArrayList<>(ordered.size());
        for (ProcessInstance instance : ordered) {
            ProcessInstanceInfo info = processApplicationQueryComponent.toProcessInstanceInfoForList(
                    instance, userNameCache, miNameMaps);
            info.setRequestId(requestIdEnricher.buildRequestId(
                    requestIdSpecs, info.getFunctionUnitCode(), info.getVariables()));
            info.setVariables(null);
            rows.add(info);
        }
        return rows;
    }

    private ProcessInstance mapListRow(ResultSet rs) throws SQLException {
        ProcessInstance instance = new ProcessInstance();
        instance.setId(rs.getString("id"));
        instance.setProcessDefinitionId(rs.getString("process_definition_id"));
        instance.setProcessDefinitionKey(rs.getString("process_definition_key"));
        instance.setProcessDefinitionName(rs.getString("process_definition_name"));
        instance.setBusinessKey(rs.getString("business_key"));
        instance.setStartUserId(rs.getString("start_user_id"));
        instance.setStartUserName(rs.getString("start_user_name"));
        instance.setCurrentNode(rs.getString("current_node"));
        instance.setCurrentAssignee(rs.getString("current_assignee"));
        instance.setCandidateUsers(rs.getString("candidate_users"));
        instance.setStatus(rs.getString("status"));
        instance.setTitle(rs.getString("title"));
        instance.setStartTime(toLocalDateTime(rs.getTimestamp("start_time")));
        instance.setEndTime(toLocalDateTime(rs.getTimestamp("end_time")));
        instance.setCompletedAt(toLocalDateTime(rs.getTimestamp("completed_at")));
        instance.setFunctionUnitCatalogId(rs.getString("function_unit_catalog_id"));
        instance.setFunctionUnitCode(rs.getString("function_unit_code"));
        instance.setFunctionUnitVersionLabel(rs.getString("function_unit_version_label"));
        instance.setVariables(readVariables(rs.getString("variables")));
        return instance;
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

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }


    private static void appendStatus(StringBuilder where, List<Object> params, String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return;
        }
        where.append(" AND pi.status = ?");
        params.add(status);
    }


    private record PageIds(List<String> ids) {
    }
}
