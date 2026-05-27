package com.admin.enums;

/**
 * Lifecycle status of an imported FunctionUnit in Admin Center.
 */
public enum FunctionUnitStatus {
    /**
     * Draft — newly imported or restored from archive; not yet deployed.
     */
    DRAFT,
    
    /**
     * Validated — passed validation and may be deployed.
     */
    VALIDATED,
    
    /**
     * Deployed — deployed to at least one environment.
     */
    DEPLOYED,
    
    /**
     * Deprecated — should not be used for new work.
     */
    DEPRECATED,

    /**
     * Archived — removed from the main list but retained for restore.
     */
    ARCHIVED
}
