package com.workflow.enums;

/**
 * 任务处理人分配类型（收敛模型）。
 * <p>产品语义见 {@code .kiro/docs/assignee-type-convergence.md}。</p>
 */
public enum AssigneeType {

    PROCESS_INITIATOR("PROCESS_INITIATOR", "任务发起人"),

    ENTITY_MANAGER("ENTITY_MANAGER", "实体经理"),

    FUNCTIONAL_MANAGER("FUNCTIONAL_MANAGER", "职能经理"),

    /**
     * 锚点用户所在 BU 及全部父级 BU 上某角色的成员并集；0/1/多人规则由解析器写入 {@link com.workflow.service.TaskAssigneeResolver.ResolveResult}。
     */
    HIERARCHY_ROLE("HIERARCHY_ROLE", "层级角色"),

    BU_ROLE("BU_ROLE", "指定BU角色"),

    /**
     * 由流程变量在任务创建前写入；监听器内解析，不进入 {@link com.workflow.service.TaskAssigneeResolver} 主 switch。
     */
    MANUAL_ASSIGN("MANUAL_ASSIGN", "手动分配"),

    ASSIGNEE_FROM_VARIABLE("ASSIGNEE_FROM_VARIABLE", "变量解析"),

    /**
     * 多实例元素变量；仅 {@link com.workflow.listener.TaskAssignmentListener} 处理。
     */
    ELEMENT_VARIABLE("ELEMENT_VARIABLE", "多实例元素变量");

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
     * 是否需在监听器内单独处理（不走路径统一的 resolve 方法体）。
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
     * 解析经理或 HIERARCHY 时，需要「锚点用户 ID」（发起人或最近完成任务者）。
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
