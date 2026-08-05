package com.developer.property;

import com.developer.security.DeveloperPermissionChecker;
import com.developer.security.DeveloperPermissionInterceptor;
import com.developer.security.RequireDeveloperPermission;
import com.platform.common.dto.UserPrincipal;
import com.platform.common.i18n.I18nService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.CharRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.util.Collections;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Preservation Property Tests for DeveloperPermissionInterceptor.
 * Feature: kong-authn-authz-fix
 *
 * **Property 7: Preservation** — DeveloperPermissionInterceptor + DeveloperPermissionChecker
 * permission check logic is preserved.
 *
 * For all valid permission check requests, the interceptor correctly:
 * 1. Reads userId from X-User-Id header or authentication.getName()
 * 2. Converts permission codes (FUNCTION_UNIT_VIEW -> function_unit:view)
 * 3. Checks permissions in ANY or ALL mode
 *
 * These tests MUST PASS on unfixed code (they verify baseline behavior to preserve).
 *
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**
 */
class PermissionInterceptorPreservationPropertyTest {

    /**
     * Property 7: Preservation — Permission check with X-User-Id header and ANY mode
     *
     * When a user has at least one of the required permissions (ANY mode),
     * the interceptor SHALL allow access. When the user has none, it SHALL deny.
     *
     * **Validates: Requirements 3.5**
     */
    @Property(tries = 100)
    void permissionCheckAnyMode(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 20) String userId,
            @ForAll @IntRange(min = 1, max = 3) int requiredCount,
            @ForAll boolean userHasAtLeastOne
    ) throws Exception {
        // Build required permission codes in UPPER_CASE format (e.g., FUNCTION_UNIT_VIEW)
        String[] requiredPermissions = new String[requiredCount];
        for (int i = 0; i < requiredCount; i++) {
            requiredPermissions[i] = "RESOURCE" + i + "_ACTION" + i;
        }

        // Build user's actual permissions in lowercase:colon format
        Set<String> userPermissions = new HashSet<>();
        if (userHasAtLeastOne) {
            // Give the user the first required permission (converted format)
            userPermissions.add("resource0:action0");
        }
        // Add some unrelated permissions
        userPermissions.add("other:read");

        // Set up mocks
        DeveloperPermissionChecker checker = mock(DeveloperPermissionChecker.class);
        when(checker.getUserPermissions(userId)).thenReturn(userPermissions);

        I18nService i18nService = mock(I18nService.class);
        when(i18nService.getMessage(anyString())).thenReturn("test message");

        DeveloperPermissionInterceptor interceptor =
                new DeveloperPermissionInterceptor(checker, i18nService);

        // Set up request with X-User-Id header
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", userId);

        // Set up authentication in SecurityContext (3-arg constructor sets authenticated=true)
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        UserPrincipal.builder().userId(userId).username(userId).build(),
                        null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletResponse response = new MockHttpServletResponse();

        // Create a mock HandlerMethod with @RequireDeveloperPermission(mode = ANY)
        HandlerMethod handlerMethod = createAnnotatedHandlerMethod(
                requiredPermissions, RequireDeveloperPermission.Mode.ANY);

        try {
            boolean result = interceptor.preHandle(request, response, handlerMethod);

            if (userHasAtLeastOne) {
                assert result : "Should allow access when user has at least one required permission (ANY mode)";
            } else {
                assert !result : "Should deny access when user has none of the required permissions (ANY mode)";
                assert response.getStatus() == 403 : "Should return 403 Forbidden";
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Property 7: Preservation — Permission check with ALL mode
     *
     * When a user has ALL required permissions, the interceptor SHALL allow access.
     * When the user is missing any, it SHALL deny.
     *
     * **Validates: Requirements 3.5**
     */
    @Property(tries = 100)
    void permissionCheckAllMode(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 20) String userId,
            @ForAll @IntRange(min = 1, max = 3) int requiredCount,
            @ForAll boolean userHasAll
    ) throws Exception {
        String[] requiredPermissions = new String[requiredCount];
        for (int i = 0; i < requiredCount; i++) {
            requiredPermissions[i] = "RESOURCE" + i + "_ACTION" + i;
        }

        Set<String> userPermissions = new HashSet<>();
        if (userHasAll) {
            // Give the user ALL required permissions (converted format)
            for (int i = 0; i < requiredCount; i++) {
                userPermissions.add("resource" + i + ":action" + i);
            }
        } else {
            // Give only the first one (if more than 1 required, this means not all)
            if (requiredCount > 1) {
                userPermissions.add("resource0:action0");
            }
            // If requiredCount == 1 and userHasAll == false, give nothing
        }

        DeveloperPermissionChecker checker = mock(DeveloperPermissionChecker.class);
        when(checker.getUserPermissions(userId)).thenReturn(userPermissions);

        I18nService i18nService = mock(I18nService.class);
        when(i18nService.getMessage(anyString())).thenReturn("test message");

        DeveloperPermissionInterceptor interceptor =
                new DeveloperPermissionInterceptor(checker, i18nService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", userId);

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        UserPrincipal.builder().userId(userId).username(userId).build(),
                        null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletResponse response = new MockHttpServletResponse();

        HandlerMethod handlerMethod = createAnnotatedHandlerMethod(
                requiredPermissions, RequireDeveloperPermission.Mode.ALL);

        try {
            boolean result = interceptor.preHandle(request, response, handlerMethod);

            if (userHasAll) {
                assert result : "Should allow access when user has ALL required permissions (ALL mode)";
            } else {
                assert !result : "Should deny access when user is missing permissions (ALL mode)";
                assert response.getStatus() == 403 : "Should return 403 Forbidden";
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Property 7: Preservation — No annotation means access is allowed
     *
     * When a handler method has no @RequireDeveloperPermission annotation,
     * the interceptor SHALL allow access without checking permissions.
     *
     * **Validates: Requirements 3.5**
     */
    @Property(tries = 100)
    void noAnnotationMeansAccessAllowed(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 20) String path
    ) throws Exception {
        DeveloperPermissionChecker checker = mock(DeveloperPermissionChecker.class);
        I18nService i18nService = mock(I18nService.class);

        DeveloperPermissionInterceptor interceptor =
                new DeveloperPermissionInterceptor(checker, i18nService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/" + path);

        MockHttpServletResponse response = new MockHttpServletResponse();

        // Create a HandlerMethod WITHOUT annotation
        HandlerMethod handlerMethod = createUnannotatedHandlerMethod();

        boolean result = interceptor.preHandle(request, response, handlerMethod);
        assert result : "Should allow access when no permission annotation is present";

        // Permission checker should never be called
        verify(checker, never()).getUserPermissions(anyString());
    }

    // ---- Helper methods ----

    /**
     * Creates a mock HandlerMethod with @RequireDeveloperPermission annotation.
     */
    private HandlerMethod createAnnotatedHandlerMethod(
            String[] permissions, RequireDeveloperPermission.Mode mode) throws Exception {

        RequireDeveloperPermission annotation = new RequireDeveloperPermission() {
            @Override
            public String[] value() { return permissions; }
            @Override
            public Mode mode() { return mode; }
            @Override
            public Class<? extends Annotation> annotationType() {
                return RequireDeveloperPermission.class;
            }
        };

        java.lang.reflect.Method method = TestController.class.getMethod("testMethod");
        HandlerMethod handlerMethod = mock(HandlerMethod.class);
        when(handlerMethod.getMethodAnnotation(RequireDeveloperPermission.class)).thenReturn(annotation);
        when(handlerMethod.getBeanType()).thenReturn((Class) TestController.class);
        when(handlerMethod.getMethod()).thenReturn(method);
        return handlerMethod;
    }

    /**
     * Creates a mock HandlerMethod without @RequireDeveloperPermission annotation.
     */
    private HandlerMethod createUnannotatedHandlerMethod() throws Exception {
        java.lang.reflect.Method method = TestController.class.getMethod("testMethod");
        HandlerMethod handlerMethod = mock(HandlerMethod.class);
        when(handlerMethod.getMethodAnnotation(RequireDeveloperPermission.class)).thenReturn(null);
        when(handlerMethod.getBeanType()).thenReturn((Class) TestController.class);
        when(handlerMethod.getMethod()).thenReturn(method);
        return handlerMethod;
    }

    /** Dummy controller class for mock HandlerMethod */
    public static class TestController {
        public void testMethod() {}
    }
}
