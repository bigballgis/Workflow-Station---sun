package com.platform.common.computedfield;

/**
 * Failure reasons for computed-field compilation and evaluation.
 *
 * <p>The names are the contract shared with {@code ComputedFieldErrorCode} in
 * {@code frontend/shared/src/computedField/types.ts} and with the {@code expectError} entries in
 * {@code goldenVectors.json}. Renaming a constant here breaks the cross-language contract test.
 *
 * <p>There is deliberately no "returned zero because something went wrong" state: every failure
 * is explicit, per {@code error-handling-governance.mdc} red line 1.
 */
public enum ComputedFieldErrorCode {

    /** Source text could not be parsed, or a stored AST node is structurally invalid. */
    SYNTAX_ERROR,

    /** Function name is not on the whitelist. */
    UNKNOWN_FUNCTION,

    /** Referenced field does not exist on the table. */
    UNKNOWN_FIELD,

    /** Referenced sub-table is not present on the record being evaluated. */
    UNKNOWN_TABLE,

    /** Function called with too few or too many arguments. */
    WRONG_ARG_COUNT,

    /** Operand or argument was of an unusable kind, e.g. arithmetic on non-numeric text. */
    TYPE_MISMATCH,

    /** Division, MOD or AVG with a zero divisor. Never silently yields zero. */
    DIVISION_BY_ZERO,

    /** SQRT of a negative number. Never silently yields NaN. */
    NEGATIVE_SQRT,

    /** POWER with a fractional exponent, which is refused to keep both engines bit-identical. */
    NON_INTEGER_EXPONENT,

    /** Formula exceeded the node, length or dependency budget. */
    BUDGET_EXCEEDED,

    /** AST contained a node kind or operator this engine version does not implement. */
    UNSUPPORTED_NODE,

    /** Formula dependencies form a cycle. Detected at design time only. */
    CIRCULAR_DEPENDENCY
}
