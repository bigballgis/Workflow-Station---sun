package com.admin.audit;

import lombok.Data;

/**
 * ThreadLocal holder for current request's audit context.
 * Populated by AuditRequestFilter at the start of each HTTP request.
 */
public class AuditContextHolder {

    private static final ThreadLocal<AuditContext> CTX = new ThreadLocal<>();

    public static void set(AuditContext ctx) {
        CTX.set(ctx);
    }

    public static AuditContext get() {
        return CTX.get();
    }

    public static void clear() {
        CTX.remove();
    }

    @Data
    public static class AuditContext {
        private String userId;
        private String userName;
        private String ipAddress;
        private String userAgent;
        private String requestMethod;
        private String requestPath;
    }
}
