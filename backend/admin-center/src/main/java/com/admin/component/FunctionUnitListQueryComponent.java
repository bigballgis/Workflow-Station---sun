package com.admin.component;

import com.admin.dto.list.AdminListPage;
import com.admin.dto.request.FunctionUnitListQueryRequest;
import com.admin.dto.response.FunctionUnitInfo;
import com.admin.entity.FunctionUnit;
import com.admin.enums.FunctionUnitStatus;
import com.admin.list.FunctionUnitColumnSpec;

import com.admin.list.ListQuerySupport;
import com.admin.repository.FunctionUnitRepository;
import com.admin.security.AdminTeamScope;
import com.admin.service.UserReferenceResolver;
import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListFilterSql;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Function Unit list and archive: DISTINCT ON (code) picks the highest version first,
 * then COUNT(*) / groups / LIMIT share the keyword + column-filter predicate.
 * Grouping writes {@link FunctionUnitInfo} only.
 *
 * <p>A Technical Lead sees only the codes of the teams they lead, each row saying whether the caller may change it
 * ({@code canManage}); both come from {@link AdminTeamScope} (§8.2, D-45).</p>
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
    private final AdminTeamScope teamScope;

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
        AdminTeamScope.appendCodeFilter(where, params, "fu.code", teamScope.visibleFunctionUnitCodes());
        appendKeyword(where, params, request.keyword());
        where.append(filterSql.whereClause(request.filters(), params));

        ResultSetExtractor<Long> countExtractor = rs -> rs.next() ? rs.getLong(1) : 0L;
        long total = ListQuerySupport.requireCount(
                ListQuerySupport.query(jdbcTemplate, "SELECT COUNT(*)" + where, params, countExtractor),
                listKey);


        PageIds pageIds = loadPageIds(filterSql, where.toString(), params, request);
        List<FunctionUnitInfo> rows = toRows(pageIds.ids(), archived);
        ListQuerySupport.logIfSlow(log, listKey, request.page(), request.size(), total, started);
        return new AdminListPage<>(columns, rows, request.page(), request.size(), total);
    }

    private PageIds loadPageIds(ListFilterSql filterSql, String where, List<Object> params,
                                FunctionUnitListQueryRequest request) {
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.size());
        pageParams.add(request.page() * request.size());
        String orderBy = filterSql.orderBy(request.sortField(), request.sortDirection());
        String sql = "SELECT fu.id" + where + orderBy + " LIMIT ? OFFSET ?";
        ResultSetExtractor<PageIds> extractor = rs -> {
            List<String> ids = new ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getString("id"));
            }
            return new PageIds(ids);
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
            FunctionUnitInfo row = FunctionUnitInfo.fromEntity(entity);
            row.setCanManage(teamScope.mayManageFunctionUnit(entity.getCode()));
            ordered.add(row);
        }
        if (archived) {
            enrichUpdatedBy(ordered);
        }
        enrichTeams(ordered);
        return ordered;
    }

    /** The team of each row's code: its latest imported version row's team (§8.1); one query per page. */
    private void enrichTeams(List<FunctionUnitInfo> rows) {
        List<Object> codes = new ArrayList<>(rows.stream().map(FunctionUnitInfo::getCode).distinct().toList());
        String placeholders = String.join(",", Collections.nCopies(codes.size(), "?"));
        Map<String, String[]> teams = ListQuerySupport.query(jdbcTemplate,
                "SELECT latest.code, latest.team_code, vg.name AS team_name FROM"
                        + " (SELECT DISTINCT ON (code) code, team_code FROM sys_function_units"
                        + " WHERE code IN (" + placeholders + ")"
                        + " ORDER BY code, " + FunctionUnitRepository.LATEST_IMPORT_ORDER + ") latest"
                        + " LEFT JOIN sys_virtual_groups vg ON vg.code = latest.team_code AND vg.type = 'DEVELOPER'",
                codes, rs -> {
                    Map<String, String[]> byCode = new HashMap<>();
                    while (rs.next()) {
                        byCode.put(rs.getString("code"),
                                new String[]{rs.getString("team_code"), rs.getString("team_name")});
                    }
                    return byCode;
                });
        for (FunctionUnitInfo row : rows) {
            String[] team = teams.get(row.getCode());
            if (team != null) {
                row.setTeamCode(team[0]);
                row.setTeamName(team[1]);
                row.setTeam(team[1] != null ? team[1] : team[0]);
            }
        }
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


    private record PageIds(List<String> ids) {
    }
}
