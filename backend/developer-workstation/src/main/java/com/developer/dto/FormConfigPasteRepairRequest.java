package com.developer.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request body for repairing a pasted form {@code configJson} against the target form's bindings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormConfigPasteRepairRequest {

    @NotNull(message = "{validation.form_config_required}")
    private Map<String, Object> configJson;

    /**
     * When true, persist the repaired config onto the form. Default false (preview / live apply only).
     */
    private boolean apply;

    /**
     * When true <em>and</em> {@link #apply} is true, create missing MAIN/SUB tables
     * (and RELATED sys_users bindings) before remapping. Ignored when {@code apply} is false
     * so Paste/Repair preview never persists new tables.
     */
    private boolean createMissingTables;
}
