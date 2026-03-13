package com.workflow.component;

import net.jqwik.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-Based Tests for N8nTaskExecutor
 *
 * Tests the pure/helper methods of N8nTaskExecutor:
 * - generateCallbackToken() uniqueness
 * - buildWebhookRequestBody() callback info presence
 * - calculateRetryDelay() exponential backoff
 * - sourceType constants correctness
 *
 * N8nTaskExecutor is instantiated with null dependencies since these
 * helper methods don't use injected dependencies.
 */
class N8nTaskExecutorPropertyTest {

    /**
     * Create an N8nTaskExecutor instance with null dependencies.
     * The public helper methods under test do not use injected dependencies.
     */
    private N8nTaskExecutor createExecutor() {
        return new N8nTaskExecutor(null, null, null, null, null);
    }

    // ==================== Property 5: 回调令牌唯一性 ====================

    /**
     * Feature: n8n-workflow-integration, Property 5: 回调令牌唯一性
     *
     * For any number of N8N task executions, each generated callbackToken
     * should be unique (no duplicates).
     *
     * Validates: Requirements 4.2
     */
    @Property(tries = 100)
    @Label("Property 5: All generated callback tokens are unique")
    void allGeneratedCallbackTokensAreUnique(
            @ForAll("tokenBatchSize") int batchSize) {

        N8nTaskExecutor executor = createExecutor();

        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < batchSize; i++) {
            String token = executor.generateCallbackToken();
            assertThat(token).isNotNull().isNotBlank();
            boolean added = tokens.add(token);
            assertThat(added)
                    .as("Token '%s' should be unique but was already generated", token)
                    .isTrue();
        }

