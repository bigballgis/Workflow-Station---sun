package com.workflow.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.AuditLogQueryRequest;
import com.workflow.dto.AuditLogStatisticsResult;
import com.workflow.entity.AuditLog;
import com.workflow.enums.AuditOperationType;
import com.workflow.enums.AuditResourceType;
import com.workflow.enums.AuditRiskLevel;
import com.workflow.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.ArrayList;

/**
 * 审计管理组件单元测试
 * 测试审计日志的记录、查询、分析和管理功能
 */
@ExtendWith(MockitoExtension.class)
class AuditManagerComponentTest {
    
    @Mock
    private AuditLogRepository auditLogRepository;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @InjectMocks
    private AuditManagerComponent auditManagerComponent;
    
    private AuditLog sampleAuditLog;
    private LocalDateTime testTime;
    
    @BeforeEach
    void setUp() {
        testTime = LocalDateTime.now();
        sampleAuditLog = createSampleAuditLog();
    }
    
    @Test
    void testRecordAuditLog_Success() throws Exception {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(sampleAuditLog);
        
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("testKey", "testValue");
        
        // When
        String auditId = auditManagerComponent.recordAuditLog(
            AuditOperationType.START_PROCESS,
            AuditResourceType.PROCESS_INSTANCE,
            "process-123",
            "Test Process",
            "user-123",
            "启动测试流程",
            null,
            null,
            "SUCCESS",
            null,
            "192.168.1.1",
            "Mozilla/5.0",
            "session-123",
            "request-123",
            1000L,
            "tenant-123",
            contextData
        );
        
        // Then
        assertThat(auditId).isNotNull();
        verify(auditLogRepository).save(any(AuditLog.class));
    }
    
    @Test
    void testRecordAuditLog_SimplifiedMethod() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(sampleAuditLog);
        
        // When
        String auditId = auditManagerComponent.recordAuditLog(
            AuditOperationType.COMPLETE_TASK,
            AuditResourceType.TASK,
            "task-123",
            "user-123",
            "SUCCESS"
        );
        
