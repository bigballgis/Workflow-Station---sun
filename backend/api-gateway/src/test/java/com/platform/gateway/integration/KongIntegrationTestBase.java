package com.platform.gateway.integration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Shared base for Kong integration tests.
 * Starts Kong (DB-less) + Redis via Testcontainers once per JVM.
 *
 * Uses locally available Docker images (kong:latest = 3.9.x, redis:7.2-alpine).
 * Ryuk is disabled via testcontainers.properties since Docker Hub may be unreachable.
 */
public class KongIntegrationTestBase {

    static final String TEST_JWT_SECRET = "integration-test-jwt-secret-key-that-is-long-enough-for-hs256";
    static final String CORS_ORIGIN = "http://localhost:3000";

    static final Network NETWORK = Network.newNetwork();

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2-alpine")
            .withImagePullPolicy(PullPolicy.defaultPolicy())
            .withExposedPorts(6379)
            .withNetwork(NETWORK)
            .withNetworkAliases("redis")
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofSeconds(30));

    @SuppressWarnings("resource")
    static final GenericContainer<?> KONG;

    static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    static {
        REDIS.start();

        // Pre-process kong.yml.template: replace placeholders with test values
        Path processedConfig;
        try {
            processedConfig = preprocessKongConfig();
        } catch (IOException e) {
            throw new RuntimeException("Failed to preprocess kong.yml.template", e);
        }

        // Use kong:latest which is locally available (Kong 3.9.x)
        KONG = new GenericContainer<>("kong:latest")
                .withImagePullPolicy(PullPolicy.defaultPolicy())
                .withExposedPorts(8000, 8001)
                .withNetwork(NETWORK)
                .withNetworkAliases("kong")
                .withEnv("KONG_DATABASE", "off")
                .withEnv("KONG_DECLARATIVE_CONFIG", "/kong/kong.yml")
                .withEnv("KONG_PROXY_ACCESS_LOG", "/dev/stdout")
                .withEnv("KONG_PROXY_ERROR_LOG", "/dev/stderr")
                .withEnv("KONG_ADMIN_LISTEN", "0.0.0.0:8001")
                .withEnv("KONG_NGINX_PROXY_CLIENT_MAX_BODY_SIZE", "100m")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(processedConfig),
                        "/kong/kong.yml")
                .waitingFor(Wait.forHttp("/status").forPort(8001).forStatusCode(200))
                .withStartupTimeout(Duration.ofSeconds(120));
        KONG.start();
    }

    static String kongBaseUrl() {
        return "http://" + KONG.getHost() + ":" + KONG.getMappedPort(8000);
    }

    private static Path preprocessKongConfig() throws IOException {
        Path templatePath = findTemplatePath();
        String raw = Files.readString(templatePath);

        String processed = raw
                .replace("__JWT_SECRET__", TEST_JWT_SECRET)
                .replace("__REDIS_HOST__", "redis")
                .replace("__REDIS_PASSWORD__", "")
                .replace("__CORS_ALLOWED_ORIGINS__", CORS_ORIGIN);

        Path tempFile = Files.createTempFile("kong-test-", ".yml");
        tempFile.toFile().deleteOnExit();
        Files.writeString(tempFile, processed);
        return tempFile;
    }

    private static Path findTemplatePath() {
        Path fromModule = Path.of("../../deploy/kong/kong.yml.template");
        if (Files.exists(fromModule)) return fromModule;
        Path fromRoot = Path.of("deploy/kong/kong.yml.template");
        if (Files.exists(fromRoot)) return fromRoot;
        throw new IllegalStateException(
                "Cannot find kong.yml.template. Tried: " + fromModule + " and " + fromRoot);
    }
}
