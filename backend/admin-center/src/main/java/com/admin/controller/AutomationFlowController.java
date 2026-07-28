package com.admin.controller;

import com.admin.dto.response.AutomationFlowSummary;
import com.admin.service.AutomationFlowService;
import com.fasterxml.jackson.databind.JsonNode;
import com.platform.common.dto.ApiResponse;
import com.platform.security.util.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 自动化 flow 迁移管理（uat 导出 → prod 导入的发布通道）。
 *
 * <p>权限:与 {@link AutomationPieceController} 同模式——Spring 层 permitAll,
 * 此处显式做 systemadmin 校验。{@code /resolve} 例外:它是引擎部署期的
 * 服务间调用,以 C-3 的 X-Service-Token 共享密钥门禁(无用户上下文)。</p>
 */
@Slf4j
@RestController
@RequestMapping("/automation/flows")
@RequiredArgsConstructor
public class AutomationFlowController {

    private static final String ERR_FORBIDDEN = "FORBIDDEN";
    private static final String SYSTEM_ADMIN_PERMISSION = "system:admin";

    /** 单个 FU 的 service task 数量级远低于此；上限只为挡异常大包 */
    private static final int MAX_RESTORE_FLOWS = 50;

    private final AutomationFlowService automationFlowService;

    /** C-3 共享密钥（docs/ap-integration/DECISIONS.md#d6）；空 = resolve 端点关闭 */
    @Value("${service.internal-token:}")
    private String serviceInternalToken;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AutomationFlowSummary>>> listFlows() {
        if (!isSystemAdmin()) {
            return forbidden();
        }
        return ResponseEntity.ok(ApiResponse.success(automationFlowService.listFlows()));
    }

