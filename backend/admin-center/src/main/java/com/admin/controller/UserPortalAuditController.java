package com.admin.controller;

import com.admin.component.UserPortalAuditClient;
import com.admin.dto.response.PageResult;
import com.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
public class UserPortalAuditController {

    private final UserPortalAuditClient userPortalAuditClient;

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

    @PostMapping("/user-portal-audit-logs/list-query")
    @Operation(summary = "Query user portal audit logs (true paging; column filters, sort and grouping)")
    public ApiResponse<Map<String, Object>> queryAuditLogList(@RequestBody Map<String, Object> queryRequest) {
        return ApiResponse.success(userPortalAuditClient.queryAuditLogList(queryRequest));
    }

    @GetMapping("/user-portal-audit-logs/function-units")
    @Operation(summary = "Get distinct function unit codes (with names) that have audit data")
    public ApiResponse<List<Map<String, String>>> getFunctionUnitCodes() {
        return ApiResponse.success(userPortalAuditClient.getFunctionUnitCodes());
    }
}
