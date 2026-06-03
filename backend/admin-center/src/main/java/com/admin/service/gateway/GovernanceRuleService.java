package com.admin.service.gateway;

import com.admin.entity.gateway.GovernanceRule;
import com.admin.repository.gateway.GovernanceRuleRepository;
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
@Transactional
public class GovernanceRuleService {

    private final GovernanceRuleRepository ruleRepository;

    public Page<GovernanceRule> listRules(String tenantId, Pageable pageable) {
        return ruleRepository.findByTenantId(tenantId, pageable);
    }

    public List<GovernanceRule> getEnabledRules(String tenantId, String environmentCode) {
        if (environmentCode != null && !environmentCode.isEmpty()) {
            return ruleRepository.findByTenantIdAndEnvironmentCodeAndEnabled(tenantId, environmentCode, true);
        }
        return ruleRepository.findByTenantIdAndEnabled(tenantId, true);
    }

    public GovernanceRule createRule(GovernanceRule rule) {
        return ruleRepository.save(rule);
    }

    public GovernanceRule updateRule(Long ruleId, GovernanceRule update) {
        GovernanceRule existing = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("Governance rule not found: " + ruleId));
        existing.setName(update.getName());
        existing.setRuleType(update.getRuleType());
        existing.setSeverity(update.getSeverity());
        existing.setExpression(update.getExpression());
        existing.setEnvironmentCode(update.getEnvironmentCode());
        existing.setEnabled(update.getEnabled());
        existing.setDescription(update.getDescription());
        return ruleRepository.save(existing);
    }

    public void deleteRule(Long ruleId) {
        ruleRepository.deleteById(ruleId);
    }

    public Optional<GovernanceRule> getRule(Long ruleId) {
        return ruleRepository.findById(ruleId);
    }
}
