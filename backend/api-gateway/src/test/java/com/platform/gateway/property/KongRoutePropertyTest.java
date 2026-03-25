package com.platform.gateway.property;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// Feature: kong-gateway-integration, Property 1: 路由路径正确匹配目标服务
class KongRoutePropertyTest {

    private static final Path TEMPLATE_PATH = Path.of("deploy/kong/kong.yml.template");

    private Map<String, Object> kongConfig;
    private List<RouteEntry> routeEntries;

    @BeforeProperty
    void setUp() throws IOException {
        String raw = Files.readString(findTemplatePath());
        // Replace __PLACEHOLDER__ values with dummy strings so SnakeYAML can parse
        String sanitized = raw.replaceAll("__([A-Z_]+)__", "PLACEHOLDER_$1");
        Yaml yaml = new Yaml();
        kongConfig = yaml.load(sanitized);
        routeEntries = extractRouteEntries(kongConfig);
    }

    /**
     * Validates: Requirements 2.2, 2.3, 2.4, 2.5, 2.8
     *
     * For any randomly generated API path, the longest-prefix-match logic
     * should route it to the correct backend service.
     */
    @Property(tries = 100)
    void routePathMatchesCorrectService(@ForAll("apiPaths") String path) {
        // Find the route with the longest matching prefix
        RouteEntry matched = findLongestPrefixMatch(path);
        assertThat(matched).as("Path '%s' should match at least one route", path).isNotNull();

        String serviceName = matched.serviceName;

        if (path.startsWith("/api/v1/ai-generation")) {
            assertThat(serviceName).contains("developer-workstation-sse");
        } else if (path.startsWith("/api/v1/upload") || path.startsWith("/api/v1/import")) {
            assertThat(serviceName).contains("upload");
        } else if (path.startsWith("/api/v1/admin/auth/login") || path.startsWith("/api/v1/admin/auth/refresh")) {
            assertThat(serviceName).contains("admin-center");
        } else if (path.startsWith("/api/v1/admin")) {
            assertThat(serviceName).contains("admin-center");
        } else if (path.startsWith("/api/portal/auth/login") || path.startsWith("/api/portal/auth/refresh")) {
            assertThat(serviceName).contains("portal");
        } else if (path.startsWith("/api/portal/ws")) {
            assertThat(serviceName).contains("user-portal-ws");
        } else if (path.startsWith("/api/portal")) {
            assertThat(serviceName).contains("portal");
        } else if (path.startsWith("/api/workflow/n8n/callback")) {
            assertThat(serviceName).contains("workflow");
        } else if (path.startsWith("/api/workflow")) {
            assertThat(serviceName).contains("workflow");
        } else if (path.startsWith("/api/v1")) {
            assertThat(serviceName).contains("developer-workstation");
        }
    }

    @Provide
    Arbitrary<String> apiPaths() {
        String[] prefixes = {
            "/api/v1/admin",
            "/api/v1/ai-generation",
            "/api/v1/upload",
            "/api/v1/import",
            "/api/v1",
            "/api/portal/ws",
            "/api/portal",
            "/api/workflow"
        };
        Arbitrary<String> prefix = Arbitraries.of(prefixes);
        Arbitrary<String> suffix = Arbitraries.of(
            "", "/", "/foo", "/bar/baz", "/123", "/test/deep/path"
        );
        return Combinators.combine(prefix, suffix).as((p, s) -> p + s);
    }

    private RouteEntry findLongestPrefixMatch(String requestPath) {
        RouteEntry best = null;
        int bestLen = -1;
        for (RouteEntry entry : routeEntries) {
            for (String routePath : entry.paths) {
                if (requestPath.startsWith(routePath) && routePath.length() > bestLen) {
                    bestLen = routePath.length();
                    best = entry;
                }
            }
        }
        return best;
    }

    @SuppressWarnings("unchecked")
    private List<RouteEntry> extractRouteEntries(Map<String, Object> config) {
        List<RouteEntry> entries = new ArrayList<>();
        List<Map<String, Object>> services = (List<Map<String, Object>>) config.get("services");
        if (services == null) return entries;

        for (Map<String, Object> service : services) {
            String serviceName = (String) service.get("name");
            List<Map<String, Object>> routes = (List<Map<String, Object>>) service.get("routes");
            if (routes == null) continue;
            for (Map<String, Object> route : routes) {
                List<String> paths = (List<String>) route.get("paths");
                String routeName = (String) route.get("name");
                if (paths != null) {
                    entries.add(new RouteEntry(serviceName, routeName, paths));
                }
            }
        }
        return entries;
    }

    private Path findTemplatePath() {
        // Try from project root (when running from backend/api-gateway)
        Path fromModule = Path.of("../../deploy/kong/kong.yml.template");
        if (Files.exists(fromModule)) return fromModule;
        // Try from workspace root
        if (Files.exists(TEMPLATE_PATH)) return TEMPLATE_PATH;
        throw new IllegalStateException(
            "Cannot find kong.yml.template. Tried: " + fromModule + " and " + TEMPLATE_PATH);
    }

    private record RouteEntry(String serviceName, String routeName, List<String> paths) {}
}
