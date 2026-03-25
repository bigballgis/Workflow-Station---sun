package com.platform.gateway.property;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

// Feature: kong-gateway-integration, Property 13: 每个上游服务配置被动健康检查
class KongHealthCheckPropertyTest {

    private static final Path TEMPLATE_PATH = Path.of("deploy/kong/kong.yml.template");

    private List<Map<String, Object>> upstreams;

    @BeforeProperty
    void setUp() throws IOException {
        String raw = Files.readString(findTemplatePath());
        String sanitized = raw.replaceAll("__([A-Z_]+)__", "PLACEHOLDER_$1");
        Yaml yaml = new Yaml();
        Map<String, Object> config = yaml.load(sanitized);
        upstreams = extractUpstreams(config);
    }

    /**
     * Validates: Requirements 10.1
     *
     * Each upstream in kong.yml.template must have passive health checks configured,
     * with unhealthy.http_failures and unhealthy.tcp_failures > 0.
     */
    @Property(tries = 100)
    void eachUpstreamHasPassiveHealthCheck(@ForAll("upstreamIndices") int index) {
        Map<String, Object> upstream = upstreams.get(index);
        String name = (String) upstream.get("name");

        Map<String, Object> healthchecks = getNestedMap(upstream, "healthchecks");
        assertThat(healthchecks)
            .as("Upstream '%s' should have healthchecks configured", name)
            .isNotNull();

        Map<String, Object> passive = getNestedMap(healthchecks, "passive");
        assertThat(passive)
            .as("Upstream '%s' should have passive health checks", name)
            .isNotNull();
    }

    @Property(tries = 100)
    void passiveHealthCheckHasPositiveHttpFailures(@ForAll("upstreamIndices") int index) {
        Map<String, Object> upstream = upstreams.get(index);
        String name = (String) upstream.get("name");

        Map<String, Object> unhealthy = getNestedMap(
            getNestedMap(getNestedMap(upstream, "healthchecks"), "passive"),
            "unhealthy"
        );
        assertThat(unhealthy)
            .as("Upstream '%s' passive health check should have unhealthy config", name)
            .isNotNull();

        Number httpFailures = (Number) unhealthy.get("http_failures");
        assertThat(httpFailures)
            .as("Upstream '%s' should have http_failures defined", name)
            .isNotNull();
        assertThat(httpFailures.intValue())
            .as("Upstream '%s' http_failures should be > 0", name)
            .isGreaterThan(0);
    }

    @Property(tries = 100)
    void passiveHealthCheckHasPositiveTcpFailures(@ForAll("upstreamIndices") int index) {
        Map<String, Object> upstream = upstreams.get(index);
        String name = (String) upstream.get("name");

        Map<String, Object> unhealthy = getNestedMap(
            getNestedMap(getNestedMap(upstream, "healthchecks"), "passive"),
            "unhealthy"
        );
        assertThat(unhealthy)
            .as("Upstream '%s' passive health check should have unhealthy config", name)
            .isNotNull();

        Number tcpFailures = (Number) unhealthy.get("tcp_failures");
        assertThat(tcpFailures)
            .as("Upstream '%s' should have tcp_failures defined", name)
            .isNotNull();
        assertThat(tcpFailures.intValue())
            .as("Upstream '%s' tcp_failures should be > 0", name)
            .isGreaterThan(0);
    }

    @Provide
    Arbitrary<Integer> upstreamIndices() {
        return Arbitraries.integers().between(0, upstreams.size() - 1);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractUpstreams(Map<String, Object> config) {
        List<Map<String, Object>> result = (List<Map<String, Object>>) config.get("upstreams");
        return result != null ? result : Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getNestedMap(Map<String, Object> map, String key) {
        if (map == null) return null;
        return (Map<String, Object>) map.get(key);
    }

    private Path findTemplatePath() {
        Path fromModule = Path.of("../../deploy/kong/kong.yml.template");
        if (Files.exists(fromModule)) return fromModule;
        if (Files.exists(TEMPLATE_PATH)) return TEMPLATE_PATH;
        throw new IllegalStateException(
            "Cannot find kong.yml.template. Tried: " + fromModule + " and " + TEMPLATE_PATH);
    }
}
