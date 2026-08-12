package com.platform.common.computedfield;

/**
 * A computed-field failure: machine-readable code plus developer-facing detail.
 *
 * <p>The {@code code} drives the i18n message shown to end users; {@code message} carries the
 * specifics (which field, which function, which type) and is safe to log. It must never contain
 * row data values beyond what the author already put in the formula.
 *
 * @param code   why it failed
 * @param message human-readable detail, English, for logs and designer tooltips
 */
public record ComputedFieldError(ComputedFieldErrorCode code, String message) {

    /**
     * Builds an error.
     *
     * @param code    failure reason
     * @param message detail text
     * @return the error
     */
    public static ComputedFieldError of(ComputedFieldErrorCode code, String message) {
        return new ComputedFieldError(code, message);
    }

    @Override
    public String toString() {
        return code + ": " + message;
    }
}
