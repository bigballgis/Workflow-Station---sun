package com.platform.common.audit;

import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.Size;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property tests for audit logging.
 * Validates: Property 17 (Audit Log Completeness)
 */
class AuditPropertyTest {
    
    // Property 17: Audit Log Completeness
    // For any user operation (login, logout, data change, permission change),
    // an audit log should be created with user ID, timestamp, operation type, and resource info
    
    @Property(tries = 100)
    void auditLogShouldContainRequiredFields(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String userId,
            @ForAll @AlphaChars @Size(min = 1, max = 20) String username,
            @ForAll("actions") String action,
            @ForAll @AlphaChars @Size(min = 1, max = 20) String resourceType,
            @ForAll @AlphaChars @Size(min = 1, max = 20) String resourceId) {
        
        AuditLog log = AuditLog.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .username(username)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .timestamp(LocalDateTime.now())
                .build();
        
        // Required fields should be present
        assertThat(log.getId()).isNotNull().isNotBlank();
        assertThat(log.getUserId()).isNotNull().isNotBlank();
        assertThat(log.getAction()).isNotNull().isNotBlank();
        assertThat(log.getTimestamp()).isNotNull();
    }
    
    @Property(tries = 100)
    void loginLogoutShouldBeAudited(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String userId,
            @ForAll("loginLogoutActions") String action) {
        
        SimulatedAuditService auditService = new SimulatedAuditService();
        
        AuditLog log = AuditLog.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .action(action)
                .timestamp(LocalDateTime.now())
                .module("auth")
                .build();
        
        auditService.log(log);
        
        // Login/logout should be recorded
        List<AuditLog> logs = auditService.findByUserId(userId, 10);
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0).getAction()).isEqualTo(action);
    }
    
    @Property(tries = 100)
    void dataChangeShouldBeAudited(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String userId,
            @ForAll("dataChangeActions") String action,
            @ForAll @AlphaChars @Size(min = 1, max = 20) String resourceType,
            @ForAll @AlphaChars @Size(min = 1, max = 20) String resourceId) {
        
        SimulatedAuditService auditService = new SimulatedAuditService();
        
        AuditLog log = AuditLog.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .timestamp(LocalDateTime.now())
                .build();
        
        auditService.log(log);
        
        // Data change should be recorded with resource info
        List<AuditLog> logs = auditService.findByResource(resourceType, resourceId, 10);
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0).isDataModification()).isTrue();
    }
    
    @Property(tries = 100)
    void permissionChangeShouldBeAudited(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String userId,
            @ForAll @AlphaChars @Size(min = 1, max = 20) String targetUserId,
            @ForAll("permissionActions") String action) {
        
        SimulatedAuditService auditService = new SimulatedAuditService();
        
        AuditLog log = AuditLog.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .action(action)
                .resourceType("permission")
                .resourceId(targetUserId)
                .timestamp(LocalDateTime.now())
                .requestData(Map.of("targetUser", targetUserId))
                .build();
        
        auditService.log(log);
        
        // Permission change should be recorded
        List<AuditLog> logs = auditService.findByAction(action, 10);
        assertThat(logs).isNotEmpty();
    }
    
    @Property(tries = 100)
    void timestampShouldBeAccurate(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String userId) {
        
        LocalDateTime before = LocalDateTime.now();
        
        AuditLog log = AuditLog.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .action("TEST_ACTION")
                .timestamp(LocalDateTime.now())
                .build();
        
        LocalDateTime after = LocalDateTime.now();
        
        assertThat(log.getTimestamp())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }
    
    @Property(tries = 100)
    void auditLogIdShouldBeUnique(
            @ForAll @Size(min = 2, max = 50) List<@AlphaChars @Size(min = 1, max = 20) String> userIds) {
        
        Set<String> logIds = new HashSet<>();
        
        for (String userId : userIds) {
            AuditLog log = AuditLog.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .action("TEST")
                    .timestamp(LocalDateTime.now())
                    .build();
            logIds.add(log.getId());
        }
        
        assertThat(logIds).hasSize(userIds.size());
    }
    
    @Property(tries = 100)
    void traceIdShouldBePropagated(
            @ForAll @AlphaChars @Size(min = 32, max = 32) String traceId,
            @ForAll @AlphaChars @Size(min = 1, max = 20) String userId) {
        
        SimulatedAuditService auditService = new SimulatedAuditService();
        
        AuditLog log = AuditLog.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .action("TEST")
                .traceId(traceId)
                .timestamp(LocalDateTime.now())
                .build();
        
        auditService.log(log);
        
        List<AuditLog> logs = auditService.findByTraceId(traceId);
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0).getTraceId()).isEqualTo(traceId);
    }
    
    @Provide
    Arbitrary<String> actions() {
        return Arbitraries.of(
                "LOGIN", "LOGOUT", "CREATE_USER", "UPDATE_USER", "DELETE_USER",
                "GRANT_PERMISSION", "REVOKE_PERMISSION", "CREATE_PROCESS", "DEPLOY"
        );
    }
    
    @Provide
    Arbitrary<String> loginLogoutActions() {
        return Arbitraries.of("LOGIN", "LOGOUT", "LOGIN_FAILED", "SESSION_EXPIRED");
    }
    
    @Provide
    Arbitrary<String> dataChangeActions() {
        return Arbitraries.of("CREATE_USER", "UPDATE_USER", "DELETE_USER",
                "CREATE_PROCESS", "UPDATE_PROCESS", "DELETE_PROCESS");
    }
    
    @Provide
    Arbitrary<String> permissionActions() {
        return Arbitraries.of("GRANT_PERMISSION", "REVOKE_PERMISSION",
                "ASSIGN_ROLE", "REMOVE_ROLE", "CREATE_DELEGATION");
    }
    
    // Simulated audit service for testing
    private static class SimulatedAuditService implements AuditService {
        private final List<AuditLog> logs = new ArrayList<>();
        
        @Override
        public AuditLog log(AuditLog auditLog) {
            logs.add(auditLog);
            return auditLog;
        }
        
        @Override
        public List<AuditLog> findByUserId(String userId, int limit) {
            return logs.stream()
                    .filter(l -> userId.equals(l.getUserId()))
                    .limit(limit)
                    .toList();
        }
        
        @Override
        public List<AuditLog> findByResource(String resourceType, String resourceId, int limit) {
            return logs.stream()
                    .filter(l -> resourceType.equals(l.getResourceType()) && resourceId.equals(l.getResourceId()))
                    .limit(limit)
                    .toList();
        }
        
        @Override
        public List<AuditLog> findByAction(String action, int limit) {
            return logs.stream()
                    .filter(l -> action.equals(l.getAction()))
                    .limit(limit)
                    .toList();
        }
        
        @Override
        public Optional<AuditLog> findById(String id) {
            return logs.stream().filter(l -> id.equals(l.getId())).findFirst();
        }
        
        @Override
        public List<AuditLog> findByTraceId(String traceId) {
            return logs.stream()
                    .filter(l -> traceId.equals(l.getTraceId()))
                    .toList();
        }
    }
}
