package com.admin.adapter.gateway.stub;

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
 * Stub gateway provider for development and testing without a real gateway.
 * Activated when gateway.adapter.mode=stub (the default).
 */
@Slf4j
@Component
public class StubGatewayProvider implements GatewayProvider {

    @Override
    public PublishResult publishRelease(ReleaseSnapshot snapshot, Environment environment) {
        String revision = "stub-rev-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("[STUB] Publish release '{}' to env '{}' — revision: {}",
                snapshot.getReleaseNo(), environment.getEnvCode(), revision);
        return PublishResult.success(revision);
    }

    @Override
    public RollbackResult rollbackRelease(String targetReleaseId, Environment environment) {
        String revision = "stub-rev-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("[STUB] Rollback to release '{}' in env '{}' — revision: {}",
                targetReleaseId, environment.getEnvCode(), revision);
        return RollbackResult.success(revision);
    }

    @Override
    public List<Map<String, Object>> fetchRuntimeState(Environment environment) {
        log.info("[STUB] Fetch runtime state for env '{}'", environment.getEnvCode());
        return List.of();
    }
}
