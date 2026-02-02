package com.platform.common.security;

import com.platform.common.config.SecurityConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

/**
 * Security Audit Logger
 * 
 * Provides comprehensive security event logging and audit trail maintenance
 * for authentication, authorization, and security-related operations.
 * 
 * Features:
 * - Security event logging without sensitive data exposure
 * - Audit trail maintenance with configurable retention
 * - Security metrics and monitoring
 * - Threat detection and alerting
 * - Compliance reporting support
 * 
 * @author Platform Team
 * @version 1.0
 */
public class SecurityAuditLogger {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityAuditLogger.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("SECURITY_AUDIT");
    
    private final SecurityConfig securityConfig;
    
    // Security metrics tracking
    private final AtomicLong totalSecurityEvents = new AtomicLong(0);
    private final AtomicLong authenticationEvents = new AtomicLong(0);
    private final AtomicLong authorizationEvents = new AtomicLong(0);
    private final AtomicLong securityViolations = new AtomicLong(0);
    private final AtomicLong suspiciousActivities = new AtomicLong(0);
    
    // Event type counters
    private final Map<String, AtomicLong> eventTypeCounts = new ConcurrentHashMap<>();
    
    // Audit trail storage (in-memory for demonstration - should use persistent storage in production)
    private final List<SecurityAuditEvent> auditTrail = new ArrayList<>();
    private final int maxAuditTrailSize = 10000; // Configurable limit
    
    // Sensitive data patterns to mask
    private final Set<String> sensitiveKeys = Set.of(
            "password", "secret", "key", "token", "credential", "pin", "ssn", "credit"
    );
    
    @Autowired
    public SecurityAuditLogger(SecurityConfig securityConfig) {
        this.securityConfig = securityConfig;
        logger.info("Security audit logger initialized with audit logging enabled: {}", 
                   securityConfig.isEnableSecurityAuditLogging());
    }
    
    /**
     * Security Audit Event record
     */
    public static class SecurityAuditEvent {
        private final String eventId;
        private final String eventType;
        private final String description;
        private final LocalDateTime timestamp;
        private final String username;
        private final String ipAddress;
        private final String userAgent;
        private final String resource;
        private final String action;
        private final boolean success;
        private final String riskLevel;
        private final Map<String, String> metadata;
        
        public SecurityAuditEvent(String eventId, String eventType, String description,
                                LocalDateTime timestamp, String username, String ipAddress,
                                String userAgent, String resource, String action,
                                boolean success, String riskLevel, Map<String, String> metadata) {
            this.eventId = eventId;
            this.eventType = eventType;
            this.description = description;
            this.timestamp = timestamp;
            this.username = username;
            this.ipAddress = ipAddress;
            this.userAgent = userAgent;
            this.resource = resource;
            this.action = action;
            this.success = success;
            this.riskLevel = riskLevel;
            this.metadata = metadata != null ? new ConcurrentHashMap<>(metadata) : new ConcurrentHashMap<>();
        }
        
        // Getters
        public String getEventId() { return eventId; }
        public String getEventType() { return eventType; }
        public String getDescription() { return description; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getUsername() { return username; }
        public String getIpAddress() { return ipAddress; }
        public String getUserAgent() { return userAgent; }
        public String getResource() { return resource; }
        public String getAction() { return action; }
        public boolean isSuccess() { return success; }
        public String getRiskLevel() { return riskLevel; }
        public Map<String, String> getMetadata() { return metadata; }
    }
    
    /**
     * Log authentication security event
     * 
     * @param eventType Type of authentication event
     * @param description Event description
     * @param username User identifier
     * @param ipAddress Client IP address
     * @param userAgent Client user agent
     * @param success Whether the authentication was successful
     * @param metadata Additional event metadata
     */
    public void logAuthenticationEvent(String eventType, String description, String username,
                                     String ipAddress, String userAgent, boolean success,
                                     Map<String, String> metadata) {
        if (!securityConfig.isEnableSecurityAuditLogging()) {
            return;
        }
        
        authenticationEvents.incrementAndGet();
        
        String riskLevel = determineRiskLevel(eventType, success, metadata);
        if ("HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel)) {
            suspiciousActivities.incrementAndGet();
        }
        
        SecurityAuditEvent auditEvent = createAuditEvent(
                eventType, description, username, ipAddress, userAgent,
                null, "AUTHENTICATE", success, riskLevel, metadata
        );
        
        logSecurityEvent(auditEvent);
    }
    
