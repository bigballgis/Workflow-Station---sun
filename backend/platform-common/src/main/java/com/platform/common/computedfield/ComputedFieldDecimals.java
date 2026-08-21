package com.platform.common.computedfield;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Fixed-point arithmetic for computed fields, kept digit-for-digit equal to the TypeScript engine.
 *
 * <p>The TS side models {@code (unscaled: bigint, scale: number)} precisely because that is what
 * BigDecimal is, so the operations map one to one:
 * <ul>
 *   <li>add / subtract take the larger scale (BigDecimal does this natively)</li>
 *   <li>multiply adds scales</li>
 *   <li>divide settles at {@link #DIVISION_SCALE} with HALF_UP</li>
 *   <li>ROUND is {@code setScale(n, HALF_UP)}, ROUNDDOWN is {@code DOWN}, ROUNDUP is {@code UP}</li>
 * </ul>
 *
 * <p>SQRT and POWER are the two places where naive implementations drift between languages, so
 * both are pinned: POWER accepts whole exponents only and expands to repeated multiplication;
 * SQRT goes through {@link BigInteger#sqrt()}, whose floor semantics the TS side reproduces with
 * an integer Newton iteration. Neither uses a transcendental routine.
 *
 * @see <a href="file:../../../../../../../../frontend/shared/src/computedField/decimal.ts">decimal.ts</a>
 */
public final class ComputedFieldDecimals {

    /**
     * Working scale for division, AVG and SQRT, which can be non-terminating.
     *
     * <p>FIXED CONTRACT: the TypeScript {@code DIVISION_SCALE} and {@code goldenVectors.json}
     * {@code divisionScale} must carry this same value. The target field's own scale is applied
     * on top of this when the value is written.
     */
    public static final int DIVISION_SCALE = 10;

    /**
     * Accepted numeric text. Intentionally identical to the TS {@code parseDecimal} regex, and
     * intentionally NOT accepting exponent notation, thousands separators or currency symbols:
     * anything the two languages might format differently stays out.
     */
    private static final Pattern DECIMAL_TEXT = Pattern.compile("^[+-]?(\\d+(\\.\\d*)?|\\.\\d+)$");

    private ComputedFieldDecimals() {
    }

    /**
     * Parses strictly decimal text into a BigDecimal, preserving the authored scale.
     *
     * @param text candidate text, may be null
     * @return the parsed value, or null when the text is not wholly a decimal number
     */
    public static BigDecimal parse(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (!DECIMAL_TEXT.matcher(trimmed).matches()) {
            return null;
        }
        // "1." is accepted by the shared regex but rejected by BigDecimal; drop the bare point so
        // both engines land on scale 0 for it.
        String normalized = trimmed.endsWith(".") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        return new BigDecimal(normalized);
    }

    /**
     * Divides at {@link #DIVISION_SCALE} using HALF_UP.
     *
     * @param dividend numerator
     * @param divisor  denominator
     * @return the quotient, or null when the divisor is zero
     */
    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        if (divisor.signum() == 0) {
            return null;
        }
        return dividend.divide(divisor, DIVISION_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Remainder carrying the dividend's sign, matching the TS MOD implementation.
     *
     * @param dividend numerator
     * @param divisor  denominator
     * @return the remainder, or null when the divisor is zero
     */
    public static BigDecimal remainder(BigDecimal dividend, BigDecimal divisor) {
        if (divisor.signum() == 0) {
            return null;
        }
        return dividend.remainder(divisor);
    }

    /**
     * Raises a base to a whole-number exponent by repeated multiplication.
     *
     * <p>Fractional exponents are refused rather than approximated: a transcendental pow would
     * differ in its final digits between the JVM and V8, and a computed field that disagrees
     * between preview and server is worse than one that refuses to compile.
     *
     * @param base     the base
     * @param exponent the exponent, must be a whole number
     * @return the power, or null when the exponent has a fractional part
     */
    public static BigDecimal power(BigDecimal base, BigDecimal exponent) {
        BigDecimal truncated = exponent.setScale(0, RoundingMode.DOWN);
        if (truncated.compareTo(exponent) != 0) {
            return null;
        }
        long n = truncated.longValueExact();
        boolean inverse = n < 0;
        long magnitude = Math.abs(n);
        BigDecimal result = BigDecimal.ONE;
        for (long i = 0; i < magnitude; i++) {
            result = result.multiply(base);
        }
        if (!inverse) {
            return result;
        }
        return divide(BigDecimal.ONE, result);
    }

    /**
     * Square root truncated at {@link #DIVISION_SCALE}.
     *
     * <p>Computed as {@code floor(sqrt(unscaled * 10^(2S - scale)))}, which is exactly what the TS
     * integer Newton iteration produces. Deliberately NOT {@code BigDecimal.sqrt(MathContext)},
     * whose rounding would not match.
     *
     * @param value the radicand
     * @return the root, or null when the value is negative
     */
    public static BigDecimal sqrt(BigDecimal value) {
        if (value.signum() < 0) {
            return null;
        }
        if (value.signum() == 0) {
            return BigDecimal.ZERO.setScale(DIVISION_SCALE, RoundingMode.UNNECESSARY);
        }
        int shift = 2 * DIVISION_SCALE - value.scale();
        BigInteger unscaled = value.unscaledValue();
        BigInteger scaled = shift >= 0
                ? unscaled.multiply(BigInteger.TEN.pow(shift))
                : unscaled.divide(BigInteger.TEN.pow(-shift));
        return new BigDecimal(scaled.sqrt(), DIVISION_SCALE);
    }

    /**
     * HALF_UP rounding to the given number of decimal places (ROUND).
     *
     * @param value  value to round
     * @param digits decimal places, negatives are clamped to zero
     * @return the rounded value
     */
    public static BigDecimal round(BigDecimal value, int digits) {
        return value.setScale(Math.max(digits, 0), RoundingMode.HALF_UP);
    }

    /**
     * Truncation toward zero (ROUNDDOWN, TRUNC, INT).
     *
     * @param value  value to truncate
     * @param digits decimal places, negatives are clamped to zero
     * @return the truncated value
     */
    public static BigDecimal truncate(BigDecimal value, int digits) {
        return value.setScale(Math.max(digits, 0), RoundingMode.DOWN);
    }

    /**
     * Rounding away from zero (ROUNDUP).
     *
     * @param value  value to round
     * @param digits decimal places, negatives are clamped to zero
     * @return the rounded value
     */
    public static BigDecimal roundAwayFromZero(BigDecimal value, int digits) {
        return value.setScale(Math.max(digits, 0), RoundingMode.UP);
    }

    /**
     * Canonical text form, matching the TS {@code toDecimalString}.
     *
     * <p>{@code toPlainString} rather than {@code toString} so a value like 1E+2 never appears —
     * exponent notation would not round-trip through the shared numeric regex.
     *
     * @param value value to render
     * @return plain decimal text
     */
    public static String toText(BigDecimal value) {
        return value.toPlainString();
    }
}
