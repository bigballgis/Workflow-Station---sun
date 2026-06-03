package com.admin.controller.module;

import com.admin.dto.module.FrontendModuleHealthDTO;
import com.admin.dto.module.FrontendModuleRuntimeDTO;
import com.admin.dto.module.FrontendModuleVersionDTO;
import com.admin.entity.module.FrontendModuleRegistry;
import com.admin.service.module.FrontendModuleService;
import com.admin.service.module.MfePackageService;
import com.admin.dto.module.ImportPackageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.web.multipart.MultipartFile;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/frontend-modules")
@RequiredArgsConstructor
@Tag(name = "Frontend Module Governance", description = "Manage frontend module registry and runtime config")
public class FrontendModuleController {

    private final FrontendModuleService service;
    private final MfePackageService mfePackageService;

    // ==================== Runtime API (host consumption) ====================

    @GetMapping("/runtime")
    @Operation(summary = "Get runtime module config for host app")
    public ResponseEntity<List<FrontendModuleRuntimeDTO>> getRuntime(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "DEFAULT") String tenantId,
            @RequestParam String hostApp,
            @RequestParam String env) {
        return ResponseEntity.ok(service.getRuntimeConfig(tenantId, hostApp, env));
    }

    // ==================== Management APIs (admin) ====================

    @GetMapping
    @Operation(summary = "List frontend modules")
    public ResponseEntity<Page<FrontendModuleRegistry>> list(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "DEFAULT") String tenantId,
            @RequestParam String hostApp,
            @RequestParam String env,
            @RequestParam(required = false) Boolean enabled,
            Pageable pageable) {
        return ResponseEntity.ok(service.list(tenantId, hostApp, env, enabled, pageable));
    }

    @PostMapping
    @Operation(summary = "Create frontend module config")
    public ResponseEntity<FrontendModuleRegistry> create(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "DEFAULT") String tenantId,
            @RequestBody FrontendModuleRegistry module) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(tenantId, module));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update frontend module config")
    public ResponseEntity<FrontendModuleRegistry> update(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "DEFAULT") String tenantId,
            @PathVariable Long id,
            @RequestBody FrontendModuleRegistry update) {
        return ResponseEntity.ok(service.update(tenantId, id, update));
    }

    @PostMapping("/{id}/enable")
    @Operation(summary = "Enable frontend module")
    public ResponseEntity<FrontendModuleRegistry> enable(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "DEFAULT") String tenantId,
            @PathVariable Long id) {
        return ResponseEntity.ok(service.enable(tenantId, id));
    }

    @PostMapping("/{id}/disable")
    @Operation(summary = "Disable frontend module")
    public ResponseEntity<FrontendModuleRegistry> disable(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "DEFAULT") String tenantId,
            @PathVariable Long id) {
        return ResponseEntity.ok(service.disable(tenantId, id));
    }

    @PostMapping("/{id}/switch-version")
    @Operation(summary = "Switch module version")
    public ResponseEntity<FrontendModuleRegistry> switchVersion(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "DEFAULT") String tenantId,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(service.switchVersion(tenantId, id,
                body.get("version"), body.get("remoteEntryUrl")));
    }

    @PostMapping("/{id}/rollback-version")
    @Operation(summary = "Rollback module version")
    public ResponseEntity<FrontendModuleRegistry> rollbackVersion(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "DEFAULT") String tenantId,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(service.rollbackVersion(tenantId, id,
                body.get("targetVersion")));
    }

    @GetMapping("/{id}/versions")
    @Operation(summary = "List version history for a module")
    public ResponseEntity<List<FrontendModuleVersionDTO>> getVersions(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "DEFAULT") String tenantId,
            @PathVariable Long id) {
        return ResponseEntity.ok(service.getVersions(tenantId, id));
    }

    @PostMapping("/{id}/health-check")
    @Operation(summary = "Run health check on module remote entry")
    public ResponseEntity<FrontendModuleHealthDTO> healthCheck(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "DEFAULT") String tenantId,
            @PathVariable Long id) {
        return ResponseEntity.ok(service.healthCheck(tenantId, id));
    }

    // ==================== Package Export / Import ====================

    @GetMapping("/{id}/export")
    @Operation(summary = "Export MFE module package as zip")
    public ResponseEntity<InputStreamResource> exportPackage(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "DEFAULT") String tenantId,
            @PathVariable Long id) throws IOException {
        Path zip = mfePackageService.exportPackage(tenantId, id);
        String filename = "mfe-export-" + id + "-" + System.currentTimeMillis() + ".zip";
        InputStreamResource resource = new InputStreamResource(
                new FileInputStream(zip.toFile()));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @PostMapping("/import-package")
    @Operation(summary = "Import MFE module package from zip")
    public ResponseEntity<ImportPackageResult> importPackage(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "DEFAULT") String tenantId,
            @RequestParam("targetEnv") String targetEnv,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(
                mfePackageService.importPackage(tenantId, targetEnv,
                        file.getInputStream()));
    }

}
