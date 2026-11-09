package com.admin.security;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * The access policy in force for one handler and HTTP method, resolved from {@link AdminAccess}.
 *
 * @param audience      who may call
 * @param services      whether first-party service calls are admitted as well
 * @param declared      false when no annotation applied and the SYS_ADMIN default was used
 * @param owner         the team resource the path addresses ({@link AdminOwner#NONE} when none)
 * @param ownerVariable the URI template variable holding the owner's key
 */
public record AdminEndpointPolicy(AdminAudience audience, boolean services, boolean declared,
                                  AdminOwner owner, String ownerVariable) {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD");

    /** Method annotation first; the class annotation only for GET / HEAD; otherwise SYS_ADMIN. */
    public static AdminEndpointPolicy resolve(Method method, Class<?> beanType, String httpMethod) {
        AdminAccess onMethod = method.getAnnotation(AdminAccess.class);
        if (onMethod != null) {
            return of(onMethod);
        }
        AdminAccess onClass = beanType.getAnnotation(AdminAccess.class);
        if (onClass != null && httpMethod != null && SAFE_METHODS.contains(httpMethod.toUpperCase())) {
            return of(onClass);
        }
        return new AdminEndpointPolicy(AdminAudience.SYS_ADMIN, false, false, AdminOwner.NONE, "");
    }

    private static AdminEndpointPolicy of(AdminAccess access) {
        return new AdminEndpointPolicy(access.value(), access.services(), true, access.owner(),
                access.ownerVariable());
    }
}
