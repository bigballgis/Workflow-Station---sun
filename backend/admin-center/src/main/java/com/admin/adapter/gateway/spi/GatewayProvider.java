package com.admin.adapter.gateway.spi;

import com.admin.adapter.gateway.dto.PublishResult;
import com.admin.adapter.gateway.dto.ReleaseSnapshot;
import com.admin.adapter.gateway.dto.RollbackResult;
import com.admin.entity.gateway.Environment;

import java.util.List;
import java.util.Map;

/**
 * SPI abstraction for gateway runtime operations.
 * Phase 1: publish and rollback.
 * Phase 2: drift detection.
 */
public interface GatewayProvider {

    /**
     * Apply a release snapshot to the gateway runtime.
     */
    PublishResult publishRelease(ReleaseSnapshot snapshot, Environment environment);

    /**
     * Rollback to a previous release state.
     */
    RollbackResult rollbackRelease(String targetReleaseId, Environment environment);

    /**
     * Fetch the current runtime state from the gateway.
     * Returns a normalized list of business objects (routes, services, plugins)
     * for comparison with the SoT metadata.
     */
    List<Map<String, Object>> fetchRuntimeState(Environment environment);
}
