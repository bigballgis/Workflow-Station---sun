package com.admin.security;

import com.admin.config.InternalServiceAuthenticator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.constant.PlatformConstants;
import com.platform.common.dto.ApiResponse;
import com.platform.common.i18n.I18nService;
import com.platform.security.service.UserRoleService;
import com.platform.security.util.SecurityContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default-deny authorization for every Admin Center endpoint (platform access model §8.1): each handler
 * declares its audience with {@link AdminAccess}; anything undeclared is SYS_ADMIN only.
 *
 * <p>Roles come from the access token for browser calls. A first-party service call (valid X-Service-Token)
 * is admitted outright where the endpoint allows services; otherwise it acts for the user in X-User-Id, whose
 * roles are read from the database — the token's roles may be stale and a service-minted identity has none.
 * No identity → 401 (the frontends renew the session on it); identity without the role → 403.
 *
 * <p>The admitted caller is stored as an {@link AdminCaller} for the handler. On a team-scoped endpoint that
 * addresses a team resource by path, {@link AdminTeamScope} decides before the handler runs (403 naming the team).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccessInterceptor implements HandlerInterceptor {

    private final InternalServiceAuthenticator serviceAuthenticator;
    private final UserRoleService userRoleService;
    private final AdminTeamScope teamScope;
    private final I18nService i18nService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // Spring's own handlers (error page, CORS pre-flight) are not Admin endpoints.
        if (!(handler instanceof HandlerMethod handlerMethod)
                || !handlerMethod.getBeanType().getName().startsWith("com.admin.")) {
            return true;
        }
        AdminEndpointPolicy policy = AdminEndpointPolicy.resolve(
                handlerMethod.getMethod(), handlerMethod.getBeanType(), request.getMethod());
        if (policy.audience() == AdminAudience.PUBLIC) {
            return true;
        }

        boolean serviceCall = serviceAuthenticator.hasValidServiceToken(request);
        if (serviceCall && (policy.services() || policy.audience() == AdminAudience.SERVICE)) {
            AdminCaller.ofService().storeOn(request);
            return true;
        }
        if (policy.audience() == AdminAudience.SERVICE) {
            return reject(request, response, SecurityContextUtils.isAuthenticated() ? 403 : 401, policy, null);
        }

        Optional<AdminCaller> caller = serviceCall ? delegatedUser(request) : tokenUser();
        if (caller.isEmpty()) {
            return reject(request, response, serviceCall ? 403 : 401, policy, null);
        }
        if (policy.audience() != AdminAudience.SIGNED_IN && !policy.audience().admitsAnyOf(caller.get().roles())) {
            return reject(request, response, 403, policy, null);
        }
        caller.get().storeOn(request);
        if (policy.audience().teamScoped() && policy.owner() != AdminOwner.NONE) {
            Optional<String> refusal = teamScope.refusal(request, policy.owner(),
                    uriVariable(request, policy.ownerVariable()), policy.audience() == AdminAudience.TEAM_WRITE);
            if (refusal.isPresent()) {
                return reject(request, response, 403, policy, refusal.get());
            }
        }
        return true;
    }

    private static Optional<AdminCaller> tokenUser() {
        return SecurityContextUtils.getCurrentUser()
                .map(user -> AdminCaller.ofUser(user.getUserId(),
                        user.getRoles() == null ? List.<String>of() : user.getRoles()));
    }

    /** Empty when the service call names no user; a named user with no roles yields an empty role list. */
    private Optional<AdminCaller> delegatedUser(HttpServletRequest request) {
        String userId = request.getHeader(PlatformConstants.HEADER_USER_ID);
        if (userId == null || userId.isBlank() || "system".equals(userId)) {
            return Optional.empty();
        }
        return Optional.of(AdminCaller.ofUser(userId.trim(),
                userRoleService.getEffectiveRoleCodesForUser(userId.trim())));
    }

    @SuppressWarnings("unchecked")
    private static String uriVariable(HttpServletRequest request, String name) {
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return variables instanceof Map<?, ?> map ? ((Map<String, String>) map).get(name) : null;
    }

    private boolean reject(HttpServletRequest request, HttpServletResponse response, int status,
                           AdminEndpointPolicy policy, String message) throws Exception {
        log.warn("Admin access denied ({}): {} {} requires {}{}", status, request.getMethod(),
                request.getServletPath(), policy.audience(), message == null ? "" : " — " + message);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Void> body = status == 401
                ? ApiResponse.error("AUTH_FAILED", i18nService.getMessage("auth.unauthorized"))
                : ApiResponse.error("PERM_ACCESS_DENIED",
                        message != null ? message : i18nService.getMessage("auth.no_permission"));
        objectMapper.writeValue(response.getWriter(), body);
        return false;
    }
}
