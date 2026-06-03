package com.admin.repository.gateway;

import com.admin.entity.gateway.ProviderRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface ProviderRevisionRepository extends JpaRepository<ProviderRevision, Long> {
    List<ProviderRevision> findByReleaseIdAndGatewayProvider(Long releaseId, String gatewayProvider);
    List<ProviderRevision> findByReleaseIdOrderByCreatedAtDesc(Long releaseId);
}
