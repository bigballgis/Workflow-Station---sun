package com.admin.controller.gateway;

import com.admin.entity.gateway.GatewayRelease;
import com.admin.entity.gateway.PublishHistory;
import com.admin.service.gateway.GatewayAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/gateway/audit")
@RequiredArgsConstructor
@Tag(name = "Gateway Audit", description = "Query gateway domain audit logs and release history")
public class GatewayAuditController {

    private final GatewayAuditService auditService;

    @GetMapping
    @Operation(summary = "List gateway audit logs (publish/rollback history)")
    public ResponseEntity<Page<PublishHistory>> listAuditLogs(
            @RequestHeader("X-Tenant-Id") String tenantId,
            Pageable pageable) {
        return ResponseEntity.ok(auditService.listAuditLogs(tenantId, pageable));
    }

    @GetMapping("/releases")
    @Operation(summary = "List all releases (audit view)")
    public ResponseEntity<Page<GatewayRelease>> listReleases(
            @RequestHeader("X-Tenant-Id") String tenantId,
            Pageable pageable) {
        return ResponseEntity.ok(auditService.listReleases(tenantId, pageable));
    }
}
