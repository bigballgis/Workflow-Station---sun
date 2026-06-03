package com.admin.repository.gateway;

import com.admin.entity.gateway.ComplianceCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface ComplianceCheckRepository extends JpaRepository<ComplianceCheck, Long> {
    List<ComplianceCheck> findByReleaseIdOrderByCheckedAtDesc(Long releaseId);
    Optional<ComplianceCheck> findTopByReleaseIdOrderByCheckedAtDesc(Long releaseId);
}
