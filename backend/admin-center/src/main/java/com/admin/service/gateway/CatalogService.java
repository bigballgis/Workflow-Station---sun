package com.admin.service.gateway;

import com.admin.entity.gateway.*;
import com.admin.repository.gateway.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CatalogService {

    private final ApiDefinitionRepository apiDefinitionRepository;
    private final ApiVersionRepository apiVersionRepository;
    private final CatalogVisibilityRepository catalogVisibilityRepository;

    /**
     * List published APIs visible in the marketplace catalog.
     * Filters by tenant, domain, and status=PUBLISHED.
     * Environment/visibility filtering deferred to P1.
     */
    public Page<ApiDefinition> listPublishedApis(String tenantId, String environmentCode, String domain, Pageable pageable) {
        if (domain != null && !domain.isEmpty()) {
            return apiDefinitionRepository.findByTenantIdAndStatusAndDomain(tenantId, "PUBLISHED", domain, pageable);
        }
        return apiDefinitionRepository.findByTenantIdAndStatus(tenantId, "PUBLISHED", pageable);
    }

    public Map<String, Object> getCatalogDetail(String tenantId, Long apiId) {
        ApiDefinition api = apiDefinitionRepository.findByIdAndTenantId(apiId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("API not found: " + apiId));
        Page<ApiVersion> versions = apiVersionRepository.findByTenantIdAndApiDefinitionId(
                tenantId, apiId, Pageable.unpaged());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("api", api);
        result.put("versions", versions.getContent());
        return result;
    }

    @Transactional
    public CatalogVisibility setVisibility(String tenantId, Long apiDefinitionId,
                                           String visibility, Boolean visibleInMarketplace,
                                           List<String> allowedEnvironments, String updatedBy) {
        CatalogVisibility cv = catalogVisibilityRepository
                .findByTenantIdAndApiDefinitionId(tenantId, apiDefinitionId)
                .orElse(CatalogVisibility.builder()
                        .tenantId(tenantId)
                        .apiDefinitionId(apiDefinitionId)
                        .build());
        cv.setVisibility(visibility);
        cv.setVisibleInMarketplace(visibleInMarketplace);
        cv.setAllowedEnvironments(allowedEnvironments);
        cv.setUpdatedBy(updatedBy);
        cv.setUpdatedAt(java.time.Instant.now());
        return catalogVisibilityRepository.save(cv);
    }

    public Optional<CatalogVisibility> getVisibility(String tenantId, Long apiDefinitionId) {
        return catalogVisibilityRepository.findByTenantIdAndApiDefinitionId(tenantId, apiDefinitionId);
    }
}
