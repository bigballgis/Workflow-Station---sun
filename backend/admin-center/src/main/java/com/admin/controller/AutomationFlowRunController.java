package com.admin.controller;

import com.admin.component.AutomationFlowRunListQueryComponent;
import com.admin.dto.list.AdminListPage;
import com.admin.dto.request.AutomationFlowRunListQueryRequest;
import com.admin.dto.response.AutomationFlowRunSummary;
import com.admin.service.AutomationFlowRunService;
import com.fasterxml.jackson.databind.JsonNode;
import com.platform.common.dto.ApiResponse;
import com.platform.security.util.SecurityContextUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自动化 flow 的执行记录（只读运维视图）。
 *
 * <p>权限：与 {@link AutomationFlowController} 同模式——Spring 层 permitAll，此处显式做
 * systemadmin 校验。全部端点只读，故不在审计切面白名单内。</p>
 */
@Slf4j
@RestController
@RequestMapping("/automation/flow-runs")
@RequiredArgsConstructor
public class AutomationFlowRunController {

    private static final String ERR_FORBIDDEN = "FORBIDDEN";
    private static final String SYSTEM_ADMIN_PERMISSION = "system:admin";

    private final AutomationFlowRunService automationFlowRunService;
    private final AutomationFlowRunListQueryComponent runListQueryComponent;

    @PostMapping("/query")
    public ResponseEntity<ApiResponse<AdminListPage<AutomationFlowRunSummary>>> queryRuns(
            @RequestBody @Valid AutomationFlowRunListQueryRequest request) {
        if (!isSystemAdmin()) {
            return forbidden();
        }
        return ResponseEntity.ok(ApiResponse.success(runListQueryComponent.query(request)));
    }

    /**
     * 一次运行的完整 JSON（含逐步骤输出）。当前操作人的 AP 会话看不到这条运行
     * （落在别的 project，或执行数据已过保留期被清理）时 404，而不是空详情。
     */
    @GetMapping("/{runId}")
    public ResponseEntity<ApiResponse<JsonNode>> getRun(@PathVariable String runId) {
        if (!isSystemAdmin()) {
            return forbidden();
        }
        return automationFlowRunService.getRunDetail(runId)
                .map(detail -> ResponseEntity.ok(ApiResponse.success(detail)))
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(ApiResponse.error("RUN_NOT_FOUND", "flow run " + runId + " is not available")));
    }

    private boolean isSystemAdmin() {
        return SecurityContextUtils.isSuperAdmin()
                || SecurityContextUtils.hasRole("SYS_ADMIN")
                || SecurityContextUtils.hasRole("SUPER_ADMIN")
                || SecurityContextUtils.hasPermission(SYSTEM_ADMIN_PERMISSION);
    }

    private <T> ResponseEntity<ApiResponse<T>> forbidden() {
        return ResponseEntity.status(403)
                .body(ApiResponse.error(ERR_FORBIDDEN, "system:admin required"));
    }
}
