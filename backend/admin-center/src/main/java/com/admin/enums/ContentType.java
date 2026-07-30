package com.admin.enums;

/**
 * 功能单元内容类型枚举
 */
public enum ContentType {
    /**
     * 流程定义 (BPMN)
     */
    PROCESS,
    
    /**
     * 表单定义
     */
    FORM,
    
    /**
     * 数据表结构
     */
    DATA_TABLE,
    
    /**
     * 脚本
     */
    SCRIPT,
    
    /**
     * 操作定义 (Action)
     */
    ACTION,

    /**
     * 主表视图设计 (Main Table View Design)
     */
    MAIN_TABLE_VIEW,

    /**
     * 邮件模板 (Send Task HTML templates from DW email-templates/)
     */
    EMAIL_TEMPLATE
}
