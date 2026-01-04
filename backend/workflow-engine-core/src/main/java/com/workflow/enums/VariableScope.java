package com.workflow.enums;

/**
 * 流程变量作用域枚举
 * 
 * 定义变量在流程执行中的可见范围和生命周期
 * 
 * @author Workflow Engine
 * @version 1.0
 */
public enum VariableScope {
    
    /**
     * 流程实例级别
     * 变量在整个流程实例中可见，所有任务和执行都可以访问
     */
    PROCESS_INSTANCE("process_instance", "流程实例级别"),
    
    /**
     * 执行级别
     * 变量仅在特定执行分支中可见，用于并行网关等场景
     */
    EXECUTION("execution", "执行级别"),
    
    /**
     * 任务级别
     * 变量仅在特定任务中可见，任务完成后变量可能被清理
     */
    TASK("task", "任务级别"),
    
    /**
     * 全局级别
     * 变量在所有流程实例中可见，用于系统级配置
     */
    GLOBAL("global", "全局级别");
    
    private final String code;
    private final String description;
    
    VariableScope(String code, String description) {
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
     * 根据代码获取变量作用域
     * 
     * @param code 作用域代码
     * @return 变量作用域枚举
     */
    public static VariableScope fromCode(String code) {
        for (VariableScope scope : values()) {
            if (scope.code.equals(code)) {
                return scope;
            }
        }
        throw new IllegalArgumentException("未知的变量作用域代码: " + code);
    }
}