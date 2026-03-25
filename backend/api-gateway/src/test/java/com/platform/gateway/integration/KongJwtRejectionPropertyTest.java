package com.platform.gateway.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import javax.crypto.SecretKey;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: kong-gateway-integration, Property 3: 无效 JWT token 被拒绝
 *
 * For any protected route and any invalid JWT token (random strings,
 * wrong signature, expired, missing), Kong should return HTTP 401.
 *
 * Validates: Requirements 3.1, 3.2, 3.4, 3.5
 */
class KongJwtRejectionPropertyTest extends KongIntegrationTestBase {

    private String baseUrl;

    @BeforeProperty
    void setUp() {
        baseUrl = kongBaseUrl();
    }

    @Property(tries = 100)
    void invalidJwtTokenIsRejected(
            @ForAll("protectedPaths") String path,
            @ForAll("invalidTokens") String token) throws Exception {

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET();

        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }

        HttpResponse<String> response = HTTP_CLIENT.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode())
                .as("Protected path '%s' with invalid token should return 401", path)
                .isEqualTo(401);
    }

    @Provide
    Arbitrary<String> protectedPaths() {
        return Arbitraries.of(
                "/api/v1/admin/users",
                "/api/v1/admin/roles",
                "/api/v1/projects",
                "/api/v1/function-units",
                "/api/portal/tasks",
                "/api/portal/processes",
                "/api/workflow/definitions"
        );
    }

    @Provide
    Arbitrary<String> invalidTokens() {
        return Arbitraries.oneOf(
                // Case 1: random garbage strings
                Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(50),
                // Case 2: empty / missing token
                Arbitraries.of("", null),
                // Case 3: expired token (valid signature but expired)
                Arbitraries.just("expired").map(ignored -> buildExpiredToken()),
                // Case 4: wrong signature (signed with different secret)
                Arbitraries.just("wrongsig").map(ignored -> buildWrongSignatureToken()),
                // Case 5: malformed JWT structure
                Arbitraries.of("abc.def.ghi", "eyJhbGciOiJIUzI1NiJ9.invalid.invalid")
        );
    }

    private String buildExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(
                TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .issuer("platform-jwt-issuer")
                .subject("test-user")
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(key)
                .compact();
    }

    private String buildWrongSignatureToken() {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "wrong-secret-key-that-is-definitely-long-enough-for-hs256"
                        .getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .issuer("platform-jwt-issuer")
                .subject("test-user")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(wrongKey)
                .compact();
    }
}
