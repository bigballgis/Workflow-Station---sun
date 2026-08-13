package com.platform.common.computedfield;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Structural validation of a stored computed-field AST, independent of any table metadata.
 *
 * <p>This is the security boundary for formula storage. The client compiles source text into an
 * AST, but the server trusts NOTHING it sends: node kinds, operators and function names are all
 * checked against whitelists here, and the dependency list is RE-DERIVED from the tree rather than
 * read from the request, so a tampered {@code dependsOn} cannot smuggle in a reference.
 *
 * <p>Table-aware checks — does the field exist, is there a dependency cycle, is the target column
 * allowed to be computed — need the Function Unit's field definitions and therefore live in the
 * developer-workstation validator that calls this one.
 */
public final class ComputedFieldAstValidator {

    /** Node budget, matching {@code MAX_AST_NODES} in parser.ts. */
    public static final int MAX_AST_NODES = 200;

    /** Nesting budget; also enforced at evaluation time. */
    public static final int MAX_DEPTH = 64;

    private static final Set<String> BINARY_OPERATORS =
            Set.of("+", "-", "*", "/", "=", "<>", "<", "<=", ">", ">=");

    private static final Set<String> AGGREGATE_FUNCTIONS =
            Set.of("SUM", "AVG", "MIN", "MAX", "COUNT");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ComputedFieldAstValidator() {
    }

    /**
     * Outcome of structural validation.
     *
     * @param errors           every problem found, empty when the AST is acceptable
     * @param fieldDependencies same-row field names the formula reads
     * @param tableDependencies sub-table references as {@code table} or {@code table.column}
     * @param hasAggregate     whether the formula reaches into sub-table rows
     */
    public record Result(List<ComputedFieldError> errors,
                         Set<String> fieldDependencies,
                         Set<String> tableDependencies,
                         boolean hasAggregate) {

        /**
         * Whether the AST passed structural validation.
         *
         * @return true when no errors were found
         */
        public boolean isValid() {
            return errors.isEmpty();
        }

        /**
         * Dependencies in the same combined form the client reports, for comparison.
         *
         * @return sorted union of field and table dependencies
         */
        public List<String> allDependencies() {
            Set<String> all = new LinkedHashSet<>(fieldDependencies);
            all.addAll(tableDependencies);
            return all.stream().sorted().toList();
        }
    }

    /**
     * Validates an AST stored as a JSONB-derived map.
     *
     * @param ast the AST map
     * @return the validation result
     */
    public static Result validate(Map<String, Object> ast) {
        if (ast == null || ast.isEmpty()) {
            return new Result(List.of(ComputedFieldError.of(ComputedFieldErrorCode.SYNTAX_ERROR,
                    "Computed field has no AST")), Set.of(), Set.of(), false);
        }
        return validate(MAPPER.convertValue(ast, JsonNode.class));
    }

    /**
     * Validates an AST node tree.
     *
     * @param ast root node
     * @return the validation result
     */
    public static Result validate(JsonNode ast) {
        Walker walker = new Walker();
        walker.walk(ast, 0);
        if (walker.nodeCount > MAX_AST_NODES) {
            walker.errors.add(ComputedFieldError.of(ComputedFieldErrorCode.BUDGET_EXCEEDED,
                    "Formula uses " + walker.nodeCount + " nodes, the limit is " + MAX_AST_NODES));
        }
        return new Result(List.copyOf(walker.errors), Set.copyOf(walker.fields),
                Set.copyOf(walker.tables), walker.hasAggregate);
    }

    private static final class Walker {
        private final List<ComputedFieldError> errors = new ArrayList<>();
        private final Set<String> fields = new LinkedHashSet<>();
        private final Set<String> tables = new LinkedHashSet<>();
        private int nodeCount;
        private boolean hasAggregate;

        private void fail(ComputedFieldErrorCode code, String message) {
            errors.add(ComputedFieldError.of(code, message));
        }

