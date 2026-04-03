package com.developer.config;

import com.developer.security.FunctionUnitWorkspaceAccessDeniedException;
import com.developer.security.FunctionUnitWorkspaceAccessService;
import com.developer.security.WorkspaceAccessAction;
import com.platform.common.i18n.I18nService;
import com.platform.security.util.SecurityContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按 URL 解析 functionUnitId，对工作区（创建者 + 虚拟组）做统一鉴权。
 * 不含 {@code /api/function-units/{name}} 等按名称路由的旧版接口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FunctionUnitWorkspaceAccessInterceptor implements HandlerInterceptor {

    private static final Pattern FUNCTION_UNITS_ID = Pattern.compile("/function-units/(\\d+)");
    private static final Pattern EXPORT_IMPORT_FU = Pattern.compile("/export-import/function-units/(\\d+)");
    private static final Pattern AI_LOCK = Pattern.compile("/ai-generation/lock/(\\d+)");
    private static final Pattern AI_EVENTS = Pattern.compile("/ai-generation/events/(\\d+)");
    private static final Pattern AI_APPLY_UNDO = Pattern.compile("/ai-generation/(\\d+)/(apply|undo)");

    private final FunctionUnitWorkspaceAccessService workspaceAccessService;
    private final I18nService i18nService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (SecurityContextUtils.getCurrentUserId().isEmpty()) {
            return true;
        }

        String path = request.getServletPath();
        Long functionUnitId = extractFunctionUnitId(path, request);
        if (functionUnitId == null) {
            return true;
        }

        try {
            if (path.matches("/function-units/\\d+/dev-groups")) {
                if ("PUT".equalsIgnoreCase(request.getMethod())) {
                    workspaceAccessService.assertCanAccess(functionUnitId, WorkspaceAccessAction.ASSIGN_DEV_GROUPS);
                } else {
                    workspaceAccessService.assertCanAccess(functionUnitId, WorkspaceAccessAction.VIEW);
                }
                return true;
            }
            if ("DELETE".equalsIgnoreCase(request.getMethod()) && path.matches("/function-units/\\d+")) {
                workspaceAccessService.assertCanAccess(functionUnitId, WorkspaceAccessAction.DELETE);
                return true;
            }
            if ("GET".equalsIgnoreCase(request.getMethod()) || "HEAD".equalsIgnoreCase(request.getMethod())) {
                workspaceAccessService.assertCanAccess(functionUnitId, WorkspaceAccessAction.VIEW);
            } else {
                workspaceAccessService.assertCanAccess(functionUnitId, WorkspaceAccessAction.MODIFY);
            }
            return true;
        } catch (FunctionUnitWorkspaceAccessDeniedException ex) {
            log.warn("Workspace interceptor denied: {} {}", request.getMethod(), path);
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json;charset=UTF-8");
            String msg = ex.getMessage() != null ? ex.getMessage()
                    : i18nService.getMessage("auth.no_permission");
            response.getWriter().write("{\"error\":\"WORKSPACE_FORBIDDEN\",\"message\":\"" + escapeJson(msg) + "\"}");
            return false;
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Long extractFunctionUnitId(String path, HttpServletRequest request) {
        if (path.startsWith("/ai-generation")) {
            String q = request.getParameter("functionUnitId");
            if (q != null && !q.isBlank()) {
                try {
                    return Long.parseLong(q.trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        Long fromPatterns = firstMatch(path, FUNCTION_UNITS_ID);
        if (fromPatterns != null) {
            return fromPatterns;
        }
        fromPatterns = firstMatch(path, EXPORT_IMPORT_FU);
        if (fromPatterns != null) {
            return fromPatterns;
        }
        fromPatterns = firstMatch(path, AI_LOCK);
        if (fromPatterns != null) {
            return fromPatterns;
        }
        fromPatterns = firstMatch(path, AI_EVENTS);
        if (fromPatterns != null) {
            return fromPatterns;
        }
        return firstMatch(path, AI_APPLY_UNDO);
    }

    private static Long firstMatch(String path, Pattern pattern) {
        Matcher m = pattern.matcher(path);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
