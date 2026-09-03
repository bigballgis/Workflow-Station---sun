package com.portal.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Values for a SUB Main Table View detail row beyond the list's visible columns.
 *
 * <p>List projection copies only the view's visible fields. The DETAIL form (e.g. ATM Transaction
 * form 50657) has more widgets than that list, so stored members such as {@code merchant_credit}
 * never reached the page. Overlay fills those gaps; projected view columns keep their list
 * resolution (lookup / {@code fk_display}).
 */
public final class PortalMainTableViewDetailValues {

    private PortalMainTableViewDetailValues() {
    }

    /**
     * Copy stored members that the list projection omitted. Existing projected keys win.
     * Underscore keys are skipped — {@code __subTables__} is attached after
     * {@code stripInternalKeys}, not here.
     */
    public static Map<String, Object> overlayStoredMembers(
            Map<String, Object> projected, Map<String, Object> stored) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (projected != null) {
            out.putAll(projected);
        }
        if (stored == null || stored.isEmpty()) {
            return out;
        }
        for (Map.Entry<String, Object> e : stored.entrySet()) {
            String key = e.getKey();
            if (key == null || key.isBlank() || key.startsWith("_") || out.containsKey(key)) {
                continue;
            }
            out.put(key, e.getValue());
        }
        return out;
    }
}
