package com.platform.gateway.integration;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: kong-gateway-integration, Property 5: 限流超阈值返回 429 并包含限流响应头
 *
 * When requests exceed the rate limit threshold, Kong should return
 * HTTP 429 and include X-RateLimit-Limit, X-RateLimit-Remaining,
 * X-RateLimit-Reset headers.
 *
 * Uses the login endpoint which has a 10/min limit (easier to exceed).
 *
 * Validates: Requirements 4.1, 4.5, 4.6
 */
class KongRateLimitIntegrationPropertyTest extends KongIntegrationTestBase {

    private String baseUrl;

    @BeforeProperty
    void setUp() {
        baseUrl = kongBaseUrl();
    }

    @Property(tries = 100)
    void rateLimitExceededReturns429WithHeaders(
            @ForAll("loginEndpoints") String path) throws Exception {

        // The login endpoint has a 10/min rate limit.
        // Send 12 requests rapidly to exceed the threshold.
        // Use a unique X-Forwarded-For per property trial to isolate rate limit counters.
        String uniqueIp = "10.99." + (int) (Math.random() * 255) + "." + (int) (Math.random() * 255);

        HttpResponse<String> lastResponse = null;
        boolean got429 = false;

        for (int i = 0; i < 12; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"test\",\"password\":\"test\"}"))
                    .header("Content-Type", "application/json")
                    .header("X-Forwarded-For", uniqueIp)
                    .build();

            lastResponse = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (lastResponse.statusCode() == 429) {
                got429 = true;
                break;
            }
        }

        assertThat(got429)
                .as("Should receive 429 after exceeding rate limit on '%s'", path)
                .isTrue();

        // Verify rate limit headers are present on the 429 response
        // Kong rate-limiting plugin uses X-RateLimit-Limit-Minute (per-window suffix)
        // and IETF draft headers: RateLimit-Limit, RateLimit-Remaining, RateLimit-Reset
        assertThat(lastResponse).isNotNull();
        assertThat(lastResponse.headers().firstValue("RateLimit-Limit"))
                .as("429 response should contain RateLimit-Limit header")
                .isPresent();
        assertThat(lastResponse.headers().firstValue("RateLimit-Remaining"))
                .as("429 response should contain RateLimit-Remaining header")
                .isPresent();
        assertThat(lastResponse.headers().firstValue("RateLimit-Reset"))
                .as("429 response should contain RateLimit-Reset header")
                .isPresent();
    }

    @Provide
    Arbitrary<String> loginEndpoints() {
        return Arbitraries.of(
                "/api/v1/admin/auth/login",
                "/api/portal/auth/login"
        );
    }
}
