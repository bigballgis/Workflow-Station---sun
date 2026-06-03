package com.admin.adapter.gateway.apisix;

import com.admin.adapter.gateway.spi.GatewayProvider;
import com.admin.adapter.gateway.dto.PublishResult;
import com.admin.adapter.gateway.dto.ReleaseSnapshot;
import com.admin.adapter.gateway.dto.RollbackResult;
import com.admin.entity.gateway.Environment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * APISIX gateway provider — translates abstract policy model to APISIX Admin API.
 * Phase 5: route, upstream, and plugin mapping for APISIX runtime.
 */
@Slf4j
@Component("apisixGatewayProvider")
public class ApisixGatewayProvider implements GatewayProvider {

    @Override
    public PublishResult publishRelease(ReleaseSnapshot snapshot, Environment environment) {
        log.info("[APISIX] Publish release '{}' to env '{}' (endpoint: {})",
                snapshot.getReleaseNo(), environment.getEnvCode(), environment.getAdminEndpoint());

        // Map API versions to APISIX routes + upstreams
        for (Map<String, Object> apiVersion : snapshot.getApiVersions()) {
            String upstreamRef = (String) apiVersion.getOrDefault("upstreamRef", "");
            log.info("[APISIX]   → Route: {} upstream: {}", apiVersion.get("basePath"), upstreamRef);
        }

        // Map access policies to APISIX plugins (jwt-auth, key-auth, acl)
        for (Map<String, Object> policy : snapshot.getAccessPolicies()) {
            log.info("[APISIX]   → Plugin: type={}, enabled={}", policy.get("policyType"), policy.get("enabled"));
        }

        // TODO: Make actual APISIX Admin API calls:
        //   PUT /apisix/admin/routes/{id}
        //   PUT /apisix/admin/upstreams/{id}
        //   PUT /apisix/admin/plugin_configs/{id}

        String revision = "apisix-rev-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("[APISIX] Publish complete — revision: {}", revision);
        return PublishResult.success(revision);
    }

    @Override
    public RollbackResult rollbackRelease(String targetReleaseId, Environment environment) {
        log.info("[APISIX] Rollback to release '{}' in env '{}'", targetReleaseId, environment.getEnvCode());
        // TODO: Restore previous APISIX configuration from revision history
        String revision = "apisix-rev-" + UUID.randomUUID().toString().substring(0, 8);
        return RollbackResult.success(revision);
    }

    @Override
    public List<Map<String, Object>> fetchRuntimeState(Environment environment) {
        log.info("[APISIX] Fetch runtime state for env '{}' (endpoint: {})",
                environment.getEnvCode(), environment.getAdminEndpoint());
        // TODO: GET /apisix/admin/routes, /apisix/admin/upstreams
        // Normalize to business model
        return List.of();
    }
}
