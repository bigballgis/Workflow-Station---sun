package com.platform.common.computedfield;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Date functions — mirrors {@code functionsDate.ts}.
 *
 * <p>{@code DATEDIFF(start, end)} follows Power Fx argument order: end minus start, in whole
 * calendar days. The {@code -} operator uses the same day count with reversed operands.
 */
public final class ComputedFieldDateFunctions {

    private ComputedFieldDateFunctions() {
    }

    /**
     * Builds the date function table.
     *
     * @return function name (upper case) to implementation
     */
    public static Map<String, ComputedFieldFunction> create() {
        Map<String, ComputedFieldFunction> functions = new LinkedHashMap<>();
        functions.put("DATEDIFF", ComputedFieldDateFunctions::datediff);
        return functions;
    }

    private static EvalOutcome datediff(List<ComputedValue> args) {
        EvalOutcome arityError = ComputedFieldArgs.checkArity("DATEDIFF", args, 2, 2);
        if (arityError != null) {
            return arityError;
        }
        Long start = ComputedFieldDates.epochDay(args.get(0));
        if (start == null) {
            return dateMismatch(1);
        }
        Long end = ComputedFieldDates.epochDay(args.get(1));
        if (end == null) {
            return dateMismatch(2);
        }
        return EvalOutcome.ok(ComputedValue.of(BigDecimal.valueOf(end - start)));
    }

    private static EvalOutcome dateMismatch(int argument) {
        return EvalOutcome.error(ComputedFieldErrorCode.TYPE_MISMATCH,
                "DATEDIFF argument " + argument
                        + " is not a calendar date (expected YYYY-MM-DD or YYYY/MM/DD)");
    }
}
