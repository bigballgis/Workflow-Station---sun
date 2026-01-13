package com.workflow.enums;

/**
 * 任务处理人分配类型枚举
 * 定义了7种标准的任务分配方式
 * 
 * 规则：非具体到人的分配都采用认领机制
 */
public enum AssigneeType {
    
    /**
     * 1. 当前人的职能经理
     * 直接分配给流程发起人的职能经理
     */
    FUNCTION_MANAGER("FUNCTION_MANAGER", "职能经理", false),
    
    /**
     * 2. 当前人的实体经理
     * 直接分配给流程发起人的实体经理
     */
    ENTITY_MANAGER("ENTITY_MANAGER", "实体经理", false),
    
    /**
     * 3. 流程发起人
     * 直接分配给流程发起人
     */
    INITIATOR("INITIATOR", "流程发起人", false),
    
    /**
     * 4. 当前人部门的非本人
     * 分配给发起人所在部门的其他成员，需要认领
     */
    DEPT_OTHERS("DEPT_OTHERS", "本部门其他人", true),
    
    /**
     * 5. 当前人上级部门
     * 分配给发起人上级部门的所有成员，需要认领
     */
    PARENT_DEPT("PARENT_DEPT", "上级部门", true),
    
    /**
     * 6. 某个部门的所有人
     * 分配给指定部门的所有成员，需要认领
     * 需要配合 assigneeValue 指定部门ID
     */
    FIXED_DEPT("FIXED_DEPT", "指定部门", true),
    
    /**
     * 7. 某个虚拟组
     * 分配给指定虚拟组的所有成员，需要认领
     * 需要配合 assigneeValue 指定虚拟组ID
     */
    VIRTUAL_GROUP("VIRTUAL_GROUP", "虚拟组", true);
    
    private final String code;
    private final String name;
    private final boolean requiresClaim;
    
    AssigneeType(String code, String name, boolean requiresClaim) {
        this.code = code;
        this.name = name;
        this.requiresClaim = requiresClaim;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getName() {
        return name;
    }
    
    /**
     * 是否需要认领（非具体到人的分配）
     */
    public boolean requiresClaim() {
        return requiresClaim;
    }
    
    /**
     * 是否需要额外的配置值（部门ID或虚拟组ID）
     */
    public boolean requiresValue() {
        return this == FIXED_DEPT || this == VIRTUAL_GROUP;
    }
    
    /**
     * 根据代码获取分配类型
     */
    public static AssigneeType fromCode(String code) {
        if (code == null) return null;
        for (AssigneeType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        // 兼容旧的类型名称
        return fromLegacyCode(code);
    }
    
    /**
     * 兼容旧的类型代码（向后兼容）
     */
    private static AssigneeType fromLegacyCode(String code) {
        return switch (code.toLowerCase()) {
            case "functionmanager", "function_manager" -> FUNCTION_MANAGER;
            case "entitymanager", "entity_manager", "manager" -> ENTITY_MANAGER;
            case "initiator" -> INITIATOR;
            case "deptothers", "dept_others" -> DEPT_OTHERS;
            case "parentdept", "parent_dept" -> PARENT_DEPT;
            case "fixeddept", "fixed_dept", "group" -> FIXED_DEPT;
            case "virtualgroup", "virtual_group" -> VIRTUAL_GROUP;
            default -> null;
        };
    }
}
