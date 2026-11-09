package com.developer.config;

import com.developer.exception.ResourceNotFoundException;
import com.developer.security.FunctionUnitWorkspaceAccessDeniedException;
import com.developer.security.FunctionUnitWorkspaceAccessService;
import com.developer.security.WorkspaceAccessAction;
import com.developer.security.WorkspaceRouteOwnership;
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
 * 按 URL 解析请求触及的功能单元，按团队规则统一鉴权（平台访问模型 §6.1、§6.2）。
 *
 * <p>路径里的子资源 id（表单、表、动作……）先由 {@link WorkspaceRouteOwnership} 从库里查出归属：
 * 不属于路径里的功能单元 / 表单时按不存在处理（404），没有功能单元 id 的路由按查出的归属鉴权。
 * 不含 {@code /api/function-units/{name}} 等按名称路由的旧版接口。</p>
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
    private static final Pattern AI_STUDIO_THREAD = Pattern.compile("/ai-generation/studio-thread/(\\d+)");
    /**
     * POSTs that only read the design: a clone reads its source (D-35) and simulate reads table definitions. The
     * lookup probe stays a modify: it reads live relation-table rows, which is more than seeing the design.
     */
    private static final Pattern READ_SHAPED_POST = Pattern.compile(
            "/function-units/\\d+/(clone|process/simulate)");
    private static final Pattern UNIT = Pattern.compile("/function-units/\\d+");
    private static final Pattern DEV_GROUPS = Pattern.compile("/function-units/\\d+/dev-groups");

    private final FunctionUnitWorkspaceAccessService workspaceAccessService;
    private final I18nService i18nService;
    private final WorkspaceRouteOwnership routeOwnership;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (SecurityContextUtils.getCurrentUserId().isEmpty()) {
            // Anonymous requests are already refused by the security chain (401); nothing to scope here.
            return true;
        }

        String path = request.getServletPath();
        try {
            Long functionUnitId = routeOwnership.functionUnitOf(path)
                    .orElseGet(() -> extractFunctionUnitId(path, request.getParameter("functionUnitId")));
            if (functionUnitId == null) {
                return true;
            }
            workspaceAccessService.assertCanAccess(functionUnitId, actionFor(path, request.getMethod()));
            return true;
        } catch (NumberFormatException ex) {
            log.warn("Workspace interceptor refused a malformed unit id: {} {}", request.getMethod(), path);
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"VAL_INVALID_INPUT\",\"message\":\"functionUnitId must be a number\"}");
            return false;
        } catch (ResourceNotFoundException ex) {
            log.warn("Workspace interceptor missing resource: {} {}", request.getMethod(), path);
            response.setStatus(HttpStatus.NOT_FOUND.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"error\":\"RES_NOT_FOUND\",\"message\":\"" + escapeJson(ex.getMessage()) + "\"}");
            return false;
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

    /** Reads are VIEW; a get-or-create read writes a default config, so it is MODIFY like every other write. */
    static WorkspaceAccessAction actionFor(String path, String method) {
        if (DEV_GROUPS.matcher(path).matches()) {
            return "PUT".equalsIgnoreCase(method) ? WorkspaceAccessAction.ASSIGN_DEV_GROUPS : WorkspaceAccessAction.VIEW;
        }
        if ("DELETE".equalsIgnoreCase(method) && UNIT.matcher(path).matches()) {
            return WorkspaceAccessAction.DELETE;
        }
        if ("POST".equalsIgnoreCase(method) && READ_SHAPED_POST.matcher(path).matches()) {
            return WorkspaceAccessAction.VIEW;
        }
        boolean read = "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
        return read && !path.endsWith("/or-create") ? WorkspaceAccessAction.VIEW : WorkspaceAccessAction.MODIFY;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * The unit a request works in: the one in the path; on {@code /ai-generation} routes without one, the
     * {@code functionUnitId} query parameter. A query id that is not a number is refused, never ignored.
     */
    static Long extractFunctionUnitId(String path, String functionUnitIdParam) {
        Long fromPath = unitIdInPath(path);
        if (fromPath != null || !path.startsWith("/ai-generation")
                || functionUnitIdParam == null || functionUnitIdParam.isBlank()) {
            return fromPath;
        }
        return Long.parseLong(functionUnitIdParam.trim());
    }

    /** The function unit id the path itself names; null when it names none. */
    public static Long unitIdInPath(String path) {
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
        fromPatterns = firstMatch(path, AI_STUDIO_THREAD);
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
