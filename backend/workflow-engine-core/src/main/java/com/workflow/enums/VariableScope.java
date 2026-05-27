package com.workflow.enums;

/**
 * Process variable scope enum
 * 
 * Defines the visibility scope and lifecycle of variables during process execution
 * 
 * @author Workflow Engine
 * @version 1.0
 */
public enum VariableScope {
    
    /**
     * Process instance level
     * Variable is visible throughout the process instance, accessible by all tasks and executions
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
     * Get variable scope by code
     * 
     * @param code scope code
     * @return variable scope enum
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