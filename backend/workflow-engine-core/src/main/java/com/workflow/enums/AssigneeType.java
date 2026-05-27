package com.workflow.enums;

/**
 * Task assignee type (convergence model).
 * <p>For product semantics, see {@code .kiro/docs/assignee-type-convergence.md}.</p>
 */
public enum AssigneeType {

    PROCESS_INITIATOR("PROCESS_INITIATOR", "Process Initiator"),

    ENTITY_MANAGER("ENTITY_MANAGER", "Entity Manager"),

    FUNCTIONAL_MANAGER("FUNCTIONAL_MANAGER", "Functional Manager"),

    /**
     * Union of members of a role on the anchor user's BU and all parent BUs;
     * 0/1/multi-person rule is written to {@link com.workflow.service.TaskAssigneeResolver.ResolveResult} by the resolver.
     */
    HIERARCHY_ROLE("HIERARCHY_ROLE", "Hierarchy Role"),

    BU_ROLE("BU_ROLE", "Specified BU Role"),

    /**
     * Written by process variables before task creation; resolved in listener,
     * not entering the main switch of {@link com.workflow.service.TaskAssigneeResolver}.
     */
    MANUAL_ASSIGN("MANUAL_ASSIGN", "Manual Assignment"),

    ASSIGNEE_FROM_VARIABLE("ASSIGNEE_FROM_VARIABLE", "Variable Resolution"),

    /**
     * Multi-instance element variable; handled only by {@link com.workflow.listener.TaskAssignmentListener}.
     */
    ELEMENT_VARIABLE("ELEMENT_VARIABLE", "Multi-instance Element Variable");

    private final String code;
    private final String name;

    AssigneeType(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    /**
     * Whether it needs separate handling in the listener
     * (bypassing the unified resolve method body).
     */
    public boolean isListenerOnly() {
        return this == MANUAL_ASSIGN || this == ASSIGNEE_FROM_VARIABLE || this == ELEMENT_VARIABLE;
    }

    public boolean requiresRoleId() {
        return this == HIERARCHY_ROLE || this == BU_ROLE;
    }

    public boolean requiresBusinessUnitId() {
        return this == BU_ROLE;
    }

    /**
     * When resolving manager or HIERARCHY, requires an anchor user ID
     * (initiator or last task completer).
     */
    public boolean requiresAnchorUserId() {
        return this == ENTITY_MANAGER || this == FUNCTIONAL_MANAGER || this == HIERARCHY_ROLE;
    }

    public boolean isDirectAssignment() {
        return this == PROCESS_INITIATOR || this == ENTITY_MANAGER || this == FUNCTIONAL_MANAGER;
    }

    public static AssigneeType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String c = code.trim();
        for (AssigneeType t : values()) {
            if (t.code.equalsIgnoreCase(c)) {
                return t;
            }
        }
        return fromLegacyCode(c);
    }

    private static AssigneeType fromLegacyCode(String code) {
        String u = code.toUpperCase();
        return switch (u) {
            case "INITIATOR" -> PROCESS_INITIATOR;
            case "FUNCTION_MANAGER", "FUNCTIONMANAGER" -> FUNCTIONAL_MANAGER;
            case "ENTITY_MANAGER", "ENTITYMANAGER", "MANAGER" -> ENTITY_MANAGER;
            case "FIXED_BU_ROLE", "FIXEDDEPT", "FIXED_DEPT" -> BU_ROLE;
            case "INITIATOR_BU_ROLE", "INITIATOR_PARENT_BU_ROLE",
                 "CURRENT_BU_ROLE", "CURRENT_PARENT_BU_ROLE",
                 "DEPTOTHERS", "DEPT_OTHERS", "PARENTDEPT", "PARENT_DEPT" -> HIERARCHY_ROLE;
            case "BU_UNBOUNDED_ROLE", "VIRTUAL_GROUP", "VIRTUALGROUP" -> null;
            default -> null;
        };
    }
}
