package com.platform.common.computedfield;

import java.math.BigDecimal;

/**
 * Runtime value of a computed (formula) field expression.
 *
 * <p>Mirrors the {@code ComputedValue} union in
 * {@code frontend/shared/src/computedField/types.ts}. {@code Blank} is a first-class kind rather
 * than null or zero so {@code ISBLANK} can tell "the user left it empty" apart from "it is zero";
 * conflating the two is how Power Platform calculated columns produce misleading results.
 */
public sealed interface ComputedValue {

    /** Numeric value. BigDecimal, never double — money must not round through binary floating point. */
    record Number(BigDecimal value) implements ComputedValue {
    }

    /** Text value. */
    record Text(String value) implements ComputedValue {
    }

    /** Boolean value, produced by comparisons and logical functions. */
    record Bool(boolean value) implements ComputedValue {
    }

    /** Absence of a value: null, missing key, or whitespace-only string. */
    record Blank() implements ComputedValue {
    }

    /** Shared blank instance. */
    ComputedValue BLANK = new Blank();

    /**
     * Whether this value is blank.
     *
     * @return true when the value carries no content
     */
    default boolean isBlank() {
        return this instanceof Blank;
    }

    /**
     * Short kind name used in error messages, matching the TypeScript {@code kind} discriminator.
     *
     * @return one of number, text, boolean, blank
     */
    default String kind() {
        if (this instanceof Number) {
            return "number";
        }
        if (this instanceof Text) {
            return "text";
        }
        if (this instanceof Bool) {
            return "boolean";
        }
        return "blank";
    }

    /**
     * Wraps a BigDecimal.
     *
     * @param value numeric value
     * @return number value
     */
    static ComputedValue of(BigDecimal value) {
        return new Number(value);
    }

    /**
     * Wraps a string.
     *
     * @param value text value
     * @return text value
     */
    static ComputedValue of(String value) {
        return new Text(value);
    }

    /**
     * Wraps a boolean.
     *
     * @param value boolean value
     * @return boolean value
     */
    static ComputedValue of(boolean value) {
        return new Bool(value);
    }
}
