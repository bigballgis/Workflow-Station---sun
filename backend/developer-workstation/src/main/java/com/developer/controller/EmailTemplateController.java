package com.developer.controller;

import com.developer.component.EmailTemplateComponent;
import com.developer.dto.EmailTemplateRequest;
import com.developer.dto.EmailTemplateResponse;
import com.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/function-units/{functionUnitId}/email-templates")
@RequiredArgsConstructor
@Tag(name = "邮件模板", description = "功能单元级邮件模板管理")
public class EmailTemplateController {

    private final EmailTemplateComponent emailTemplateComponent;

    @GetMapping
    @Operation(summary = "列出功能单元的邮件模板")
    public ResponseEntity<ApiResponse<List<EmailTemplateResponse>>> list(@PathVariable Long functionUnitId) {
        return ResponseEntity.ok(ApiResponse.success(emailTemplateComponent.listByFunctionUnitId(functionUnitId)));
    }

    @GetMapping("/{templateId}")
    @Operation(summary = "获取模板详情")
    public ResponseEntity<ApiResponse<EmailTemplateResponse>> get(
            @PathVariable Long functionUnitId,
            @PathVariable Long templateId) {
        return ResponseEntity.ok(ApiResponse.success(emailTemplateComponent.getById(functionUnitId, templateId)));
    }

    @PostMapping
    @Operation(summary = "创建邮件模板")
    public ResponseEntity<ApiResponse<EmailTemplateResponse>> create(
            @PathVariable Long functionUnitId,
            @Valid @RequestBody EmailTemplateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(emailTemplateComponent.create(functionUnitId, request)));
    }

    @PutMapping("/{templateId}")
    @Operation(summary = "更新邮件模板")
    public ResponseEntity<ApiResponse<EmailTemplateResponse>> update(
            @PathVariable Long functionUnitId,
            @PathVariable Long templateId,
            @Valid @RequestBody EmailTemplateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                emailTemplateComponent.update(functionUnitId, templateId, request)));
    }

    @DeleteMapping("/{templateId}")
    @Operation(summary = "删除邮件模板")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long functionUnitId,
            @PathVariable Long templateId) {
        emailTemplateComponent.delete(functionUnitId, templateId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