        private void walk(JsonNode node, int depth) {
            if (depth > MAX_DEPTH) {
                fail(ComputedFieldErrorCode.BUDGET_EXCEEDED,
                        "Formula nesting exceeds " + MAX_DEPTH + " levels");
                return;
            }
            if (node == null || !node.isObject()) {
                fail(ComputedFieldErrorCode.SYNTAX_ERROR, "Malformed AST node");
                return;
            }
            nodeCount++;
            if (nodeCount > MAX_AST_NODES) {
                // Stop descending; the caller reports the budget error once.
                return;
            }
            String type = node.path("type").asText("");
            switch (type) {
                case "number" -> validateNumber(node);
                case "text" -> requireField(node, "value", "text literal");
                case "boolean" -> requireField(node, "value", "boolean literal");
                case "field" -> validateFieldRef(node);
                case "aggregate" -> validateAggregate(node);
                case "unary" -> validateUnary(node, depth);
                case "binary" -> validateBinary(node, depth);
                case "call" -> validateCall(node, depth);
                default -> fail(ComputedFieldErrorCode.UNSUPPORTED_NODE,
                        "Unsupported node type '" + type + "'");
            }
        }

        private void requireField(JsonNode node, String property, String what) {
            if (!node.has(property)) {
                fail(ComputedFieldErrorCode.SYNTAX_ERROR,
                        "Malformed " + what + ": missing '" + property + "'");
            }
        }

        private void validateNumber(JsonNode node) {
            String text = node.path("text").asText("");
            if (ComputedFieldDecimals.parse(text) == null) {
                fail(ComputedFieldErrorCode.SYNTAX_ERROR, "Invalid numeric literal '" + text + "'");
            }
        }

        private void validateFieldRef(JsonNode node) {
            String name = node.path("name").asText("");
            if (name.isBlank()) {
                fail(ComputedFieldErrorCode.SYNTAX_ERROR, "Field reference has no name");
                return;
            }
            fields.add(name);
        }

        private void validateAggregate(JsonNode node) {
            hasAggregate = true;
            String fn = node.path("fn").asText("");
            if (!AGGREGATE_FUNCTIONS.contains(fn)) {
                fail(ComputedFieldErrorCode.UNKNOWN_FUNCTION, "Unknown aggregate '" + fn + "'");
            }
            String table = node.path("table").asText("");
            if (table.isBlank()) {
                fail(ComputedFieldErrorCode.SYNTAX_ERROR, "Aggregate has no sub-table reference");
                return;
            }
            String column = node.hasNonNull("column") ? node.path("column").asText() : null;
            if (column == null && !"COUNT".equals(fn)) {
                fail(ComputedFieldErrorCode.SYNTAX_ERROR,
                        fn + " requires a column, e.g. " + fn + "(" + table + ".amount)");
                return;
            }
            tables.add(column == null ? table : table + "." + column);
        }

        private void validateUnary(JsonNode node, int depth) {
            if (!"-".equals(node.path("op").asText(""))) {
                fail(ComputedFieldErrorCode.UNSUPPORTED_NODE,
                        "Unsupported unary operator '" + node.path("op").asText("") + "'");
            }
            walk(node.path("operand"), depth + 1);
        }

        private void validateBinary(JsonNode node, int depth) {
            String op = node.path("op").asText("");
            if (!BINARY_OPERATORS.contains(op)) {
                fail(ComputedFieldErrorCode.UNSUPPORTED_NODE, "Unsupported operator '" + op + "'");
            }
            walk(node.path("left"), depth + 1);
            walk(node.path("right"), depth + 1);
        }

        private void validateCall(JsonNode node, int depth) {
            String fn = node.path("fn").asText("");
            if (!ComputedFieldFunctions.isKnown(fn)) {
                fail(ComputedFieldErrorCode.UNKNOWN_FUNCTION, "Unknown function '" + fn + "'");
            }
            JsonNode args = node.path("args");
            if (!args.isArray()) {
                fail(ComputedFieldErrorCode.SYNTAX_ERROR, fn + " has no argument list");
                return;
            }
            for (JsonNode arg : args) {
                walk(arg, depth + 1);
            }
        }
    }
}