    /**
     * Log authorization security event
     * 
     * @param eventType Type of authorization event
     * @param description Event description
     * @param username User identifier
     * @param resource Resource being accessed
     * @param action Action being performed
     * @param success Whether the authorization was successful
     * @param metadata Additional event metadata
     */
    public void logAuthorizationEvent(String eventType, String description, String username,
                                    String resource, String action, boolean success,
                                    Map<String, String> metadata) {
        if (!securityConfig.isEnableSecurityAuditLogging()) {
            return;
        }
        
        authorizationEvents.incrementAndGet();
        
        String riskLevel = determineRiskLevel(eventType, success, metadata);
        if (!success) {
            securityViolations.incrementAndGet();
        }
        
        SecurityAuditEvent auditEvent = createAuditEvent(
                eventType, description, username, null, null,
                resource, action, success, riskLevel, metadata
        );
        
        logSecurityEvent(auditEvent);
    }
    
    /**
     * Log general security event
     * 
     * @param eventType Type of security event
     * @param description Event description
     * @param metadata Additional event metadata
     */
    public void logSecurityEvent(String eventType, String description, Map<String, String> metadata) {
        if (!securityConfig.isEnableSecurityAuditLogging()) {
            return;
        }
        
        String username = metadata != null ? metadata.get("username") : null;
        String ipAddress = metadata != null ? metadata.get("ipAddress") : null;
        String userAgent = metadata != null ? metadata.get("userAgent") : null;
        String resource = metadata != null ? metadata.get("resource") : null;
        String action = metadata != null ? metadata.get("action") : null;
        boolean success = metadata != null ? "true".equals(metadata.get("success")) : false;
        
        String riskLevel = determineRiskLevel(eventType, success, metadata);
        
        SecurityAuditEvent auditEvent = createAuditEvent(
                eventType, description, username, ipAddress, userAgent,
                resource, action, success, riskLevel, metadata
        );
        
        logSecurityEvent(auditEvent);
    }
    
    /**
     * Log security violation event
     * 
     * @param violationType Type of security violation
     * @param description Violation description
     * @param username User identifier
     * @param ipAddress Client IP address
     * @param resource Resource involved
     * @param metadata Additional violation metadata
     */
    public void logSecurityViolation(String violationType, String description, String username,
                                   String ipAddress, String resource, Map<String, String> metadata) {
        if (!securityConfig.isEnableSecurityAuditLogging()) {
            return;
        }
        
        securityViolations.incrementAndGet();
        suspiciousActivities.incrementAndGet();
        
        SecurityAuditEvent auditEvent = createAuditEvent(
                "SECURITY_VIOLATION_" + violationType, description, username, ipAddress, null,
                resource, "VIOLATION", false, "CRITICAL", metadata
        );
        
        logSecurityEvent(auditEvent);
        
        // Additional alerting for security violations
        if (securityConfig.isEnableSecurityEventNotification()) {
            alertSecurityViolation(auditEvent);
        }
    }
    
    /**
     * Create security audit event
     * 
     * @param eventType Event type
     * @param description Event description
     * @param username User identifier
     * @param ipAddress IP address
     * @param userAgent User agent
     * @param resource Resource
     * @param action Action
     * @param success Success flag
     * @param riskLevel Risk level
     * @param metadata Additional metadata
     * @return Security audit event
     */
    private SecurityAuditEvent createAuditEvent(String eventType, String description, String username,
                                              String ipAddress, String userAgent, String resource,
                                              String action, boolean success, String riskLevel,
                                              Map<String, String> metadata) {
        String eventId = generateEventId();
        LocalDateTime timestamp = LocalDateTime.now();
        
        // Sanitize metadata to remove sensitive information
        Map<String, String> sanitizedMetadata = sanitizeMetadata(metadata);
        
        return new SecurityAuditEvent(
                eventId, eventType, description, timestamp, username, ipAddress,
                userAgent, resource, action, success, riskLevel, sanitizedMetadata
        );
    }
    
