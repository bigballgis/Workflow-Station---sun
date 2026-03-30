package com.developer.security;

import com.platform.common.i18n.I18nService;
import com.platform.security.util.SecurityContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * 开发者权限检查拦截器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeveloperPermissionInterceptor implements HandlerInterceptor {
    
    private final DeveloperPermissionChecker permissionChecker;
    private final I18nService i18nService;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) 
            throws Exception {
        
        log.debug("DeveloperPermissionInterceptor triggered");
        log.debug("Request: {} {}", request.getMethod(), request.getRequestURI());
        log.debug("Handler: {}", handler.getClass().getName());
        
        if (!(handler instanceof HandlerMethod)) {
            log.info("Handler is not HandlerMethod, skipping");
            return true;
        }
        
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        log.debug("Handler method: {}.{}", handlerMethod.getBeanType().getSimpleName(), handlerMethod.getMethod().getName());
        
        // 检查方法级别的注解
        RequireDeveloperPermission methodAnnotation = handlerMethod.getMethodAnnotation(RequireDeveloperPermission.class);
        
        // 检查类级别的注解
        RequireDeveloperPermission classAnnotation = handlerMethod.getBeanType().getAnnotation(RequireDeveloperPermission.class);
        
        // 方法级别优先
        RequireDeveloperPermission annotation = methodAnnotation != null ? methodAnnotation : classAnnotation;
        
        if (annotation == null) {
            log.debug("No permission annotation found, allowing access");
            return true;
        }
        
        log.debug("Required permissions: {}", Arrays.toString(annotation.value()));
        
        // 获取当前用户
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"" + i18nService.getMessage("auth.unauthorized") + "\"}");
            return false;
        }
        
        String userId = getUserIdFromRequest(request);
        if (userId == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"" + i18nService.getMessage("auth.cannot_get_user_id") + "\"}");
            return false;
        }
        
        String[] requiredPermissions = annotation.value();
        RequireDeveloperPermission.Mode mode = annotation.mode();
        
        boolean hasPermission = checkPermissions(userId, requiredPermissions, mode);
        
        if (!hasPermission) {
            log.warn("User {} does not have required permissions: {}", userId, Arrays.toString(requiredPermissions));
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"FORBIDDEN\",\"message\":\"" + i18nService.getMessage("auth.no_permission") + "\"}");
            return false;
        }
        
        return true;
    }
    
    private String getUserIdFromRequest(HttpServletRequest request) {
        // Priority 1: SecurityContext (JWT-parsed UserPrincipal)
        Optional<String> securityContextUserId = SecurityContextUtils.getCurrentUserId();
        if (securityContextUserId.isPresent()) {
            return securityContextUserId.get();
        }
        // Priority 2: X-User-Id header (fallback for compatibility)
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isEmpty()) {
            return userId;
        }
        return null;
    }
    
    private boolean checkPermissions(String userId, String[] requiredPermissions, RequireDeveloperPermission.Mode mode) {
        Set<String> userPermissions = permissionChecker.getUserPermissions(userId);
        
        // 将注解中的权限代码转换为小写格式（FUNCTION_UNIT_VIEW -> function_unit:view）
        if (mode == RequireDeveloperPermission.Mode.ALL) {
            return Arrays.stream(requiredPermissions)
                .map(this::convertPermissionCode)
                .allMatch(userPermissions::contains);
        } else {
            return Arrays.stream(requiredPermissions)
                .map(this::convertPermissionCode)
                .anyMatch(userPermissions::contains);
        }
    }
    
    /**
     * 将权限代码从大写下划线格式转换为小写冒号格式
     * 例如: FUNCTION_UNIT_VIEW -> function_unit:view
     */
    private String convertPermissionCode(String code) {
        // 找到最后一个下划线的位置（分隔资源和操作）
        int lastUnderscore = code.lastIndexOf('_');
        if (lastUnderscore > 0) {
            String resource = code.substring(0, lastUnderscore).toLowerCase();
            String action = code.substring(lastUnderscore + 1).toLowerCase();
            return resource + ":" + action;
        }
        return code.toLowerCase();
    }
}
