package com.workflow.aspect;

import com.workflow.component.AuditManagerComponent;
import com.workflow.enums.AuditOperationType;
import com.workflow.enums.AuditResourceType;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 审计切面
 * 自动拦截标注了@Auditable的方法，记录审计日志
 */
@Aspect
@Component
public class AuditAspect {
    
    private static final Logger logger = LoggerFactory.getLogger(AuditAspect.class);
    
    @Autowired
    private AuditManagerComponent auditManagerComponent;
    
    /**
     * 审计注解
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Auditable {
        AuditOperationType operationType();
        AuditResourceType resourceType();
        String description() default "";
        boolean captureArgs() default false;
        boolean captureResult() default false;
    }
    
    /**
     * 拦截标注了@Auditable的方法
     */
    @Around("@annotation(auditable)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        
        // 获取HTTP请求信息
        HttpServletRequest request = getCurrentHttpRequest();
        String userId = getCurrentUserId(request);
        String ipAddress = getClientIpAddress(request);
        String userAgent = getUserAgent(request);
        String sessionId = getSessionId(request);
        String tenantId = getTenantId(request);
        
        // 获取方法参数
        Object[] args = joinPoint.getArgs();
        String resourceId = extractResourceId(args);
        String resourceName = extractResourceName(args);
        
        Object beforeData = null;
        if (auditable.captureArgs()) {
            beforeData = captureMethodArgs(args);
        }
        
        Object result = null;
        String operationResult = "SUCCESS";
        String errorMessage = null;
        Object afterData = null;
        
        try {
            // 执行原方法
            result = joinPoint.proceed();
            
            if (auditable.captureResult()) {
                afterData = result;
            }
            
        } catch (Exception e) {
            operationResult = "FAILURE";
            errorMessage = e.getMessage();
            logger.error("审计方法执行失败: {}", joinPoint.getSignature().getName(), e);
            throw e;
            
        } finally {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            try {
                // 记录审计日志
                String description = auditable.description().isEmpty() ? 
                    auditable.operationType().getDescription() : auditable.description();
                
                Map<String, Object> contextData = new HashMap<>();
                contextData.put("methodName", joinPoint.getSignature().getName());
                contextData.put("className", joinPoint.getTarget().getClass().getSimpleName());
                
                auditManagerComponent.recordAuditLog(
                    auditable.operationType(),
                    auditable.resourceType(),
                    resourceId,
                    resourceName,
                    userId,
                    description,
                    beforeData,
                    afterData,
                    operationResult,
                    errorMessage,
                    ipAddress,
                    userAgent,
                    sessionId,
                    requestId,
                    duration,
                    tenantId,
                    contextData
                );
                
            } catch (Exception auditException) {
                // 审计日志记录失败不应该影响业务逻辑
                logger.error("记录审计日志失败，但不影响业务执行", auditException);
            }
        }
        
        return result;
    }
    
    /**
     * 获取当前HTTP请求
     */
    private HttpServletRequest getCurrentHttpRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attributes.getRequest();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 获取当前用户ID
     */
    private String getCurrentUserId(HttpServletRequest request) {
        if (request == null) {
            return "SYSTEM";
        }
        
        // 从JWT token或session中获取用户ID
        String userId = request.getHeader("X-User-Id");
        if (userId != null) {
            return userId;
        }
        
        // 从认证上下文中获取
        // 这里简化处理，实际应该从Spring Security上下文获取
        return "ANONYMOUS";
    }
    
    /**
     * 获取客户端IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * 获取用户代理信息
     */
    private String getUserAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return request.getHeader("User-Agent");
    }
    
    /**
     * 获取会话ID
     */
    private String getSessionId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        
        try {
            return request.getSession(false) != null ? request.getSession().getId() : null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 获取租户ID
     */
    private String getTenantId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return request.getHeader("X-Tenant-Id");
    }
    
    /**
     * 从方法参数中提取资源ID
     */
    private String extractResourceId(Object[] args) {
        if (args == null || args.length == 0) {
            return "UNKNOWN";
        }
        
        // 简单策略：使用第一个String参数作为资源ID
        for (Object arg : args) {
            if (arg instanceof String) {
                return (String) arg;
            }
        }
        
        return "UNKNOWN";
    }
    
    /**
     * 从方法参数中提取资源名称
     */
    private String extractResourceName(Object[] args) {
        // 这里可以根据具体业务逻辑提取资源名称
        // 简化处理，返回null
        return null;
    }
    
    /**
     * 捕获方法参数
     */
    private Map<String, Object> captureMethodArgs(Object[] args) {
        Map<String, Object> argsMap = new HashMap<>();
        
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                argsMap.put("arg" + i, args[i]);
            }
        }
        
        return argsMap;
    }
}