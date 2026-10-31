package com.portal.component;

import com.portal.exception.PortalException;
import java.util.Map;

/** Identity at the assignment boundary; the caller retains the original Lookup value. */
final class AssignmentPersonIdentity {
    private AssignmentPersonIdentity() { }

    static String optional(Object value) {
        if (value == null || "".equals(value)) return "";
        Object identity = value instanceof Map<?, ?> row ? row.get("id") : value;
        if (identity instanceof String text && !text.isBlank()) return text.trim();
        throw new PortalException("409", "Assignment person must contain one valid user identity");
    }
}
