package com.platform.common.computedfield;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Authoritative AST interpreter for computed fields.
 *
 * <p>This is the server-side twin of {@code frontend/shared/src/computedField/evaluator.ts}. The
 * client computes the same formula for live preview, but whatever it submits is discarded and
 * recomputed here — a value that drives a BPMN gateway or an approval threshold cannot be taken
 * from the browser.
 *
 * <p>There is no parser on this side by design: only the stored, already-validated AST is ever
 * evaluated, so no server code path turns user text into executable anything.
 *
 * <p>{@code ComputedFieldGoldenVectorTest} runs this against the same vector file as the
 * TypeScript engine; that test is the mechanism that keeps preview and authority in agreement.
 */
public final class ComputedFieldEvaluator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Depth ceiling — a hand-edited or tampered AST must not be able to blow the JVM stack. */
    private static final int MAX_DEPTH = 64;

    private ComputedFieldEvaluator() {
    }

    /**
     * Evaluates an AST stored as a JSONB-derived map.
     *
     * @param ast     the AST, as read from {@code computed_field_json.ast}
     * @param context row and sub-table data
     * @return the value or an explicit failure
     */
    public static EvalOutcome evaluate(Map<String, Object> ast, ComputedFieldContext context) {
        if (ast == null || ast.isEmpty()) {
            return EvalOutcome.error(ComputedFieldErrorCode.SYNTAX_ERROR, "Computed field has no AST");
        }
        return evaluate(MAPPER.convertValue(ast, JsonNode.class), context);
    }

    /**
     * Evaluates an AST node tree.
     *
     * @param ast     root node
     * @param context row and sub-table data
     * @return the value or an explicit failure
     */
    public static EvalOutcome evaluate(JsonNode ast, ComputedFieldContext context) {
        return evaluateNode(ast, context, 0);
    }

    private static EvalOutcome evaluateNode(JsonNode node, ComputedFieldContext context, int depth) {
        if (depth > MAX_DEPTH) {
            return EvalOutcome.error(ComputedFieldErrorCode.BUDGET_EXCEEDED,
                    "Formula nesting exceeds " + MAX_DEPTH + " levels");
        }
        if (node == null || !node.isObject()) {
            return EvalOutcome.error(ComputedFieldErrorCode.SYNTAX_ERROR, "Malformed AST node");
        }
        String type = node.path("type").asText("");
        switch (type) {
            case "number":
                return numberLiteral(node);
            case "text":
                return EvalOutcome.ok(ComputedValue.of(node.path("value").asText("")));
            case "boolean":
                return EvalOutcome.ok(ComputedValue.of(node.path("value").asBoolean(false)));
            case "field":
                return field(node, context);
            case "aggregate":
                return aggregate(node, context);
            case "unary":
                return unary(node, context, depth);
            case "binary":
                return binary(node, context, depth);
            case "call":
                return call(node, context, depth);
            default:
                return EvalOutcome.error(ComputedFieldErrorCode.UNSUPPORTED_NODE,
                        "Unsupported node type '" + type + "'");
        }
    }

    private static EvalOutcome numberLiteral(JsonNode node) {
        String text = node.path("text").asText("");
        BigDecimal parsed = ComputedFieldDecimals.parse(text);
        if (parsed == null) {
            return EvalOutcome.error(ComputedFieldErrorCode.SYNTAX_ERROR,
                    "Invalid numeric literal '" + text + "'");
        }
        return EvalOutcome.ok(parsed);
    }

    private static EvalOutcome field(JsonNode node, ComputedFieldContext context) {
        String name = node.path("name").asText("");
        return EvalOutcome.ok(ComputedValues.fromRowValue(context.fieldValue(name)));
    }

    private static EvalOutcome aggregate(JsonNode node, ComputedFieldContext context) {
        String fn = node.path("fn").asText("");
        String table = node.path("table").asText("");
        String column = node.hasNonNull("column") ? node.path("column").asText() : null;
        List<Map<String, Object>> rows = context.rowsOf(table);
        if (rows == null) {
            return EvalOutcome.error(ComputedFieldErrorCode.UNKNOWN_TABLE,
                    "Sub-table '" + table + "' is not present on this record");
        }
        if ("COUNT".equals(fn) && column == null) {
            return EvalOutcome.ok(BigDecimal.valueOf(rows.size()));
        }
        if (column == null) {
            return EvalOutcome.error(ComputedFieldErrorCode.SYNTAX_ERROR,
                    fn + " requires a column, e.g. " + fn + "(" + table + ".amount)");
        }
        List<BigDecimal> numbers = new ArrayList<>();
        int nonBlank = 0;
        for (Map<String, Object> row : rows) {
            ComputedValue cell = ComputedValues.fromRowValue(row.get(column));
            if (cell.isBlank()) {
                continue;
            }
            nonBlank++;
            if ("COUNT".equals(fn)) {
                continue;
            }
            Object asNumber = ComputedValues.toNumber(cell, fn + "(" + table + "." + column + ")");
            if (asNumber instanceof EvalOutcome failure) {
                return failure;
            }
            numbers.add((BigDecimal) asNumber);
        }
        if ("COUNT".equals(fn)) {
            return EvalOutcome.ok(BigDecimal.valueOf(nonBlank));
        }
        // Empty aggregate: SUM is 0, but MIN/MAX/AVG are blank. There is no meaningful minimum of
        // nothing, and returning 0 there would put an invented number into the record.
        if (numbers.isEmpty()) {
            return "SUM".equals(fn)
                    ? EvalOutcome.ok(BigDecimal.ZERO)
                    : EvalOutcome.ok(ComputedValue.BLANK);
        }
        return reduce(fn, numbers);
    }

    private static EvalOutcome reduce(String fn, List<BigDecimal> numbers) {
        switch (fn) {
            case "SUM": {
                BigDecimal total = numbers.get(0);
                for (int i = 1; i < numbers.size(); i++) {
                    total = total.add(numbers.get(i));
                }
                return EvalOutcome.ok(total);
            }
            case "MIN": {
                BigDecimal min = numbers.get(0);
                for (BigDecimal candidate : numbers) {
                    if (candidate.compareTo(min) < 0) {
                        min = candidate;
                    }
                }
                return EvalOutcome.ok(min);
            }
            case "MAX": {
                BigDecimal max = numbers.get(0);
                for (BigDecimal candidate : numbers) {
                    if (candidate.compareTo(max) > 0) {
                        max = candidate;
                    }
                }
                return EvalOutcome.ok(max);
            }
            case "AVG": {
                BigDecimal total = numbers.get(0);
                for (int i = 1; i < numbers.size(); i++) {
                    total = total.add(numbers.get(i));
                }
                BigDecimal mean = ComputedFieldDecimals.divide(total,
                        BigDecimal.valueOf(numbers.size()));
                if (mean == null) {
                    return EvalOutcome.error(ComputedFieldErrorCode.DIVISION_BY_ZERO,
                            "AVG over an empty set");
                }
                return EvalOutcome.ok(mean);
            }
            default:
                return EvalOutcome.error(ComputedFieldErrorCode.UNSUPPORTED_NODE,
                        "Unsupported aggregate '" + fn + "'");
        }
    }

    private static EvalOutcome unary(JsonNode node, ComputedFieldContext context, int depth) {
        EvalOutcome operand = evaluateNode(node.path("operand"), context, depth + 1);
        if (!(operand instanceof EvalOutcome.Success success)) {
            return operand;
        }
        Object value = ComputedValues.toNumber(success.value(), "Unary '-'");
        if (value instanceof EvalOutcome failure) {
            return failure;
        }
        return EvalOutcome.ok(((BigDecimal) value).negate());
    }

    private static EvalOutcome binary(JsonNode node, ComputedFieldContext context, int depth) {
        String op = node.path("op").asText("");
        EvalOutcome left = evaluateNode(node.path("left"), context, depth + 1);
        if (!(left instanceof EvalOutcome.Success leftOk)) {
            return left;
        }
        EvalOutcome right = evaluateNode(node.path("right"), context, depth + 1);
        if (!(right instanceof EvalOutcome.Success rightOk)) {
            return right;
        }
        if ("=".equals(op) || "<>".equals(op)) {
            Object equal = ComputedValues.valuesEqual(leftOk.value(), rightOk.value());
            if (equal instanceof EvalOutcome failure) {
                return failure;
            }
            boolean same = (Boolean) equal;
            return EvalOutcome.ok(ComputedValue.of("=".equals(op) ? same : !same));
        }
        if ("<".equals(op) || "<=".equals(op) || ">".equals(op) || ">=".equals(op)) {
            Object order = ComputedValues.compareValues(leftOk.value(), rightOk.value());
            if (order instanceof EvalOutcome failure) {
                return failure;
            }
            int c = (Integer) order;
            boolean result = switch (op) {
                case "<" -> c < 0;
                case "<=" -> c <= 0;
                case ">" -> c > 0;
                default -> c >= 0;
            };
            return EvalOutcome.ok(ComputedValue.of(result));
        }
        Object a = ComputedValues.toNumber(leftOk.value(), "Operator '" + op + "'");
        if (a instanceof EvalOutcome failure) {
            return failure;
        }
        Object b = ComputedValues.toNumber(rightOk.value(), "Operator '" + op + "'");
        if (b instanceof EvalOutcome failure) {
            return failure;
        }
        BigDecimal x = (BigDecimal) a;
        BigDecimal y = (BigDecimal) b;
        switch (op) {
            case "+":
                return EvalOutcome.ok(x.add(y));
            case "-":
                return EvalOutcome.ok(x.subtract(y));
            case "*":
                return EvalOutcome.ok(x.multiply(y));
            case "/": {
                BigDecimal quotient = ComputedFieldDecimals.divide(x, y);
                if (quotient == null) {
                    return EvalOutcome.error(ComputedFieldErrorCode.DIVISION_BY_ZERO,
                            "Division by zero. Guard it with IF(divisor = 0, …, …).");
                }
                return EvalOutcome.ok(quotient);
            }
            default:
                return EvalOutcome.error(ComputedFieldErrorCode.UNSUPPORTED_NODE,
                        "Unsupported operator '" + op + "'");
        }
    }

    private static EvalOutcome call(JsonNode node, ComputedFieldContext context, int depth) {
        String fn = node.path("fn").asText("");
        JsonNode rawArgs = node.path("args");
        List<JsonNode> argNodes = new ArrayList<>();
        if (rawArgs.isArray()) {
            rawArgs.forEach(argNodes::add);
        }
        if (ComputedFieldFunctions.LAZY_FUNCTIONS.contains(fn)) {
            return ComputedFieldLazyFunctions.evaluate(fn, argNodes, context, depth,
                    ComputedFieldEvaluator::evaluateNode);
        }
        ComputedFieldFunction implementation = ComputedFieldFunctions.eager(fn);
        if (implementation == null) {
            return EvalOutcome.error(ComputedFieldErrorCode.UNKNOWN_FUNCTION,
                    "Unknown function '" + fn + "'");
        }
        List<ComputedValue> args = new ArrayList<>(argNodes.size());
        for (JsonNode argNode : argNodes) {
            EvalOutcome evaluated = evaluateNode(argNode, context, depth + 1);
            if (!(evaluated instanceof EvalOutcome.Success success)) {
                return evaluated;
            }
            args.add(success.value());
        }
        return implementation.apply(args);
    }

    /** Lets the lazy-function helper recurse back into this interpreter. */
    @FunctionalInterface
    interface NodeEvaluator {

        EvalOutcome evaluate(JsonNode node, ComputedFieldContext context, int depth);
    }
}
