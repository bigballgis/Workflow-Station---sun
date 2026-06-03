package com.admin.controller.gateway;

import com.admin.entity.gateway.ApiDefinition;
import com.admin.entity.gateway.ApiVersion;
import com.admin.service.gateway.ApiDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/gateway/apis")
@RequiredArgsConstructor
@Tag(name = "Gateway API Management", description = "Manage gateway API definitions and versions")
public class ApiDefinitionController {

    private final ApiDefinitionService apiDefService;

    @PostMapping
    @Operation(summary = "Create API definition")
    public ResponseEntity<ApiDefinition> createApi(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestBody ApiDefinition apiDef) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(apiDefService.createApi(tenantId, apiDef));
    }

    @GetMapping
    @Operation(summary = "List API definitions")
    public ResponseEntity<Page<ApiDefinition>> listApis(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return ResponseEntity.ok(apiDefService.listApis(tenantId, keyword, status, pageable));
    }

    @GetMapping("/{apiId}")
    @Operation(summary = "Get API definition detail")
    public ResponseEntity<ApiDefinition> getApi(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable Long apiId) {
        return apiDefService.getApi(tenantId, apiId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{apiId}")
    @Operation(summary = "Update API definition")
    public ResponseEntity<ApiDefinition> updateApi(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable Long apiId,
            @RequestBody ApiDefinition update) {
        return ResponseEntity.ok(apiDefService.updateApi(tenantId, apiId, update));
    }

    @GetMapping("/{apiId}/versions")
    @Operation(summary = "List API versions")
    public ResponseEntity<Page<ApiVersion>> listVersions(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable Long apiId,
            Pageable pageable) {
        return ResponseEntity.ok(apiDefService.listVersions(tenantId, apiId, pageable));
    }

    @PostMapping("/{apiId}/versions")
    @Operation(summary = "Create API version")
    public ResponseEntity<ApiVersion> createVersion(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable Long apiId,
            @RequestBody ApiVersion version) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(apiDefService.createVersion(tenantId, apiId, version));
    }

    @PostMapping("/import-openapi")
    @Operation(summary = "Import from OpenAPI spec")
    public ResponseEntity<Map<String, Object>> importOpenApi(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestBody Map<String, Object> openApiSpec) {
        // Phase 1: stub — return accepted with spec content preview
        log.info("[STUB] OpenAPI import requested, spec keys: {}", openApiSpec.keySet());
        return ResponseEntity.accepted().body(Map.of(
                "status", "ACCEPTED",
                "message", "OpenAPI import accepted; full parsing deferred to Phase 2"
        ));
    }
}
