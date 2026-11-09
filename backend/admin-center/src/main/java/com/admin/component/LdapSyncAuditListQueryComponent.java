package com.admin.component;

import com.admin.dto.list.AdminListPage;
import com.admin.dto.request.LdapSyncAuditListQueryRequest;
import com.admin.ldap.LdapSyncAudit;
import com.admin.ldap.LdapSyncAuditRepository;
import com.admin.ldap.LdapSyncAuditView;
import com.admin.list.LdapSyncAuditColumnSpec;
import com.admin.list.ListQuerySupport;
import com.platform.common.list.ListFilterSql;
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
 * LDAP sync records page (D-51): COUNT(*) and the page share the toolbar keyword plus column filters; read-only for
 * everyone who sees it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LdapSyncAuditListQueryComponent {

    static final String LIST_KEY = "admin-ldap-sync-audits";

    private static final String FROM = " FROM ac_ldap_sync_audit a WHERE 1=1";

    private final JdbcTemplate jdbcTemplate;
    private final LdapSyncAuditRepository auditRepository;

    public AdminListPage<LdapSyncAuditView> query(LdapSyncAuditListQueryRequest request) {
        long started = System.nanoTime();
        ListFilterSql filterSql = LdapSyncAuditColumnSpec.sql();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(FROM);
        appendKeyword(where, params, request.keyword());
        where.append(filterSql.whereClause(request.filters(), params));

        ResultSetExtractor<Long> countExtractor = rs -> rs.next() ? rs.getLong(1) : 0L;
        long total = ListQuerySupport.requireCount(
                ListQuerySupport.query(jdbcTemplate, "SELECT COUNT(*)" + where, params, countExtractor),
                LIST_KEY);

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.size());
        pageParams.add(request.page() * request.size());
        String sql = "SELECT a.id" + where + filterSql.orderBy(request.sortField(), request.sortDirection())
                + " LIMIT ? OFFSET ?";
        List<String> ids = ListQuerySupport.query(jdbcTemplate, sql, pageParams, rs -> {
            List<String> page = new ArrayList<>();
            while (rs.next()) {
                page.add(rs.getString("id"));
            }
            return page;
        });
        ListQuerySupport.logIfSlow(log, LIST_KEY, request.page(), request.size(), total, started);
        return new AdminListPage<>(LdapSyncAuditColumnSpec.columns(), toRows(ids), request.page(), request.size(),
                total);
    }

    private List<LdapSyncAuditView> toRows(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, LdapSyncAudit> byId = auditRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(LdapSyncAudit::getId, Function.identity()));
        List<LdapSyncAuditView> ordered = new ArrayList<>(ids.size());
        for (String id : ids) {
            LdapSyncAudit audit = byId.get(id);
            if (audit == null) {
                throw new IllegalStateException("ldap-sync page referenced missing record " + id);
            }
            ordered.add(LdapSyncAuditView.from(audit));
        }
        return ordered;
    }

    private static void appendKeyword(StringBuilder where, List<Object> params, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String like = "%" + ListFilterSql.escapeLike(keyword.trim()) + "%";
        where.append(" AND (a.sync_type ILIKE ? OR a.status ILIKE ? OR COALESCE(a.message, '') ILIKE ?"
                + " OR COALESCE(a.groups, '') ILIKE ?)");
        params.add(like);
        params.add(like);
        params.add(like);
        params.add(like);
    }
}
