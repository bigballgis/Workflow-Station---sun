package com.admin.adapter.gateway.kong;

import com.admin.adapter.gateway.spi.GatewayProvider;
import com.admin.adapter.gateway.dto.PublishResult;
import com.admin.adapter.gateway.dto.ReleaseSnapshot;
import com.admin.adapter.gateway.dto.RollbackResult;
import com.admin.entity.gateway.Environment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Kong gateway provider — maps abstract policy model to Kong Admin API calls.
 * Phase 1: publish/rollback stubs.
 * Phase 2: OAuth2, ACL, Canary, Blue-Green policy mapping + drift detection.
 */
@Slf4j
@Component
public class KongGatewayProvider implements GatewayProvider {

    @Override
    public PublishResult publishRelease(ReleaseSnapshot snapshot, Environment environment) {
        log.info("[KONG] Publish release '{}' to env '{}' (endpoint: {})",
                snapshot.getReleaseNo(), environment.getEnvCode(), environment.getAdminEndpoint());

        // Map access policies (OAuth2, ACL) to Kong plugins
        List<Map<String, Object>> accessPlugins = KongPolicyMapper.mapAccessPolicies(
                snapshot.getAccessPolicies());
        log.info("[KONG] Mapped {} access policies to Kong plugins", accessPlugins.size());

        // Map traffic policies (Canary, Blue-Green) to Kong upstream configs
        List<Map<String, Object>> trafficConfigs = KongPolicyMapper.mapTrafficPolicies(
                snapshot.getTrafficPolicies());
        log.info("[KONG] Mapped {} traffic policies to Kong upstream configs", trafficConfigs.size());

        // TODO: Phase 2 — make actual Kong Admin API calls:
        //   1. POST /services, /routes for each API version (snapshot.getApiVersions())
        //   2. POST /plugins for each access plugin
        //   3. PUT /upstreams/{name} for each traffic config

        log.info("[KONG] Skipping actual Kong Admin API calls (not yet connected to Kong runtime)");
        return PublishResult.failure("GATEWAY_ADAPTER_ERROR",
                "Kong adapter publish: policies mapped but Admin API calls not yet implemented");
    }

    @Override
    public RollbackResult rollbackRelease(String targetReleaseId, Environment environment) {
        log.info("[KONG] Rollback to release '{}' in env '{}'",
                targetReleaseId, environment.getEnvCode());
        // TODO: Phase 1 — restore previous Kong configuration
        return RollbackResult.failure("GATEWAY_ADAPTER_ERROR",
                "Kong adapter rollback not yet implemented");
    }

    @Override
    public List<Map<String, Object>> fetchRuntimeState(Environment environment) {
        log.info("[KONG] Fetch runtime state for env '{}' (endpoint: {})",
                environment.getEnvCode(), environment.getAdminEndpoint());
        // TODO: Phase 2 — GET /routes, /services, /plugins from Kong Admin API
        // Normalize to business objects (not Kong native names)
        return List.of();
    }
}
