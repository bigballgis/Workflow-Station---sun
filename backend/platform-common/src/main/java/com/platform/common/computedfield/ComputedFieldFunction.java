package com.platform.common.computedfield;

import java.util.List;

/**
 * An eager computed-field function: arguments are already evaluated when it is invoked.
 *
 * <p>The lazy forms (IF, AND, OR, SWITCH, COALESCE) are NOT implemented through this interface —
 * they must not evaluate every branch, so {@link ComputedFieldEvaluator} handles them directly.
 */
@FunctionalInterface
public interface ComputedFieldFunction {

    /**
     * Applies the function.
     *
     * @param args evaluated arguments, in source order
     * @return the result, or a failure outcome
     */
    EvalOutcome apply(List<ComputedValue> args);
}
