package com.platform.common.computedfield;

import java.math.BigDecimal;

/**
 * Result of evaluating a computed field: either a value or an error, never a "probably fine" value.
 *
 * <p>Callers must branch on the variant. Returning zero or blank on failure would put an invented
 * number into a BPMN gateway condition or an approval amount, which is the exact class of bug
 * {@code error-handling-governance.mdc} red line 1 forbids.
 */
public sealed interface EvalOutcome {

    /** Successful evaluation. */
    record Success(ComputedValue value) implements EvalOutcome {
    }

    /** Failed evaluation. */
    record Failure(ComputedFieldError error) implements EvalOutcome {
    }

    /**
     * Whether evaluation succeeded.
     *
     * @return true when this is a {@link Success}
     */
    default boolean isOk() {
        return this instanceof Success;
    }

    /**
     * Wraps a value as success.
     *
     * @param value evaluated value
     * @return success outcome
     */
    static EvalOutcome ok(ComputedValue value) {
        return new Success(value);
    }

    /**
     * Wraps a BigDecimal as a successful numeric result.
     *
     * @param value numeric result
     * @return success outcome
     */
    static EvalOutcome ok(BigDecimal value) {
        return new Success(ComputedValue.of(value));
    }

    /**
     * Builds a failure outcome.
     *
     * @param code    failure reason
     * @param message detail text
     * @return failure outcome
     */
    static EvalOutcome error(ComputedFieldErrorCode code, String message) {
        return new Failure(ComputedFieldError.of(code, message));
    }

    /**
     * Re-wraps an existing error, used when propagating a nested failure unchanged.
     *
     * @param error error to propagate
     * @return failure outcome
     */
    static EvalOutcome error(ComputedFieldError error) {
        return new Failure(error);
    }
}
