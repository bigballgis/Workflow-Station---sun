package com.admin.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares who may call an Admin Center endpoint; enforced by {@link AdminAccessInterceptor}.
 *
 * <p>A method annotation wins. A class annotation applies only to the class's GET / HEAD handlers, so a
 * write never inherits a weaker class-wide read policy: every non-GET handler that is not SYS_ADMIN-only
 * declares its own annotation (including POST-shaped queries). Without either, the endpoint is SYS_ADMIN.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface AdminAccess {

    /** Users who may call the endpoint. */
    AdminAudience value();

    /**
     * Also admit first-party backend calls carrying a valid X-Service-Token (Portal, engine, DW), whatever
     * the delegated user's roles: those services authorize their own users before calling.
     */
    boolean services() default false;

    /**
     * For a team-scoped audience: the team resource the path addresses, checked before the handler runs. A class
     * annotation names none; a method that addresses one declares it here.
     */
    AdminOwner owner() default AdminOwner.NONE;

    /** The URI template variable that holds the {@link #owner()}'s key. */
    String ownerVariable() default "";
}
