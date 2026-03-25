package com.platform.gateway.integration;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: kong-gateway-integration, Property 8: CORS 预检请求正确响应
 *
 * For any OPTIONS preflight request to random API paths, Kong should
 * return Access-Control-Allow-Origin, Access-Control-Allow-Methods,
 * Access-Control-Allow-Headers headers, and Access-Control-Max-Age = 3600.
 *
 * Validates: Requirements 6.1, 6.5, 6.6
 */
class KongCorsPreflightPropertyTest extends KongIntegrationTestBase {

    private String baseUrl;

    @BeforeProperty
    void setUp() {
        baseUrl = kongBaseUrl();
    }

    @Property(tries = 100)
    void corsPreflightReturnsCorrectHeaders(
            @ForAll("apiPaths") String path) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", CORS_ORIGIN)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization, Content-Type")
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofString());

        // Verify CORS headers are present
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .as("Response should contain Access-Control-Allow-Origin for path '%s'", path)
                .isPresent();

        assertThat(response.headers().firstValue("Access-Control-Allow-Methods"))
                .as("Response should contain Access-Control-Allow-Methods for path '%s'", path)
                .isPresent();

        assertThat(response.headers().firstValue("Access-Control-Allow-Headers"))
                .as("Response should contain Access-Control-Allow-Headers for path '%s'", path)
                .isPresent();

        // Verify max-age is 3600
        var maxAge = response.headers().firstValue("Access-Control-Max-Age");
        assertThat(maxAge)
                .as("Response should contain Access-Control-Max-Age for path '%s'", path)
                .isPresent();
        assertThat(maxAge.get())
                .as("Access-Control-Max-Age should be 3600")
                .isEqualTo("3600");
    }

    @Provide
    Arbitrary<String> apiPaths() {
        String[] prefixes = {
                "/api/v1/admin",
                "/api/v1/projects",
                "/api/v1/function-units",
                "/api/portal/tasks",
                "/api/portal/processes",
                "/api/workflow/definitions",
                "/api/v1/admin/auth/login",
                "/api/portal/auth/login"
        };
        Arbitrary<String> prefix = Arbitraries.of(prefixes);
        Arbitrary<String> suffix = Arbitraries.of("", "/", "/foo", "/bar/baz");
        return Combinators.combine(prefix, suffix).as((p, s) -> p + s);
    }
}
