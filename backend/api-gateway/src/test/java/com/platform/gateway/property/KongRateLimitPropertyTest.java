package com.platform.gateway.property;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

// Feature: kong-gateway-integration, Property 6: 登录端点限流阈值严于全局默认
class KongRateLimitPropertyTest {

    private static final Path TEMPLATE_PATH = Path.of("deploy/kong/kong.yml.template");

    private int globalRateLimit;
    private Map<String, Integer> routeRateLimits;

    @BeforeProperty
    void setUp() throws IOException {
        String raw = Files.readString(findTemplatePath());
        String sanitized = raw.replaceAll("__([A-Z_]+)__", "PLACEHOLDER_$1");
        Yaml yaml = new Yaml();
        Map<String, Object> config = yaml.load(sanitized);
        parseRateLimits(config);
    }

    /**
     * Validates: Requirements 4.4
     *
     * Login endpoints (10/min) and refresh endpoints (20/min) must have
     * stricter rate limits than the global default (100/min).
     */
    @Property(tries = 100)
    void loginEndpointRateLimitStricterThanGlobal(
            @ForAll("loginRoutes") String routeName) {
        assertThat(routeRateLimits).containsKey(routeName);
        int routeLimit = routeRateLimits.get(routeName);
        assertThat(routeLimit)
            .as("Route '%s' rate limit (%d) should be stricter than global (%d)",
                routeName, routeLimit, globalRateLimit)
            .isLessThan(globalRateLimit);
    }

    @Property(tries = 100)
    void refreshEndpointRateLimitStricterThanGlobal(
            @ForAll("refreshRoutes") String routeName) {
        assertThat(routeRateLimits).containsKey(routeName);
        int routeLimit = routeRateLimits.get(routeName);
        assertThat(routeLimit)
            .as("Route '%s' rate limit (%d) should be stricter than global (%d)",
                routeName, routeLimit, globalRateLimit)
            .isLessThan(globalRateLimit);
    }

    @Property(tries = 100)
    void loginLimitStricterThanRefreshLimit(
            @ForAll("loginRoutes") String loginRoute,
            @ForAll("refreshRoutes") String refreshRoute) {
        int loginLimit = routeRateLimits.get(loginRoute);
        int refreshLimit = routeRateLimits.get(refreshRoute);
        assertThat(loginLimit)
            .as("Login limit (%d) should be <= refresh limit (%d)", loginLimit, refreshLimit)
            .isLessThanOrEqualTo(refreshLimit);
    }

    @Provide
    Arbitrary<String> loginRoutes() {
        return Arbitraries.of("admin-auth-login-route", "portal-auth-login-route");
    }

    @Provide
    Arbitrary<String> refreshRoutes() {
        return Arbitraries.of("admin-auth-refresh-route", "portal-auth-refresh-route");
    }

    @SuppressWarnings("unchecked")
    private void parseRateLimits(Map<String, Object> config) {
        routeRateLimits = new HashMap<>();
        List<Map<String, Object>> plugins = (List<Map<String, Object>>) config.get("plugins");
        if (plugins == null) return;

        for (Map<String, Object> plugin : plugins) {
            if (!"rate-limiting".equals(plugin.get("name"))) continue;

            Map<String, Object> pluginConfig = (Map<String, Object>) plugin.get("config");
            if (pluginConfig == null) continue;

            int minute = ((Number) pluginConfig.get("minute")).intValue();
            String route = (String) plugin.get("route");

            if (route == null) {
                // Global rate limit
                globalRateLimit = minute;
            } else {
                routeRateLimits.put(route, minute);
            }
        }
    }

    private Path findTemplatePath() {
        Path fromModule = Path.of("../../deploy/kong/kong.yml.template");
        if (Files.exists(fromModule)) return fromModule;
        if (Files.exists(TEMPLATE_PATH)) return TEMPLATE_PATH;
        throw new IllegalStateException(
            "Cannot find kong.yml.template. Tried: " + fromModule + " and " + TEMPLATE_PATH);
    }
}
