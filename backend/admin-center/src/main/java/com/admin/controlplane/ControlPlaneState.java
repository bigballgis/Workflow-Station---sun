package com.admin.controlplane;

/**
 * Canonical release lifecycle states per release-control-plane-blueprint.md §5.
 *
 * <pre>
 * DRAFT → VALIDATING → APPROVAL_PENDING → APPROVED → PUBLISHING → PUBLISHED
 * PUBLISHED → ROLLBACK_PENDING → ROLLED_BACK
 * PUBLISHING / ROLLBACK_PENDING → FAILED
 * </pre>
 */
public final class ControlPlaneState {

    private ControlPlaneState() {}

    public static final String DRAFT             = "DRAFT";
    public static final String VALIDATING        = "VALIDATING";
    public static final String APPROVAL_PENDING  = "APPROVAL_PENDING";
    public static final String APPROVED          = "APPROVED";
    public static final String PUBLISHING        = "PUBLISHING";
    public static final String PUBLISHED         = "PUBLISHED";
    public static final String ROLLBACK_PENDING  = "ROLLBACK_PENDING";
    public static final String ROLLED_BACK       = "ROLLED_BACK";
    public static final String FAILED            = "FAILED";

    /**
     * Map a domain-specific state (e.g. Gateway's TESTING → VALIDATING) to canonical.
     * Returns null if no mapping exists.
     */
    public static String fromDomain(String domainState) {
        if (domainState == null) return null;
        return switch (domainState.toUpperCase()) {
            case "DRAFT"            -> DRAFT;
            case "TESTING"          -> VALIDATING;  // Gateway: TESTING ≅ VALIDATING
            case "VALIDATING"       -> VALIDATING;
            case "APPROVAL_PENDING" -> APPROVAL_PENDING;
            case "APPROVED"         -> APPROVED;
            case "PUBLISHING"       -> PUBLISHING;
            case "PUBLISHED"        -> PUBLISHED;
            case "ROLLBACK_PENDING" -> ROLLBACK_PENDING;
            case "ROLLED_BACK"      -> ROLLED_BACK;
            case "PROMOTED"         -> PUBLISHED;   // Gateway: PROMOTED is terminal PUBLISHED variant
            case "FAILED"           -> FAILED;
            default                 -> null;
        };
    }
}
