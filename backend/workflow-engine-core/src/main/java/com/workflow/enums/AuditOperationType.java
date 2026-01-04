package com.workflow.enums;

/**
 * 审计操作类型枚举
 * 定义所有需要记录审计日志的操作类型
 */
public enum AuditOperationType {
    
    // 流程定义操作
    DEPLOY_PROCESS("部署流程定义"),
    UPDATE_PROCESS("更新流程定义"),
    DELETE_PROCESS("删除流程定义"),
    SUSPEND_PROCESS_DEFINITION("挂起流程定义"),
    ACTIVATE_PROCESS_DEFINITION("激活流程定义"),
    
    // 流程实例操作
    START_PROCESS("启动流程实例"),
    SUSPEND_PROCESS_INSTANCE("挂起流程实例"),
    RESUME_PROCESS_INSTANCE("恢复流程实例"),
    TERMINATE_PROCESS_INSTANCE("终止流程实例"),
    DELETE_PROCESS_INSTANCE("删除流程实例"),
    
    // 任务操作
    CREATE_TASK("创建任务"),
    ASSIGN_TASK("分配任务"),
    CLAIM_TASK("认领任务"),
    DELEGATE_TASK("委托任务"),
    COMPLETE_TASK("完成任务"),
    DELETE_TASK("删除任务"),
    UPDATE_TASK("更新任务"),
    
    // 变量操作
    SET_VARIABLE("设置变量"),
    UPDATE_VARIABLE("更新变量"),
    DELETE_VARIABLE("删除变量"),
    
    // 表单操作
    CREATE_FORM("创建表单"),
    UPDATE_FORM("更新表单"),
    DELETE_FORM("删除表单"),
    SUBMIT_FORM("提交表单"),
    
    // 用户和权限操作
    CREATE_USER("创建用户"),
    UPDATE_USER("更新用户"),
    DELETE_USER("删除用户"),
    ASSIGN_ROLE("分配角色"),
    REVOKE_ROLE("撤销角色"),
    
    // 系统操作
    LOGIN("用户登录"),
    LOGOUT("用户登出"),
    ACCESS_DENIED("访问被拒绝"),
    SYSTEM_ERROR("系统错误"),
    SECURITY_EVENT("安全事件"),
    
    // 数据操作
    EXPORT_DATA("导出数据"),
    IMPORT_DATA("导入数据"),
    BACKUP_DATA("备份数据"),
    RESTORE_DATA("恢复数据"),
    
    // 监控操作
    VIEW_PROCESS_DIAGRAM("查看流程图"),
    VIEW_STATISTICS("查看统计信息"),
    VIEW_AUDIT_LOG("查看审计日志");
    
    private final String description;
    
    AuditOperationType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据字符串获取操作类型
     */
    public static AuditOperationType fromString(String operationType) {
        for (AuditOperationType type : AuditOperationType.values()) {
            if (type.name().equals(operationType)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的操作类型: " + operationType);
    }
}