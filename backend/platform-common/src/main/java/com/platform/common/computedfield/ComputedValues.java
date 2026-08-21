package com.platform.common.computedfield;

import java.math.BigDecimal;

/**
 * Coercion and comparison rules — the semantic contract with {@code coerce.ts}.
 *
 * <p>Two rules matter most, and both are pinned by golden vectors:
 * <ol>
 *   <li><b>Non-numeric text never becomes a number.</b> {@code amount + "abc"} is a TYPE_MISMATCH,
 *       not zero. The superseded {@code evaluateFormula} coerced junk to 0 and returned it as a
 *       result, which is a silently wrong number rather than a visible failure.</li>
 *   <li><b>Blank behaves as 0 in arithmetic</b> and as "" in text context. That is Excel / Power Fx
 *       behaviour and a deliberate semantic definition, not an error swallow: ISBLANK still
 *       distinguishes empty from zero, so authors can always branch on emptiness.</li>
 * </ol>
 */
public final class ComputedValues {

    private ComputedValues() {
    }

    /**
     * Normalizes a raw JSON row value into a {@link ComputedValue}.
     *
     * <p>A string that is <i>entirely</i> a decimal number becomes a number. This is not a
     * loosening of rule 1: JSON row storage loses declared types, so a DECIMAL field edited in a
     * text input arrives as {@code "1999.99"}. Treating that as text would make every numeric
     * field unusable in a formula. Text that is not wholly numeric ({@code "abc"}, {@code "12abc"},
     * {@code "1,999.99"}) stays text and still fails loudly in arithmetic.
     *
     * @param raw value straight out of the process-variable map, may be null
     * @return the normalized value, blank when absent or whitespace-only
     */
    public static ComputedValue fromRowValue(Object raw) {
        if (raw == null) {
            return ComputedValue.BLANK;
        }
        if (raw instanceof Boolean flag) {
            return ComputedValue.of(flag.booleanValue());
        }
        if (raw instanceof BigDecimal decimal) {
            return ComputedValue.of(decimal);
        }
        if (raw instanceof Integer || raw instanceof Long || raw instanceof Short
                || raw instanceof Byte) {
            return ComputedValue.of(BigDecimal.valueOf(((java.lang.Number) raw).longValue()));
        }
        if (raw instanceof java.lang.Number number) {
            // Double/Float: go through the shared numeric text form so both engines see the same
            // digits. BigDecimal.valueOf is the fallback for the rare value whose Java rendering
            // uses exponent notation (e.g. 1.0E-7), which the shared regex does not accept.
            BigDecimal viaText = ComputedFieldDecimals.parse(String.valueOf(number));
            return ComputedValue.of(viaText != null ? viaText : BigDecimal.valueOf(number.doubleValue()));
        }
        if (raw instanceof String text) {
            if (text.trim().isEmpty()) {
                return ComputedValue.BLANK;
            }
            BigDecimal parsed = ComputedFieldDecimals.parse(text);
            return parsed != null ? ComputedValue.of(parsed) : ComputedValue.of(text);
        }
        return ComputedValue.BLANK;
    }

    /**
     * Numeric view of a value, with blank reading as zero.
     *
     * @param value   value to interpret
     * @param context operation name used in the error message
     * @return the number, or a failure outcome describing the mismatch
     */
    public static Object toNumber(ComputedValue value, String context) {
        if (value instanceof ComputedValue.Number number) {
            return number.value();
        }
        if (value.isBlank()) {
            return BigDecimal.ZERO;
        }
        if (value instanceof ComputedValue.Bool) {
            return EvalOutcome.error(ComputedFieldErrorCode.TYPE_MISMATCH,
                    context + " expects a number but received a boolean");
        }
        String text = ((ComputedValue.Text) value).value();
        return EvalOutcome.error(ComputedFieldErrorCode.TYPE_MISMATCH,
                context + " expects a number but received the text \"" + truncate(text)
                        + "\". Wrap it in VALUE() to parse it.");
    }

