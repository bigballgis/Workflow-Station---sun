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
 * Backend access guard for {@code /sla-policies}. Reads (GET and the list {@code POST /query}) need
 * {@code sla:policy:view} or {@code sla:policy:edit}; every other call changes a lead time or starts
 * a recalculation and needs {@code sla:policy:edit}. SYS_ADMIN / SUPER_ADMIN bypass; a pure AUDITOR
 * never writes, whatever permissions it was granted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlaPolicyAccessInterceptor implements HandlerInterceptor {

    static final String VIEW = "sla:policy:view";
    static final String EDIT = "sla:policy:edit";

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final I18nService i18nService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        boolean elevated = SecurityContextUtils.isSuperAdmin()
                || SecurityContextUtils.hasRole("SYS_ADMIN")
                || SecurityContextUtils.hasRole("SUPER_ADMIN");
        if (elevated) {
            return true;
        }
        boolean read = SAFE_METHODS.contains(request.getMethod().toUpperCase())
                || ("POST".equalsIgnoreCase(request.getMethod())
                    && "/sla-policies/query".equals(request.getServletPath()));
        boolean allowed = read
                ? SecurityContextUtils.hasPermission(VIEW) || SecurityContextUtils.hasPermission(EDIT)
                : SecurityContextUtils.hasPermission(EDIT) && !SecurityContextUtils.hasRole("AUDITOR");
        if (allowed) {
            return true;
        }
        log.warn("SLA policy access denied: {} {}", request.getMethod(), request.getServletPath());
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.error(
                "PERM_ACCESS_DENIED", i18nService.getMessage("auth.no_permission"));
        objectMapper.writeValue(response.getWriter(), body);
        return false;
    }
}
