package com.platform.security.encryption;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark fields that should be encrypted when stored.
 * Validates: Requirements 13.1
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Encrypted {
    
    /**
     * Whether to encrypt the field.
     */
    boolean value() default true;
}
