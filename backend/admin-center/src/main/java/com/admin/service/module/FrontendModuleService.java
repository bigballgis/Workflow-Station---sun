package com.admin.service.module;

import com.admin.dto.module.FrontendModuleHealthDTO;
import com.admin.dto.module.FrontendModuleRuntimeDTO;
import com.admin.dto.module.FrontendModuleVersionDTO;
import com.admin.entity.module.FrontendModuleHealthLog;
import com.admin.entity.module.FrontendModuleRegistry;
import com.admin.entity.module.FrontendModuleVersion;
import com.admin.repository.module.FrontendModuleHealthLogRepository;
import com.admin.repository.module.FrontendModuleRegistryRepository;
import com.admin.repository.module.FrontendModuleVersionRepository;
import com.admin.controlplane.ControlPlaneEventPublisher;
import com.admin.controlplane.ControlPlaneEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FrontendModuleService {

    private final FrontendModuleRegistryRepository repo;
    private final FrontendModuleVersionRepository versionRepo;
    private final FrontendModuleHealthLogRepository healthLogRepo;
    private final ControlPlaneEventPublisher cpEventPublisher;

    // ==================== Management APIs ====================

    @Transactional(readOnly = true)
    public Page<FrontendModuleRegistry> list(String tenantId, String hostApp, String env,
                                              Boolean enabled, Pageable pageable) {
        if (enabled != null) {
            return repo.findByTenantIdAndHostAppAndEnvAndEnabled(tenantId, hostApp, env, enabled, pageable);
        }
        return repo.findByTenantIdAndHostAppAndEnv(tenantId, hostApp, env, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<FrontendModuleRegistry> getById(String tenantId, Long id) {
        return repo.findByIdAndTenantId(id, tenantId);
    }

    @Transactional
    public FrontendModuleRegistry create(String tenantId, FrontendModuleRegistry module) {
        module.setTenantId(tenantId);

        // Validate uniqueness
        if (repo.countByTenantIdAndHostAppAndEnvAndModuleCodeAndIdNot(
                tenantId, module.getHostApp(), module.getEnv(), module.getModuleCode(), 0L) > 0) {
            throw new RuntimeException("MFE_MODULE_DUPLICATE_CODE: module_code already exists for this host/env");
        }
        if (repo.countByTenantIdAndHostAppAndEnvAndRoutePathAndIdNot(
                tenantId, module.getHostApp(), module.getEnv(), module.getRoutePath(), 0L) > 0) {
            throw new RuntimeException("MFE_ROUTE_CONFLICT: route_path already exists for this host/env");
        }

        return repo.save(module);
    }

    @Transactional
    public FrontendModuleRegistry update(String tenantId, Long id, FrontendModuleRegistry update) {
        FrontendModuleRegistry existing = repo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("MFE_MODULE_NOT_FOUND: " + id));

        // Check module_code uniqueness (excluding self)
        if (update.getModuleCode() != null
                && !update.getModuleCode().equals(existing.getModuleCode())
                && repo.countByTenantIdAndHostAppAndEnvAndModuleCodeAndIdNot(
                        tenantId, existing.getHostApp(), existing.getEnv(), update.getModuleCode(), id) > 0) {
            throw new RuntimeException("MFE_MODULE_DUPLICATE_CODE: module_code already exists");
        }

        // Check route_path uniqueness (excluding self)
        if (update.getRoutePath() != null
                && !update.getRoutePath().equals(existing.getRoutePath())
                && repo.countByTenantIdAndHostAppAndEnvAndRoutePathAndIdNot(
                        tenantId, existing.getHostApp(), existing.getEnv(), update.getRoutePath(), id) > 0) {
            throw new RuntimeException("MFE_ROUTE_CONFLICT: route_path already exists");
        }

        // Apply non-null fields
        if (update.getHostApp() != null) existing.setHostApp(update.getHostApp());
        if (update.getModuleCode() != null) existing.setModuleCode(update.getModuleCode());
        if (update.getDisplayName() != null) existing.setDisplayName(update.getDisplayName());
        if (update.getRoutePath() != null) existing.setRoutePath(update.getRoutePath());
        if (update.getIcon() != null) existing.setIcon(update.getIcon());
        if (update.getOrderNo() != null) existing.setOrderNo(update.getOrderNo());
        if (update.getRemoteEntryUrl() != null) existing.setRemoteEntryUrl(update.getRemoteEntryUrl());
        if (update.getExposedModule() != null) existing.setExposedModule(update.getExposedModule());
        if (update.getEnabled() != null) existing.setEnabled(update.getEnabled());
        if (update.getRequiredPermissions() != null) existing.setRequiredPermissions(update.getRequiredPermissions());
        if (update.getTenantScope() != null) existing.setTenantScope(update.getTenantScope());
        if (update.getEnv() != null) existing.setEnv(update.getEnv());
        if (update.getVersion() != null) existing.setVersion(update.getVersion());

        return repo.save(existing);
    }

    @Transactional
    public FrontendModuleRegistry enable(String tenantId, Long id) {
        FrontendModuleRegistry module = repo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("MFE_MODULE_NOT_FOUND: " + id));
        module.setEnabled(true);
        return repo.save(module);
    }

    @Transactional
    public FrontendModuleRegistry disable(String tenantId, Long id) {
        FrontendModuleRegistry module = repo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("MFE_MODULE_NOT_FOUND: " + id));
        module.setEnabled(false);
        return repo.save(module);
    }

    @Transactional
    public FrontendModuleRegistry switchVersion(String tenantId, Long id, String version, String remoteEntryUrl) {
        FrontendModuleRegistry module = repo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("MFE_MODULE_NOT_FOUND: " + id));

        if (version == null || version.isBlank()) {
            throw new RuntimeException("MFE_INVALID_VERSION_SWITCH: version is required");
        }

        // Check if this version already exists in history
        Optional<FrontendModuleVersion> existingVersion =
                versionRepo.findByModuleRegistryIdAndVersion(id, version);
        if (existingVersion.isPresent()) {
            throw new RuntimeException("MFE_SWITCH_BLOCKED: version " + version + " already exists for this module");
        }

        // Deactivate current active version
        versionRepo.findByModuleRegistryIdAndIsActiveTrue(id).ifPresent(active -> {
            active.setIsActive(false);
            versionRepo.save(active);
        });

        // Record new version in history
        FrontendModuleVersion newVersion = FrontendModuleVersion.builder()
                .moduleRegistryId(id)
                .version(version)
                .remoteEntryUrl(remoteEntryUrl != null && !remoteEntryUrl.isBlank()
                        ? remoteEntryUrl : module.getRemoteEntryUrl())
                .isActive(true)
                .build();
        versionRepo.save(newVersion);

        // Update registry entry
        module.setVersion(version);
        if (remoteEntryUrl != null && !remoteEntryUrl.isBlank()) {
            module.setRemoteEntryUrl(remoteEntryUrl);
        }
        // Emit canonical publish events (switch-version ≅ publish)
        cpEventPublisher.publish(this, ControlPlaneEventType.RELEASE_PUBLISH_STARTED,
                "MFE", tenantId, String.valueOf(id), module.getModuleCode());
        try {
            FrontendModuleRegistry savedSw = repo.save(module);
            cpEventPublisher.publish(this, ControlPlaneEventType.RELEASE_PUBLISH_SUCCEEDED,
                    "MFE", tenantId, String.valueOf(id), module.getModuleCode());
            return savedSw;
        } catch (RuntimeException e) {
            cpEventPublisher.publish(this, ControlPlaneEventType.RELEASE_PUBLISH_FAILED,
                    "MFE", tenantId, String.valueOf(id), module.getModuleCode());
            throw e;
        }
    }

    @Transactional
    public FrontendModuleRegistry rollbackVersion(String tenantId, Long id, String targetVersion) {
        FrontendModuleRegistry module = repo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("MFE_MODULE_NOT_FOUND: " + id));

        if (targetVersion == null || targetVersion.isBlank()) {
            throw new RuntimeException("MFE_INVALID_VERSION_SWITCH: targetVersion is required");
        }

        // Find target version in history
        FrontendModuleVersion target = versionRepo.findByModuleRegistryIdAndVersion(id, targetVersion)
                .orElseThrow(() -> new RuntimeException("MFE_VERSION_NOT_FOUND: " + targetVersion));

        // Deactivate current active version
        versionRepo.findByModuleRegistryIdAndIsActiveTrue(id).ifPresent(active -> {
            active.setIsActive(false);
            versionRepo.save(active);
        });

        // Activate target version
        target.setIsActive(true);
        versionRepo.save(target);

        // Update registry entry
        module.setVersion(targetVersion);
        if (target.getRemoteEntryUrl() != null && !target.getRemoteEntryUrl().isBlank()) {
            module.setRemoteEntryUrl(target.getRemoteEntryUrl());
        }
        // Emit canonical rollback events
        cpEventPublisher.publish(this, ControlPlaneEventType.RELEASE_ROLLBACK_STARTED,
                "MFE", tenantId, String.valueOf(id), module.getModuleCode());
        try {
            FrontendModuleRegistry savedRb = repo.save(module);
            cpEventPublisher.publish(this, ControlPlaneEventType.RELEASE_ROLLBACK_SUCCEEDED,
                    "MFE", tenantId, String.valueOf(id), module.getModuleCode());
            return savedRb;
        } catch (RuntimeException e) {
            cpEventPublisher.publish(this, ControlPlaneEventType.RELEASE_ROLLBACK_FAILED,
                    "MFE", tenantId, String.valueOf(id), module.getModuleCode());
            throw e;
        }
    }

    // ==================== Version History ====================

    @Transactional(readOnly = true)
    public List<FrontendModuleVersionDTO> getVersions(String tenantId, Long moduleRegistryId) {
        // Verify module exists and belongs to tenant
        repo.findByIdAndTenantId(moduleRegistryId, tenantId)
                .orElseThrow(() -> new RuntimeException("MFE_MODULE_NOT_FOUND: " + moduleRegistryId));

        return versionRepo.findByModuleRegistryIdOrderByCreatedAtDesc(moduleRegistryId)
                .stream()
                .map(FrontendModuleVersionDTO::from)
                .toList();
    }

    // ==================== Health Check ====================

    // No @Transactional — health check performs a network call (up to 20s timeout).
    // Holding a DB connection during HTTP I/O risks connection pool exhaustion.
    // Spring Data JPA auto-creates a transaction for healthLogRepo.save().
    public FrontendModuleHealthDTO healthCheck(String tenantId, Long moduleRegistryId) {
        FrontendModuleRegistry module = repo.findByIdAndTenantId(moduleRegistryId, tenantId)
                .orElseThrow(() -> new RuntimeException("MFE_MODULE_NOT_FOUND: " + moduleRegistryId));

        String status = "UNHEALTHY";
        String detail;

        try {
            // Perform HTTP HEAD request to remoteEntryUrl.
            // In Docker, localhost:3000 resolves to the container itself, not the edge proxy.
            // Rewrite to use the internal Docker service name.
            // Rewrite localhost URLs to internal Docker service names.
            // In Docker, localhost:3000 resolves to the container itself.
            // The edge proxy (nginx) is accessible as "edge-frontend" on the Docker network.
            String dockerEdgeHost = System.getenv().getOrDefault("MFE_HEALTH_EDGE_HOST", "edge-frontend");
            String url = module.getRemoteEntryUrl()
                    .replaceAll("localhost:[0-9]+", dockerEdgeHost);
            URI uri = URI.create(url);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            int responseCode = conn.getResponseCode();
            conn.disconnect();

            if (responseCode >= 200 && responseCode < 400) {
                status = "HEALTHY";
                detail = "Remote entry reachable (HTTP " + responseCode + ")";
            } else {
                detail = "Remote entry returned HTTP " + responseCode;
            }
        } catch (Exception e) {
            detail = "Health check failed: " + e.getMessage();
            log.warn("Health check failed for module {} ({}): {}", module.getModuleCode(),
                    module.getRemoteEntryUrl(), e.getMessage());
        }

        FrontendModuleHealthLog logEntry = FrontendModuleHealthLog.builder()
                .moduleRegistryId(moduleRegistryId)
                .status(status)
                .detail(detail)
                .checkedAt(Instant.now())
                .build();
        healthLogRepo.save(logEntry);

        return FrontendModuleHealthDTO.from(logEntry);
    }

    // ==================== Runtime API ====================

    @Transactional(readOnly = true)
    public List<FrontendModuleRuntimeDTO> getRuntimeConfig(String tenantId, String hostApp, String env) {
        return repo.findByTenantIdAndHostAppAndEnvAndEnabledTrueOrderByOrderNoAsc(tenantId, hostApp, env)
                .stream()
                .map(FrontendModuleRuntimeDTO::from)
                .toList();
    }
}
