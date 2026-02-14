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
    STRING("string", "String"),
    
    /**
     * 整数类型
     */
    INTEGER("integer", "Integer"),
    
    /**
     * 长整数类型
     */
    LONG("long", "Long"),
    
    /**
     * 双精度浮点数类型
     */
    DOUBLE("double", "Double"),
    
    /**
     * 布尔类型
     */
    BOOLEAN("boolean", "Boolean"),
    
    /**
     * 日期时间类型
     */
    DATE("date", "Date"),
    
    /**
     * JSON对象类型
     * 用于存储复杂对象，使用PostgreSQL JSONB格式
     */
    JSON("json", "JSON"),
    
    /**
     * 文件类型
     * 存储文件引用信息
     */
    FILE("file", "File"),
    
    /**
     * 二进制数据类型
     */
    BINARY("binary", "Binary"),
    
    /**
     * 已删除标记
     * 用于标记已删除的变量历史记录
     */
    DELETED("deleted", "Deleted");
    
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
        throw new IllegalArgumentException("Unknown variable type code: " + code);
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