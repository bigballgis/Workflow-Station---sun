package com.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.ApiResponse;
import com.platform.common.i18n.I18nService;
import com.platform.security.util.SecurityContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Backend write guard for {@code /configs}. SecurityConfig only authenticates, so without this
 * any Admin Center user (including a pure AUDITOR) could create / update / delete / rollback /
 * sync system configs. Writers must be SYS_ADMIN / SUPER_ADMIN or hold {@code system:config}
 * or {@code system:admin}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigMutationAccessInterceptor implements HandlerInterceptor {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final I18nService i18nService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (SAFE_METHODS.contains(request.getMethod().toUpperCase())
                || SecurityContextUtils.isSuperAdmin()
                || SecurityContextUtils.hasRole("SYS_ADMIN")
                || SecurityContextUtils.hasRole("SUPER_ADMIN")
                || SecurityContextUtils.hasPermission("system:config")
                || SecurityContextUtils.hasPermission("system:admin")) {
            return true;
        }
        log.warn("Config mutation denied: {} {}", request.getMethod(), request.getServletPath());
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.error(
                "PERM_ACCESS_DENIED", i18nService.getMessage("auth.no_permission"));
        objectMapper.writeValue(response.getWriter(), body);
        return false;
    }
}
