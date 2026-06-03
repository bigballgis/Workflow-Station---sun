package com.admin.controlplane;

/**
 * Control Plane generic error codes per release-control-plane-blueprint.md §7.
 * These are layered ABOVE domain-specific error codes (MFE_*, GATEWAY_*).
 * Cross-domain tooling can depend on CP_* categories without knowing domain internals.
 */
public final class ControlPlaneErrorCode {

    private ControlPlaneErrorCode() {}

    /** Operation cannot proceed because the domain object is in an incompatible state. */
    public static final String CP_INVALID_STATE    = "CP_INVALID_STATE";

    /** A required approval ticket is missing or not yet decided. */
    public static final String CP_APPROVAL_REQUIRED = "CP_APPROVAL_REQUIRED";

    /** A policy gate evaluated to BLOCK, preventing the operation. */
    public static final String CP_POLICY_BLOCKED   = "CP_POLICY_BLOCKED";

    /** The publish/rollback operation failed at the runtime adapter level. */
    public static final String CP_PUBLISH_FAILED   = "CP_PUBLISH_FAILED";

    /** The requested resource was not found. */
    public static final String CP_NOT_FOUND        = "CP_NOT_FOUND";

    /** A concurrent modification conflict was detected. */
    public static final String CP_CONFLICT           = "CP_CONFLICT";

    // ── Policy decision constants ─────────────────────────────────────
    public static final String POLICY_PASS  = "PASS";
    public static final String POLICY_WARN  = "WARN";
    public static final String POLICY_BLOCK = "BLOCK";
}
