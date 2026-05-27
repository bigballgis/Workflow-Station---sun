package com.workflow.enums;

/**
 * Process variable type enum
 * 
 * Defines all variable data types supported by the workflow engine
 * Supports both primitive and complex object types
 * 
 * @author Workflow Engine
 * @version 1.0
 */
public enum VariableType {
    
    /**
     * String type
     */
    STRING("string", "String"),
    
    /**
     * Integer type
     */
    INTEGER("integer", "Integer"),
    
    /**
     * Long integer type
     */
    LONG("long", "Long"),
    
    /**
     * Double-precision floating-point type
     */
    DOUBLE("double", "Double"),
    
    /**
     * Boolean type
     */
    BOOLEAN("boolean", "Boolean"),
    
    /**
     * Date/time type
     */
    DATE("date", "Date"),
    
    /**
     * JSON object type
     * For storing complex objects, uses PostgreSQL JSONB format
     */
    JSON("json", "JSON"),
    
    /**
     * File type
     * Stores file reference information
     */
    FILE("file", "File"),
    
    /**
     * Binary data type
     */
    BINARY("binary", "Binary"),
    
    /**
     * Deleted marker
     * Used to mark deleted variable history records
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
     * Get variable type by code
     * 
     * @param code type code
     * @return variable type enum
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
     * Check if this is a numeric type
     * 
     * @return true if numeric type
     */
    public boolean isNumeric() {
        return this == INTEGER || this == LONG || this == DOUBLE;
    }
    
    /**
     * Check if this is a complex object type
     * 
     * @return true if complex object type
     */
    public boolean isComplexType() {
        return this == JSON || this == FILE || this == BINARY;
    }
}