        // Then
        assertThat(auditId).isNotNull();
        verify(auditLogRepository).save(argThat(auditLog -> 
            auditLog.getOperationType().equals("COMPLETE_TASK") &&
            auditLog.getResourceType().equals("TASK") &&
            auditLog.getResourceId().equals("task-123") &&
            auditLog.getUserId().equals("user-123") &&
            auditLog.getOperationResult().equals("SUCCESS")
        ));
    }
    
    @Test
    void testRecordAuditLog_WithSensitiveData() throws Exception {
        // Given
        Map<String, Object> sensitiveData = new HashMap<>();
        sensitiveData.put("password", "secret123");
        sensitiveData.put("email", "user@example.com");
        sensitiveData.put("normalField", "normalValue");
        
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"password\":\"secret123\",\"email\":\"user@example.com\",\"normalField\":\"normalValue\"}");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(sensitiveData);
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(sampleAuditLog);
        
        // When
        String auditId = auditManagerComponent.recordAuditLog(
            AuditOperationType.UPDATE_USER,
            AuditResourceType.USER,
            "user-123",
            "Test User",
            "admin-123",
            "更新用户信息",
            sensitiveData,
            null,
            "SUCCESS",
            null,
            "192.168.1.1",
            "Mozilla/5.0",
            "session-123",
            "request-123",
            500L,
            "tenant-123",
            null
        );
        
        // Then
        assertThat(auditId).isNotNull();
        verify(auditLogRepository).save(any(AuditLog.class));
    }
    
    @Test
    void testQueryAuditLogs_WithComplexConditions() {
        // Given
        AuditLogQueryRequest request = new AuditLogQueryRequest();
        request.setUserId("user-123");
        request.setOperationType("START_PROCESS");
        request.setStartTime(testTime.minusDays(1));
        request.setEndTime(testTime);
        request.setPage(0);
        request.setSize(10);
        
        List<AuditLog> auditLogs = Arrays.asList(sampleAuditLog);
        Page<AuditLog> auditLogPage = new PageImpl<>(auditLogs);
        
        when(auditLogRepository.findByComplexConditions(
            eq("user-123"), eq("START_PROCESS"), isNull(), isNull(),
            any(LocalDateTime.class), any(LocalDateTime.class),
            isNull(), isNull(), isNull(), any(Pageable.class)
        )).thenReturn(auditLogPage);
        
        // When
        Page<AuditLog> result = auditManagerComponent.queryAuditLogs(request);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(sampleAuditLog.getId());
    }
    
    @Test
    void testQueryAuditLogs_WithKeywordSearch() {
        // Given
        AuditLogQueryRequest request = new AuditLogQueryRequest();
        request.setKeyword("test process");
        request.setPage(0);
        request.setSize(10);
        
        List<AuditLog> auditLogs = Arrays.asList(sampleAuditLog);
        Page<AuditLog> auditLogPage = new PageImpl<>(auditLogs);
        
        when(auditLogRepository.searchByKeyword(eq("test process"), any(Pageable.class)))
            .thenReturn(auditLogPage);
        
        // When
        Page<AuditLog> result = auditManagerComponent.queryAuditLogs(request);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(auditLogRepository).searchByKeyword(eq("test process"), any(Pageable.class));
    }
    
    @Test
    void testGetAuditStatistics_Success() {
        // Given
        LocalDateTime startTime = testTime.minusDays(7);
        LocalDateTime endTime = testTime;
        
        when(auditLogRepository.countByTimestampBetween(startTime, endTime)).thenReturn(100L);
        
        List<Object[]> operationTypeStats = Arrays.asList(
            new Object[]{"START_PROCESS", 50L},
            new Object[]{"COMPLETE_TASK", 30L},
            new Object[]{"ASSIGN_TASK", 20L}
        );
        when(auditLogRepository.countByOperationTypeAndTimestampBetween(startTime, endTime))
            .thenReturn(operationTypeStats);
        
        List<Object[]> userStats = Arrays.asList(
            new Object[]{"user-1", 40L},
            new Object[]{"user-2", 35L},
            new Object[]{"user-3", 25L}
        );
        when(auditLogRepository.countByUserIdAndTimestampBetween(startTime, endTime))
            .thenReturn(userStats);
        
        List<Object[]> riskLevelStats = Arrays.asList(
            new Object[]{"LOW", 70L},
            new Object[]{"MEDIUM", 20L},
            new Object[]{"HIGH", 10L}
        );
        when(auditLogRepository.countByRiskLevelAndTimestampBetween(startTime, endTime))
            .thenReturn(riskLevelStats);
        
        List<Object[]> activeIps = Arrays.asList(
            new Object[]{"192.168.1.1", 150L},
            new Object[]{"192.168.1.2", 120L}
        );
        when(auditLogRepository.findActiveIpAddresses(startTime, endTime, 100))
            .thenReturn(activeIps);
        
        Page<AuditLog> failedOps = new PageImpl<>(Collections.emptyList());
        when(auditLogRepository.findFailedOperations(eq(startTime), eq(endTime), any(Pageable.class)))
            .thenReturn(failedOps);
        
        // When
        AuditLogStatisticsResult result = auditManagerComponent.getAuditStatistics(startTime, endTime, null);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotalOperations()).isEqualTo(100L);
        assertThat(result.getSuccessfulOperations()).isEqualTo(100L);
        assertThat(result.getFailedOperations()).isEqualTo(0L);
        assertThat(result.getSuccessRate()).isEqualTo(100.0);
        
        assertThat(result.getOperationTypeStats()).hasSize(3);
        assertThat(result.getOperationTypeStats().get("START_PROCESS")).isEqualTo(50L);
        
        assertThat(result.getUserStats()).hasSize(3);
        assertThat(result.getUserStats().get("user-1")).isEqualTo(40L);
        
        assertThat(result.getRiskLevelStats()).hasSize(3);
        assertThat(result.getRiskLevelStats().get("LOW")).isEqualTo(70L);
        
        assertThat(result.getActiveIpAddresses()).hasSize(2);
        assertThat(result.getActiveIpAddresses().get(0).getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(result.getActiveIpAddresses().get(0).getOperationCount()).isEqualTo(150L);
    }
    
    @Test
    void testGetUserOperationTrace_Success() {
        // Given
        String sessionId = "session-123";
        List<AuditLog> auditLogs = Arrays.asList(sampleAuditLog);
        
        when(auditLogRepository.findBySessionIdOrderByTimestampDesc(sessionId))
            .thenReturn(auditLogs);
        
        // When
        List<AuditLog> result = auditManagerComponent.getUserOperationTrace(sessionId);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSessionId()).isEqualTo(sessionId);
    }
    
    @Test
    void testGetRelatedOperations_Success() {
        // Given
        String requestId = "request-123";
        List<AuditLog> auditLogs = Arrays.asList(sampleAuditLog);
        
        when(auditLogRepository.findByRequestIdOrderByTimestampDesc(requestId))
            .thenReturn(auditLogs);
        
        // When
        List<AuditLog> result = auditManagerComponent.getRelatedOperations(requestId);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRequestId()).isEqualTo(requestId);
    }
    
    @Test
    void testCleanupExpiredAuditLogs_Success() {
        // Given
        int retentionDays = 365;
        
        // When
        auditManagerComponent.cleanupExpiredAuditLogs(retentionDays);
        
        // Then
        verify(auditLogRepository).deleteByTimestampBefore(any(LocalDateTime.class));
    }
    
    @Test
    void testRecordAuditLog_WithException() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenThrow(new RuntimeException("Database error"));
        
        // When & Then
        assertThatThrownBy(() -> auditManagerComponent.recordAuditLog(
            AuditOperationType.START_PROCESS,
            AuditResourceType.PROCESS_INSTANCE,
            "process-123",
            "user-123",
            "SUCCESS"
        )).isInstanceOf(RuntimeException.class)
          .hasMessageContaining("记录审计日志失败");
    }
    
    @Test
    void testQueryAuditLogs_WithException() {
        // Given
        AuditLogQueryRequest request = new AuditLogQueryRequest();
        when(auditLogRepository.findByComplexConditions(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)
        )).thenThrow(new RuntimeException("Database error"));
        
        // When & Then
        assertThatThrownBy(() -> auditManagerComponent.queryAuditLogs(request))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("查询审计日志失败");
    }
    
    @Test
    void testRiskLevelEvaluation() {
        // Test different risk level evaluations
        assertThat(AuditRiskLevel.evaluateRiskLevel(AuditOperationType.DELETE_PROCESS, AuditResourceType.PROCESS_DEFINITION))
            .isEqualTo(AuditRiskLevel.HIGH);
        
        assertThat(AuditRiskLevel.evaluateRiskLevel(AuditOperationType.ASSIGN_ROLE, AuditResourceType.USER))
            .isEqualTo(AuditRiskLevel.HIGH);
        
        assertThat(AuditRiskLevel.evaluateRiskLevel(AuditOperationType.SYSTEM_ERROR, AuditResourceType.SYSTEM))
            .isEqualTo(AuditRiskLevel.CRITICAL);
        
        assertThat(AuditRiskLevel.evaluateRiskLevel(AuditOperationType.DEPLOY_PROCESS, AuditResourceType.PROCESS_DEFINITION))
            .isEqualTo(AuditRiskLevel.MEDIUM);
        
        assertThat(AuditRiskLevel.evaluateRiskLevel(AuditOperationType.COMPLETE_TASK, AuditResourceType.TASK))
            .isEqualTo(AuditRiskLevel.LOW);
    }
    
    @Test
    void testSensitiveOperationDetection() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(sampleAuditLog);
        
        // Test sensitive operations
        auditManagerComponent.recordAuditLog(
            AuditOperationType.DELETE_PROCESS,
            AuditResourceType.PROCESS_DEFINITION,
            "process-123",
            "user-123",
            "SUCCESS"
        );
        
        verify(auditLogRepository).save(argThat(auditLog -> auditLog.getIsSensitive() == true));
        
        reset(auditLogRepository);
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(sampleAuditLog);
        
        // Test non-sensitive operations
        auditManagerComponent.recordAuditLog(
            AuditOperationType.VIEW_STATISTICS,
            AuditResourceType.SYSTEM,
            "stats-123",
            "user-123",
            "SUCCESS"
        );
        
        verify(auditLogRepository).save(argThat(auditLog -> auditLog.getIsSensitive() == false));
    }
    
    // ==================== 数据脱敏功能测试 ====================
    
    @Test
    void testDataMasking_PasswordField() throws Exception {
        // Given - 测试密码字段脱敏
        Map<String, Object> dataWithPassword = new HashMap<>();
        dataWithPassword.put("username", "testuser");
        dataWithPassword.put("password", "secretPassword123");
        dataWithPassword.put("normalField", "normalValue");
        
        when(objectMapper.writeValueAsString(any())).thenReturn(
            "{\"username\":\"testuser\",\"password\":\"secretPassword123\",\"normalField\":\"normalValue\"}");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(dataWithPassword);
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(sampleAuditLog);
        
        // When
        String auditId = auditManagerComponent.recordAuditLog(
            AuditOperationType.CREATE_USER,
            AuditResourceType.USER,
            "user-new",
            "New User",
            "admin-123",
            "创建新用户",
            dataWithPassword,
            null,
            "SUCCESS",
            null,
            "192.168.1.1",
            "Mozilla/5.0",
            "session-123",
            "request-123",
            500L,
            "tenant-123",
            null
        );
        
        // Then
        assertThat(auditId).isNotNull();
        verify(auditLogRepository).save(argThat(auditLog -> {
            // 验证beforeData被设置（脱敏后的数据）
            return auditLog.getBeforeData() != null;
        }));
    }
    
    @Test
    void testDataMasking_EmailField() throws Exception {
        // Given - 测试邮箱字段脱敏
        Map<String, Object> dataWithEmail = new HashMap<>();
        dataWithEmail.put("name", "Test User");
        dataWithEmail.put("email", "user@example.com");
        
        when(objectMapper.writeValueAsString(any())).thenReturn(
            "{\"name\":\"Test User\",\"email\":\"user@example.com\"}");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(dataWithEmail);
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(sampleAuditLog);
        
        // When
        String auditId = auditManagerComponent.recordAuditLog(
            AuditOperationType.UPDATE_USER,
            AuditResourceType.USER,
            "user-123",
            "Test User",
            "admin-123",
            "更新用户邮箱",
            null,
            dataWithEmail,
            "SUCCESS",
            null,
            "192.168.1.1",
            "Mozilla/5.0",
            "session-123",
            "request-123",
            300L,
            "tenant-123",
            null
        );
        
        // Then
        assertThat(auditId).isNotNull();
        verify(auditLogRepository).save(any(AuditLog.class));
    }
    
    @Test
    void testDataMasking_NestedSensitiveData() throws Exception {
        // Given - 测试嵌套敏感数据脱敏
        Map<String, Object> nestedData = new HashMap<>();
        Map<String, Object> credentials = new HashMap<>();
        credentials.put("apiKey", "secret-api-key-12345");
        credentials.put("token", "jwt-token-xyz");
        nestedData.put("credentials", credentials);
        nestedData.put("publicInfo", "public value");
        
        when(objectMapper.writeValueAsString(any())).thenReturn(
            "{\"credentials\":{\"apiKey\":\"secret-api-key-12345\",\"token\":\"jwt-token-xyz\"},\"publicInfo\":\"public value\"}");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(nestedData);
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(sampleAuditLog);
        
        // When
        String auditId = auditManagerComponent.recordAuditLog(
            AuditOperationType.UPDATE_USER,
            AuditResourceType.USER,
            "user-123",
            "Test User",
            "admin-123",
            "更新用户凭证",
            nestedData,
            null,
            "SUCCESS",
            null,
            "192.168.1.1",
            "Mozilla/5.0",
            "session-123",
            "request-123",
            400L,
            "tenant-123",
            null
        );
        
        // Then
        assertThat(auditId).isNotNull();
        verify(auditLogRepository).save(any(AuditLog.class));
    }
    
    // ==================== 权限控制逻辑测试 ====================
    
    @Test
    void testRoleAssignmentIsSensitive() {
        // Given - 角色分配操作应该被标记为敏感
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(sampleAuditLog);
        
        // When
        auditManagerComponent.recordAuditLog(
            AuditOperationType.ASSIGN_ROLE,
            AuditResourceType.USER,
            "user-123",
            "admin-123",
            "SUCCESS"
        );
        
        // Then
        verify(auditLogRepository).save(argThat(auditLog -> 
            auditLog.getIsSensitive() == true &&
            auditLog.getRiskLevel().equals("HIGH")
        ));
    }
    
    @Test
    void testRoleRevocationIsSensitive() {
        // Given - 角色撤销操作应该被标记为敏感
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(sampleAuditLog);
        
        // When
        auditManagerComponent.recordAuditLog(
            AuditOperationType.REVOKE_ROLE,
            AuditResourceType.USER,
            "user-123",
            "admin-123",
            "SUCCESS"
        );
        
        // Then
        verify(auditLogRepository).save(argThat(auditLog -> 
            auditLog.getIsSensitive() == true &&
            auditLog.getRiskLevel().equals("HIGH")
        ));
    }
    
    @Test
    void testDataExportIsSensitive() {
        // Given - 数据导出操作应该被标记为敏感
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(sampleAuditLog);
        
        // When
        auditManagerComponent.recordAuditLog(
            AuditOperationType.EXPORT_DATA,
            AuditResourceType.PROCESS_INSTANCE,
            "export-123",
            "admin-123",
            "SUCCESS"
        );
        
        // Then
        verify(auditLogRepository).save(argThat(auditLog -> 
            auditLog.getIsSensitive() == true
        ));
    }
    
    @Test
    void testUserResourceOperationIsSensitive() {
        // Given - 用户资源操作应该被标记为敏感
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(sampleAuditLog);
        
        // When
        auditManagerComponent.recordAuditLog(
            AuditOperationType.CREATE_USER,
            AuditResourceType.USER,
            "user-new",
            "admin-123",
            "SUCCESS"
        );
        
        // Then
        verify(auditLogRepository).save(argThat(auditLog -> 
            auditLog.getIsSensitive() == true
        ));
    }
    
    // ==================== 操作日志完整记录测试 ====================
    
    @Test
    void testAuditLogContainsAllRequiredFields() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(sampleAuditLog);
        
        // When
        String auditId = auditManagerComponent.recordAuditLog(
            AuditOperationType.START_PROCESS,
            AuditResourceType.PROCESS_INSTANCE,
            "process-123",
            "Test Process",
            "user-123",
            "启动测试流程",
            null,
            null,
            "SUCCESS",
            null,
            "192.168.1.100",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
            "session-abc-123",
            "request-xyz-456",
            1500L,
            "tenant-001",
            null
        );
        
        // Then
        assertThat(auditId).isNotNull();
        verify(auditLogRepository).save(argThat(auditLog -> 
            auditLog.getId() != null &&
            auditLog.getUserId().equals("user-123") &&
            auditLog.getOperationType().equals("START_PROCESS") &&
            auditLog.getResourceType().equals("PROCESS_INSTANCE") &&
            auditLog.getResourceId().equals("process-123") &&
            auditLog.getResourceName().equals("Test Process") &&
            auditLog.getOperationDescription().equals("启动测试流程") &&
            auditLog.getOperationResult().equals("SUCCESS") &&
            auditLog.getIpAddress().equals("192.168.1.100") &&
            auditLog.getUserAgent().equals("Mozilla/5.0 (Windows NT 10.0; Win64; x64)") &&
            auditLog.getSessionId().equals("session-abc-123") &&
            auditLog.getRequestId().equals("request-xyz-456") &&
            auditLog.getDurationMs().equals(1500L) &&
            auditLog.getTenantId().equals("tenant-001") &&
            auditLog.getTimestamp() != null &&
            auditLog.getRiskLevel() != null
        ));
    }
    
    @Test
    void testAuditLogWithFailureResult() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(sampleAuditLog);
        
        // When
        String auditId = auditManagerComponent.recordAuditLog(
            AuditOperationType.DELETE_PROCESS,
            AuditResourceType.PROCESS_DEFINITION,
            "process-def-123",
            "Test Process Definition",
            "user-123",
            "删除流程定义",
            null,
            null,
            "FAILURE",
            "流程定义存在运行中的实例，无法删除",
            "192.168.1.1",
            "Mozilla/5.0",
            "session-123",
            "request-123",
            500L,
            "tenant-123",
            null
        );
        
        // Then
        assertThat(auditId).isNotNull();
        verify(auditLogRepository).save(argThat(auditLog -> 
            auditLog.getOperationResult().equals("FAILURE") &&
            auditLog.getErrorMessage().equals("流程定义存在运行中的实例，无法删除")
        ));
    }
    
    @Test
    void testAuditLogWithContextData() throws Exception {
        // Given
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("processVersion", 3);
        contextData.put("deploymentId", "deploy-123");
        contextData.put("category", "approval");
        
        when(objectMapper.writeValueAsString(any())).thenReturn(
            "{\"processVersion\":3,\"deploymentId\":\"deploy-123\",\"category\":\"approval\"}");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(contextData);
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(sampleAuditLog);
        
        // When
        String auditId = auditManagerComponent.recordAuditLog(
            AuditOperationType.DEPLOY_PROCESS,
            AuditResourceType.PROCESS_DEFINITION,
            "process-def-123",
            "Approval Process",
            "user-123",
            "部署审批流程",
            null,
            null,
            "SUCCESS",
            null,
            "192.168.1.1",
            "Mozilla/5.0",
            "session-123",
            "request-123",
            2000L,
            "tenant-123",
            contextData
        );
        
        // Then
        assertThat(auditId).isNotNull();
        verify(auditLogRepository).save(argThat(auditLog -> 
            auditLog.getContextData() != null
        ));
    }
    
    // ==================== 风险等级评估测试 ====================
    
    @Test
    void testSystemErrorIsCriticalRisk() {
        assertThat(AuditRiskLevel.evaluateRiskLevel(AuditOperationType.SYSTEM_ERROR, AuditResourceType.SYSTEM))
            .isEqualTo(AuditRiskLevel.CRITICAL);
    }
    
    @Test
    void testAccessDeniedIsHighRisk() {
        assertThat(AuditRiskLevel.evaluateRiskLevel(AuditOperationType.ACCESS_DENIED, AuditResourceType.TASK))
            .isEqualTo(AuditRiskLevel.HIGH);
    }
    
    @Test
    void testDeleteTaskIsHighRisk() {
        assertThat(AuditRiskLevel.evaluateRiskLevel(AuditOperationType.DELETE_TASK, AuditResourceType.TASK))
            .isEqualTo(AuditRiskLevel.HIGH);
    }
    
    @Test
    void testImportDataIsMediumRisk() {
        assertThat(AuditRiskLevel.evaluateRiskLevel(AuditOperationType.IMPORT_DATA, AuditResourceType.PROCESS_INSTANCE))
            .isEqualTo(AuditRiskLevel.MEDIUM);
    }
    
    @Test
    void testViewStatisticsIsLowRisk() {
        assertThat(AuditRiskLevel.evaluateRiskLevel(AuditOperationType.VIEW_STATISTICS, AuditResourceType.TASK))
            .isEqualTo(AuditRiskLevel.LOW);
    }
    
    // ==================== 枚举转换测试 ====================
    
    @Test
    void testAuditOperationTypeFromString() {
        assertThat(AuditOperationType.fromString("START_PROCESS")).isEqualTo(AuditOperationType.START_PROCESS);
        assertThat(AuditOperationType.fromString("COMPLETE_TASK")).isEqualTo(AuditOperationType.COMPLETE_TASK);
        assertThat(AuditOperationType.fromString("DELETE_PROCESS")).isEqualTo(AuditOperationType.DELETE_PROCESS);
    }
    
    @Test
    void testAuditOperationTypeFromString_Invalid() {
        assertThatThrownBy(() -> AuditOperationType.fromString("INVALID_TYPE"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("未知的操作类型");
    }
    
    @Test
    void testAuditRiskLevelFromString() {
        assertThat(AuditRiskLevel.fromString("LOW")).isEqualTo(AuditRiskLevel.LOW);
        assertThat(AuditRiskLevel.fromString("MEDIUM")).isEqualTo(AuditRiskLevel.MEDIUM);
        assertThat(AuditRiskLevel.fromString("HIGH")).isEqualTo(AuditRiskLevel.HIGH);
        assertThat(AuditRiskLevel.fromString("CRITICAL")).isEqualTo(AuditRiskLevel.CRITICAL);
    }
    
    @Test
    void testAuditRiskLevelFromString_Invalid() {
        assertThatThrownBy(() -> AuditRiskLevel.fromString("INVALID_LEVEL"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("未知的风险等级");
    }
    
    // ==================== 查询边界条件测试 ====================
    
    @Test
    void testQueryAuditLogs_EmptyResult() {
        // Given
        AuditLogQueryRequest request = new AuditLogQueryRequest();
        request.setUserId("non-existent-user");
        request.setPage(0);
        request.setSize(10);
        
        Page<AuditLog> emptyPage = new PageImpl<>(Collections.emptyList());
        when(auditLogRepository.findByComplexConditions(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)
        )).thenReturn(emptyPage);
        
        // When
        Page<AuditLog> result = auditManagerComponent.queryAuditLogs(request);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }
    
    @Test
    void testGetUserOperationTrace_EmptyResult() {
        // Given
        String sessionId = "non-existent-session";
        when(auditLogRepository.findBySessionIdOrderByTimestampDesc(sessionId))
            .thenReturn(Collections.emptyList());
        
        // When
        List<AuditLog> result = auditManagerComponent.getUserOperationTrace(sessionId);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }
    
    @Test
    void testGetRelatedOperations_EmptyResult() {
        // Given
        String requestId = "non-existent-request";
        when(auditLogRepository.findByRequestIdOrderByTimestampDesc(requestId))
            .thenReturn(Collections.emptyList());
        
        // When
        List<AuditLog> result = auditManagerComponent.getRelatedOperations(requestId);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }
    
    @Test
    void testGetAuditStatistics_WithFailedOperations() {
        // Given
        LocalDateTime startTime = testTime.minusDays(7);
        LocalDateTime endTime = testTime;
        
        when(auditLogRepository.countByTimestampBetween(startTime, endTime)).thenReturn(100L);
        when(auditLogRepository.countByOperationTypeAndTimestampBetween(startTime, endTime))
            .thenReturn(Collections.emptyList());
        when(auditLogRepository.countByUserIdAndTimestampBetween(startTime, endTime))
            .thenReturn(Collections.emptyList());
        when(auditLogRepository.countByRiskLevelAndTimestampBetween(startTime, endTime))
            .thenReturn(Collections.emptyList());
        when(auditLogRepository.findActiveIpAddresses(startTime, endTime, 100))
            .thenReturn(Collections.emptyList());
        
        // 模拟有20个失败操作
        List<AuditLog> failedLogs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            AuditLog log = new AuditLog();
            log.setId("failed-" + i);
            log.setOperationResult("FAILURE");
            failedLogs.add(log);
        }
        Page<AuditLog> failedOps = new PageImpl<>(failedLogs, PageRequest.of(0, 1), 20);
        when(auditLogRepository.findFailedOperations(eq(startTime), eq(endTime), any(Pageable.class)))
            .thenReturn(failedOps);
        
        // When
        AuditLogStatisticsResult result = auditManagerComponent.getAuditStatistics(startTime, endTime, null);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotalOperations()).isEqualTo(100L);
        assertThat(result.getFailedOperations()).isEqualTo(20L);
        assertThat(result.getSuccessfulOperations()).isEqualTo(80L);
        assertThat(result.getSuccessRate()).isEqualTo(80.0);
    }
    
    private AuditLog createSampleAuditLog() {
        AuditLog auditLog = new AuditLog();
        auditLog.setId("audit-123");
        auditLog.setUserId("user-123");
        auditLog.setOperationType("START_PROCESS");
        auditLog.setResourceType("PROCESS_INSTANCE");
        auditLog.setResourceId("process-123");
        auditLog.setResourceName("Test Process");
        auditLog.setOperationDescription("启动测试流程");
        auditLog.setOperationResult("SUCCESS");
        auditLog.setTimestamp(testTime);
        auditLog.setIpAddress("192.168.1.1");
        auditLog.setUserAgent("Mozilla/5.0");
        auditLog.setSessionId("session-123");
        auditLog.setRequestId("request-123");
        auditLog.setDurationMs(1000L);
        auditLog.setTenantId("tenant-123");
        auditLog.setRiskLevel("LOW");
        auditLog.setIsSensitive(false);
        return auditLog;
    }
}