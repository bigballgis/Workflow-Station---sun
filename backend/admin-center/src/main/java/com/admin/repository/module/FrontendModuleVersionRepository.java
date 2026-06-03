package com.admin.repository.module;

import com.admin.entity.module.FrontendModuleVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FrontendModuleVersionRepository extends JpaRepository<FrontendModuleVersion, Long> {

    List<FrontendModuleVersion> findByModuleRegistryIdOrderByCreatedAtDesc(Long moduleRegistryId);

    Optional<FrontendModuleVersion> findByModuleRegistryIdAndVersion(Long moduleRegistryId, String version);

    Optional<FrontendModuleVersion> findByModuleRegistryIdAndIsActiveTrue(Long moduleRegistryId);

    long countByModuleRegistryIdAndVersionAndIdNot(Long moduleRegistryId, String version, Long id);
}
