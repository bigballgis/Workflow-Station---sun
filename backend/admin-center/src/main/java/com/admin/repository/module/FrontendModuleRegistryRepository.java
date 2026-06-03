package com.admin.repository.module;

import com.admin.entity.module.FrontendModuleRegistry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FrontendModuleRegistryRepository extends JpaRepository<FrontendModuleRegistry, Long> {

    // -- Management queries (admin-scoped, tenant-aware) --

    Page<FrontendModuleRegistry> findByTenantIdAndHostAppAndEnv(
            String tenantId, String hostApp, String env, Pageable pageable);

    Page<FrontendModuleRegistry> findByTenantIdAndHostAppAndEnvAndEnabled(
            String tenantId, String hostApp, String env, Boolean enabled, Pageable pageable);

    Optional<FrontendModuleRegistry> findByIdAndTenantId(Long id, String tenantId);

    // -- Uniqueness checks --

    Optional<FrontendModuleRegistry> findByTenantIdAndHostAppAndEnvAndModuleCode(
            String tenantId, String hostApp, String env, String moduleCode);

    Optional<FrontendModuleRegistry> findByTenantIdAndHostAppAndEnvAndRoutePath(
            String tenantId, String hostApp, String env, String routePath);

    // -- Runtime query (host consumption, always enabled=true) --

    List<FrontendModuleRegistry> findByTenantIdAndHostAppAndEnvAndEnabledTrueOrderByOrderNoAsc(
            String tenantId, String hostApp, String env);

    // -- Count helper for uniqueness validation (exclude self on update) --

    long countByTenantIdAndHostAppAndEnvAndModuleCodeAndIdNot(
            String tenantId, String hostApp, String env, String moduleCode, Long id);

    long countByTenantIdAndHostAppAndEnvAndRoutePathAndIdNot(
            String tenantId, String hostApp, String env, String routePath, Long id);
}
