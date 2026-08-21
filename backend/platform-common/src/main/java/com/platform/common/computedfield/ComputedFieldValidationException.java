package com.platform.common.computedfield;

/**
 * Raised when a computed field design is rejected.
 *
 * <p>Deliberately not one of the {@code platform-common} business exceptions: this package is
 * shared by services that each surface errors through their own exception type. Callers catch
 * this and re-throw their own, keeping the {@link #getCode() code} so the message the designer
 * sees is identical regardless of which service validated the table.
 */
public class ComputedFieldValidationException extends RuntimeException {

    private final String code;

    /**
     * Creates a validation failure.
     *
     * @param code    stable machine-readable reason, e.g. {@code COMPUTED_FIELD_CANNOT_BE_PK}
     * @param message human-readable explanation naming the offending field
     */
    public ComputedFieldValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * The machine-readable reason for the rejection.
     *
     * @return the error code
     */
    public String getCode() {
        return code;
    }
}
