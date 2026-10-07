package com.platform.common.functionunit;

import java.util.Map;

/**
 * The single definition of "the form a process starts with": the {@code PROCESS} form in the
 * {@code TASK} scene.
 *
 * <p>Developer Workstation's {@code createBothScenes} adds a second {@code PROCESS} form with
 * {@code scene = REQUEST} — the initiator's read-only My Request design. It never drives start
 * rendering, start writes, or the start form's PRIMARY-table configuration.
 */
public final class ProcessStartForm {

    /** {@code dw_form_definitions} predicate; the query must alias that table as {@code fd}. */
    public static final String SQL_PREDICATE = "fd.form_type = 'PROCESS' AND fd.scene = 'TASK'";

    private ProcessStartForm() {
    }

    /** Matches a catalog FORM content ({@code sys_function_unit_contents.content_data}). */
    public static boolean matches(Map<String, ?> catalogForm) {
        if (!"PROCESS".equals(catalogForm.get("formType"))) {
            return false;
        }
        Object scene = catalogForm.get("scene");
        // Packages built before the scene axis (init-script 68) carry no scene; the column default is TASK.
        return scene == null || "TASK".equals(scene);
    }
}
