package com.admin.repository.gateway;

import com.admin.entity.gateway.CatalogVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface CatalogVisibilityRepository extends JpaRepository<CatalogVisibility, Long> {
    Optional<CatalogVisibility> findByTenantIdAndApiDefinitionId(String tenantId, Long apiDefinitionId);
}
