package com.platform.common.computedfield;

import java.math.BigDecimal;
import java.util.List;

/**
 * Argument reading and validation shared by every computed-field function.
 *
 * <p>Exists so that "how many arguments" and "what counts as a whole number" are each decided in
 * exactly one place on this side, mirroring {@code checkArity} / {@code wholeNumberArg} in
 * {@code functionsMath.ts}.
 */
public final class ComputedFieldArgs {

    /** Passed as {@code max} to allow any number of trailing arguments. */
    public static final int VARIADIC = -1;

    private ComputedFieldArgs() {
    }

    /**
     * Validates argument count.
     *
     * @param fn   function name, for the message
     * @param args supplied arguments
     * @param min  minimum accepted count
     * @param max  maximum accepted count, or {@link #VARIADIC}
     * @return null when the count is acceptable, otherwise a failure outcome
     */
    public static EvalOutcome checkArity(String fn, List<ComputedValue> args, int min, int max) {
        int size = args.size();
        if (size >= min && (max == VARIADIC || size <= max)) {
            return null;
        }
        String expected;
        if (max == VARIADIC) {
            expected = "at least " + min;
        } else if (min == max) {
            expected = String.valueOf(min);
        } else {
            expected = min + "-" + max;
        }
        return EvalOutcome.error(ComputedFieldErrorCode.WRONG_ARG_COUNT,
                fn + " expects " + expected + " argument(s) but got " + size);
    }

    /**
     * Reads an argument as a number.
     *
     * @param fn    function name, for the message
     * @param args  supplied arguments
     * @param index zero-based argument position
     * @return BigDecimal, or a failure outcome
     */
    public static Object number(String fn, List<ComputedValue> args, int index) {
        return ComputedValues.toNumber(args.get(index), fn + " argument " + (index + 1));
    }

    /**
     * Reads an argument that must be a whole number: ROUND digit counts, LEFT/MID lengths,
     * FIND start positions.
     *
     * @param fn       function name, for the message
     * @param args     supplied arguments
     * @param index    zero-based argument position
     * @param fallback value to use when the argument was omitted
     * @return Integer, or a failure outcome
     */
    public static Object wholeNumber(String fn, List<ComputedValue> args, int index, int fallback) {
        if (args.size() <= index) {
            return Integer.valueOf(fallback);
        }
        Object raw = number(fn, args, index);
        if (raw instanceof EvalOutcome failure) {
            return failure;
        }
        BigDecimal value = (BigDecimal) raw;
        BigDecimal truncated = ComputedFieldDecimals.truncate(value, 0);
        if (truncated.compareTo(value) != 0) {
            return EvalOutcome.error(ComputedFieldErrorCode.TYPE_MISMATCH,
                    fn + " argument " + (index + 1) + " must be a whole number");
        }
        return Integer.valueOf(truncated.intValueExact());
    }

    /**
     * Reads an argument in text form, applying the canonical rendering rules.
     *
     * @param args  supplied arguments
     * @param index zero-based argument position
     * @return text form of the argument
     */
    public static String text(List<ComputedValue> args, int index) {
        return ComputedValues.toText(args.get(index));
    }
}
