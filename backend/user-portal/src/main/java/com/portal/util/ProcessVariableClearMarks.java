package com.portal.util;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tracks which process variables a portal form submit deliberately CLEARED.
 *
 * <p>In {@code up_process_instance.variables} a cleared field and a never-filled field are the
 * same shape — the key is present with a {@code null} value — but the read path must treat them
 * oppositely:
 *
 * <ul>
 *   <li><b>never filled</b>: a service task (e.g. an Activepieces node writing back
 *       {@code output_text}) may have produced a value in the Flowable engine that the portal
 *       store never received, so
 *       {@code ProcessApplicationQueryComponent#hydrateEngineScalarsIntoStore} should gap-fill it.</li>
 *   <li><b>user cleared</b>: the user emptied the field on purpose. The engine keeps its own copy
 *       of that variable which form submissions never update, so gap-filling would push the stale
 *       value straight back — the clear appeared to save (200, correct {@code null} in the DB) and
 *       then silently reverted on the next page load.</li>
 * </ul>
 *
 * <p>The marker rides along in the variables blob under {@link #CLEARED_FIELDS_KEY}, matching the
 * existing {@code __}-prefixed platform-metadata convention ({@code __subTables__},
 * {@code __request_id}, …). It is additive: a process that never had a field cleared carries no
 * marker and behaves exactly as before.
 */
public final class ProcessVariableClearMarks {

    /** Platform metadata key holding the names of fields cleared by a form submit. */
    public static final String CLEARED_FIELDS_KEY = "__clearedFields";

    private ProcessVariableClearMarks() {
    }

    /**
     * Updates the cleared-field marker to reflect one submission.
     *
     * <p>A key submitted with a {@code null} value is added; a key submitted with any real value is
     * removed again, so re-filling a previously cleared field restores normal gap-fill behavior.
     * Keys absent from {@code submitted} keep whatever mark they already had — this submit says
     * nothing about them.
     *
     * @param variables the merged variable map about to be persisted (mutated in place)
     * @param submitted the fields this submit actually carried (already permission-filtered)
     */
    public static void recordClearedFields(Map<String, Object> variables, Map<String, Object> submitted) {
        if (variables == null || submitted == null || submitted.isEmpty()) {
            return;
        }
        Set<String> cleared = new LinkedHashSet<>(readClearedFields(variables));
        boolean changed = false;
        for (Map.Entry<String, Object> entry : submitted.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.startsWith("__") || key.startsWith("_")) {
                continue;
            }
            if (entry.getValue() == null) {
                changed |= cleared.add(key);
            } else {
                changed |= cleared.remove(key);
            }
        }
        if (!changed) {
            return;
        }
        if (cleared.isEmpty()) {
            variables.remove(CLEARED_FIELDS_KEY);
        } else {
            // Stored as a List so the JSONB round-trip stays a plain array.
            variables.put(CLEARED_FIELDS_KEY, new java.util.ArrayList<>(cleared));
        }
    }

    /**
     * Field names a submit previously cleared. Never {@code null}; tolerates a missing or
     * malformed marker (older rows, hand-edited JSON) by reporting nothing cleared.
     */
    public static Set<String> readClearedFields(Map<String, Object> variables) {
        if (variables == null) {
            return Collections.emptySet();
        }
        Object raw = variables.get(CLEARED_FIELDS_KEY);
        if (!(raw instanceof Collection<?> values)) {
            return Collections.emptySet();
        }
        Set<String> out = new LinkedHashSet<>();
        for (Object value : values) {
            if (value != null) {
                out.add(String.valueOf(value));
            }
        }
        return out;
    }
}
