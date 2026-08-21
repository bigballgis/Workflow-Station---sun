package com.platform.common.computedfield;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * The short-circuiting functions: IF, AND, OR, SWITCH, COALESCE.
 *
 * <p>These take unevaluated argument nodes rather than values, because evaluating every branch
 * would break the guard pattern authors rely on: {@code IF(qty = 0, 0, total / qty)} must not
 * raise DIVISION_BY_ZERO from the branch it does not take. Mirrors {@code evaluateLazyCall} in
 * {@code evaluator.ts}.
 */
final class ComputedFieldLazyFunctions {

    private ComputedFieldLazyFunctions() {
    }

    /**
     * Evaluates a lazy function.
     *
     * @param fn        upper-case function name, already known to be lazy
     * @param args      unevaluated argument nodes
     * @param context   row and sub-table data
     * @param depth     current recursion depth
     * @param evaluator callback into the interpreter
     * @return the value or an explicit failure
     */
    static EvalOutcome evaluate(String fn,
                                List<JsonNode> args,
                                ComputedFieldContext context,
                                int depth,
                                ComputedFieldEvaluator.NodeEvaluator evaluator) {
        switch (fn) {
            case "IF":
                return evaluateIf(args, context, depth, evaluator);
            case "AND":
            case "OR":
                return evaluateAndOr(fn, args, context, depth, evaluator);
            case "SWITCH":
                return evaluateSwitch(args, context, depth, evaluator);
            case "COALESCE":
                return evaluateCoalesce(args, context, depth, evaluator);
            default:
                return EvalOutcome.error(ComputedFieldErrorCode.UNKNOWN_FUNCTION,
                        "Unknown function '" + fn + "'");
        }
    }

    private static EvalOutcome evaluateIf(List<JsonNode> args,
                                          ComputedFieldContext context,
                                          int depth,
                                          ComputedFieldEvaluator.NodeEvaluator evaluator) {
        if (args.size() < 2 || args.size() > 3) {
            return EvalOutcome.error(ComputedFieldErrorCode.WRONG_ARG_COUNT,
                    "IF expects 2-3 arguments but got " + args.size());
        }
        EvalOutcome condition = evaluator.evaluate(args.get(0), context, depth + 1);
        if (!(condition instanceof EvalOutcome.Success success)) {
            return condition;
        }
        Object flag = ComputedValues.toBoolean(success.value(), "IF condition");
        if (flag instanceof EvalOutcome failure) {
            return failure;
        }
        if ((Boolean) flag) {
            return evaluator.evaluate(args.get(1), context, depth + 1);
        }
        return args.size() == 3
                ? evaluator.evaluate(args.get(2), context, depth + 1)
                : EvalOutcome.ok(ComputedValue.BLANK);
    }

    private static EvalOutcome evaluateAndOr(String fn,
                                             List<JsonNode> args,
                                             ComputedFieldContext context,
                                             int depth,
                                             ComputedFieldEvaluator.NodeEvaluator evaluator) {
        if (args.isEmpty()) {
            return EvalOutcome.error(ComputedFieldErrorCode.WRONG_ARG_COUNT,
                    fn + " expects at least 1 argument");
        }
        boolean shortCircuitOn = "OR".equals(fn);
        for (JsonNode arg : args) {
            EvalOutcome evaluated = evaluator.evaluate(arg, context, depth + 1);
            if (!(evaluated instanceof EvalOutcome.Success success)) {
                return evaluated;
            }
            Object flag = ComputedValues.toBoolean(success.value(), fn);
            if (flag instanceof EvalOutcome failure) {
                return failure;
            }
            if ((Boolean) flag == shortCircuitOn) {
                return EvalOutcome.ok(ComputedValue.of(shortCircuitOn));
            }
        }
        return EvalOutcome.ok(ComputedValue.of(!shortCircuitOn));
    }

    /** SWITCH(expr, match1, result1, [match2, result2, ...], [default]). */
    private static EvalOutcome evaluateSwitch(List<JsonNode> args,
                                              ComputedFieldContext context,
                                              int depth,
                                              ComputedFieldEvaluator.NodeEvaluator evaluator) {
        if (args.size() < 3) {
            return EvalOutcome.error(ComputedFieldErrorCode.WRONG_ARG_COUNT,
                    "SWITCH expects at least 3 arguments");
        }
        EvalOutcome subject = evaluator.evaluate(args.get(0), context, depth + 1);
        if (!(subject instanceof EvalOutcome.Success subjectOk)) {
            return subject;
        }
        int i = 1;
        while (i + 1 < args.size()) {
            EvalOutcome candidate = evaluator.evaluate(args.get(i), context, depth + 1);
            if (!(candidate instanceof EvalOutcome.Success candidateOk)) {
                return candidate;
            }
            Object equal = ComputedValues.valuesEqual(subjectOk.value(), candidateOk.value());
            if (equal instanceof EvalOutcome failure) {
                return failure;
            }
            if ((Boolean) equal) {
                return evaluator.evaluate(args.get(i + 1), context, depth + 1);
            }
            i += 2;
        }
        // A trailing odd argument is the default branch.
        return i < args.size()
                ? evaluator.evaluate(args.get(i), context, depth + 1)
                : EvalOutcome.ok(ComputedValue.BLANK);
    }

    private static EvalOutcome evaluateCoalesce(List<JsonNode> args,
                                                ComputedFieldContext context,
                                                int depth,
                                                ComputedFieldEvaluator.NodeEvaluator evaluator) {
        if (args.isEmpty()) {
            return EvalOutcome.error(ComputedFieldErrorCode.WRONG_ARG_COUNT,
                    "COALESCE expects at least 1 argument");
        }
        for (JsonNode arg : args) {
            EvalOutcome evaluated = evaluator.evaluate(arg, context, depth + 1);
            if (!(evaluated instanceof EvalOutcome.Success success)) {
                return evaluated;
            }
            if (!success.value().isBlank()) {
                return evaluated;
            }
        }
        return EvalOutcome.ok(ComputedValue.BLANK);
    }
}
