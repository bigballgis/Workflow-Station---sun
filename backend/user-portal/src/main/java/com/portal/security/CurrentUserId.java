package com.portal.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Resolves the current authenticated user's ID from the JWT SecurityContext.
 * Replaces {@code @RequestHeader("X-User-Id")} to prevent client-side identity spoofing.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {
}
