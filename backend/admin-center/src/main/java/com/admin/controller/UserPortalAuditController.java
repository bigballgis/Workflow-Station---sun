package com.admin.controller;

import com.admin.component.UserPortalAuditClient;
import com.admin.dto.request.UserPortalAuditListQueryRequest;
import com.admin.dto.response.PageResult;
import com.admin.exception.UpstreamScopeNotAppliedException;
import com.platform.common.i18n.I18nService;
import com.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.admin.security.AdminAccess;
import com.admin.security.AdminAudience;
import com.admin.security.AdminTeamScope;

/**
 * User Portal audit log query API.
 * Proxies requests to user-portal internal API via RestTemplate (UserPortalAuditClient),
 * matching the existing cross-module REST pattern.
 */
@Slf4j
@RestController
@RequestMapping("/security")
@RequiredArgsConstructor
@Tag(name = "User Portal Audit", description = "Cross-process user portal audit log queries")
@AdminAccess(AdminAudience.ADMIN_READ)
public class UserPortalAuditController {

    private final UserPortalAuditClient userPortalAuditClient;
    private final AdminTeamScope teamScope;
    private final I18nService i18nService;

    @AdminAccess(AdminAudience.ADMIN_READ)
    @PostMapping("/user-portal-audit-logs/query")
    @Operation(summary = "Query user portal audit logs with pagination")
    public ApiResponse<PageResult<Map<String, Object>>> queryAuditLogs(
            @RequestBody Map<String, Object> queryRequest,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "timestamp,desc") String sort) {

        // Inject pagination and sort from query params
        queryRequest.put("page", page);
        queryRequest.put("size", size);
        String[] sortParts = sort.split(",");
        if (sortParts.length >= 1) {
            queryRequest.put("sortField", sortParts[0]);
        }
        if (sortParts.length >= 2) {
            queryRequest.put("sortOrder", sortParts[1]);
        }

        Map<String, Object> response = userPortalAuditClient.queryAuditLogs(queryRequest);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = response.get("content") instanceof List
                ? (List<Map<String, Object>>) response.get("content")
                : new ArrayList<>();
        long totalElements = response.get("totalElements") instanceof Number
                ? ((Number) response.get("totalElements")).longValue() : 0L;

        PageResult<Map<String, Object>> pageResult = PageResult.of(content, page, size, totalElements);
        return ApiResponse.success(pageResult);
    }

    /** A Technical Lead reads the audit of the teams they lead; the Portal filters by the codes Admin sends (§8.2). */
    @AdminAccess(AdminAudience.TEAM_READ)
    @PostMapping("/user-portal-audit-logs/list-query")
    @Operation(summary = "Query user portal audit logs (true paging; column filters and sort)")
    public ApiResponse<Map<String, Object>> queryAuditLogList(
            @RequestBody @Valid UserPortalAuditListQueryRequest queryRequest) {
        Set<String> visible = teamScope.visibleFunctionUnitCodes();
        Map<String, Object> page = userPortalAuditClient.queryAuditLogList(queryRequest.withFunctionUnitCodes(visible));
        if (visible != null) {
            requireRowsWithin(page, visible);
        }
        return ApiResponse.success(page);
    }

    /**
     * Fail closed: a Portal that did not apply the code filter (an older version, a regression) returns other teams'
     * rows, and none of them may reach a team-scoped caller.
     */
    private void requireRowsWithin(Map<String, Object> page, Set<String> visible) {
        if (!(page.get("content") instanceof List<?> rows)) {
            return;
        }
        Set<Object> outside = new java.util.LinkedHashSet<>();
        for (Object row : rows) {
            Object code = row instanceof Map<?, ?> map ? map.get("functionUnitCode") : null;
            if (!(code instanceof String text) || !visible.contains(text)) {
                outside.add(code);
            }
        }
        if (!outside.isEmpty()) {
            log.error("User Portal audit page holds rows of function units {} outside the caller's teams; "
                    + "the Portal did not apply functionUnitCodes, page withheld", outside);
            throw new UpstreamScopeNotAppliedException(i18nService.getMessage("admin.audit.up_scope_not_applied"));
        }
    }

    @AdminAccess(AdminAudience.TEAM_READ)
    @GetMapping("/user-portal-audit-logs/function-units")
    @Operation(summary = "Get distinct function unit codes (with names) that have audit data")
    public ApiResponse<List<Map<String, String>>> getFunctionUnitCodes() {
        Set<String> visible = teamScope.visibleFunctionUnitCodes();
        return ApiResponse.success(userPortalAuditClient.getFunctionUnitCodes().stream()
                .filter(unit -> visible == null || visible.contains(unit.get("code")))
                .toList());
    }
}
