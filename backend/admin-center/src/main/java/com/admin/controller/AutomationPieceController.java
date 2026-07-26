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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