    @GetMapping("/{flowId}/export")
    public ResponseEntity<byte[]> exportFlow(@PathVariable String flowId) {
        if (!isSystemAdmin()) {
            return ResponseEntity.status(403).build();
        }
        AutomationFlowService.FlowExportFile file = automationFlowService.exportFlow(flowId);
        log.info("Automation flow exported: {} by {}", flowId,
                SecurityContextUtils.getCurrentUsername());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file.filename())
                .contentType(MediaType.APPLICATION_JSON)
                .body(file.content());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> importFlow(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean publish) throws IOException {
        if (!isSystemAdmin()) {
            return forbidden();
        }
        AutomationFlowService.FlowImportResult result =
                automationFlowService.importFlow(file.getBytes(), publish);
        log.info("Automation flow imported: {} (key={}, created={}, published={}) by {}",
                result.flowId(), result.flowKey(), result.created(), result.published(),
                SecurityContextUtils.getCurrentUsername());
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "flowId", result.flowId(),
                "flowKey", result.flowKey(),
                "displayName", result.displayName(),
                "created", result.created(),
                "published", result.published())));
    }

    /**
     * 启停:prod 运维的主控制,可逆且保留执行历史。启用要求已有发布版本。
     */
    @PostMapping("/{flowId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> setFlowStatus(
            @PathVariable String flowId,
            @RequestParam boolean enabled) {
        if (!isSystemAdmin()) {
            return forbidden();
        }
        automationFlowService.setFlowEnabled(flowId, enabled);
        log.info("Automation flow {}: {} by {}", enabled ? "enabled" : "disabled", flowId,
                SecurityContextUtils.getCurrentUsername());
        return ResponseEntity.ok(ApiResponse.success(Map.of("flowId", flowId, "enabled", enabled)));
    }

    /**
     * 删除（不可逆,执行历史随 CASCADE 一并消失）。被 FU 的 BPMN 引用时 409,
     * 响应体带占用它的 FU 名称;{@code force=true} 强删。
     */
    @DeleteMapping("/{flowId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteFlow(
            @PathVariable String flowId,
            @RequestParam(defaultValue = "false") boolean force) {
        if (!isSystemAdmin()) {
            return forbidden();
        }
        try {
            automationFlowService.deleteFlow(flowId, force);
        } catch (AutomationFlowService.FlowInUseException e) {
            return ResponseEntity.status(409).body(ApiResponse.error("FLOW_IN_USE",
                    String.join(", ", e.getFunctionUnitNames())));
        }
        log.info("Automation flow deleted: {} (force={}) by {}", flowId, force,
                SecurityContextUtils.getCurrentUsername());
        return ResponseEntity.ok(ApiResponse.success(Map.of("flowId", flowId)));
    }

    /**
     * connection 清单比对（导入前预检）:前端解析导出包的 connections 清单后调用,
     * 返回各 externalId 在目标 project 是否已存在。仅提示,不阻塞导入。
     */
    @PostMapping("/connections-check")
    public ResponseEntity<ApiResponse<List<AutomationFlowService.ConnectionCheckItem>>> checkConnections(
            @RequestBody ConnectionsCheckRequest request) {
        if (!isSystemAdmin()) {
            return forbidden();
        }
        List<String> ids = request != null && request.externalIds() != null
                ? request.externalIds() : List.of();
        return ResponseEntity.ok(ApiResponse.success(automationFlowService.checkConnections(ids)));
    }

    public record ConnectionsCheckRequest(List<String> externalIds) {}

    /**
     * FU 导出随包携带 flow（DW 的 FunctionUnitExporter 调用）：按 BPMN 里的
     * {@code ap:flowId} 取可携带 JSON。与 {@code /resolve} 同门禁——服务间调用,
     * 无用户上下文,以 C-3 的 X-Service-Token 把关。
     */
    @GetMapping("/internal/export")
    public ResponseEntity<byte[]> exportFlowForService(
            @RequestParam String ref,
            @RequestHeader(value = "X-Service-Token", required = false) String serviceToken) {
        if (!isValidServiceToken(serviceToken)) {
            return ResponseEntity.status(403).build();
        }
        return automationFlowService.exportFlowByRef(ref)
                .map(file -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(file.content()))
                .orElseGet(() -> ResponseEntity.<byte[]>notFound().build());
    }

    /**
     * FU 导入随包还原 flow（DW 的 FunctionUnitImporter 调用；admin-center 自身的
     * ZIP 导入直接走 service）。只补齐本环境缺失的 flow,不覆盖既有草稿。
     */
    @PostMapping("/internal/restore")
    public ResponseEntity<ApiResponse<List<AutomationFlowService.FlowRestoreResult>>> restoreFlows(
            @RequestBody FlowRestoreRequest request,
            @RequestHeader(value = "X-Service-Token", required = false) String serviceToken) {
        if (!isValidServiceToken(serviceToken)) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.error(ERR_FORBIDDEN, "valid X-Service-Token required"));
        }
        List<JsonNode> flows = request != null && request.flows() != null ? request.flows() : List.of();
        if (flows.size() > MAX_RESTORE_FLOWS) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "BAD_REQUEST", "too many flows in one request (>" + MAX_RESTORE_FLOWS + ")"));
        }
        List<AutomationFlowService.FlowRestoreResult> results =
                automationFlowService.restoreFlows(flows);
        log.info("Automation flows restored from function unit package: {}", results);
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    public record FlowRestoreRequest(List<JsonNode> flows) {}

    /**
     * 引擎部署期解析:BPMN 里的 {@code ap:flowId} → 本环境实际 flowId（Q7）。
     * 找不到映射时 404,引擎侧保留原引用并告警。
     */
    @GetMapping("/resolve")
    public ResponseEntity<ApiResponse<Map<String, String>>> resolveFlowRef(
            @RequestParam String ref,
            @RequestHeader(value = "X-Service-Token", required = false) String serviceToken) {
        if (!isValidServiceToken(serviceToken)) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.error(ERR_FORBIDDEN, "valid X-Service-Token required"));
        }
        Optional<String> resolved = automationFlowService.resolveFlowRef(ref);
        return resolved
                .map(id -> ResponseEntity.ok(ApiResponse.success(Map.of("flowId", id))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private boolean isValidServiceToken(String provided) {
        if (serviceInternalToken == null || serviceInternalToken.isBlank()
                || provided == null || provided.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                serviceInternalToken.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
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
