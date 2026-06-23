package com.portal.security;

import com.platform.security.util.SecurityContextUtils;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@link CurrentUserId} annotated parameters from the JWT SecurityContext.
 * Falls back to {@code X-User-Id} header when SecurityContext has no authenticated user
 * (e.g. when httpOnly cookies are unavailable).
 * Returns {@code null} if no identity is available from either source.
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
                .orElseGet(() -> webRequest.getHeader("X-User-Id"));
    }
}
