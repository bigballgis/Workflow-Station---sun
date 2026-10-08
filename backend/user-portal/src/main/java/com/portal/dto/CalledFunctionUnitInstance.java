package com.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * One Function Unit sub-process started by a request's call activity, as shown read-only on the
 * calling request's detail page.
 *
 * <p>The data is read from the child instance live rather than copied into the parent: the two are
 * separate process instances with separate variable blobs, and a copy would go stale the moment the
 * child moved on — the sort of divergence that has repeatedly bitten sub-table snapshots here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalledFunctionUnitInstance {

    /** The child process instance. */
    private String processInstanceId;

    /**
     * BPMN element id of the call activity that started it. Several children share this id when
     * that call activity is multi-instance.
     */
    private String callActivityId;

    /** Display name of the call activity, so the section can be labelled as the designer named it. */
    private String callActivityName;

    /** Code of the called Function Unit — stable across environments. */
    private String functionUnitCode;

    /** Human-readable name of the called Function Unit. */
    private String functionUnitName;

    /** Name of the called unit's form used to render {@link #formData}; null when unconfigured. */
    private String childFormName;

    /** RUNNING / COMPLETED / REJECTED / WITHDRAWN. */
    private String status;

    /** Step the child is currently on; null once it has ended. */
    private String currentNode;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /**
     * The child's business data, read-only.
     *
     * <p>Shaped exactly like a request's own form data — scalars at the top level, sub-table rows
     * under {@code __subTables__} — so the portal renders it with the ordinary form renderer
     * instead of a second, divergent display path.
     */
    private Map<String, Object> formData;

    /**
     * The called unit's {@link #childFormName} form as deployed — its config ({@code data}) and its
     * {@code tableBindings} — so the portal renders the child with the layout designed in that
     * unit's Form Design. Delivered here rather than fetched by the page: viewing is decided by the
     * calling request, and a call-only unit has no start roles that would let its content
     * endpoint serve the viewer. Null when no form is configured or it cannot be found.
     */
    private Map<String, Object> childForm;

    /**
     * The called unit's main-table fields ({@code fieldName}, {@code displayName}), in design order —
     * what the portal lists when no form is configured, so it shows business fields under their
     * designed names instead of every engine variable under its technical name.
     */
    private List<Map<String, Object>> childFields;
}
