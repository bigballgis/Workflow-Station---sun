package com.admin.service.gateway;

import com.admin.entity.gateway.*;
import com.admin.repository.gateway.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Evaluates governance rules against a release before publish.
 * Phase 5 compliance engine — BLOCK/WARN severity.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ComplianceService {

    private final GovernanceRuleRepository ruleRepository;
    private final ComplianceCheckRepository complianceCheckRepository;
    private final GatewayReleaseRepository releaseRepository;
    private final EnvironmentRepository environmentRepository;

    /**
     * Evaluate governance rules for a release.
     * Returns a ComplianceCheck with violations and warnings.
     */
    public ComplianceCheck evaluate(String tenantId, Long releaseId, String operator) {
        GatewayRelease release = releaseRepository.findByIdAndTenantId(releaseId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Release not found: " + releaseId));

        Environment env = environmentRepository.findByIdAndTenantId(release.getEnvironmentId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Environment not found"));

        // Get enabled rules for this environment + global rules
        List<GovernanceRule> rules = new ArrayList<>();
        rules.addAll(ruleRepository.findByTenantIdAndEnvironmentCodeAndEnabled(tenantId, env.getEnvCode(), true));
        rules.addAll(ruleRepository.findByTenantIdAndEnvironmentCodeAndEnabled(tenantId, null, true));

        List<Map<String, Object>> violations = new ArrayList<>();
        List<Map<String, Object>> warnings = new ArrayList<>();

        for (GovernanceRule rule : rules) {
            RuleResult result = evaluateRule(rule, release, env);
            if (result.violated()) {
                Map<String, Object> entry = Map.of(
                        "ruleCode", rule.getRuleCode(),
                        "ruleName", rule.getName(),
                        "severity", rule.getSeverity(),
                        "message", result.message(),
                        "ruleType", rule.getRuleType()
                );
                if ("BLOCK".equals(rule.getSeverity())) {
                    violations.add(entry);
                } else {
                    warnings.add(entry);
                }
            }
        }

        boolean passed = violations.isEmpty();

        ComplianceCheck check = ComplianceCheck.builder()
                .tenantId(tenantId)
                .releaseId(releaseId)
                .passed(passed)
                .violationsJson(violations)
                .warningsJson(warnings)
                .checkedAt(Instant.now())
                .checkedBy(operator)
                .build();

        ComplianceCheck saved = complianceCheckRepository.save(check);
        log.info("Compliance check for release {}: passed={}, violations={}, warnings={}",
                release.getReleaseNo(), passed, violations.size(), warnings.size());

        return saved;
    }

    /**
     * Get the latest compliance check for a release.
     */
    public Optional<ComplianceCheck> getLatestCheck(Long releaseId) {
        return complianceCheckRepository.findTopByReleaseIdOrderByCheckedAtDesc(releaseId);
    }

    /**
     * Get all compliance checks for a release.
     */
    public List<ComplianceCheck> getChecks(Long releaseId) {
        return complianceCheckRepository.findByReleaseIdOrderByCheckedAtDesc(releaseId);
    }

    /**
     * Evaluate a single rule against a release snapshot.
     * For Phase 5 MVP, uses simple string-matching heuristics as the
     * rules engine is not yet connected to a real expression evaluator.
     */
    private RuleResult evaluateRule(GovernanceRule rule, GatewayRelease release, Environment env) {
        Map<String, Object> snapshot = release.getSnapshotJson();

        return switch (rule.getRuleType()) {
            case "NAMING" -> evaluateNaming(rule, snapshot);
            case "SECURITY" -> evaluateSecurity(rule, snapshot);
            case "VERSIONING" -> evaluateVersioning(rule, snapshot);
            case "TRAFFIC" -> evaluateTraffic(rule, snapshot);
            case "ENVIRONMENT" -> evaluateEnvironment(rule, snapshot, env);
            default -> {
                log.warn("Unknown rule type '{}' for rule '{}'", rule.getRuleType(), rule.getRuleCode());
                yield RuleResult.pass();
            }
        };
    }

    private RuleResult evaluateNaming(GovernanceRule rule, Map<String, Object> snapshot) {
        // Check API versions use valid naming conventions
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> apiVersions = (List<Map<String, Object>>) snapshot.getOrDefault("apiVersions", List.of());
        for (Map<String, Object> api : apiVersions) {
            String version = (String) api.get("version");
            if (version != null && !version.matches("^[a-zA-Z0-9._-]+$")) {
                return RuleResult.violated("API version '" + version + "' contains invalid characters");
            }
        }
        return RuleResult.pass();
    }

    private RuleResult evaluateSecurity(GovernanceRule rule, Map<String, Object> snapshot) {
        // Check access policies are present
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> accessPolicies = (List<Map<String, Object>>) snapshot.getOrDefault("accessPolicies", List.of());
        if (accessPolicies.isEmpty()) {
            return RuleResult.violated("No access policies configured for any API version");
        }

        // Check for JWT or OAuth2
        boolean hasAuth = accessPolicies.stream()
                .anyMatch(p -> {
                    String type = (String) p.get("type");
                    return "OAUTH2".equalsIgnoreCase(type) || "JWT".equalsIgnoreCase(type);
                });

        if (!hasAuth) {
            return RuleResult.violated("No JWT or OAuth2 access policy found");
        }
        return RuleResult.pass();
    }

    private RuleResult evaluateVersioning(GovernanceRule rule, Map<String, Object> snapshot) {
        // Basic check: at least one API version present
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> apiVersions = (List<Map<String, Object>>) snapshot.getOrDefault("apiVersions", List.of());
        if (apiVersions.isEmpty()) {
            return RuleResult.violated("Release contains no API versions");
        }
        return RuleResult.pass();
    }

    private RuleResult evaluateTraffic(GovernanceRule rule, Map<String, Object> snapshot) {
        // Check rate limiting is configured
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> trafficPolicies = (List<Map<String, Object>>) snapshot.getOrDefault("trafficPolicies", List.of());
        boolean hasRateLimit = trafficPolicies.stream()
                .anyMatch(p -> "RATE_LIMIT".equalsIgnoreCase((String) p.get("type")));

        if (!hasRateLimit) {
            return RuleResult.violated("No rate limiting policy configured");
        }
        return RuleResult.pass();
    }

    private RuleResult evaluateEnvironment(GovernanceRule rule, Map<String, Object> snapshot, Environment env) {
        // Only applies to specific environments defined in the rule
        if (rule.getEnvironmentCode() != null && !rule.getEnvironmentCode().equalsIgnoreCase(env.getEnvCode())) {
            return RuleResult.pass();
        }
        return RuleResult.pass(); // Environment-specific rules: pass for now
    }

    private record RuleResult(boolean violated, String message) {
        static RuleResult pass() { return new RuleResult(false, null); }
        static RuleResult violated(String msg) { return new RuleResult(true, msg); }
    }
}
