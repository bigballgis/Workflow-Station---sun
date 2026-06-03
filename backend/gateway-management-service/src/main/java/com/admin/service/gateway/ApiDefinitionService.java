package com.admin.service.gateway;

import com.admin.entity.gateway.ApiDefinition;
import com.admin.entity.gateway.ApiVersion;
import com.admin.repository.gateway.ApiDefinitionRepository;
import com.admin.repository.gateway.ApiVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiDefinitionService {

    private final ApiDefinitionRepository apiDefRepo;
    private final ApiVersionRepository apiVersionRepo;

    @Transactional(readOnly = true)
    public Page<ApiDefinition> listApis(String tenantId, String keyword, String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            return apiDefRepo.findByTenantIdAndStatus(tenantId, status, pageable);
        }
        return apiDefRepo.findByTenantId(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<ApiDefinition> getApi(String tenantId, Long apiId) {
        return apiDefRepo.findByIdAndTenantId(apiId, tenantId);
    }

    @Transactional
    public ApiDefinition createApi(String tenantId, ApiDefinition apiDef) {
        apiDef.setTenantId(tenantId);
        apiDef.setStatus("DRAFT");
        return apiDefRepo.save(apiDef);
    }

    @Transactional
    public ApiDefinition updateApi(String tenantId, Long apiId, ApiDefinition update) {
        ApiDefinition existing = apiDefRepo.findByIdAndTenantId(apiId, tenantId)
                .orElseThrow(() -> new RuntimeException("API definition not found: " + apiId));
        existing.setName(update.getName());
        existing.setDomain(update.getDomain());
        existing.setBasePath(update.getBasePath());
        existing.setProtocol(update.getProtocol());
        existing.setDescription(update.getDescription());
        return apiDefRepo.save(existing);
    }

    @Transactional(readOnly = true)
    public Page<ApiVersion> listVersions(String tenantId, Long apiId, Pageable pageable) {
        return apiVersionRepo.findByTenantIdAndApiDefinitionId(tenantId, apiId, pageable);
    }

    @Transactional
    public ApiVersion createVersion(String tenantId, Long apiId, ApiVersion version) {
        apiDefRepo.findByIdAndTenantId(apiId, tenantId)
                .orElseThrow(() -> new RuntimeException("API definition not found: " + apiId));
        version.setTenantId(tenantId);
        version.setApiDefinitionId(apiId);
        version.setLifecycleStatus("DRAFT");
        return apiVersionRepo.save(version);
    }
}
