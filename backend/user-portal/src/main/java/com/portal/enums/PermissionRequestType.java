package com.portal.enums;

/**
 * Permission request type enum
 */
public enum PermissionRequestType {
    /** Legacy role assignment request: can no longer be created; a pending one has no approver and only its applicant can withdraw it */
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
    /** @deprecated Legacy type, kept so old requests stay readable */
    @Deprecated
    FUNCTION,
    /** @deprecated Legacy type, kept so old requests stay readable */
    @Deprecated
    DATA,
    /** @deprecated Legacy type, kept so old requests stay readable; nothing creates it and no approver can act on it */
    @Deprecated
    TEMPORARY
}
