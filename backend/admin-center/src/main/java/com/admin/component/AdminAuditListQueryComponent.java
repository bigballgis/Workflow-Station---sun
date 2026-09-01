package com.admin.component;

import com.admin.audit.AuditActorResolver;
import com.admin.dto.list.AdminListPage;
import com.admin.dto.request.AdminAuditListQueryRequest;
import com.admin.dto.response.AdminAuditListRow;
import com.admin.entity.AuditLog;
import com.admin.enums.AuditAction;
import com.admin.enums.AuditActionConverter;
import com.admin.list.AdminAuditColumnSpec;
import com.admin.list.ListQuerySupport;
import com.admin.repository.AuditLogRepository;
import com.admin.repository.UserRepository;
import com.platform.common.list.ListFilterSql;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Admin Center audit list: COUNT(*) and the page share the toolbar predicate
 * plus column filters. List rows omit TEXT snapshots; those load by id.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuditListQueryComponent {

    static final String LIST_KEY = "admin-audit";

    /** Matches {@code hibernate.jdbc.time_zone} in admin-center application.yml. */
    private static final ZoneId DB_ZONE = ZoneId.of("Asia/Shanghai");

    private static final String PAGE_COLUMNS = "SELECT al.id, al.action, al.resource_type, al.resource_id,"
            + " al.user_id, al.user_name, al.ip_address, al.success, al.duration_ms, al.timestamp";

    private static final AuditActionConverter ACTION_CONVERTER = new AuditActionConverter();

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AdminListPage<AdminAuditListRow> query(AdminAuditListQueryRequest request) {
        long started = System.nanoTime();
        ListFilterSql filterSql = AdminAuditColumnSpec.sql();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" FROM admin_audit_logs al WHERE 1=1");
        appendToolbar(where, params, request);
        where.append(filterSql.whereClause(request.filters(), params));

        ResultSetExtractor<Long> countExtractor = rs -> rs.next() ? rs.getLong(1) : 0L;
        long total = ListQuerySupport.requireCount(
                ListQuerySupport.query(jdbcTemplate, "SELECT COUNT(*)" + where, params, countExtractor),
                LIST_KEY);

        List<AdminAuditListRow> rows = loadPageRows(filterSql, where.toString(), params, request);
        ListQuerySupport.logIfSlow(log, LIST_KEY, request.page(), request.size(), total, started);
        return new AdminListPage<>(AdminAuditColumnSpec.columns(), rows,
                request.page(), request.size(), total);
    }

    public Optional<AuditLog> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        Optional<AuditLog> found = auditLogRepository.findById(id);
        found.ifPresent(log -> AuditActorResolver.enrichOperatorUsernames(List.of(log), userRepository));
        return found;
    }

    private List<AdminAuditListRow> loadPageRows(ListFilterSql filterSql, String where, List<Object> params,
                                                 AdminAuditListQueryRequest request) {
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.size());
        pageParams.add(request.page() * request.size());
        String orderBy = filterSql.orderBy(request.sortField(), request.sortDirection());
        String sql = PAGE_COLUMNS + where + orderBy + " LIMIT ? OFFSET ?";
        ResultSetExtractor<List<RawListRow>> extractor = rs -> {
            List<RawListRow> raw = new ArrayList<>();
            while (rs.next()) {
                raw.add(readRaw(rs));
            }
            return raw;
        };
        return toListRows(ListQuerySupport.query(jdbcTemplate, sql, pageParams, extractor));
    }

    private List<AdminAuditListRow> toListRows(List<RawListRow> raw) {
        if (raw.isEmpty()) {
            return List.of();
        }
        Set<String> userIds = new HashSet<>();
        for (RawListRow row : raw) {
            if (row.userId() != null) {
                userIds.add(row.userId());
            }
        }
        Map<String, String> usernames = AuditActorResolver.usernamesByUserId(userIds, userRepository);
        List<AdminAuditListRow> rows = new ArrayList<>(raw.size());
        for (RawListRow row : raw) {
            rows.add(row.toDto(usernames));
        }
        return rows;
    }

    private static RawListRow readRaw(ResultSet rs) throws SQLException {
        AuditAction action = ACTION_CONVERTER.convertToEntityAttribute(rs.getString("action"));
        Boolean success = rs.getObject("success", Boolean.class);
        Integer durationMs = rs.getObject("duration_ms", Integer.class);
        LocalDateTime ts = rs.getObject("timestamp", LocalDateTime.class);
        String createdAt = ts == null ? null : ts.atZone(DB_ZONE).toInstant().toString();
        return new RawListRow(
                rs.getString("id"),
                action != null ? action.name() : null,
                rs.getString("resource_type"),
                rs.getString("resource_id"),
                rs.getString("user_id"),
                rs.getString("user_name"),
                rs.getString("ip_address"),
                Boolean.TRUE.equals(success) ? "SUCCESS" : "FAILED",
                durationMs,
                createdAt);
    }

    private static void appendToolbar(StringBuilder where, List<Object> params,
                                      AdminAuditListQueryRequest request) {
        if (notBlank(request.action())) {
            where.append(" AND al.action = ?");
            params.add(request.action());
        }
        if (notBlank(request.resourceType())) {
            where.append(" AND al.resource_type = ?");
            params.add(request.resourceType());
        }
        if (notBlank(request.username())) {
            where.append(" AND al.user_name = ?");
            params.add(request.username());
        }
        if (notBlank(request.result())) {
            where.append(" AND al.success = ?");
            params.add("SUCCESS".equalsIgnoreCase(request.result()));
        }
        if (notBlank(request.ipAddress())) {
            where.append(" AND al.ip_address = ?");
            params.add(request.ipAddress().trim());
        }
        if (notBlank(request.resourceId())) {
            where.append(" AND al.resource_id = ?");
            params.add(request.resourceId());
        }
        if (notBlank(request.startTime())) {
            where.append(" AND al.timestamp >= ?");
            params.add(java.sql.Timestamp.from(java.time.Instant.parse(request.startTime())));
        }
        if (notBlank(request.endTime())) {
            where.append(" AND al.timestamp <= ?");
            params.add(java.sql.Timestamp.from(java.time.Instant.parse(request.endTime())));
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private record RawListRow(
            String id,
            String action,
            String resourceType,
            String resourceId,
            String userId,
            String userName,
            String ipAddress,
            String result,
            Integer duration,
            String createdAt) {

        AdminAuditListRow toDto(Map<String, String> usernames) {
            String username = usernames.get(userId);
            if (username == null) {
                username = AuditActorResolver.isUnknown(userName) ? userId : userName;
            }
            return new AdminAuditListRow(id, action, resourceType, resourceId,
                    username, ipAddress, result, duration, createdAt);
        }
    }
}
