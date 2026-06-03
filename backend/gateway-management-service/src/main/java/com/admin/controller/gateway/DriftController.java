package com.admin.controller.gateway;

import com.admin.entity.gateway.DriftReport;
import com.admin.service.gateway.DriftDetectorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/gateway/drift")
@RequiredArgsConstructor
@Tag(name = "Gateway Drift Detection", description = "Detect drift between metadata SoT and gateway runtime")
public class DriftController {

    private final DriftDetectorService driftService;

    @GetMapping("/reports")
    @Operation(summary = "List drift reports")
    public ResponseEntity<Page<DriftReport>> listReports(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestParam(required = false) Long environmentId,
            Pageable pageable) {
        return ResponseEntity.ok(driftService.listReports(tenantId, environmentId, pageable));
    }

    @GetMapping("/reports/{reportId}")
    @Operation(summary = "Get drift report detail")
    public ResponseEntity<DriftReport> getReport(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable Long reportId) {
        return driftService.getReport(tenantId, reportId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/sync")
    @Operation(summary = "Trigger drift sync for an environment")
    public ResponseEntity<DriftReport> syncEnvironment(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestBody Map<String, Object> body) {
        String environmentCode = (String) body.get("environmentCode");
        return ResponseEntity.ok(driftService.syncEnvironment(tenantId, environmentCode));
    }
}