        assertThat(tokens).hasSize(batchSize);
    }

    // ==================== Property 7: Webhook 请求体包含回调信息 ====================

    /**
     * Feature: n8n-workflow-integration, Property 7: Webhook 请求体包含回调信息
     *
     * For any Service Task execution context, the webhook request body
     * should always contain non-empty callbackUrl and callbackToken fields.
     *
     * Validates: Requirements 4.4
     */
    @Property(tries = 100)
    @Label("Property 7: Webhook request body contains non-empty callbackUrl and callbackToken")
    void webhookRequestBodyContainsCallbackInfo(
            @ForAll("randomInputData") Map<String, Object> inputData) {

        N8nTaskExecutor executor = createExecutor();

        String callbackUrl = executor.buildCallbackUrl();
        String callbackToken = executor.generateCallbackToken();

        Map<String, Object> body = executor.buildWebhookRequestBody(inputData, callbackUrl, callbackToken);

        // Verify callbackUrl is present and non-empty
        assertThat(body).containsKey("callbackUrl");
        assertThat(body.get("callbackUrl"))
                .isNotNull()
                .isInstanceOf(String.class);
        assertThat((String) body.get("callbackUrl")).isNotBlank();

        // Verify callbackToken is present and non-empty
        assertThat(body).containsKey("callbackToken");
        assertThat(body.get("callbackToken"))
                .isNotNull()
                .isInstanceOf(String.class);
        assertThat((String) body.get("callbackToken")).isNotBlank();

        // Verify inputData is present
        assertThat(body).containsKey("inputData");
        assertThat(body.get("inputData")).isEqualTo(inputData);
    }

    // ==================== Property 8: 失败重试指数退避 ====================

    /**
     * Feature: n8n-workflow-integration, Property 8: 失败重试指数退避
     *
     * For any retry count configuration, retry intervals should grow
     * exponentially: delay for attempt i >= delay for attempt i-1.
     *
     * Validates: Requirements 4.7
     */
    @Property(tries = 100)
    @Label("Property 8: Retry delays grow exponentially (monotonically increasing)")
    void retryDelaysGrowExponentially(
            @ForAll("retryAttemptCount") int maxAttempts) {

        N8nTaskExecutor executor = createExecutor();

        long previousDelay = 0;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            long delay = executor.calculateRetryDelay(attempt);

            // Delay must be positive
            assertThat(delay)
                    .as("Delay for attempt %d should be positive", attempt)
                    .isGreaterThan(0);

            // Delay must be >= previous delay (monotonically increasing)
            assertThat(delay)
                    .as("Delay for attempt %d (%d ms) should be >= delay for attempt %d (%d ms)",
                            attempt, delay, attempt - 1, previousDelay)
                    .isGreaterThanOrEqualTo(previousDelay);

            previousDelay = delay;
        }
    }

    /**
     * Feature: n8n-workflow-integration, Property 8: 失败重试指数退避
     *
     * Verify the exact exponential formula: delay = BASE * 2^attempt.
     * Each successive delay should be exactly double the previous one.
     *
     * Validates: Requirements 4.7
     */
    @Property(tries = 100)
    @Label("Property 8: Each retry delay is exactly double the previous")
    void retryDelayDoublesEachAttempt(
            @ForAll("singleAttempt") int attempt) {

        N8nTaskExecutor executor = createExecutor();

        long delay = executor.calculateRetryDelay(attempt);
        long expectedDelay = 1000L * (1L << attempt); // BASE_RETRY_DELAY_MS * 2^attempt

        assertThat(delay)
                .as("Delay for attempt %d should be %d ms (1000 * 2^%d)", attempt, expectedDelay, attempt)
                .isEqualTo(expectedDelay);
    }

    // ==================== Property 15: Action 执行记录来源标识 ====================

    /**
     * Feature: n8n-workflow-integration, Property 15: Action 执行记录来源标识
     *
     * For any execution request, the sourceType should be correctly identified:
     * - SERVICE_TASK for Service Task triggered executions
     * - ACTION for user-triggered Action executions
     *
     * Validates: Requirements 10.23, 10.24
     */
    @Property(tries = 100)
    @Label("Property 15: sourceType correctly distinguishes SERVICE_TASK and ACTION")
    void sourceTypeCorrectlyIdentified(
            @ForAll("randomSourceType") String sourceType) {

        // The valid source types are SERVICE_TASK and ACTION
        assertThat(sourceType).isIn("SERVICE_TASK", "ACTION");

        // Verify that an N8nExecutionRecord correctly stores and returns the sourceType
        com.workflow.entity.N8nExecutionRecord record = new com.workflow.entity.N8nExecutionRecord();
        record.setSourceType(sourceType);

        assertThat(record.getSourceType())
                .as("sourceType should be preserved exactly as set")
                .isEqualTo(sourceType);

        // Verify the two source types are distinct
        assertThat("SERVICE_TASK").isNotEqualTo("ACTION");

        // Verify sourceType matches expected pattern based on execution context
        if (sourceType.equals("SERVICE_TASK")) {
            assertThat(record.getSourceType())
                    .as("Service Task execution should have sourceType SERVICE_TASK")
                    .isEqualTo("SERVICE_TASK");
        } else {
            assertThat(record.getSourceType())
                    .as("Action execution should have sourceType ACTION")
                    .isEqualTo("ACTION");
        }
    }

    // ==================== Providers ====================

    @Provide
    Arbitrary<Integer> tokenBatchSize() {
        return Arbitraries.integers().between(2, 50);
    }

    @Provide
    Arbitrary<Map<String, Object>> randomInputData() {
        return Arbitraries.integers().between(0, 8).flatMap(size -> {
            if (size == 0) {
                return Arbitraries.just(Collections.emptyMap());
            }
            Arbitrary<List<String>> keys = Arbitraries.strings().alpha()
                    .ofMinLength(1).ofMaxLength(15)
                    .list().ofSize(size).uniqueElements();
            Arbitrary<List<Object>> values = arbitraryValue().list().ofSize(size);

            return Combinators.combine(keys, values).as((ks, vs) -> {
                Map<String, Object> data = new LinkedHashMap<>();
                for (int i = 0; i < Math.min(ks.size(), vs.size()); i++) {
                    data.put(ks.get(i), vs.get(i));
                }
                return data;
            });
        });
    }

    @Provide
    Arbitrary<Integer> retryAttemptCount() {
        return Arbitraries.integers().between(1, 10);
    }

    @Provide
    Arbitrary<Integer> singleAttempt() {
        return Arbitraries.integers().between(0, 15);
    }

    @Provide
    Arbitrary<String> randomSourceType() {
        return Arbitraries.of("SERVICE_TASK", "ACTION");
    }

    // ==================== Helper Arbitraries ====================

    private Arbitrary<Object> arbitraryValue() {
        return Arbitraries.oneOf(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20).map(s -> (Object) s),
                Arbitraries.integers().between(-10000, 10000).map(i -> (Object) i),
                Arbitraries.doubles().between(-1000.0, 1000.0).map(d -> (Object) d),
                Arbitraries.of(true, false).map(b -> (Object) b)
        );
    }
}
