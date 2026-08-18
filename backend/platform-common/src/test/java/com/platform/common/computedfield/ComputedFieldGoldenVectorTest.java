package com.platform.common.computedfield;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Cross-language contract test: the Java interpreter must agree with the TypeScript one on every
 * golden vector.
 *
 * <p>This is the mechanism that keeps the client's live preview and the server's authoritative
 * recalculation from drifting apart. Both sides read the SAME vector file — the TS engine reads
 * {@code goldenVectors.json} directly, this test reads {@code goldenVectors.compiled.json}, which
 * is that same file with ASTs attached by the (single, TypeScript-side) parser.
 *
 * <p>The file is located by walking up from the module directory rather than through the classpath:
 * it is frontend source, deliberately NOT copied into the jar. Its absence is asserted first and
 * loudly, because a silently skipped contract test is worse than no contract test.
 */
@DisplayName("Computed field golden vectors (Java side of the cross-language contract)")
class ComputedFieldGoldenVectorTest {

    private static final String RELATIVE_PATH =
            "frontend/shared/src/computedField/goldenVectors.compiled.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode suite;

    @BeforeAll
    static void loadVectors() throws IOException {
        Path path = locateVectorFile();
        assertThat(path)
                .as("Golden vector file must exist at <repo>/%s. Regenerate it with: "
                        + "cd frontend/user-portal && npm run vectors:build", RELATIVE_PATH)
                .isNotNull();
        suite = MAPPER.readTree(Files.readString(path));
        assertThat(suite.path("cases").isArray())
                .as("Golden vector file must contain a 'cases' array")
                .isTrue();
        assertThat(suite.path("cases")).as("Golden vectors must not be empty").isNotEmpty();
    }

