package com.admin.adapter.gateway.kong;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps abstract policy model to Kong-native plugin configurations.
 * Phase 2: OAuth2, ACL, Canary, Blue-Green policy types.
 */
@Slf4j
public final class KongPolicyMapper {

    private KongPolicyMapper() {
        // utility class
    }

    /**
     * Map access policies (OAuth2, ACL) to Kong plugin configs.
     */
    public static List<Map<String, Object>> mapAccessPolicies(List<Map<String, Object>> policies) {
        List<Map<String, Object>> plugins = new ArrayList<>();
        if (policies == null) return plugins;

        for (Map<String, Object> policy : policies) {
            String type = (String) policy.get("type");
            if (type == null) continue;

            switch (type.toUpperCase()) {
                case "OAUTH2" -> plugins.add(mapOAuth2(policy));
                case "ACL" -> plugins.add(mapAcl(policy));
                default -> log.warn("[KONG] Unknown access policy type: {}", type);
            }
        }
        return plugins;
    }

    /**
     * Map traffic policies (CANARY, BLUE_GREEN) to Kong configs.
     */
    public static List<Map<String, Object>> mapTrafficPolicies(List<Map<String, Object>> policies) {
        List<Map<String, Object>> configs = new ArrayList<>();
        if (policies == null) return configs;

        for (Map<String, Object> policy : policies) {
            String type = (String) policy.get("type");
            if (type == null) continue;

            switch (type.toUpperCase()) {
                case "CANARY" -> configs.add(mapCanary(policy));
                case "BLUE_GREEN" -> configs.add(mapBlueGreen(policy));
                default -> log.warn("[KONG] Unknown traffic policy type: {}", type);
            }
        }
        return configs;
    }

    // -------------------------------------------------------------------------
    // OAuth2 → Kong OAuth2 plugin (requires Kong OAuth2 plugin enabled)
    // -------------------------------------------------------------------------
    private static Map<String, Object> mapOAuth2(Map<String, Object> policy) {
        Map<String, Object> plugin = new HashMap<>();
        plugin.put("name", "oauth2");

        Map<String, Object> config = new HashMap<>();
        Map<String, Object> policyConfig = getConfig(policy);
        // Kong OAuth2: provision_key, scopes, token_expiration, enable_authorization_code, etc.
        config.put("provision_key", generateKey("oauth2-provision-"));

        @SuppressWarnings("unchecked")
        List<String> scopes = (List<String>) policyConfig.get("scopes");
        config.put("scopes", scopes != null ? scopes : List.of("api:read"));
        config.put("enable_authorization_code", true);
        config.put("enable_client_credentials", true);
        config.put("accept_http_if_already_terminated", true);

        if (policyConfig.containsKey("tokenEndpoint")) {
            config.put("global_credentials", false); // use per-consumer credentials
        }

        plugin.put("config", config);
        plugin.put("apiVersionId", policy.get("apiVersionId"));
        return plugin;
    }

    // -------------------------------------------------------------------------
    // ACL → Kong ACL plugin
    // -------------------------------------------------------------------------
    private static Map<String, Object> mapAcl(Map<String, Object> policy) {
        Map<String, Object> plugin = new HashMap<>();
        plugin.put("name", "acl");

        Map<String, Object> config = new HashMap<>();
        Map<String, Object> policyConfig = getConfig(policy);

        @SuppressWarnings("unchecked")
        List<String> allow = (List<String>) policyConfig.get("allow");

        config.put("allow", allow != null ? allow : List.of());
        config.put("hide_groups_header", false);

        plugin.put("config", config);
        plugin.put("apiVersionId", policy.get("apiVersionId"));
        return plugin;
    }

    // -------------------------------------------------------------------------
    // Canary → Kong canary upstream weight configuration
    // -------------------------------------------------------------------------
    private static Map<String, Object> mapCanary(Map<String, Object> policy) {
        Map<String, Object> config = new HashMap<>();
        Map<String, Object> policyConfig = getConfig(policy);

        Object baselineWeight = policyConfig.getOrDefault("baselineWeight", 90);
        Object canaryWeight = policyConfig.getOrDefault("canaryWeight", 10);
        String canaryUpstreamRef = (String) policyConfig.get("canaryUpstreamRef");

        // Kong canary: configure upstream targets with weights
        Map<String, Object> upstreamConfig = new HashMap<>();
        upstreamConfig.put("name", canaryUpstreamRef != null ? canaryUpstreamRef : "canary-upstream");
        upstreamConfig.put("algorithm", "weighted-round-robin");

        List<Map<String, Object>> targets = new ArrayList<>();
        Map<String, Object> baselineTarget = new HashMap<>();
        baselineTarget.put("target", "baseline-service:8080");
        baselineTarget.put("weight", baselineWeight);
        targets.add(baselineTarget);

        if (canaryUpstreamRef != null) {
            Map<String, Object> canaryTarget = new HashMap<>();
            canaryTarget.put("target", canaryUpstreamRef);
            canaryTarget.put("weight", canaryWeight);
            targets.add(canaryTarget);
        }
        upstreamConfig.put("targets", targets);

        config.put("type", "CANARY");
        config.put("upstream", upstreamConfig);
        config.put("apiVersionId", policy.get("apiVersionId"));
        return config;
    }

    // -------------------------------------------------------------------------
    // Blue-Green → Two upstream sets with instant switch
    // -------------------------------------------------------------------------
    private static Map<String, Object> mapBlueGreen(Map<String, Object> policy) {
        Map<String, Object> config = new HashMap<>();
        Map<String, Object> policyConfig = getConfig(policy);

        String activeUpstream = (String) policyConfig.getOrDefault("activeUpstream", "blue:8080");
        String standbyUpstream = (String) policyConfig.getOrDefault("standbyUpstream", "green:8080");

        // Kong blue-green: use two upstreams, switch which one is active
        Map<String, Object> upstreamConfig = new HashMap<>();
        upstreamConfig.put("name", "blue-green-upstream");
        upstreamConfig.put("algorithm", "round-robin");

        List<Map<String, Object>> targets = new ArrayList<>();
        Map<String, Object> active = new HashMap<>();
        active.put("target", activeUpstream);
        active.put("weight", 100);
        active.put("tags", List.of("active"));
        targets.add(active);

        Map<String, Object> standby = new HashMap<>();
        standby.put("target", standbyUpstream);
        standby.put("weight", 0);
        standby.put("tags", List.of("standby"));
        targets.add(standby);

        upstreamConfig.put("targets", targets);

        config.put("type", "BLUE_GREEN");
        config.put("upstream", upstreamConfig);
        config.put("apiVersionId", policy.get("apiVersionId"));
        return config;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getConfig(Map<String, Object> policy) {
        Object cfg = policy.get("config");
        if (cfg instanceof Map) {
            return (Map<String, Object>) cfg;
        }
        // For flat policy format where config fields are at top level
        Map<String, Object> flat = new HashMap<>(policy);
        flat.remove("type");
        flat.remove("apiVersionId");
        return flat;
    }

    private static String generateKey(String prefix) {
        return prefix + java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
