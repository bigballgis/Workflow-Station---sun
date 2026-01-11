package com.admin.enums;

import lombok.Getter;

/**
 * 开发者权限枚举
 * 定义开发者工作站中各种操作的权限代码
 */
@Getter
public enum DeveloperPermission {
    // 功能单元权限
    FUNCTION_UNIT_CREATE("function_unit:create", "创建功能单元"),
    FUNCTION_UNIT_UPDATE("function_unit:update", "更新功能单元"),
    FUNCTION_UNIT_DELETE("function_unit:delete", "删除功能单元"),
    FUNCTION_UNIT_VIEW("function_unit:view", "查看功能单元"),
    FUNCTION_UNIT_DEVELOP("function_unit:develop", "开发功能单元"),
    FUNCTION_UNIT_PUBLISH("function_unit:publish", "发布功能单元"),
    
    // 表单设计权限
    FORM_CREATE("form:create", "创建表单"),
    FORM_UPDATE("form:update", "更新表单"),
    FORM_DELETE("form:delete", "删除表单"),
    FORM_VIEW("form:view", "查看表单"),
    
    // 流程设计权限
    PROCESS_CREATE("process:create", "创建流程"),
    PROCESS_UPDATE("process:update", "更新流程"),
    PROCESS_DELETE("process:delete", "删除流程"),
    PROCESS_VIEW("process:view", "查看流程"),
    
    // 数据表权限
    TABLE_CREATE("table:create", "创建数据表"),
    TABLE_UPDATE("table:update", "更新数据表"),
    TABLE_DELETE("table:delete", "删除数据表"),
    TABLE_VIEW("table:view", "查看数据表"),
    
    // 动作配置权限
    ACTION_CREATE("action:create", "创建动作"),
    ACTION_UPDATE("action:update", "更新动作"),
    ACTION_DELETE("action:delete", "删除动作"),
    ACTION_VIEW("action:view", "查看动作");
    
    private final String code;
    private final String description;
    
    DeveloperPermission(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    /**
     * 根据权限代码查找枚举
     */
    public static DeveloperPermission fromCode(String code) {
        for (DeveloperPermission permission : values()) {
            if (permission.getCode().equals(code)) {
                return permission;
            }
        }
        throw new IllegalArgumentException("Unknown permission code: " + code);
    }
}
