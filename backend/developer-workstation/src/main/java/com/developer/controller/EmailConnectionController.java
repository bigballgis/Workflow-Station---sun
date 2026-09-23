package com.developer.controller;

import com.developer.component.EmailConnectionComponent;
import com.developer.dto.EmailConnectionRequest;
import com.developer.dto.EmailConnectionResponse;
import com.developer.dto.VaultEnvOption;
import com.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/function-units/{functionUnitId}/connections")
@RequiredArgsConstructor
@Tag(name = "邮件连接", description = "功能单元级 SMTP 连接管理")
public class EmailConnectionController {

    private final EmailConnectionComponent emailConnectionComponent;

    @GetMapping("/vault-options")
    @Operation(summary = "列出当前环境可用的 VAULT 环境变量")
    public ResponseEntity<ApiResponse<List<VaultEnvOption>>> vaultOptions(
            @PathVariable Long functionUnitId) {
        emailConnectionComponent.listByFunctionUnitId(functionUnitId);
        return ResponseEntity.ok(ApiResponse.success(emailConnectionComponent.listVaultOptions()));
    }

    @GetMapping
    @Operation(summary = "列出功能单元的邮件连接")
    public ResponseEntity<ApiResponse<List<EmailConnectionResponse>>> list(@PathVariable Long functionUnitId) {
        return ResponseEntity.ok(ApiResponse.success(emailConnectionComponent.listByFunctionUnitId(functionUnitId)));
    }

    @GetMapping("/{connectionId}")
    @Operation(summary = "获取连接详情")
    public ResponseEntity<ApiResponse<EmailConnectionResponse>> get(
            @PathVariable Long functionUnitId,
            @PathVariable Long connectionId) {
        return ResponseEntity.ok(ApiResponse.success(emailConnectionComponent.getById(functionUnitId, connectionId)));
    }

    @PostMapping
    @Operation(summary = "创建邮件连接")
    public ResponseEntity<ApiResponse<EmailConnectionResponse>> create(
            @PathVariable Long functionUnitId,
            @Valid @RequestBody EmailConnectionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(emailConnectionComponent.create(functionUnitId, request)));
    }

    @PutMapping("/{connectionId}")
    @Operation(summary = "更新邮件连接")
    public ResponseEntity<ApiResponse<EmailConnectionResponse>> update(
            @PathVariable Long functionUnitId,
            @PathVariable Long connectionId,
            @Valid @RequestBody EmailConnectionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                emailConnectionComponent.update(functionUnitId, connectionId, request)));
    }

    @DeleteMapping("/{connectionId}")
    @Operation(summary = "删除邮件连接")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long functionUnitId,
            @PathVariable Long connectionId) {
        emailConnectionComponent.delete(functionUnitId, connectionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{connectionId}/test")
    @Operation(summary = "测试邮件连接")
    public ResponseEntity<ApiResponse<Map<String, Object>>> test(
            @PathVariable Long functionUnitId,
            @PathVariable Long connectionId,
            @RequestBody Map<String, String> body) {
        String testRecipient = body != null ? body.get("testRecipient") : null;
        return ResponseEntity.ok(ApiResponse.success(
                emailConnectionComponent.testConnection(functionUnitId, connectionId, testRecipient)));
    }
}
