package com.portal.enums;

/**
 * Permission request type enum
 */
public enum PermissionRequestType {
    /** Role assignment request - apply for a business role in an organization unit */
    ROLE_ASSIGNMENT,
    /** Virtual group join request - apply to join a virtual group */
    VIRTUAL_GROUP_JOIN,
    /** Business unit join request - apply to join a business unit */
    BUSINESS_UNIT_JOIN,

    /** Apply to remove a business role under a specified business unit (takes effect after approver's approval) */
    BUSINESS_UNIT_ROLE_REMOVAL,

    /** Apply to exit a business unit membership (removes member and all UBR under this BU after approval) */
    BUSINESS_UNIT_EXIT,
    
    // ========== Deprecated types below ==========
    /** @deprecated Use ROLE_ASSIGNMENT instead */
    @Deprecated
    FUNCTION,
    /** @deprecated Use ROLE_ASSIGNMENT instead */
    @Deprecated
    DATA,
    /** @deprecated Use ROLE_ASSIGNMENT instead */
    @Deprecated
    TEMPORARY
}
