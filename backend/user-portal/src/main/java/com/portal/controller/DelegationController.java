package com.portal.controller;

import com.portal.component.DelegationComponent;
import com.portal.dto.ApiResponse;
import com.portal.dto.DelegationRuleRequest;
import com.portal.dto.PageResponse;
import com.portal.entity.DelegationAudit;
import com.portal.entity.DelegationRule;
import com.platform.common.i18n.I18nService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 委托管理API
 */
@Tag(name = "委托管理", description = "委托规则管理和代理任务处理")
@RestController
@RequestMapping("/delegations")
@RequiredArgsConstructor
public class DelegationController {

    private final DelegationComponent delegationComponent;
    private final I18nService i18nService;

    @Operation(summary = "获取委托规则列表")
    @GetMapping
    public ApiResponse<List<DelegationRule>> getDelegationRules(
            @RequestHeader("X-User-Id") String userId) {
        List<DelegationRule> rules = delegationComponent.getDelegationRules(userId);
        return ApiResponse.success(rules);
    }

    @Operation(summary = "获取有效委托规则")
    @GetMapping("/active")
    public ApiResponse<List<DelegationRule>> getActiveDelegationRules(
            @RequestHeader("X-User-Id") String userId) {
        List<DelegationRule> rules = delegationComponent.getActiveDelegationRules(userId);
        return ApiResponse.success(rules);
    }

    @Operation(summary = "创建委托规则")
    @PostMapping
    public ApiResponse<DelegationRule> createDelegationRule(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody DelegationRuleRequest request) {
        DelegationRule rule = delegationComponent.createDelegationRule(userId, request);
        return ApiResponse.success(i18nService.getMessage("portal.delegation_created"), rule);
    }

    @Operation(summary = "更新委托规则")
    @PutMapping("/{ruleId}")
    public ApiResponse<DelegationRule> updateDelegationRule(
            @PathVariable Long ruleId,
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody DelegationRuleRequest request) {
        DelegationRule rule = delegationComponent.updateDelegationRule(ruleId, userId, request);
        return ApiResponse.success(i18nService.getMessage("portal.delegation_updated"), rule);
    }

    @Operation(summary = "删除委托规则")
    @DeleteMapping("/{ruleId}")
    public ApiResponse<Void> deleteDelegationRule(
            @PathVariable Long ruleId,
            @RequestHeader("X-User-Id") String userId) {
        delegationComponent.deleteDelegationRule(ruleId, userId);
        return ApiResponse.success(i18nService.getMessage("portal.delegation_deleted"), null);
    }

    @Operation(summary = "暂停委托规则")
    @PostMapping("/{ruleId}/suspend")
    public ApiResponse<DelegationRule> suspendDelegationRule(
            @PathVariable Long ruleId,
            @RequestHeader("X-User-Id") String userId) {
        DelegationRule rule = delegationComponent.suspendDelegationRule(ruleId, userId);
        return ApiResponse.success(i18nService.getMessage("portal.delegation_suspended"), rule);
    }

    @Operation(summary = "恢复委托规则")
    @PostMapping("/{ruleId}/resume")
    public ApiResponse<DelegationRule> resumeDelegationRule(
            @PathVariable Long ruleId,
            @RequestHeader("X-User-Id") String userId) {
        DelegationRule rule = delegationComponent.resumeDelegationRule(ruleId, userId);
        return ApiResponse.success(i18nService.getMessage("portal.delegation_resumed"), rule);
    }

    @Operation(summary = "获取代理任务（委托给我的）")
    @GetMapping("/proxy-tasks")
    public ApiResponse<List<DelegationRule>> getProxyTasks(
            @RequestHeader("X-User-Id") String userId) {
        List<DelegationRule> delegations = delegationComponent.getDelegationsForDelegate(userId);
        return ApiResponse.success(delegations);
    }

    @Operation(summary = "获取委托人ID列表")
    @GetMapping("/delegators")
    public ApiResponse<List<String>> getDelegatorIds(
            @RequestHeader("X-User-Id") String userId) {
        List<String> delegatorIds = delegationComponent.getDelegatorIds(userId);
        return ApiResponse.success(delegatorIds);
    }

    @Operation(summary = "检查代理权限")
    @GetMapping("/check-proxy")
    public ApiResponse<Boolean> checkProxyPermission(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam String delegatorId,
            @RequestParam(required = false) String processType,
            @RequestParam(required = false) String priority) {
        boolean canProcess = delegationComponent.canProcessAsDelegate(
                userId, delegatorId, processType, priority);
        return ApiResponse.success(canProcess);
    }

    @Operation(summary = "获取委托审计记录")
    @GetMapping("/audit")
    public ApiResponse<PageResponse<DelegationAudit>> getDelegationAuditRecords(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<DelegationAudit> auditPage = delegationComponent.getDelegationAuditRecords(
                userId, PageRequest.of(page, size));
        PageResponse<DelegationAudit> response = PageResponse.of(
                auditPage.getContent(), page, size, auditPage.getTotalElements());
        return ApiResponse.success(response);
    }
}
