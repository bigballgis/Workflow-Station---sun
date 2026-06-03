package com.admin.controller.gateway;

import com.admin.entity.gateway.ApiDefinition;
import com.admin.entity.gateway.CatalogVisibility;
import com.admin.service.gateway.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/gateway/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    /**
     * List published APIs in the marketplace catalog.
     */
    @GetMapping("/apis")
    public ResponseEntity<Page<ApiDefinition>> listApis(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestParam(required = false) String environmentCode,
            @RequestParam(required = false) String domain,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                catalogService.listPublishedApis(tenantId, environmentCode, domain, PageRequest.of(page, size)));
    }

    /**
     * Get API catalog detail with versions.
     */
    @GetMapping("/apis/{apiId}")
    public ResponseEntity<Map<String, Object>> getApiDetail(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable Long apiId) {
        return ResponseEntity.ok(catalogService.getCatalogDetail(tenantId, apiId));
    }

    /**
     * Set catalog visibility for an API.
     */
    @PutMapping("/apis/{apiId}/visibility")
    public ResponseEntity<CatalogVisibility> setVisibility(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator,
            @PathVariable Long apiId,
            @RequestBody Map<String, Object> body) {
        String visibility = (String) body.getOrDefault("visibility", "INTERNAL");
        Boolean visibleInMarketplace = (Boolean) body.getOrDefault("visibleInMarketplace", true);
        @SuppressWarnings("unchecked")
        List<String> allowedEnvironments = (List<String>) body.getOrDefault("allowedEnvironments", List.of());

        return ResponseEntity.ok(
                catalogService.setVisibility(tenantId, apiId, visibility, visibleInMarketplace, allowedEnvironments, operator));
    }
}
