package com.admin.controller.gateway;

import com.admin.entity.gateway.Environment;
import com.admin.entity.gateway.ProviderRevision;
import com.admin.repository.gateway.EnvironmentRepository;
import com.admin.repository.gateway.ProviderRevisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/gateway")
@RequiredArgsConstructor
public class ProviderController {

    private final EnvironmentRepository environmentRepository;
    private final ProviderRevisionRepository providerRevisionRepository;

    /**
     * List supported providers.
     */
    @GetMapping("/providers")
    public ResponseEntity<List<String>> listProviders() {
        return ResponseEntity.ok(List.of("KONG", "APISIX", "ENVOY"));
    }

    /**
     * Update environment's gateway provider.
     */
    @PutMapping("/environments/{envId}/provider")
    public ResponseEntity<Environment> updateProvider(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable Long envId,
            @RequestBody Map<String, Object> body) {
        Environment env = environmentRepository.findByIdAndTenantId(envId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + envId));

        String provider = (String) body.get("gatewayProvider");
        String endpoint = (String) body.get("adminEndpoint");

        if (provider != null) env.setGatewayProvider(provider);
        if (endpoint != null) env.setAdminEndpoint(endpoint);

        return ResponseEntity.ok(environmentRepository.save(env));
    }

    /**
     * Get provider revisions for a release.
     */
    @GetMapping("/releases/{releaseId}/provider-revisions")
    public ResponseEntity<List<ProviderRevision>> getRevisions(@PathVariable Long releaseId) {
        return ResponseEntity.ok(providerRevisionRepository.findByReleaseIdOrderByCreatedAtDesc(releaseId));
    }
}
