package com.platform.gateway.property;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

// Feature: kong-gateway-integration, Property 2: 路径透传不变量
class KongStripPathPropertyTest {

    private static final Path TEMPLATE_PATH = Path.of("deploy/kong/kong.yml.template");

    private List<Map<String, Object>> allRoutes;

    @BeforeProperty
    void setUp() throws IOException {
        String raw = Files.readString(findTemplatePath());
        String sanitized = raw.replaceAll("__([A-Z_]+)__", "PLACEHOLDER_$1");
        Yaml yaml = new Yaml();
        Map<String, Object> config = yaml.load(sanitized);
        allRoutes = extractAllRoutes(config);
    }

    /**
     * Validates: Requirements 2.6
     *
     * All routes in kong.yml.template must have strip_path: false,
     * ensuring the upstream service receives the original request path.
     */
    @Property(tries = 100)
    void allRoutesHaveStripPathFalse(@ForAll("routeIndices") int index) {
        Map<String, Object> route = allRoutes.get(index);
        String routeName = (String) route.get("name");
        Object stripPath = route.get("strip_path");

        assertThat(stripPath)
            .as("Route '%s' should have strip_path defined", routeName)
            .isNotNull();
        assertThat(stripPath)
            .as("Route '%s' should have strip_path: false", routeName)
            .isEqualTo(false);
    }

    @Provide
    Arbitrary<Integer> routeIndices() {
        return Arbitraries.integers().between(0, allRoutes.size() - 1);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractAllRoutes(Map<String, Object> config) {
        List<Map<String, Object>> routes = new ArrayList<>();
        List<Map<String, Object>> services = (List<Map<String, Object>>) config.get("services");
        if (services == null) return routes;

        for (Map<String, Object> service : services) {
            List<Map<String, Object>> serviceRoutes = (List<Map<String, Object>>) service.get("routes");
            if (serviceRoutes != null) {
                routes.addAll(serviceRoutes);
            }
        }
        return routes;
    }

    private Path findTemplatePath() {
        Path fromModule = Path.of("../../deploy/kong/kong.yml.template");
        if (Files.exists(fromModule)) return fromModule;
        if (Files.exists(TEMPLATE_PATH)) return TEMPLATE_PATH;
        throw new IllegalStateException(
            "Cannot find kong.yml.template. Tried: " + fromModule + " and " + TEMPLATE_PATH);
    }
}
