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
        return emailConnectionSyncComponent.getCredentials(functionUnitId, connectionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-code/{functionUnitCode}/id")
    @Operation(summary = "按 code 解析功能单元 ID（内部）")
    public ResponseEntity<Map<String, String>> resolveByCode(@PathVariable String functionUnitCode) {
        return emailConnectionSyncComponent.resolveFunctionUnitIdByCode(functionUnitCode)
                .map(id -> ResponseEntity.ok(Map.of("functionUnitId", id)))
                .orElse(ResponseEntity.notFound().build());
    }
}
