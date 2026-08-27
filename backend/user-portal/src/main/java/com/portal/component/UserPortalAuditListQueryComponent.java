package com.portal.component;

import com.platform.common.audit.SystemAuditFields;
import com.platform.common.jdbc.SqlIdentifiers;
import com.portal.dto.PortalListPage;
import com.portal.dto.UserPortalAuditListQueryRequest;
import com.portal.dto.UserPortalAuditRecord;
import com.portal.entity.ChangeHistory;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ChangeHistoryRepository;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.util.ListFilterSql;
import com.portal.util.ListQuerySupport;
import com.portal.util.UserPortalAuditColumnSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Admin User Portal audit list: {@code COUNT(*)}, the page shares the
 * toolbar predicate plus column filters. Outer alias is {@code ch}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserPortalAuditListQueryComponent {

    static final String LIST_KEY = "admin-up-audit";

    private final JdbcTemplate jdbcTemplate;
    private final ChangeHistoryRepository changeHistoryRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final UserPortalAuditEnricher userPortalAuditEnricher;
    private final UserPortalAuditProcessInstanceMatcher processInstanceMatcher;

    public PortalListPage<UserPortalAuditRecord> query(UserPortalAuditListQueryRequest request) {
        long started = System.nanoTime();
        ListFilterSql filterSql = UserPortalAuditColumnSpec.sql();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(
                " FROM up_change_history ch"
                        + " LEFT JOIN up_process_instance pi ON pi.id = ch.process_instance_id"
                        + " WHERE 1=1");
        appendExcludedFields(where, params);
        appendToolbar(where, params, request);
        where.append(filterSql.whereClause(request.filters(), params));

        ResultSetExtractor<Long> countExtractor = rs -> rs.next() ? rs.getLong(1) : 0L;
        long total = ListQuerySupport.requireCount(
                ListQuerySupport.query(jdbcTemplate, "SELECT COUNT(*)" + where, params, countExtractor),
                LIST_KEY);


        PageIds pageIds = loadPageIds(filterSql, where.toString(), params, request);
        List<UserPortalAuditRecord> rows = toRows(pageIds.ids());
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        ListQuerySupport.logIfSlow(log, LIST_KEY, request.page(), request.size(), total, started);
        ListQuerySupport.logIfOverSla(log, LIST_KEY, request.page(), request.size(), total, elapsedMs, elapsedMs, 0L);
        return new PortalListPage<>(UserPortalAuditColumnSpec.columns(), rows,
                request.page(), request.size(), total);
    }

    private PageIds loadPageIds(ListFilterSql filterSql, String where, List<Object> params,
                                UserPortalAuditListQueryRequest request) {
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.size());
        pageParams.add(request.page() * request.size());
        String orderBy = filterSql.orderBy(request.sortField(), request.sortDirection());
        String sql = "SELECT ch.id" + where + orderBy + " LIMIT ? OFFSET ?";
        ResultSetExtractor<PageIds> extractor = rs -> {
            List<Long> ids = new ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getLong("id"));
            }
            return new PageIds(ids);
        };
        return ListQuerySupport.query(jdbcTemplate, sql, pageParams, extractor);
    }

    private List<UserPortalAuditRecord> toRows(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, ChangeHistory> byId = changeHistoryRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ChangeHistory::getId, Function.identity()));
        List<ChangeHistory> ordered = new ArrayList<>(ids.size());
        for (Long id : ids) {
            ChangeHistory row = byId.get(id);
            if (row == null) {
                throw new IllegalStateException("admin-up-audit page referenced missing row " + id);
            }
            ordered.add(row);
        }
        Set<String> processInstanceIds = ordered.stream()
                .map(ChangeHistory::getProcessInstanceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, ProcessInstance> piMap = new HashMap<>();
        if (!processInstanceIds.isEmpty()) {
            processInstanceRepository.findAllById(processInstanceIds)
                    .forEach(pi -> piMap.put(pi.getId(), pi));
        }
        return userPortalAuditEnricher.toAuditRecords(ordered, piMap);
    }

    private void appendToolbar(StringBuilder where, List<Object> params,
                               UserPortalAuditListQueryRequest request) {
        appendChangeType(where, params, request.changeType());
        appendFunctionUnit(where, params, request.functionUnitCode());
        appendUsername(where, params, request.username());
        appendProcessKeyword(where, params, request);
        Instant start = parseIsoInstant(request.startTime());
        Instant end = parseIsoInstant(request.endTime());
        if (start != null) {
            where.append(" AND ch.timestamp >= ?");
            params.add(Timestamp.from(start));
        }
        if (end != null) {
            where.append(" AND ch.timestamp <= ?");
            params.add(Timestamp.from(end));
        }
    }

    private static void appendChangeType(StringBuilder where, List<Object> params, String changeType) {
        if (!notBlank(changeType)) {
            return;
        }
        where.append(" AND ch.change_type = ?");
        params.add(changeType.trim());
    }

    private static void appendFunctionUnit(StringBuilder where, List<Object> params, String code) {
        if (!notBlank(code)) {
            return;
        }
        where.append(" AND pi.function_unit_code = ?");
        params.add(code.trim());
    }

    private static void appendUsername(StringBuilder where, List<Object> params, String username) {
        if (!notBlank(username)) {
            return;
        }
        String like = "%" + UserPortalAuditProcessInstanceMatcher.escapeLike(username.trim().toLowerCase()) + "%";
        String users = SqlIdentifiers.requireQualifiedName("sys_users");
        where.append(" AND ch.user_id IN (SELECT u.id FROM ").append(users)
                .append(" u WHERE LOWER(u.username) LIKE ? ESCAPE '\\'")
                .append(" OR LOWER(u.full_name) LIKE ? ESCAPE '\\')");
        params.add(like);
        params.add(like);
    }

    private void appendProcessKeyword(StringBuilder where, List<Object> params,
                                      UserPortalAuditListQueryRequest request) {
        if (!notBlank(request.processInstanceId())) {
            return;
        }
        Instant start = parseIsoInstant(request.startTime());
        Instant end = parseIsoInstant(request.endTime());
        List<String> ids = processInstanceMatcher.resolveMatchingProcessInstanceIds(
                request.processInstanceId(), start, end);
        if (ids.isEmpty()) {
            where.append(" AND 1=0");
            return;
        }
        where.append(" AND ch.process_instance_id IN (");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                where.append(',');
            }
            where.append('?');
            params.add(ids.get(i));
        }
        where.append(')');
    }

    private static void appendExcludedFields(StringBuilder where, List<Object> params) {
        List<String> excluded = new ArrayList<>();
        excluded.addAll(ChangeHistoryComponent.INTERNAL_FIELD_BLACKLIST);
        excluded.addAll(SystemAuditFields.ALL);
        where.append(" AND ch.field_name NOT IN (");
        for (int i = 0; i < excluded.size(); i++) {
            if (i > 0) {
                where.append(',');
            }
            where.append('?');
            params.add(excluded.get(i));
        }
        where.append(')');
    }


    private static Instant parseIsoInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(value));
        } catch (Exception ignored) {
            try {
                return java.time.LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        .atZone(java.time.ZoneOffset.UTC)
                        .toInstant();
            } catch (Exception ignored2) {
                return null;
            }
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }


    private record PageIds(List<Long> ids) {
    }
}
