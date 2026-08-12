package com.platform.common.computedfield;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

import static com.platform.common.computedfield.ComputedFieldArgs.checkArity;
import static com.platform.common.computedfield.ComputedFieldArgs.number;
import static com.platform.common.computedfield.ComputedFieldArgs.wholeNumber;

/**
 * Math functions — the Power Fx numeric set, mirroring {@code functionsMath.ts}.
 *
 * <p>All arithmetic delegates to {@link ComputedFieldDecimals} so that the rounding mode and the
 * division scale are decided in one place rather than restated per function.
 */
public final class ComputedFieldMathFunctions {

    private ComputedFieldMathFunctions() {
    }

    /**
     * Builds the math function table.
     *
     * @return function name (upper case) to implementation
     */
    public static Map<String, ComputedFieldFunction> create() {
        Map<String, ComputedFieldFunction> functions = new LinkedHashMap<>();
        functions.put("ROUND", rounding("ROUND", ComputedFieldDecimals::round));
        functions.put("ROUNDUP", rounding("ROUNDUP", ComputedFieldDecimals::roundAwayFromZero));
        functions.put("ROUNDDOWN", rounding("ROUNDDOWN", ComputedFieldDecimals::truncate));
        functions.put("TRUNC", rounding("TRUNC", ComputedFieldDecimals::truncate));
        functions.put("ABS", unary("ABS", BigDecimal::abs));
        functions.put("INT", unary("INT", value -> ComputedFieldDecimals.truncate(value, 0)));
        functions.put("SQRT", ComputedFieldMathFunctions::sqrt);
        functions.put("POWER", ComputedFieldMathFunctions::power);
        functions.put("MOD", ComputedFieldMathFunctions::mod);
        return functions;
    }

    private static ComputedFieldFunction unary(String fn, UnaryOperator<BigDecimal> apply) {
        return args -> {
            EvalOutcome arityError = checkArity(fn, args, 1, 1);
            if (arityError != null) {
                return arityError;
            }
            Object value = number(fn, args, 0);
            if (value instanceof EvalOutcome failure) {
                return failure;
            }
            return EvalOutcome.ok(apply.apply((BigDecimal) value));
        };
    }

    private static ComputedFieldFunction rounding(String fn,
                                                  BiFunction<BigDecimal, Integer, BigDecimal> apply) {
        return args -> {
            EvalOutcome arityError = checkArity(fn, args, 1, 2);
            if (arityError != null) {
                return arityError;
            }
            Object value = number(fn, args, 0);
            if (value instanceof EvalOutcome failure) {
                return failure;
            }
            Object places = wholeNumber(fn, args, 1, 0);
            if (places instanceof EvalOutcome failure) {
                return failure;
            }
            return EvalOutcome.ok(apply.apply((BigDecimal) value, (Integer) places));
        };
    }

    private static EvalOutcome sqrt(List<ComputedValue> args) {
        EvalOutcome arityError = checkArity("SQRT", args, 1, 1);
        if (arityError != null) {
            return arityError;
        }
        Object value = number("SQRT", args, 0);
        if (value instanceof EvalOutcome failure) {
            return failure;
        }
        BigDecimal result = ComputedFieldDecimals.sqrt((BigDecimal) value);
        if (result == null) {
            return EvalOutcome.error(ComputedFieldErrorCode.NEGATIVE_SQRT,
                    "SQRT is undefined for negative numbers");
        }
        return EvalOutcome.ok(result);
    }

    private static EvalOutcome power(List<ComputedValue> args) {
        EvalOutcome arityError = checkArity("POWER", args, 2, 2);
        if (arityError != null) {
            return arityError;
        }
        Object base = number("POWER", args, 0);
        if (base instanceof EvalOutcome failure) {
            return failure;
        }
        Object exponent = number("POWER", args, 1);
        if (exponent instanceof EvalOutcome failure) {
            return failure;
        }
        BigDecimal result = ComputedFieldDecimals.power((BigDecimal) base, (BigDecimal) exponent);
        if (result == null) {
            return EvalOutcome.error(ComputedFieldErrorCode.NON_INTEGER_EXPONENT,
                    "POWER supports whole-number exponents only, so that results are identical "
                            + "on client and server");
        }
        return EvalOutcome.ok(result);
    }

    private static EvalOutcome mod(List<ComputedValue> args) {
        EvalOutcome arityError = checkArity("MOD", args, 2, 2);
        if (arityError != null) {
            return arityError;
        }
        Object dividend = number("MOD", args, 0);
        if (dividend instanceof EvalOutcome failure) {
            return failure;
        }
        Object divisor = number("MOD", args, 1);
        if (divisor instanceof EvalOutcome failure) {
            return failure;
        }
        BigDecimal result = ComputedFieldDecimals.remainder((BigDecimal) dividend, (BigDecimal) divisor);
        if (result == null) {
            return EvalOutcome.error(ComputedFieldErrorCode.DIVISION_BY_ZERO, "MOD by zero");
        }
        return EvalOutcome.ok(result);
    }
}