    /**
     * Log security audit event
     * 
     * @param auditEvent Security audit event to log
     */
    private void logSecurityEvent(SecurityAuditEvent auditEvent) {
        totalSecurityEvents.incrementAndGet();
        eventTypeCounts.computeIfAbsent(auditEvent.getEventType(), k -> new AtomicLong(0)).incrementAndGet();
        
        // Add to audit trail
        synchronized (auditTrail) {
            auditTrail.add(auditEvent);
            
            // Maintain audit trail size limit
            while (auditTrail.size() > maxAuditTrailSize) {
                auditTrail.remove(0);
            }
        }
        
        try {
            // Set MDC context for structured logging
            MDC.put("audit.eventId", auditEvent.getEventId());
            MDC.put("audit.eventType", auditEvent.getEventType());
            MDC.put("audit.timestamp", auditEvent.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            MDC.put("audit.username", auditEvent.getUsername() != null ? auditEvent.getUsername() : "anonymous");
            MDC.put("audit.ipAddress", auditEvent.getIpAddress() != null ? auditEvent.getIpAddress() : "unknown");
            MDC.put("audit.resource", auditEvent.getResource() != null ? auditEvent.getResource() : "none");
            MDC.put("audit.action", auditEvent.getAction() != null ? auditEvent.getAction() : "none");
            MDC.put("audit.success", String.valueOf(auditEvent.isSuccess()));
            MDC.put("audit.riskLevel", auditEvent.getRiskLevel());
            
            // Add metadata to MDC
            auditEvent.getMetadata().forEach((key, value) -> MDC.put("metadata." + key, value));
            
            // Log based on risk level and success
            if ("CRITICAL".equals(auditEvent.getRiskLevel()) || "HIGH".equals(auditEvent.getRiskLevel())) {
                auditLogger.error("SECURITY_EVENT: {} - {} [Risk: {}]", 
                                auditEvent.getEventType(), auditEvent.getDescription(), auditEvent.getRiskLevel());
            } else if (!auditEvent.isSuccess()) {
                auditLogger.warn("SECURITY_EVENT: {} - {} [Risk: {}]", 
                               auditEvent.getEventType(), auditEvent.getDescription(), auditEvent.getRiskLevel());
            } else {
                auditLogger.info("SECURITY_EVENT: {} - {} [Risk: {}]", 
                               auditEvent.getEventType(), auditEvent.getDescription(), auditEvent.getRiskLevel());
            }
            
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Determine risk level based on event characteristics
     * 
     * @param eventType Event type
     * @param success Success flag
     * @param metadata Event metadata
     * @return Risk level (LOW, MEDIUM, HIGH, CRITICAL)
     */
    private String determineRiskLevel(String eventType, boolean success, Map<String, String> metadata) {
        // Critical risk events
        if (eventType.contains("HIJACKING") || eventType.contains("INJECTION") || 
            eventType.contains("VIOLATION") || eventType.contains("BREACH")) {
            return "CRITICAL";
        }
        
        // High risk events
        if (!success && (eventType.contains("AUTHENTICATION") || eventType.contains("AUTHORIZATION"))) {
            return "HIGH";
        }
        
        if (eventType.contains("LOCKOUT") || eventType.contains("BLOCKED") || eventType.contains("FAILED")) {
            return "HIGH";
        }
        
        // Medium risk events
        if (eventType.contains("SESSION") && !success) {
            return "MEDIUM";
        }
        
        if (metadata != null && metadata.containsKey("failedAttempts")) {
            try {
                int attempts = Integer.parseInt(metadata.get("failedAttempts"));
                if (attempts >= 3) {
                    return "MEDIUM";
                }
            } catch (NumberFormatException e) {
                // Ignore parsing errors
            }
        }
        
        // Default to low risk
        return "LOW";
    }
    
    /**
     * Sanitize metadata to remove sensitive information
     * 
     * @param metadata Original metadata
     * @return Sanitized metadata
     */
    private Map<String, String> sanitizeMetadata(Map<String, String> metadata) {
        if (metadata == null) {
            return new ConcurrentHashMap<>();
        }
        
        Map<String, String> sanitized = new ConcurrentHashMap<>();
        
        metadata.forEach((key, value) -> {
            if (isSensitiveKey(key)) {
                sanitized.put(key, maskSensitiveValue(value));
            } else {
                sanitized.put(key, value);
            }
        });
        
        return sanitized;
    }
    
    /**
     * Check if a key contains sensitive information
     * 
     * @param key Key to check
     * @return true if key is sensitive
     */
    private boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        
        String lowerKey = key.toLowerCase();
        return sensitiveKeys.stream().anyMatch(lowerKey::contains);
    }
    
    /**
     * Mask sensitive value for logging
     * 
     * @param value Value to mask
     * @return Masked value
     */
    private String maskSensitiveValue(String value) {
        if (value == null || value.isEmpty()) {
            return "[EMPTY]";
        }
        
        if (value.length() <= 4) {
            return "[MASKED]";
        }
        
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }
    
    /**
     * Generate unique event ID
     * 
     * @return Unique event identifier
     */
    private String generateEventId() {
        return "SEC_" + System.currentTimeMillis() + "_" + 
               String.format("%06d", (int) (Math.random() * 1000000));
    }
    
    /**
     * Alert security violation (placeholder for notification system)
     * 
     * @param auditEvent Security audit event
     */
    private void alertSecurityViolation(SecurityAuditEvent auditEvent) {
        // In a real implementation, this would integrate with:
        // - Email notification system
        // - SIEM systems
        // - Security monitoring dashboards
        // - Incident response systems
        
        logger.error("SECURITY ALERT: {} - {} [User: {}, IP: {}, Risk: {}]",
                    auditEvent.getEventType(), auditEvent.getDescription(),
                    auditEvent.getUsername(), auditEvent.getIpAddress(), auditEvent.getRiskLevel());
    }
    
    /**
     * Get security audit metrics
     * 
     * @return Security metrics
     */
    public Map<String, Object> getSecurityMetrics() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();
        metrics.put("totalSecurityEvents", totalSecurityEvents.get());
        metrics.put("authenticationEvents", authenticationEvents.get());
        metrics.put("authorizationEvents", authorizationEvents.get());
        metrics.put("securityViolations", securityViolations.get());
        metrics.put("suspiciousActivities", suspiciousActivities.get());
        metrics.put("eventTypeCounts", new ConcurrentHashMap<>(eventTypeCounts));
        metrics.put("auditTrailSize", auditTrail.size());
        metrics.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return metrics;
    }
    
    /**
     * Get audit trail events
     * 
     * @param limit Maximum number of events to return
     * @return List of recent audit events
     */
    public List<SecurityAuditEvent> getAuditTrail(int limit) {
        synchronized (auditTrail) {
            int size = auditTrail.size();
            int fromIndex = Math.max(0, size - limit);
            return new ArrayList<>(auditTrail.subList(fromIndex, size));
        }
    }
    
    /**
     * Search audit trail by criteria
     * 
     * @param username User to search for (optional)
     * @param eventType Event type to search for (optional)
     * @param fromTime Start time for search (optional)
     * @param toTime End time for search (optional)
     * @return List of matching audit events
     */
    public List<SecurityAuditEvent> searchAuditTrail(String username, String eventType,
                                                    LocalDateTime fromTime, LocalDateTime toTime) {
        synchronized (auditTrail) {
            return auditTrail.stream()
                    .filter(event -> username == null || username.equals(event.getUsername()))
                    .filter(event -> eventType == null || eventType.equals(event.getEventType()))
                    .filter(event -> fromTime == null || event.getTimestamp().isAfter(fromTime))
                    .filter(event -> toTime == null || event.getTimestamp().isBefore(toTime))
                    .toList();
        }
    }
    
    /**
     * Clear audit trail (for testing or maintenance)
     */
    public void clearAuditTrail() {
        synchronized (auditTrail) {
            auditTrail.clear();
        }
        
        auditLogger.warn("Security audit trail cleared");
    }
    
    /**
     * Reset security metrics
     */
    public void resetMetrics() {
        totalSecurityEvents.set(0);
        authenticationEvents.set(0);
        authorizationEvents.set(0);
        securityViolations.set(0);
        suspiciousActivities.set(0);
        eventTypeCounts.clear();
        
        auditLogger.info("Security audit metrics reset");
    }
}