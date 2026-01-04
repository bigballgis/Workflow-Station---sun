package com.workflow.enums;

/**
 * 流程变量类型枚举
 * 
 * 定义工作流引擎支持的所有变量数据类型
 * 支持基本数据类型和复杂对象类型
 * 
 * @author Workflow Engine
 * @version 1.0
 */
public enum VariableType {
    
    /**
     * 字符串类型
     */
    STRING("string", "字符串"),
    
    /**
     * 整数类型
     */
    INTEGER("integer", "整数"),
    
    /**
     * 长整数类型
     */
    LONG("long", "长整数"),
    
    /**
     * 双精度浮点数类型
     */
    DOUBLE("double", "双精度浮点数"),
    
    /**
     * 布尔类型
     */
    BOOLEAN("boolean", "布尔值"),
    
    /**
     * 日期时间类型
     */
    DATE("date", "日期时间"),
    
    /**
     * JSON对象类型
     * 用于存储复杂对象，使用PostgreSQL JSONB格式
     */
    JSON("json", "JSON对象"),
    
    /**
     * 文件类型
     * 存储文件引用信息
     */
    FILE("file", "文件"),
    
    /**
     * 二进制数据类型
     */
    BINARY("binary", "二进制数据"),
    
    /**
     * 已删除标记
     * 用于标记已删除的变量历史记录
     */
    DELETED("deleted", "已删除");
    
    private final String code;
    private final String description;
    
    VariableType(String code, String description) {
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
     * 根据代码获取变量类型
     * 
     * @param code 类型代码
     * @return 变量类型枚举
     */
    public static VariableType fromCode(String code) {
        for (VariableType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的变量类型代码: " + code);
    }
    
    /**
     * 判断是否为数值类型
     * 
     * @return true如果是数值类型
     */
    public boolean isNumeric() {
        return this == INTEGER || this == LONG || this == DOUBLE;
    }
    
    /**
     * 判断是否为复杂对象类型
     * 
     * @return true如果是复杂对象类型
     */
    public boolean isComplexType() {
        return this == JSON || this == FILE || this == BINARY;
    }
}