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

// Feature: kong-gateway-integration, Property 12: Kong 声明式配置包含所有必需资源
class KongRequiredResourcesPropertyTest {

    private static final Path TEMPLATE_PATH = Path.of("deploy/kong/kong.yml.template");

    private Set<String> serviceNames;
    private Map<String, List<String>> serviceRoutes; // serviceName -> list of route names
    private Set<String> globalPluginNames;
    private Map<String, Set<String>> servicePluginNames; // serviceName -> set of plugin names

    @BeforeProperty
    void setUp() throws IOException {
        String raw = Files.readString(findTemplatePath());
        String sanitized = raw.replaceAll("__([A-Z_]+)__", "PLACEHOLDER_$1");
        Yaml yaml = new Yaml();
        Map<String, Object> config = yaml.load(sanitized);
        parseConfig(config);
    }

    /**
     * Validates: Requirements 2.1, 8.3, 9.2
     *
     * All 4 backend services must have Service and Route definitions.
     * Global plugins must include jwt, rate-limiting, cors, correlation-id, prometheus.
     */
    @Property(tries = 100)
    void eachBackendServiceHasServiceDefinition(
            @ForAll("backendServices") String backendName) {
        boolean hasService = serviceNames.stream()
            .anyMatch(name -> name.contains(backendName));
        assertThat(hasService)
            .as("Backend '%s' should have a Service definition in kong.yml.template", backendName)
            .isTrue();
    }

    @Property(tries = 100)
    void eachBackendServiceHasAtLeastOneRoute(
            @ForAll("backendServices") String backendName) {
        boolean hasRoute = serviceRoutes.entrySet().stream()
            .filter(e -> e.getKey().contains(backendName))
            .anyMatch(e -> !e.getValue().isEmpty());
        assertThat(hasRoute)
            .as("Backend '%s' should have at least one Route in kong.yml.template", backendName)
            .isTrue();
    }

    @Property(tries = 100)
    void globalPluginsContainRequiredPlugin(
            @ForAll("requiredGlobalPlugins") String pluginName) {
        assertThat(globalPluginNames)
            .as("Global plugins should include '%s'", pluginName)
            .contains(pluginName);
    }

    @Property(tries = 100)
    void protectedServicesHaveJwtPlugin(
            @ForAll("protectedServices") String serviceName) {
        Set<String> plugins = servicePluginNames.getOrDefault(serviceName, Collections.emptySet());
        assertThat(plugins)
            .as("Protected service '%s' should have jwt plugin", serviceName)
            .contains("jwt");
    }

    @Provide
    Arbitrary<String> backendServices() {
        return Arbitraries.of(
            "admin-center",
            "developer-workstation",
            "user-portal",
            "workflow-engine"
        );
    }

    @Provide
    Arbitrary<String> requiredGlobalPlugins() {
        return Arbitraries.of(
            "rate-limiting",
            "cors",
            "correlation-id",
            "prometheus"
        );
    }

    @Provide
    Arbitrary<String> protectedServices() {
        return Arbitraries.of(
            "admin-center-service",
            "developer-workstation-service",
            "developer-workstation-sse-service",
            "user-portal-service",
            "user-portal-ws-service",
            "workflow-engine-service",
            "developer-workstation-upload-service"
        );
    }

    @SuppressWarnings("unchecked")
    private void parseConfig(Map<String, Object> config) {
        serviceNames = new HashSet<>();
        serviceRoutes = new HashMap<>();
        servicePluginNames = new HashMap<>();

        List<Map<String, Object>> services = (List<Map<String, Object>>) config.get("services");
        if (services != null) {
            for (Map<String, Object> service : services) {
                String name = (String) service.get("name");
                serviceNames.add(name);

                List<String> routeNames = new ArrayList<>();
                List<Map<String, Object>> routes = (List<Map<String, Object>>) service.get("routes");
                if (routes != null) {
                    for (Map<String, Object> route : routes) {
                        routeNames.add((String) route.get("name"));
                    }
                }
                serviceRoutes.put(name, routeNames);
            }
        }

        // Parse global plugins (those without a "route" or "service" key)
        // and service-level plugins (those with a "service" key)
        globalPluginNames = new HashSet<>();
        List<Map<String, Object>> plugins = (List<Map<String, Object>>) config.get("plugins");
        if (plugins != null) {
            for (Map<String, Object> plugin : plugins) {
                if (plugin.containsKey("service")) {
                    String svcName = (String) plugin.get("service");
                    servicePluginNames
                        .computeIfAbsent(svcName, k -> new HashSet<>())
                        .add((String) plugin.get("name"));
                } else if (!plugin.containsKey("route")) {
                    globalPluginNames.add((String) plugin.get("name"));
                }
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
