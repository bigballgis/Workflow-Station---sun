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
    PROCESS_INSTANCE("process_instance", "Process Instance"),
    EXECUTION("execution", "Execution"),
    TASK("task", "Task"),
    GLOBAL("global", "Global");
    
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
        throw new IllegalArgumentException("Unknown variable scope code: " + code);
    }
}