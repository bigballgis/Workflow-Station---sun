package com.admin.controller;

import com.admin.component.AutomationFlowRunListQueryComponent;
import com.admin.dto.list.AdminListPage;
import com.admin.dto.request.AutomationFlowRunListQueryRequest;
import com.admin.dto.response.AutomationFlowRunSummary;
import com.admin.service.AutomationFlowRunService;
import com.fasterxml.jackson.databind.JsonNode;
import com.platform.common.dto.ApiResponse;
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
import com.admin.security.AdminAccess;
import com.admin.security.AdminAudience;
import com.admin.security.AdminOwner;

/**
 * 自动化 flow 的执行记录（只读运维视图）。
 *
 * <p>权限：全部端点只读，由 {@code @AdminAccess} 对 SYS_ADMIN、AUDITOR 与 Technical Lead 开放；Technical Lead
 * 只看本人团队与 Public 的运行（D-48），由 {@code AdminTeamScope} 判定。不在审计切面白名单内。</p>
 */
@Slf4j
@RestController
@RequestMapping("/automation/flow-runs")
@RequiredArgsConstructor
@AdminAccess(AdminAudience.TEAM_READ)
public class AutomationFlowRunController {

    private final AutomationFlowRunService automationFlowRunService;
    private final AutomationFlowRunListQueryComponent runListQueryComponent;

    // Read-only for Technical Leads (their teams and Public, D-48) and Auditors; @AdminAccess admits them.
    @AdminAccess(AdminAudience.TEAM_READ)
    @PostMapping("/query")
    public ResponseEntity<ApiResponse<AdminListPage<AutomationFlowRunSummary>>> queryRuns(
            @RequestBody @Valid AutomationFlowRunListQueryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(runListQueryComponent.query(request)));
    }

    /**
     * 一次运行的完整 JSON（含逐步骤输出）。当前操作人的 AP 会话看不到这条运行
     * （落在别的 project，或执行数据已过保留期被清理）时 404，而不是空详情。
     */
    @AdminAccess(value = AdminAudience.TEAM_READ, owner = AdminOwner.AUTOMATION_RUN, ownerVariable = "runId")
    @GetMapping("/{runId}")
    public ResponseEntity<ApiResponse<JsonNode>> getRun(@PathVariable String runId) {
        return automationFlowRunService.getRunDetail(runId)
                .map(detail -> ResponseEntity.ok(ApiResponse.success(detail)))
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(ApiResponse.error("RUN_NOT_FOUND", "flow run " + runId + " is not available")));
    }
}
