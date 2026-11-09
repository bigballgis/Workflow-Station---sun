package com.developer.util;

import com.developer.exception.DeveloperBusinessException;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A View is identified across published versions by its name: the User Portal and BI Data View
 * Assignments find it as "function unit code + View name". Names must therefore be unique within a
 * function unit. They are compared trimmed and case-insensitively, which is stricter than the Admin
 * import check, so a package accepted here is accepted there too.
 */
public final class MainTableViewNameValidator {

    public static final String DUPLICATE_CODE = "BIZ_VIEW_NAME_DUPLICATE";

    private MainTableViewNameValidator() {
    }

    /** Rejects the View names of one function unit when any name occurs twice. */
    public static void assertUnique(Collection<String> viewNames) {
        Map<String, String> seen = new HashMap<>();
        for (String name : viewNames) {
            String key = key(name);
            if (key == null) {
                continue;
            }
            String earlier = seen.putIfAbsent(key, name);
            if (earlier != null) {
                throw duplicate(earlier, name);
            }
        }
    }

    /** Rejects {@code viewName} when one of the function unit's other Views already uses it. */
    public static void assertAvailable(String viewName, Collection<String> otherViewNames) {
        String key = key(viewName);
        if (key == null) {
            return;
        }
        for (String other : otherViewNames) {
            if (key.equals(key(other))) {
                throw duplicate(other, viewName);
            }
        }
    }

    private static String key(String name) {
        return name == null || name.isBlank() ? null : name.trim().toLowerCase(Locale.ROOT);
    }

    private static DeveloperBusinessException duplicate(String first, String second) {
        String a = first.trim();
        String b = second.trim();
        return new DeveloperBusinessException(DUPLICATE_CODE,
                "View name '" + a + "'" + (a.equals(b) ? "" : " (also as '" + b + "')")
                        + " is used by more than one View in this function unit. View names must be unique,"
                        + " ignoring case and surrounding spaces, because the User Portal and BI dashboard"
                        + " assignments identify a View by its name. Rename one of them.");
    }
}
