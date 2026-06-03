package com.admin.controller.gateway;

import com.admin.entity.gateway.ComplianceCheck;
import com.admin.service.gateway.ComplianceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/gateway")
@RequiredArgsConstructor
public class ComplianceController {

    private final ComplianceService complianceService;

    /**
     * Evaluate compliance for a release (pre-publish check).
     */
    @PostMapping("/releases/{releaseId}/compliance-check")
    public ResponseEntity<ComplianceCheck> evaluate(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long releaseId) {
        return ResponseEntity.ok(complianceService.evaluate(tenantId, releaseId, userId));
    }

    /**
     * Get latest compliance check for a release.
     */
    @GetMapping("/releases/{releaseId}/compliance-check")
    public ResponseEntity<Map<String, Object>> getLatestCheck(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable Long releaseId) {
        Optional<ComplianceCheck> check = complianceService.getLatestCheck(releaseId);
        if (check.isEmpty()) {
            return ResponseEntity.ok(Map.of("passed", true, "violations", List.of(), "warnings", List.of()));
        }
        ComplianceCheck c = check.get();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", c.getPassed());
        result.put("violations", c.getViolationsJson());
        result.put("warnings", c.getWarningsJson());
        result.put("checkedAt", c.getCheckedAt().toString());
        return ResponseEntity.ok(result);
    }
}
