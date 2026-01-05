package com.platform.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods for audit logging.
 * Validates: Requirements 13.3
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    
    /**
     * Action name for the audit log.
     */
    String action();
    
    /**
     * Resource type being operated on.
     */
    String resourceType() default "";
    
    /**
     * SpEL expression to extract resource ID from method parameters.
     */
    String resourceId() default "";
    
    /**
     * Whether to log request data.
     */
    boolean logRequest() default true;
    
    /**
     * Whether to log response data.
     */
    boolean logResponse() default false;
    
    /**
     * Module name (defaults to class package).
     */
    String module() default "";
}
