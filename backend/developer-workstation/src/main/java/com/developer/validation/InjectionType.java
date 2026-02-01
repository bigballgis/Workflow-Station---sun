package com.developer.validation;

/**
 * Enumeration of different types of injection attacks that can be detected
 */
public enum InjectionType {
    /**
     * SQL injection attacks targeting database queries
     */
    SQL_INJECTION("SQL Injection"),
    
    /**
     * Cross-site scripting (XSS) attacks
     */
    XSS("Cross-Site Scripting"),
    
    /**
     * Command injection attacks targeting system commands
     */
    COMMAND_INJECTION("Command Injection"),
    
    /**
     * Path traversal attacks targeting file system access
     */
    PATH_TRAVERSAL("Path Traversal"),
    
    /**
     * LDAP injection attacks targeting directory services
     */
    LDAP_INJECTION("LDAP Injection"),
    
    /**
     * XML injection attacks targeting XML parsers
     */
    XML_INJECTION("XML Injection"),
    
    /**
     * NoSQL injection attacks targeting NoSQL databases
     */
    NOSQL_INJECTION("NoSQL Injection"),
    
    /**
     * Server-side template injection attacks
     */
    TEMPLATE_INJECTION("Template Injection");
    
    private final String displayName;
    
    InjectionType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}