package com.portal.security;

import com.platform.security.util.SecurityContextUtils;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@link CurrentUserId} annotated parameters from the authenticated user only.
 *
 * <p>A client-supplied {@code X-User-Id} is never an identity: without a valid credential the request is answered
 * 401, so the frontend renews the session or signs in (platform access model §7.2). No backend calls the Portal with
 * that header — the engine and Admin use the service / internal token on their own endpoints.</p>
 */
@Component
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Sign-in required"));
    }
}
