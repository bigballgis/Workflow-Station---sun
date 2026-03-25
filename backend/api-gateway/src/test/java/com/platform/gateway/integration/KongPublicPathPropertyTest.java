package com.platform.gateway.integration;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: kong-gateway-integration, Property 4: 公开路径免认证
 *
 * For any public path, requests WITHOUT a JWT token should NOT be
 * rejected with 401. They may return 502 (upstream not available)
 * but never 401.
 *
 * Validates: Requirements 3.6
 */
class KongPublicPathPropertyTest extends KongIntegrationTestBase {

    private String baseUrl;

    @BeforeProperty
    void setUp() {
        baseUrl = kongBaseUrl();
    }

    @Property(tries = 100)
    void publicPathsDoNotRequireAuth(
            @ForAll("publicPaths") String path,
            @ForAll("httpMethods") String method) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode())
                .as("Public path '%s' without JWT should not return 401", path)
                .isNotEqualTo(401);
    }

    @Provide
    Arbitrary<String> publicPaths() {
        return Arbitraries.of(
                "/api/v1/admin/auth/login",
                "/api/v1/admin/auth/refresh",
                "/api/portal/auth/login",
                "/api/portal/auth/refresh",
                "/api/workflow/n8n/callback"
        );
    }

    @Provide
    Arbitrary<String> httpMethods() {
        return Arbitraries.of("GET", "POST");
    }
}
