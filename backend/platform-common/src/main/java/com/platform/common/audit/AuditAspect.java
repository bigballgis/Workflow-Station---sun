package com.platform.common.audit;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.annotation.Lazy;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AOP aspect for automatic audit logging.
 * Validates: Requirements 13.3
 */
@Slf4j
@Aspect
public class AuditAspect {
    
    private final AuditService auditService;
    private final AuditContextProvider contextProvider;
    private final ExpressionParser parser = new SpelExpressionParser();
    
    public AuditAspect(AuditService auditService, AuditContextProvider contextProvider) {
        this.auditService = auditService;
        this.contextProvider = contextProvider;
    }
    
    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        long startTime = System.currentTimeMillis();
        LocalDateTime timestamp = LocalDateTime.now();
        
        AuditLog.AuditLogBuilder logBuilder = AuditLog.builder()
                .id(UUID.randomUUID().toString())
                .action(audited.action())
                .resourceType(audited.resourceType())
                .timestamp(timestamp)
                .module(getModule(joinPoint, audited));
        
        // Get context information
        AuditContext context = contextProvider.getContext();
        if (context != null) {
            logBuilder.userId(context.getUserId())
                    .username(context.getUsername())
                    .ipAddress(context.getIpAddress())
                    .userAgent(context.getUserAgent())
                    .traceId(context.getTraceId());
        }
        
        // Extract resource ID if specified
        if (!audited.resourceId().isEmpty()) {
            String resourceId = extractResourceId(joinPoint, audited.resourceId());
            logBuilder.resourceId(resourceId);
        }
        
        // Log request data if enabled
        if (audited.logRequest()) {
            Map<String, Object> requestData = extractRequestData(joinPoint);
            logBuilder.requestData(requestData);
        }
        
        Object result = null;
        boolean success = true;
        String errorMessage = null;
        
        try {
            result = joinPoint.proceed();
            
            // Log response data if enabled
            if (audited.logResponse() && result != null) {
                logBuilder.responseData(Map.of("result", result.toString()));
            }
            
            return result;
        } catch (Throwable t) {
            success = false;
            errorMessage = t.getMessage();
            throw t;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            
            AuditLog auditLog = logBuilder
                    .success(success)
                    .errorMessage(errorMessage)
                    .durationMs(duration)
                    .statusCode(success ? 200 : 500)
                    .build();
            
            try {
                auditService.log(auditLog);
            } catch (Exception e) {
                log.error("Failed to save audit log: {}", e.getMessage());
            }
        }
    }
    
    private String getModule(ProceedingJoinPoint joinPoint, Audited audited) {
        if (!audited.module().isEmpty()) {
            return audited.module();
        }
        String className = joinPoint.getTarget().getClass().getName();
        if (className.contains(".developer.")) return "developer-workstation";
        if (className.contains(".admin.")) return "admin-center";
        if (className.contains(".portal.")) return "user-portal";
        if (className.contains(".workflow.")) return "workflow-engine";
        return "platform";
    }
    
    private String extractResourceId(ProceedingJoinPoint joinPoint, String expression) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] paramNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();
            
            EvaluationContext context = new StandardEvaluationContext();
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
            
            Object value = parser.parseExpression(expression).getValue(context);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.warn("Failed to extract resource ID: {}", e.getMessage());
            return null;
        }
    }
    
    private Map<String, Object> extractRequestData(ProceedingJoinPoint joinPoint) {
        Map<String, Object> data = new HashMap<>();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        
        for (int i = 0; i < paramNames.length; i++) {
            if (args[i] != null && !isSensitive(paramNames[i])) {
                data.put(paramNames[i], args[i].toString());
            }
        }
        
        return data;
    }
    
    private boolean isSensitive(String paramName) {
        String lower = paramName.toLowerCase();
        return lower.contains("password") || 
               lower.contains("secret") || 
               lower.contains("token") ||
               lower.contains("credential");
    }
}
