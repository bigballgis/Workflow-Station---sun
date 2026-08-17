package com.platform.common.computedfield;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Conservative static type inference for a computed-field AST.
 *
 * <p>Used at design time to reject a formula whose result cannot fit the column it is stored in —
 * a text-producing formula on a DECIMAL column would otherwise fail on every single write at
 * runtime instead of at save time.
 *
 * <p>Deliberately conservative: anything not provably one kind comes back {@link ResultKind#UNKNOWN}
 * and the caller skips the check. Guessing here would reject valid formulas, which is worse than
 * letting an exotic one through to the runtime error path.
 */
public final class ComputedFieldTypeInference {

    /** Inferred result kind of an expression. */
    public enum ResultKind {
        /** Produces a number. */
        NUMBER,
        /** Produces text. */
        TEXT,
        /** Produces true/false. */
        BOOLEAN,
        /** Cannot be determined statically; the caller must not enforce a type. */
        UNKNOWN
    }

    private static final Set<String> COMPARISON_OPERATORS = Set.of("=", "<>", "<", "<=", ">", ">=");

    private static final Set<String> NUMBER_FUNCTIONS = Set.of(
            "ROUND", "ROUNDUP", "ROUNDDOWN", "TRUNC", "ABS", "INT", "SQRT", "POWER", "MOD",
            "LEN", "FIND", "VALUE", "DATEDIFF");

    private static final Set<String> TEXT_FUNCTIONS = Set.of(
            "CONCAT", "TRIM", "UPPER", "LOWER", "LEFT", "RIGHT", "MID", "SUBSTITUTE");

    private static final Set<String> BOOLEAN_FUNCTIONS = Set.of(
            "STARTSWITH", "ENDSWITH", "ISBLANK", "NOT", "AND", "OR");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ComputedFieldTypeInference() {
    }

    /**
     * Infers the result kind of an AST stored as a JSONB-derived map.
     *
     * @param ast           the AST map
     * @param fieldResolver maps a same-row field name to its kind, returning UNKNOWN when unsure
     * @return the inferred kind
     */
    public static ResultKind infer(Map<String, Object> ast, Function<String, ResultKind> fieldResolver) {
        if (ast == null || ast.isEmpty()) {
            return ResultKind.UNKNOWN;
        }
        return infer(MAPPER.convertValue(ast, JsonNode.class), fieldResolver);
    }

    /**
     * Infers the result kind of an AST node tree.
     *
     * @param node          root node
     * @param fieldResolver maps a same-row field name to its kind
     * @return the inferred kind
     */
    public static ResultKind infer(JsonNode node, Function<String, ResultKind> fieldResolver) {
        if (node == null || !node.isObject()) {
            return ResultKind.UNKNOWN;
        }
        switch (node.path("type").asText("")) {
            case "number":
                return ResultKind.NUMBER;
            case "text":
                return ResultKind.TEXT;
            case "boolean":
                return ResultKind.BOOLEAN;
            case "aggregate":
                return ResultKind.NUMBER;
            case "unary":
                return ResultKind.NUMBER;
            case "field": {
                String table = node.path("table").asText("");
                String name = node.path("name").asText("");
                if (fieldResolver == null) {
                    return ResultKind.UNKNOWN;
                }
                String key = table.isBlank() ? name : table + "." + name;
                return orUnknown(fieldResolver.apply(key));
            }
            case "binary":
                return COMPARISON_OPERATORS.contains(node.path("op").asText(""))
                        ? ResultKind.BOOLEAN
                        : ResultKind.NUMBER;
            case "call":
                return inferCall(node, fieldResolver);
            default:
                return ResultKind.UNKNOWN;
        }
    }

    private static ResultKind inferCall(JsonNode node, Function<String, ResultKind> fieldResolver) {
        String fn = node.path("fn").asText("");
        if (NUMBER_FUNCTIONS.contains(fn)) {
            return ResultKind.NUMBER;
        }
        if (TEXT_FUNCTIONS.contains(fn)) {
            return ResultKind.TEXT;
        }
        if (BOOLEAN_FUNCTIONS.contains(fn)) {
            return ResultKind.BOOLEAN;
        }
        JsonNode args = node.path("args");
        if (!args.isArray()) {
            return ResultKind.UNKNOWN;
        }
        // The branching forms are as specific as their branches agree on being.
        switch (fn) {
            case "IF":
                return unify(fieldResolver, branchesOfIf(args));
            case "SWITCH":
                return unify(fieldResolver, branchesOfSwitch(args));
            case "COALESCE":
                return unify(fieldResolver, allOf(args));
            default:
                return ResultKind.UNKNOWN;
        }
    }

    private static List<JsonNode> branchesOfIf(JsonNode args) {
        if (args.size() == 3) {
            return List.of(args.get(1), args.get(2));
        }
        if (args.size() == 2) {
            // No else branch: the untaken path yields blank, which fits any column kind.
            return List.of(args.get(1));
        }
        return List.of();
    }

    private static List<JsonNode> branchesOfSwitch(JsonNode args) {
        // SWITCH(expr, match1, result1, [match2, result2, ...], [default]) — results sit at even
        // indexes from 2 onward; an even total argument count means the last one is the default.
        int size = args.size();
        List<JsonNode> branches = new ArrayList<>();
        for (int i = 2; i < size; i += 2) {
            branches.add(args.get(i));
        }
        if (size % 2 == 0) {
            branches.add(args.get(size - 1));
        }
        return branches;
    }

    private static List<JsonNode> allOf(JsonNode args) {
        List<JsonNode> branches = new ArrayList<>();
        args.forEach(branches::add);
        return branches;
    }

    private static ResultKind unify(Function<String, ResultKind> fieldResolver, List<JsonNode> branches) {
        ResultKind agreed = null;
        for (JsonNode branch : branches) {
            ResultKind kind = infer(branch, fieldResolver);
            if (kind == ResultKind.UNKNOWN) {
                return ResultKind.UNKNOWN;
            }
            if (agreed == null) {
                agreed = kind;
            } else if (agreed != kind) {
                return ResultKind.UNKNOWN;
            }
        }
        return agreed == null ? ResultKind.UNKNOWN : agreed;
    }

    private static ResultKind orUnknown(ResultKind kind) {
        return kind == null ? ResultKind.UNKNOWN : kind;
    }
}