    /**
     * Canonical text form of any value; blank renders as the empty string.
     *
     * @param value value to render
     * @return text form
     */
    public static String toText(ComputedValue value) {
        if (value instanceof ComputedValue.Text text) {
            return text.value();
        }
        if (value instanceof ComputedValue.Number number) {
            return ComputedFieldDecimals.toText(number.value());
        }
        if (value instanceof ComputedValue.Bool flag) {
            return flag.value() ? "true" : "false";
        }
        return "";
    }

    /**
     * Boolean view of a value, with blank reading as false.
     *
     * <p>Numbers and text are refused rather than given truthiness: "is 0 false, is \"\" false"
     * has no answer that stays obvious to a formula author.
     *
     * @param value   value to interpret
     * @param context operation name used in the error message
     * @return Boolean, or a failure outcome describing the mismatch
     */
    public static Object toBoolean(ComputedValue value, String context) {
        if (value instanceof ComputedValue.Bool flag) {
            return Boolean.valueOf(flag.value());
        }
        if (value.isBlank()) {
            return Boolean.FALSE;
        }
        String received = value instanceof ComputedValue.Number ? "a number" : "text";
        return EvalOutcome.error(ComputedFieldErrorCode.TYPE_MISMATCH,
                context + " expects a condition (true/false) but received " + received);
    }

    /**
     * Equality. Blank equals only blank.
     *
     * <p>Power Fx treats {@code Blank() = 0} as true, which makes "is it empty or is it zero"
     * untestable; requiring ISBLANK for that question is more predictable.
     *
     * @param left  left operand
     * @param right right operand
     * @return Boolean, or a failure outcome when the kinds cannot be compared
     */
    public static Object valuesEqual(ComputedValue left, ComputedValue right) {
        if (left.isBlank() || right.isBlank()) {
            return Boolean.valueOf(left.isBlank() && right.isBlank());
        }
        if (left instanceof ComputedValue.Number a && right instanceof ComputedValue.Number b) {
            return Boolean.valueOf(a.value().compareTo(b.value()) == 0);
        }
        if (left instanceof ComputedValue.Text a && right instanceof ComputedValue.Text b) {
            return Boolean.valueOf(a.value().equals(b.value()));
        }
        if (left instanceof ComputedValue.Bool a && right instanceof ComputedValue.Bool b) {
            return Boolean.valueOf(a.value() == b.value());
        }
        return EvalOutcome.error(ComputedFieldErrorCode.TYPE_MISMATCH,
                "Cannot compare " + left.kind() + " with " + right.kind());
    }

    /**
     * Ordering for {@code < <= > >=}. Numbers compare numerically, text lexicographically by UTF-16
     * code unit (which is what both {@code String.compareTo} and the JS relational operators do).
     * Mixed kinds are an error rather than a guess.
     *
     * @param left  left operand
     * @param right right operand
     * @return Integer sign of the comparison, or a failure outcome
     */
    public static Object compareValues(ComputedValue left, ComputedValue right) {
        boolean eitherText = left instanceof ComputedValue.Text || right instanceof ComputedValue.Text;
        if (eitherText) {
            if (left instanceof ComputedValue.Text a && right instanceof ComputedValue.Text b) {
                return Integer.valueOf(Integer.signum(a.value().compareTo(b.value())));
            }
            if (left.isBlank() || right.isBlank()) {
                return Integer.valueOf(Integer.signum(toText(left).compareTo(toText(right))));
            }
            return EvalOutcome.error(ComputedFieldErrorCode.TYPE_MISMATCH,
                    "Cannot order " + left.kind() + " against " + right.kind());
        }
        if (left instanceof ComputedValue.Bool || right instanceof ComputedValue.Bool) {
            return EvalOutcome.error(ComputedFieldErrorCode.TYPE_MISMATCH,
                    "Booleans support = and <> but not ordering comparisons");
        }
        Object a = toNumber(left, "Comparison");
        if (a instanceof EvalOutcome failure) {
            return failure;
        }
        Object b = toNumber(right, "Comparison");
        if (b instanceof EvalOutcome failure) {
            return failure;
        }
        return Integer.valueOf(((BigDecimal) a).compareTo((BigDecimal) b));
    }

    private static String truncate(String text) {
        return text.length() <= 24 ? text : text.substring(0, 24) + "…";
    }
}
