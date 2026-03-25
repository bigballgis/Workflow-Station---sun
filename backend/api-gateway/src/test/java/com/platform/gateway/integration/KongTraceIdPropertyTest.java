package com.platform.gateway.integration;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: kong-gateway-integration, Property 7: 每个请求携带追踪 ID
 *
 * For any request through Kong, whether or not the request carries
 * an X-Trace-Id header, the response should always contain a
 * non-empty X-Trace-Id header.
 *
 * Validates: Requirements 5.1, 5.2
 */
class KongTraceIdPropertyTest extends KongIntegrationTestBase {

    private String baseUrl;

    @BeforeProperty
    void setUp() {
        baseUrl = kongBaseUrl();
    }

    @Property(tries = 100)
    void responseAlwaysContainsTraceId(
            @ForAll("apiPaths") String path,
            @ForAll("optionalTraceId") String traceId) throws Exception {

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET();

        if (traceId != null && !traceId.isEmpty()) {
            builder.header("X-Trace-Id", traceId);
        }

        HttpResponse<String> response = HTTP_CLIENT.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString());

        // Response should always contain a non-empty X-Trace-Id regardless of status code
        var traceIdHeader = response.headers().firstValue("X-Trace-Id");
        assertThat(traceIdHeader)
                .as("Response should contain X-Trace-Id header for path '%s'", path)
                .isPresent();
        assertThat(traceIdHeader.get())
                .as("X-Trace-Id should not be empty")
                .isNotBlank();
    }

    @Provide
    Arbitrary<String> apiPaths() {
        // Use public paths to avoid 401 interfering with the test.
        // Also include some protected paths — even 401 responses should have trace IDs.
        return Arbitraries.of(
                "/api/v1/admin/auth/login",
                "/api/v1/admin/auth/refresh",
                "/api/portal/auth/login",
                "/api/portal/auth/refresh",
                "/api/workflow/n8n/callback",
                "/api/v1/admin/users",
                "/api/v1/projects",
                "/api/portal/tasks"
        );
    }

    @Provide
    Arbitrary<String> optionalTraceId() {
        return Arbitraries.oneOf(
                // No trace ID provided
                Arbitraries.of("", null),
                // Client-provided trace ID
                Arbitraries.just("custom").map(ignored -> UUID.randomUUID().toString()),
                // Custom string trace ID
                Arbitraries.strings().alpha().ofMinLength(8).ofMaxLength(32)
        );
    }
}
