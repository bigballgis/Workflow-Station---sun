package com.admin.service.gateway;

import com.admin.entity.gateway.Application;
import com.admin.entity.gateway.Credential;
import com.admin.repository.gateway.ApplicationRepository;
import com.admin.repository.gateway.CredentialRepository;
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
public class ApplicationService {

    private final ApplicationRepository appRepo;
    private final CredentialRepository credentialRepo;

    @Transactional(readOnly = true)
    public Page<Application> listApps(String tenantId, String keyword, Pageable pageable) {
        return appRepo.findByTenantId(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<Application> getApp(String tenantId, Long appId) {
        return appRepo.findByIdAndTenantId(appId, tenantId);
    }

    @Transactional
    public Application createApp(String tenantId, Application app) {
        app.setTenantId(tenantId);
        app.setStatus("ACTIVE");
        return appRepo.save(app);
    }

    @Transactional
    public Application updateApp(String tenantId, Long appId, Application update) {
        Application existing = appRepo.findByIdAndTenantId(appId, tenantId)
                .orElseThrow(() -> new RuntimeException("Application not found: " + appId));
        existing.setName(update.getName());
        existing.setOwner(update.getOwner());
        existing.setDescription(update.getDescription());
        return appRepo.save(existing);
    }

    @Transactional(readOnly = true)
    public Page<Credential> listCredentials(String tenantId, Long appId, Pageable pageable) {
        return credentialRepo.findByTenantIdAndApplicationId(tenantId, appId, pageable);
    }

    @Transactional
    public Credential createCredential(String tenantId, Long appId, Credential credential) {
        appRepo.findByIdAndTenantId(appId, tenantId)
                .orElseThrow(() -> new RuntimeException("Application not found: " + appId));
        credential.setTenantId(tenantId);
        credential.setApplicationId(appId);
        credential.setStatus("ACTIVE");
        return credentialRepo.save(credential);
    }
}
