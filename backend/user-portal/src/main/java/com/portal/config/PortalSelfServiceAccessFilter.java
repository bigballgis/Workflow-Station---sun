package com.portal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.UserPrincipal;
import com.portal.controller.AuthController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 无 UBR（JWT {@code portalAccessMode=PERMISSION_SELF_SERVICE_ONLY}）时仅允许权限自助等白名单路径。
 */
@Component
@RequiredArgsConstructor
public class PortalSelfServiceAccessFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!AuthController.PORTAL_ACCESS_MODE_SELF_SERVICE.equals(principal.getPortalAccessMode())) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        if (uri == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (isAllowedSelfServicePath(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", "PORTAL_ACCESS_DENIED");
        err.put("message", "当前为权限自助模式，无法使用此功能");
        body.put("error", err);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private static boolean isAllowedSelfServicePath(String uri) {
        return startsWithAny(uri,
                "/api/portal/auth/",
                "/api/portal/permissions/",
                "/api/portal/permission-requests/",
                "/api/portal/notifications",
                "/api/portal/preferences",
                "/api/portal/my-permissions",
                "/api/portal/exit/");
    }

    private static boolean startsWithAny(String uri, String... prefixes) {
        for (String p : prefixes) {
            if (uri.startsWith(p)) {
                return true;
            }
        }
        return false;
    }
}
