package com.developer.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 开发者权限注解
 * 用于标注需要特定开发者权限的方法
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireDeveloperPermission {
    
    /**
     * 需要的权限代码
     */
    String[] value();
    
    /**
     * 权限检查模式
     * ANY: 拥有任一权限即可
     * ALL: 必须拥有所有权限
     */
    Mode mode() default Mode.ANY;
    
    enum Mode {
        ANY, ALL
    }
}
