package com.admin.controlplane;

/**
 * Canonical release lifecycle event types per release-control-plane-blueprint.md.
 * Both MFE and Gateway domains emit these shared event names for cross-domain tooling.
 */
public final class ControlPlaneEventType {

    private ControlPlaneEventType() {}

    // ── Release Lifecycle ──────────────────────────────────────────────
    public static final String RELEASE_CREATED           = "release.created";
    public static final String RELEASE_VALIDATED         = "release.validated";

    // ── Approval ───────────────────────────────────────────────────────
    public static final String RELEASE_APPROVAL_REQUESTED = "release.approval.requested";
    public static final String RELEASE_APPROVAL_DECIDED   = "release.approval.decided";

    // ── Publish ────────────────────────────────────────────────────────
    public static final String RELEASE_PUBLISH_STARTED    = "release.publish.started";
    public static final String RELEASE_PUBLISH_SUCCEEDED  = "release.publish.succeeded";
    public static final String RELEASE_PUBLISH_FAILED     = "release.publish.failed";

    // ── Rollback ───────────────────────────────────────────────────────
    public static final String RELEASE_ROLLBACK_STARTED   = "release.rollback.started";
    public static final String RELEASE_ROLLBACK_SUCCEEDED = "release.rollback.succeeded";
    public static final String RELEASE_ROLLBACK_FAILED    = "release.rollback.failed";

    // ── Policy & Drift ────────────────────────────────────────────────
    public static final String POLICY_EVALUATED           = "policy.evaluated";
    public static final String DRIFT_DETECTED             = "drift.detected";
}
