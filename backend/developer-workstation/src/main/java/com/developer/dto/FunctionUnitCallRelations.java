package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Which Function Units this one calls, and which ones call it.
 *
 * <p>Derived from the design-time BPMN of every unit rather than stored: a call is
 * declared by a {@code callActivity}'s {@code calledElement}, so the BPMN already is
 * the record. Keeping a second copy in a table would only create something to drift.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionUnitCallRelations {

    /** The unit the relations were requested for. */
    private Long functionUnitId;
    private String functionUnitCode;
    private String functionUnitName;

    /** Units this one calls, in diagram order. */
    private List<CalledUnit> calls;

    /** Units whose process contains a call activity targeting this one. */
    private List<CallerUnit> calledBy;

    /** One outgoing call. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CalledUnit {
        /** BPMN element id of the call step, so the UI can tie it to the diagram. */
        private String callActivityId;
        /** Label the designer gave that step. */
        private String callActivityName;
        /** Target code, as written in {@code calledElement}. */
        private String code;
        /** Resolved name; null when the code matches no existing unit. */
        private String name;
        /** Null when the target does not exist — a dangling reference worth showing. */
        private Long id;
        /** Whether the target's Startup Mode actually permits being called. */
        private boolean callable;
        /** True when the call step repeats per row of a collection. */
        private boolean multiInstance;

        /**
         * The published version this call is pinned to, e.g. {@code 1.2.0}.
         *
         * <p>Null means "whatever is deployed now". Pinning matters because the callee
         * is owned by someone else: without it, their next publish silently changes what
         * this process does.
         */
        private String pinnedVersion;

        /** False when {@link #pinnedVersion} names a version the target no longer has. */
        private boolean pinnedVersionAvailable;

        /** The target's current version, shown when the call is not pinned. */
        private String currentVersion;

        /**
         * True when this call is pinned to an older version than the target now has.
         *
         * <p>Pinning deliberately stops a call from following the callee's new
         * publishes — which also means nobody is told one happened. Without this, a
         * caller can sit on a superseded version indefinitely without anyone noticing;
         * the pin is doing its job, but the decision to stay on it was never made.
         *
         * <p>Derived here rather than by each screen comparing the two version
         * strings, so "what counts as out of date" has one definition.
         */
        private boolean newerVersionAvailable;
    }

    /** One incoming call. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallerUnit {
        private Long id;
        private String code;
        private String name;
        /** BPMN element id of the call step in the caller's diagram. */
        private String callActivityId;
        private String callActivityName;
        /** Version of THIS unit that the caller pinned to, when it pinned one. */
        private String pinnedVersion;
    }
}
