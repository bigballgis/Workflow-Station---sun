package com.workflow.enums;

/**
 * 任务分配类型枚举
 * 支持多维度任务分配：用户、虚拟组
 * 
 * 注意：此枚举是旧的分配类型，新的任务分配机制使用 AssigneeType 枚举
 * AssigneeType 定义了9种标准的任务分配方式，包括基于业务单元角色的分配
 * 
 * @see AssigneeType
 */
public enum AssignmentType {
    
    /**
     * 直接分配给用户
     * 任务直接分配给特定用户，该用户在待办任务中可以看到
     */
    USER("USER", "User Assignment"),
    
    /**
     * 分配给虚拟组
     * 任务分配给虚拟组，虚拟组的所有成员都可以看到并认领处理
     */
    VIRTUAL_GROUP("VIRTUAL_GROUP", "Virtual Group Assignment");
    
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
        throw new IllegalArgumentException("Unknown assignment type: " + code);
    }
}