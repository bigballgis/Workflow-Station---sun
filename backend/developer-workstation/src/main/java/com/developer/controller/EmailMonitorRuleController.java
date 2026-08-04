package com.developer.controller;

import com.developer.component.EmailMonitorRuleComponent;
import com.developer.dto.EmailMonitorRuleRequest;
import com.developer.dto.EmailMonitorRuleResponse;
import com.developer.dto.EmailMonitorStartEventBindRequest;
import com.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/function-units/{functionUnitId}/email-monitors")
@RequiredArgsConstructor
@Tag(name = "邮件监听规则", description = "功能单元级入站邮件监听与字段提取规则")
public class EmailMonitorRuleController {

    private final EmailMonitorRuleComponent emailMonitorRuleComponent;

    @GetMapping("/templates")
    @Operation(summary = "列出邮件监听模板（Email Monitors 页）")
    public ResponseEntity<ApiResponse<List<EmailMonitorRuleResponse>>> listTemplates(
            @PathVariable Long functionUnitId) {
        return ResponseEntity.ok(ApiResponse.success(
                emailMonitorRuleComponent.listTemplates(functionUnitId)));
    }

    @GetMapping
    @Operation(summary = "列出功能单元的全部邮件监听规则（含 Start Event 绑定）")
    public ResponseEntity<ApiResponse<List<EmailMonitorRuleResponse>>> list(@PathVariable Long functionUnitId) {
        return ResponseEntity.ok(ApiResponse.success(
                emailMonitorRuleComponent.listByFunctionUnitId(functionUnitId)));
    }

    @GetMapping("/by-start-event/{startEventId}")
    @Operation(summary = "按 BPMN 开始事件 ID 获取绑定规则")
    public ResponseEntity<ApiResponse<EmailMonitorRuleResponse>> getByStartEvent(
            @PathVariable Long functionUnitId,
            @PathVariable String startEventId) {
        return ResponseEntity.ok(ApiResponse.success(
                emailMonitorRuleComponent.getByStartEventId(functionUnitId, startEventId)));
    }

    @GetMapping("/{ruleId}")
    @Operation(summary = "获取监听规则详情")
    public ResponseEntity<ApiResponse<EmailMonitorRuleResponse>> get(
            @PathVariable Long functionUnitId,
            @PathVariable Long ruleId) {
        return ResponseEntity.ok(ApiResponse.success(
                emailMonitorRuleComponent.getById(functionUnitId, ruleId)));
    }

    @PostMapping
    @Operation(summary = "创建监听模板")
    public ResponseEntity<ApiResponse<EmailMonitorRuleResponse>> create(
            @PathVariable Long functionUnitId,
            @Valid @RequestBody EmailMonitorRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                emailMonitorRuleComponent.create(functionUnitId, request)));
    }

    @PutMapping("/{ruleId}")
    @Operation(summary = "更新监听模板")
    public ResponseEntity<ApiResponse<EmailMonitorRuleResponse>> update(
            @PathVariable Long functionUnitId,
            @PathVariable Long ruleId,
            @Valid @RequestBody EmailMonitorRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                emailMonitorRuleComponent.update(functionUnitId, ruleId, request)));
    }

    @DeleteMapping("/{ruleId}")
    @Operation(summary = "删除监听模板")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long functionUnitId,
            @PathVariable Long ruleId) {
        emailMonitorRuleComponent.delete(functionUnitId, ruleId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/start-event-bindings")
    @Operation(summary = "将监听模板绑定到 BPMN Start Event（含过滤条件）")
    public ResponseEntity<ApiResponse<EmailMonitorRuleResponse>> bindStartEvent(
            @PathVariable Long functionUnitId,
            @Valid @RequestBody EmailMonitorStartEventBindRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                emailMonitorRuleComponent.bindStartEvent(functionUnitId, request)));
    }

    @DeleteMapping("/start-event-bindings/{startEventId}")
    @Operation(summary = "解除 Start Event 与监听绑定的关联")
    public ResponseEntity<ApiResponse<Void>> unbindStartEvent(
            @PathVariable Long functionUnitId,
            @PathVariable String startEventId) {
        emailMonitorRuleComponent.unbindStartEvent(functionUnitId, startEventId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
