package com.admin.service.gateway;

import com.admin.entity.gateway.GatewayRelease;
import com.admin.entity.gateway.PublishHistory;
import com.admin.repository.gateway.GatewayReleaseRepository;
import com.admin.repository.gateway.PublishHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayAuditService {

    private final PublishHistoryRepository historyRepo;
    private final GatewayReleaseRepository releaseRepo;

    @Transactional(readOnly = true)
    public Page<PublishHistory> listAuditLogs(String tenantId, Pageable pageable) {
        return historyRepo.findByTenantId(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<GatewayRelease> listReleases(String tenantId, Pageable pageable) {
        return releaseRepo.findByTenantId(tenantId, pageable);
    }
}
