package com.portal.controller;

import com.portal.component.ChangeHistoryComponent;
import com.portal.component.UserPortalAuditListQueryComponent;
import com.portal.config.PortalInternalApiProperties;
import com.portal.dto.PortalListPage;
import com.portal.dto.UserPortalAuditListQueryRequest;
import com.portal.dto.UserPortalAuditQueryRequest;
import com.portal.dto.UserPortalAuditRecord;
import com.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Internal REST API for admin-center cross-module audit log queries.
 * Protected by X-Internal-Token header — same pattern as InternalRuntimeController.
 */
@Slf4j
@Hidden
@RestController
@RequestMapping("/internal/audit-logs")
@RequiredArgsConstructor
public class InternalAuditController {

    private final PortalInternalApiProperties portalInternalApiProperties;
    private final ChangeHistoryComponent changeHistoryComponent;
    private final UserPortalAuditListQueryComponent userPortalAuditListQueryComponent;

    @PostMapping("/query")
    public ApiResponse<Map<String, Object>> queryAuditLogs(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody UserPortalAuditQueryRequest request) {
        portalInternalApiProperties.requireValidToken(token);
        Page<UserPortalAuditRecord> page = changeHistoryComponent.queryGlobalAuditLogs(request);
        Map<String, Object> result = Map.of(
                "content", page.getContent(),
                "totalElements", page.getTotalElements(),
                "totalPages", page.getTotalPages(),
                "page", page.getNumber(),
                "size", page.getSize(),
                "first", page.isFirst(),
                "last", page.isLast()
        );
        return ApiResponse.success(result);
    }

    @PostMapping("/list-query")
    public ApiResponse<PortalListPage<UserPortalAuditRecord>> queryAuditLogList(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody @Valid UserPortalAuditListQueryRequest request) {
        portalInternalApiProperties.requireValidToken(token);
        return ApiResponse.success(userPortalAuditListQueryComponent.query(request));
    }

    @GetMapping("/function-units")
    public ApiResponse<List<Map<String, String>>> getFunctionUnitCodes(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        portalInternalApiProperties.requireValidToken(token);
        return ApiResponse.success(changeHistoryComponent.getDistinctFunctionUnitCodes());
    }
}
