package com.admin.controller.gateway;

import com.admin.entity.gateway.GovernanceRule;
import com.admin.service.gateway.GovernanceRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/gateway/governance")
@RequiredArgsConstructor
public class GovernanceController {

    private final GovernanceRuleService ruleService;

    /**
     * List governance rules.
     */
    @GetMapping("/rules")
    public ResponseEntity<Page<GovernanceRule>> listRules(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ruleService.listRules(tenantId, PageRequest.of(page, size)));
    }

    /**
     * Create a governance rule.
     */
    @PostMapping("/rules")
    public ResponseEntity<GovernanceRule> createRule(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody GovernanceRule rule) {
        rule.setTenantId(tenantId);
        rule.setCreatedBy(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ruleService.createRule(rule));
    }

    /**
     * Update a governance rule.
     */
    @PutMapping("/rules/{ruleId}")
    public ResponseEntity<GovernanceRule> updateRule(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long ruleId,
            @RequestBody GovernanceRule update) {
        update.setUpdatedBy(userId);
        return ResponseEntity.ok(ruleService.updateRule(ruleId, update));
    }

    /**
     * Delete a governance rule.
     */
    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long ruleId) {
        ruleService.deleteRule(ruleId);
        return ResponseEntity.noContent().build();
    }
}
