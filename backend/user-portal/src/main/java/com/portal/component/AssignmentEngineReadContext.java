package com.portal.component;

import java.util.Map;

/** This marker comes only from Engine's verified response. Missing/string-valued markers do not grant legacy writes. */
final class AssignmentEngineReadContext {
    private AssignmentEngineReadContext() { }
    static boolean isLegacy(Map<?,?> response) {
        return response!=null && Boolean.FALSE.equals(response.get("assignmentTracked"));
    }
}
