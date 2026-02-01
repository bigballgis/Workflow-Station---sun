package com.platform.common.constant;

/**
 * Platform-wide constants.
 */
public final class PlatformConstants {
    
    private PlatformConstants() {
        // Constants class
    }
    
    // HTTP Headers
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String HEADER_BEARER_PREFIX = "Bearer ";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_LANGUAGE = "Accept-Language";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    
    // JWT Claims
    public static final String JWT_CLAIM_USER_ID = "userId";
    public static final String JWT_CLAIM_USERNAME = "username";
    public static final String JWT_CLAIM_ROLES = "roles";
    public static final String JWT_CLAIM_PERMISSIONS = "permissions";
    public static final String JWT_CLAIM_LANGUAGE = "language";
    
    // Cache Keys
    public static final String CACHE_PREFIX_USER = "user:";
    public static final String CACHE_PREFIX_PERMISSION = "permission:";
    public static final String CACHE_PREFIX_SESSION = "session:";
    public static final String CACHE_PREFIX_RATE_LIMIT = "rate_limit:";
    public static final String CACHE_PREFIX_LOCK = "lock:";
    
    // Default Values
    public static final String DEFAULT_LANGUAGE = "en";
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_TOKEN_EXPIRY_MINUTES = 60;
    public static final int DEFAULT_REFRESH_TOKEN_EXPIRY_DAYS = 7;
    
    // Rate Limiting
    public static final int DEFAULT_RATE_LIMIT_REQUESTS = 100;
    public static final int DEFAULT_RATE_LIMIT_WINDOW_SECONDS = 60;
    
    // Audit
    public static final String AUDIT_ACTION_CREATE = "CREATE";
    public static final String AUDIT_ACTION_UPDATE = "UPDATE";
    public static final String AUDIT_ACTION_DELETE = "DELETE";
    public static final String AUDIT_ACTION_LOGIN = "LOGIN";
    public static final String AUDIT_ACTION_LOGOUT = "LOGOUT";
    public static final String AUDIT_ACTION_PERMISSION_CHANGE = "PERMISSION_CHANGE";
    
    // Kafka Topics
    public static final String TOPIC_PROCESS_EVENTS = "platform.process.events";
    public static final String TOPIC_TASK_EVENTS = "platform.task.events";
    public static final String TOPIC_PERMISSION_EVENTS = "platform.permission.events";
    public static final String TOPIC_DEPLOYMENT_EVENTS = "platform.deployment.events";
    public static final String TOPIC_AUDIT_EVENTS = "platform.audit.events";
}
