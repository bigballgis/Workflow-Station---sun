package com.admin.controller;

import com.admin.dto.response.AutomationPieceSummary;
import com.admin.service.AutomationPieceService;
import com.platform.common.dto.ApiResponse;
import com.platform.security.util.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 自动化组件(piece)管理 — P1 只读目录 + 导出。
 *
 * <p>权限:本服务 Spring 层 permitAll(Kong 做边缘认证),故此处显式做 systemadmin
 * 校验(SYS_ADMIN / SUPER_ADMIN / 权限 {@code system:admin}),与 LdapSyncController 同模式。</p>
 *
 * <p>P2(import/delete/启停)将经 AP API 走写路径,本控制器当前不含任何写操作。</p>
 */
@Slf4j
@RestController
@RequestMapping("/automation/pieces")
@RequiredArgsConstructor
public class AutomationPieceController {

    private static final String ERR_FORBIDDEN = "FORBIDDEN";
    private static final String SYSTEM_ADMIN_PERMISSION = "system:admin";

    private final AutomationPieceService automationPieceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AutomationPieceSummary>>> listPieces() {
        if (!isSystemAdmin()) {
            return forbidden();
        }
        return ResponseEntity.ok(ApiResponse.success(automationPieceService.listPieces()));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportPiece(
            @RequestParam String name,
            @RequestParam String version) {
        if (!isSystemAdmin()) {
            return ResponseEntity.status(403).build();
        }
        AutomationPieceService.PieceExportFile file = automationPieceService.exportPiece(name, version);
        log.info("Automation piece exported: {}@{} by {}", name, version,
                SecurityContextUtils.getCurrentUsername());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file.filename())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    // ==================== P2 写路径(均代理 AP API,SYS_ADMIN 专属) ====================

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, String>>> importPiece(
            @RequestParam("file") MultipartFile file) throws IOException {
        if (!isSystemAdmin()) {
            return forbidden();
        }
        AutomationPieceService.PieceImportResult result =
                automationPieceService.importPiece(file.getBytes(), file.getOriginalFilename());
        log.info("Automation piece imported: {}@{} by {}", result.name(), result.version(),
                SecurityContextUtils.getCurrentUsername());
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "name", result.name(),
                "version", result.version(),
                "displayName", result.displayName())));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> deletePiece(
            @RequestParam String name,
            @RequestParam String version,
            @RequestParam(defaultValue = "false") boolean force) {
        if (!isSystemAdmin()) {
            return forbidden();
        }
        try {
            automationPieceService.deletePiece(name, version, force);
        } catch (AutomationPieceService.PieceInUseException e) {
            return ResponseEntity.status(409).body(ApiResponse.error("PIECE_IN_USE",
                    String.valueOf(e.getFlowCount())));
        }
        log.info("Automation piece deleted: {}@{} (force={}) by {}", name, version, force,
                SecurityContextUtils.getCurrentUsername());
        return ResponseEntity.ok(ApiResponse.success(Map.of("name", name, "version", version)));
    }

    @PostMapping("/toggle")
    public ResponseEntity<ApiResponse<Map<String, Object>>> togglePiece(
            @RequestParam String name,
            @RequestParam boolean disabled) {
        if (!isSystemAdmin()) {
            return forbidden();
        }
        automationPieceService.setPieceDisabled(name, disabled);
        log.info("Automation piece {}: {} by {}", disabled ? "disabled" : "enabled", name,
                SecurityContextUtils.getCurrentUsername());
        return ResponseEntity.ok(ApiResponse.success(Map.of("name", name, "disabled", disabled)));
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
