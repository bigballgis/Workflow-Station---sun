package com.platform.common.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Runtime Updatable Annotation
 * 
 * Marks configuration classes that support runtime updates
 * 
 * @author Platform Team
 * @version 1.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RuntimeUpdatable {
    
    /**
     * Indicates if the configuration supports hot reloading
     * 
     * @return true if hot reloading is supported
     */
    boolean hotReload() default true;
    
    /**
     * Properties that require application restart when changed
     * 
     * @return Array of property names that require restart
     */
    String[] requiresRestart() default {};
}