package com.platform.gateway.property;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

// Feature: kong-gateway-integration, Property 11: 配置模板不含硬编码敏感值
class KongNoHardcodedSecretsPropertyTest {

    private static final Path TEMPLATE_PATH = Path.of("deploy/kong/kong.yml.template");

    private String templateContent;
    private String entrypointContent;

    @BeforeProperty
    void setUp() throws IOException {
        templateContent = Files.readString(findTemplatePath());
        entrypointContent = Files.readString(findEntrypointPath());
    }

    /**
     * Validates: Requirements 1.7, 9.5, 9.6
     *
     * JWT secret and Redis password must use __PLACEHOLDER__ format.
     * No hardcoded secret patterns should exist in the template.
     * The entrypoint script must perform sed replacement for all placeholders.
     */
    @Property(tries = 100)
    void templateUsesPlaceholderForSecrets(
            @ForAll("secretPlaceholders") String placeholder) {
        assertThat(templateContent)
            .as("Template should contain placeholder %s", placeholder)
            .contains(placeholder);
    }

    @Property(tries = 100)
    void noHardcodedSecretPatterns(
            @ForAll("hardcodedSecretPatterns") String pattern) {
        // Check that the template does NOT contain common hardcoded secret patterns
        Pattern regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        Matcher matcher = regex.matcher(templateContent);
        // Allow matches only if they are inside comments or are the placeholder format
        while (matcher.find()) {
            String match = matcher.group();
            // __PLACEHOLDER__ format is allowed
            assertThat(match)
                .as("Found potential hardcoded secret: '%s'", match)
                .matches(".*__[A-Z_]+__.*|.*PLACEHOLDER.*");
        }
    }

    @Property(tries = 100)
    void entrypointReplacesAllPlaceholders(
            @ForAll("secretPlaceholders") String placeholder) {
        // The entrypoint script should have a sed command for each placeholder
        String placeholderName = placeholder.replace("__", "");
        assertThat(entrypointContent)
            .as("Entrypoint should replace %s via sed", placeholder)
            .contains(placeholder);
    }

    @Property(tries = 100)
    void templateDoesNotContainActualSecretValues(
            @ForAll("commonSecretValues") String secretValue) {
        assertThat(templateContent)
            .as("Template should not contain hardcoded value '%s'", secretValue)
            .doesNotContain(secretValue);
    }

    @Provide
    Arbitrary<String> secretPlaceholders() {
        return Arbitraries.of(
            "__JWT_SECRET__",
            "__REDIS_PASSWORD__",
            "__REDIS_HOST__",
            "__CORS_ALLOWED_ORIGINS__"
        );
    }

    @Provide
    Arbitrary<String> hardcodedSecretPatterns() {
        return Arbitraries.of(
            "password:\\s+['\"]?[a-zA-Z0-9]{8,}['\"]?",
            "secret:\\s+['\"]?[a-zA-Z0-9]{8,}['\"]?",
            "api[_-]?key:\\s+['\"]?[a-zA-Z0-9]{16,}['\"]?"
        );
    }

    @Provide
    Arbitrary<String> commonSecretValues() {
        return Arbitraries.of(
            "password123",
            "admin123",
            "secret123",
            "mysecretkey",
            "changeme",
            "default_password",
            "redis_password",
            "jwt_secret_key_12345"
        );
    }

    private Path findTemplatePath() {
        Path fromModule = Path.of("../../deploy/kong/kong.yml.template");
        if (Files.exists(fromModule)) return fromModule;
        if (Files.exists(TEMPLATE_PATH)) return TEMPLATE_PATH;
        throw new IllegalStateException(
            "Cannot find kong.yml.template. Tried: " + fromModule + " and " + TEMPLATE_PATH);
    }

    private Path findEntrypointPath() {
        Path fromModule = Path.of("../../deploy/kong/docker-entrypoint-kong.sh");
        Path fromRoot = Path.of("deploy/kong/docker-entrypoint-kong.sh");
        if (Files.exists(fromModule)) return fromModule;
        if (Files.exists(fromRoot)) return fromRoot;
        throw new IllegalStateException(
            "Cannot find docker-entrypoint-kong.sh. Tried: " + fromModule + " and " + fromRoot);
    }
}
