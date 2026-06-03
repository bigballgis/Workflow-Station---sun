package com.admin.adapter.gateway.envoy;

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
 * Envoy gateway provider — basic route + rate limit via xDS (Phase 5 P1).
 * Stub implementation: logs intent, structural mapping framework in place.
 */
@Slf4j
@Component("envoyGatewayProvider")
public class EnvoyGatewayProvider implements GatewayProvider {

    @Override
    public PublishResult publishRelease(ReleaseSnapshot snapshot, Environment environment) {
        log.info("[ENVOY] Publish release '{}' to env '{}' (endpoint: {})",
                snapshot.getReleaseNo(), environment.getEnvCode(), environment.getAdminEndpoint());

        // Map to Envoy xDS: routes → RouteConfiguration, upstreams → Cluster
        for (Map<String, Object> apiVersion : snapshot.getApiVersions()) {
            log.info("[ENVOY]   → Cluster/Route: basePath={}", apiVersion.get("basePath"));
        }

        // TODO: Push to Envoy control plane (xDS or REST admin API):
        //   POST /v2/discovery:routes
        //   POST /v2/discovery:clusters

        String revision = "envoy-rev-" + UUID.randomUUID().toString().substring(0, 8);
        return PublishResult.success(revision);
    }

    @Override
    public RollbackResult rollbackRelease(String targetReleaseId, Environment environment) {
        log.info("[ENVOY] Rollback to release '{}' in env '{}'", targetReleaseId, environment.getEnvCode());
        String revision = "envoy-rev-" + UUID.randomUUID().toString().substring(0, 8);
        return RollbackResult.success(revision);
    }

    @Override
    public List<Map<String, Object>> fetchRuntimeState(Environment environment) {
        log.info("[ENVOY] Fetch runtime state for env '{}'", environment.getEnvCode());
        return List.of();
    }
}
