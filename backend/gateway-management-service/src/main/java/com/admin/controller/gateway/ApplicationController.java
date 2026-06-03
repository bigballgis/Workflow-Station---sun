package com.admin.controller.gateway;

import com.admin.entity.gateway.Application;
import com.admin.entity.gateway.Credential;
import com.admin.service.gateway.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/gateway/applications")
@RequiredArgsConstructor
@Tag(name = "Gateway Application Management", description = "Manage gateway applications and credentials")
public class ApplicationController {

    private final ApplicationService appService;

    @PostMapping
    @Operation(summary = "Create application")
    public ResponseEntity<Application> createApp(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestBody Application app) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(appService.createApp(tenantId, app));
    }

    @GetMapping
    @Operation(summary = "List applications")
    public ResponseEntity<Page<Application>> listApps(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestParam(required = false) String keyword,
            Pageable pageable) {
        return ResponseEntity.ok(appService.listApps(tenantId, keyword, pageable));
    }

    @GetMapping("/{appId}")
    @Operation(summary = "Get application detail")
    public ResponseEntity<Application> getApp(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable Long appId) {
        return appService.getApp(tenantId, appId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{appId}")
    @Operation(summary = "Update application")
    public ResponseEntity<Application> updateApp(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable Long appId,
            @RequestBody Application update) {
        return ResponseEntity.ok(appService.updateApp(tenantId, appId, update));
    }

    @GetMapping("/{appId}/credentials")
    @Operation(summary = "List credentials for application")
    public ResponseEntity<Page<Credential>> listCredentials(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable Long appId,
            Pageable pageable) {
        return ResponseEntity.ok(appService.listCredentials(tenantId, appId, pageable));
    }

    @PostMapping("/{appId}/credentials")
    @Operation(summary = "Create credential for application")
    public ResponseEntity<Credential> createCredential(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable Long appId,
            @RequestBody Credential credential) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(appService.createCredential(tenantId, appId, credential));
    }
}
