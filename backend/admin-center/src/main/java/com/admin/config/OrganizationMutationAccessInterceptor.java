package com.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.constant.PlatformConstants;
import com.platform.common.dto.ApiResponse;
import com.platform.common.i18n.I18nService;
import com.platform.security.util.SecurityContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Backend read-only guard for organization / virtual-group / approver / membership-exit writes.
 * Pure AUDITOR callers are rejected; SYS_ADMIN / SUPER_ADMIN bypass; other writers need
 * {@code user:write} or {@code system:admin}. First-party service calls that carry a valid
 * {@code X-Service-Token} (portal approval writes) are allowed — the minted principal has
 * empty roles and would otherwise always be denied.
 */
@Slf4j
@Component
public class OrganizationMutationAccessInterceptor implements HandlerInterceptor {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Pattern CLAIM_PATH =
            Pattern.compile("^/virtual-groups/[^/]+/tasks/[^/]+/claim$");
    private static final Pattern DELEGATE_PATH =
            Pattern.compile("^/virtual-groups/tasks/[^/]+/delegate$");

    private final I18nService i18nService;
    private final ObjectMapper objectMapper;
    private final String serviceInternalToken;

    public OrganizationMutationAccessInterceptor(
            I18nService i18nService,
            ObjectMapper objectMapper,
            @Value("${service.internal-token:}") String serviceInternalToken) {
        this.i18nService = i18nService;
        this.objectMapper = objectMapper;
        this.serviceInternalToken = serviceInternalToken;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (SAFE_METHODS.contains(request.getMethod().toUpperCase())) {
            return true;
        }
        String path = request.getServletPath();
        if (path == null) {
            path = "";
        }
        if (CLAIM_PATH.matcher(path).matches() || DELEGATE_PATH.matcher(path).matches()) {
            return true;
        }
        if (isTrustedServiceCall(request)) {
            return true;
        }

        boolean elevatedAdmin = isElevatedAdmin();
        boolean auditor = SecurityContextUtils.hasRole("AUDITOR");
        boolean exitPath = "/exit".equals(path) || path.startsWith("/exit/");

        if (exitPath) {
            if (auditor && !elevatedAdmin) {
                return reject(response, request);
            }
            return true;
        }

        if (auditor && !elevatedAdmin) {
            return reject(response, request);
        }
        if (elevatedAdmin
                || SecurityContextUtils.hasPermission("user:write")
                || SecurityContextUtils.hasPermission("system:admin")) {
            return true;
        }
        return reject(response, request);
    }

    private boolean isTrustedServiceCall(HttpServletRequest request) {
        if (serviceInternalToken == null || serviceInternalToken.isBlank()) {
            return false;
        }
        String provided = request.getHeader(PlatformConstants.HEADER_SERVICE_TOKEN);
        if (provided == null || provided.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                serviceInternalToken.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isElevatedAdmin() {
        return SecurityContextUtils.isSuperAdmin()
                || SecurityContextUtils.hasRole("SYS_ADMIN")
                || SecurityContextUtils.hasRole("SUPER_ADMIN");
    }

    private boolean reject(HttpServletResponse response, HttpServletRequest request) throws Exception {
        log.warn("Organization mutation denied: {} {}", request.getMethod(), request.getServletPath());
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.error(
                "PERM_ACCESS_DENIED", i18nService.getMessage("auth.no_permission"));
        objectMapper.writeValue(response.getWriter(), body);
        return false;
    }
}
