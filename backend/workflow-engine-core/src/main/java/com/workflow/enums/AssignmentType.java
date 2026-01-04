package com.workflow.enums;

/**
 * 任务分配类型枚举
 * 支持多维度任务分配：用户、虚拟组、部门角色
 */
public enum AssignmentType {
    
    /**
     * 直接分配给用户
     * 任务直接分配给特定用户，该用户在待办任务中可以看到
     */
    USER("USER", "用户分配"),
    
    /**
     * 分配给虚拟组
     * 任务分配给虚拟组，虚拟组的所有成员都可以看到并认领处理
     */
    VIRTUAL_GROUP("VIRTUAL_GROUP", "虚拟组分配"),
    
    /**
     * 分配给部门角色
     * 任务分配给部门的特定角色，该部门中拥有该角色的用户可以处理
     */
    DEPT_ROLE("DEPT_ROLE", "部门角色分配");
    
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
     * 根据代码获取分配类型
     */
    public static AssignmentType fromCode(String code) {
        for (AssignmentType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的任务分配类型: " + code);
    }
}