    /**
     * Walks up from the working directory looking for the frontend vector file, so the test works
     * whether Maven runs from the module directory or the repository root.
     */
    private static Path locateVectorFile() {
        Path cursor = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 6 && cursor != null; depth++) {
            Path candidate = cursor.resolve(RELATIVE_PATH);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            cursor = cursor.getParent();
        }
        return null;
    }

    @Test
    @DisplayName("division scale matches the value pinned in the shared vectors")
    void divisionScaleMatches() {
        assertThat(suite.path("divisionScale").asInt())
                .as("DIVISION_SCALE must be identical on both sides")
                .isEqualTo(ComputedFieldDecimals.DIVISION_SCALE);
    }

    @Test
    @DisplayName("function whitelist is identical on both sides")
    void functionSetMatches() {
        List<String> fromTypeScript = new ArrayList<>();
        suite.path("functionNames").forEach(name -> fromTypeScript.add(name.asText()));
        assertThat(fromTypeScript)
                .as("goldenVectors.compiled.json must carry the TypeScript function list")
                .isNotEmpty();
        assertThat(ComputedFieldFunctions.allNames().stream().sorted().collect(Collectors.toList()))
                .as("a function supported by one engine but not the other would make a formula "
                        + "compile in the designer and fail on write, or vice versa")
                .isEqualTo(fromTypeScript.stream().sorted().collect(Collectors.toList()));
    }

    @TestFactory
    @DisplayName("evaluation cases")
    List<DynamicTest> evaluationCases() {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode testCase : suite.path("cases")) {
            tests.add(DynamicTest.dynamicTest(testCase.path("name").asText(),
                    () -> runEvaluationCase(testCase)));
        }
        return tests;
    }

    @TestFactory
    @DisplayName("sub-table alias de-duplication")
    List<DynamicTest> aliasDeduplicationCases() {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode testCase : suite.path("aliasDeduplicationCases")) {
            tests.add(DynamicTest.dynamicTest(testCase.path("name").asText(),
                    () -> runAliasCase(testCase)));
        }
        return tests;
    }

    private void runEvaluationCase(JsonNode testCase) {
        Map<String, Object> row = toMap(testCase.path("row"));
        Map<String, List<Map<String, Object>>> subTables = toSubTables(testCase.path("subTables"));
        Map<String, Map<String, Object>> parents = toParents(testCase.path("parents"));
        ComputedFieldContext context = new ComputedFieldContext(row, subTables, parents);
        EvalOutcome outcome = ComputedFieldEvaluator.evaluate(testCase.path("ast"), context);
        assertOutcome(testCase, outcome);
    }

    private void runAliasCase(JsonNode testCase) {
        Map<String, Object> raw = toMap(testCase.path("rawSubTables"));
        JsonNode identities = testCase.path("sliceIdentities");
        Map<String, List<Map<String, Object>>> normalized = SubTableNormalizer.normalize(raw,
                sliceKey -> {
                    JsonNode identity = identities.path(sliceKey);
                    if (identity.isMissingNode()) {
                        return null;
                    }
                    Object tableId = identity.hasNonNull("tableId")
                            ? identity.path("tableId").asText() : null;
                    String tableName = identity.hasNonNull("tableName")
                            ? identity.path("tableName").asText() : null;
                    return new SubTableNormalizer.SliceIdentity(tableId, tableName);
                });

        List<String> expectedKeys = new ArrayList<>();
        testCase.path("expectedCanonicalKeys").forEach(key -> expectedKeys.add(key.asText()));
        assertThat(normalized.keySet().stream().sorted().collect(Collectors.toList()))
                .as("aliases must collapse to one slice per table, keyed by table name")
                .isEqualTo(expectedKeys.stream().sorted().collect(Collectors.toList()));

        EvalOutcome outcome = ComputedFieldEvaluator.evaluate(testCase.path("ast"),
                new ComputedFieldContext(Map.of(), normalized));
        assertOutcome(testCase, outcome);
    }

    private void assertOutcome(JsonNode testCase, EvalOutcome outcome) {
        String name = testCase.path("name").asText();
        if (testCase.hasNonNull("expectError")) {
            String expected = testCase.path("expectError").asText();
            if (!(outcome instanceof EvalOutcome.Failure failure)) {
                fail("[%s] expected error %s but evaluation succeeded with %s",
                        name, expected, describe(outcome));
                return;
            }
            assertThat(failure.error().code().name()).as("[%s] error code", name).isEqualTo(expected);
            return;
        }
        if (!(outcome instanceof EvalOutcome.Success success)) {
            EvalOutcome.Failure failure = (EvalOutcome.Failure) outcome;
            fail("[%s] unexpected evaluation error: %s", name, failure.error());
            return;
        }
        ComputedValue value = success.value();
        if (testCase.path("expectBlank").asBoolean(false)) {
            assertThat(value.isBlank()).as("[%s] expected a blank result but got %s",
                    name, describe(outcome)).isTrue();
            return;
        }
        JsonNode expected = testCase.path("expect");
        if (expected.isBoolean()) {
            assertThat(value).as("[%s] expected a boolean", name)
                    .isInstanceOf(ComputedValue.Bool.class);
            assertThat(((ComputedValue.Bool) value).value()).as("[%s]", name)
                    .isEqualTo(expected.asBoolean());
            return;
        }
        String expectedText = expected.asText();
        if (value instanceof ComputedValue.Number number) {
            // Compare the canonical text form, not the numeric value: scale is part of the
            // contract, so 31.00 and 31 must NOT be treated as interchangeable.
            assertThat(ComputedFieldDecimals.toText(number.value()))
                    .as("[%s] numeric result including scale", name)
                    .isEqualTo(expectedText);
            return;
        }
        assertThat(ComputedValues.toText(value)).as("[%s] text result", name).isEqualTo(expectedText);
    }

    private String describe(EvalOutcome outcome) {
        if (outcome instanceof EvalOutcome.Success success) {
            ComputedValue value = success.value();
            if (value instanceof ComputedValue.Number number) {
                return "number " + ComputedFieldDecimals.toText(number.value());
            }
            return value.kind() + " " + ComputedValues.toText(value);
        }
        return String.valueOf(((EvalOutcome.Failure) outcome).error());
    }

    /**
     * Converts a JSON object to a raw value map, preserving numeric text so the interpreter sees
     * exactly what the TypeScript side sees. Numbers stay BigDecimal rather than becoming double.
     */
    private Map<String, Object> toMap(JsonNode node) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!node.isObject()) {
            return result;
        }
        node.fields().forEachRemaining(entry -> result.put(entry.getKey(), toValue(entry.getValue())));
        return result;
    }

    private Object toValue(JsonNode node) {
        if (node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNumber()) {
            return new BigDecimal(node.asText());
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray()) {
            List<Object> rows = new ArrayList<>();
            node.forEach(element -> rows.add(toValue(element)));
            return rows;
        }
        if (node.isObject()) {
            return toMap(node);
        }
        throw new UncheckedIOException(new IOException("Unsupported vector value: " + node));
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<Map<String, Object>>> toSubTables(JsonNode node) {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        if (!node.isObject()) {
            return result;
        }
        node.fields().forEachRemaining(entry -> {
            List<Map<String, Object>> rows = new ArrayList<>();
            entry.getValue().forEach(row -> rows.add(toMap(row)));
            // Vector keys are already canonical table names; runtime callers must go through
            // SubTableNormalizer instead.
            result.put(entry.getKey().toLowerCase(), rows);
        });
        return result;
    }

    private Map<String, Map<String, Object>> toParents(JsonNode node) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (!node.isObject()) {
            return result;
        }
        node.fields().forEachRemaining(entry ->
                result.put(entry.getKey().toLowerCase(), toMap(entry.getValue())));
        return result;
    }
}
