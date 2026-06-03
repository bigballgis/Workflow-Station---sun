package com.admin.repository.gateway;

import com.admin.entity.gateway.GovernanceRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface GovernanceRuleRepository extends JpaRepository<GovernanceRule, Long> {
    Page<GovernanceRule> findByTenantId(String tenantId, Pageable pageable);
    List<GovernanceRule> findByTenantIdAndEnabled(String tenantId, Boolean enabled);
    List<GovernanceRule> findByTenantIdAndEnvironmentCodeAndEnabled(String tenantId, String environmentCode, Boolean enabled);
    Optional<GovernanceRule> findByTenantIdAndRuleCode(String tenantId, String ruleCode);
}
