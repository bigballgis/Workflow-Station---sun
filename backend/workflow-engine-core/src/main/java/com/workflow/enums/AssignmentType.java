package com.workflow.enums;

/**
 * Task assignment type enum
 * Supports multi-dimensional task assignment: user, virtual group
 * 
 * Note: This enum is the legacy assignment type. The new task assignment mechanism
 * uses the AssigneeType enum, which defines 9 standard task assignment methods
 * including business-unit-role-based assignment.
 * 
 * @see AssigneeType
 */
public enum AssignmentType {
    
    /**
     * Directly assign to user
     * Task is assigned directly to a specific user who can see it in their to-do list
     */
    USER("USER", "User Assignment"),
    
    /**
     * Assign to virtual group
     * Task is assigned to a virtual group; all group members can see and claim it
     */
    VIRTUAL_GROUP("VIRTUAL_GROUP", "Virtual Group Assignment"),

    /**
     * Candidate user list (Flowable candidate users)
     * Only users in the list can claim; assignmentTarget can be comma-separated user IDs
     * (for permission validation)
     */
    CANDIDATE_USERS("CANDIDATE_USERS", "Candidate Users (claim pool)");
    
    private final String code;
    private final String description;
    
    AssignmentType(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Get assignment type by code
     */
    public static AssignmentType fromCode(String code) {
        for (AssignmentType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown assignment type: " + code);
    }
}