package com.portal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.UserPrincipal;
import com.portal.controller.AuthController;
import com.portal.util.PortalUserSecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Set;

/**
 * 无 UBR（JWT {@code portalAccessMode=PERMISSION_SELF_SERVICE_ONLY}）时仅允许权限自助等白名单路径。
 */
@Slf4j
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
        if (!AuthController.PORTAL_ACCESS_MODE_SELF_SERVICE.equals(PortalUserSecurityUtils.getPortalAccessMode(principal))) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawUri = request.getRequestURI();
        if (rawUri == null) {
            filterChain.doFilter(request, response);
            return;
        }
        String normalizedUri = normalizeSelfServiceUri(request);
        String method = request.getMethod();
        boolean allowed = isAllowedSelfServiceUri(normalizedUri, method);
        if (allowed) {
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

    /**
     * 顶层路径段与流程实例 ID 区分：UUID/引擎实例 ID 不会与下列保留字冲突。
     */
    private static final Set<String> PROCESS_TOP_LEVEL_RESERVED = Set.of(
            "definitions",
            "startable",
            "my-applications",
            "drafts",
            "actions",
            "function-units",
            "fu-data",
            "function-unit-contents");

    /**
     * 将请求 URI 规范成与路由判断一致的形式（含 {@code /api/portal}）。
     * 部分网关/容器组合下 {@link HttpServletRequest#getRequestURI()} 可能仅为 {@code /processes/...}，
     * 导致自助模式白名单前缀匹配失败。
     */
    private static String normalizeSelfServiceUri(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isEmpty()) {
            return "";
        }
        if (uri.startsWith("/api/portal")) {
            return uri;
        }
        if (uri.startsWith("/processes")) {
            return "/api/portal" + (uri.startsWith("/") ? uri : "/" + uri);
        }
        String cp = request.getContextPath() != null ? request.getContextPath() : "";
        String sp = request.getServletPath() != null ? request.getServletPath() : "";
        String pi = request.getPathInfo() != null ? request.getPathInfo() : "";
        String composed = cp + sp + pi;
        if (composed.startsWith("/api/portal")) {
            return composed;
        }
        return uri;
    }

    private static boolean isAllowedSelfServiceUri(String uri, String method) {
        if (uri == null || uri.isEmpty()) {
            return false;
        }
        if (startsWithAny(uri,
                "/api/portal/auth/",
                "/api/portal/debug/",
                "/api/portal/permissions/",
                "/api/portal/permission-requests/",
                "/api/portal/notifications",
                "/api/portal/preferences",
                "/api/portal/my-permissions",
                "/api/portal/exit/")) {
            return true;
        }
        return isAllowedSelfServiceProcessRequest(uri, method);
    }

    /**
     * 自助模式用户仍需查看「我的申请」、草稿与本人流程详情；禁止发起流程（POST .../start）及无关写接口。
     */
    private static boolean isAllowedSelfServiceProcessRequest(String uri, String method) {
        final String prefix = "/api/portal/processes/";
        if (!uri.startsWith(prefix)) {
            return false;
        }

        if (uri.startsWith(prefix + "drafts")) {
            return "GET".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method);
        }

        if (uri.endsWith("/draft")) {
            String afterPrefix = uri.substring(prefix.length());
            if (afterPrefix.contains("/") && afterPrefix.endsWith("/draft")) {
                return "GET".equalsIgnoreCase(method)
                        || "POST".equalsIgnoreCase(method)
                        || "DELETE".equalsIgnoreCase(method);
            }
        }

        if (uri.startsWith(prefix + "my-applications")) {
            return "GET".equalsIgnoreCase(method);
        }

        if (uri.startsWith(prefix + "function-units/")
                || uri.startsWith(prefix + "fu-data/")
                || uri.startsWith(prefix + "function-unit-contents/")) {
            return "GET".equalsIgnoreCase(method);
        }

        if (uri.startsWith(prefix + "actions")) {
            return "GET".equalsIgnoreCase(method);
        }

        if (uri.startsWith(prefix + "definitions") || uri.startsWith(prefix + "startable")) {
            return "GET".equalsIgnoreCase(method);
        }

        String rel = uri.substring(prefix.length());
        if (rel.isEmpty()) {
            return false;
        }
        int slash = rel.indexOf('/');
        String top = slash < 0 ? rel : rel.substring(0, slash);
        if (PROCESS_TOP_LEVEL_RESERVED.contains(top)) {
            return false;
        }

        if ("GET".equalsIgnoreCase(method)) {
            return true;
        }
        return "POST".equalsIgnoreCase(method) && (uri.endsWith("/withdraw") || uri.endsWith("/urge"));
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
