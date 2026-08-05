package com.admin.controller;

import com.admin.component.EmailConnectionSyncComponent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Internal API for service-to-service email connection credential lookup.
 */
@RestController
@RequestMapping("/internal/function-units")
@RequiredArgsConstructor
@Tag(name = "内部-邮件连接", description = "工作流引擎内部调用")
public class InternalEmailConnectionController {

    private final EmailConnectionSyncComponent emailConnectionSyncComponent;

    @GetMapping("/{functionUnitId}/connections/{connectionId}/credentials")
    @Operation(summary = "获取邮件连接凭据（内部）")
    public ResponseEntity<Map<String, Object>> getCredentials(
            @PathVariable String functionUnitId,
            @PathVariable String connectionId) {
        try {
            return emailConnectionSyncComponent.getCredentials(functionUnitId, connectionId)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "SYSTEM_SMTP_NOT_CONFIGURED",
                    "message", ex.getMessage() != null ? ex.getMessage() : "System SMTP not configured"
            ));
        }
    }

    @GetMapping("/by-code/{functionUnitCode}/id")
    @Operation(summary = "按 code 解析功能单元 ID（内部）")
    public ResponseEntity<Map<String, String>> resolveByCode(@PathVariable String functionUnitCode) {
        return emailConnectionSyncComponent.resolveFunctionUnitIdByCode(functionUnitCode)
                .map(id -> ResponseEntity.ok(Map.of("functionUnitId", id)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{functionUnitId}/code")
    @Operation(summary = "按功能单元 ID 解析 code（内部）")
    public ResponseEntity<Map<String, String>> resolveCodeById(@PathVariable String functionUnitId) {
        return emailConnectionSyncComponent.resolveFunctionUnitCodeById(functionUnitId)
                .map(code -> ResponseEntity.ok(Map.of("functionUnitCode", code)))
                .orElse(ResponseEntity.notFound().build());
    }
}
