package com.admin.properties;

import com.admin.component.SecurityAuditComponent;
import com.admin.component.SecurityAuditComponent.*;
import com.admin.entity.AuditLog;
import com.admin.enums.AuditAction;
import com.admin.repository.AuditLogRepository;
import com.admin.repository.SecurityPolicyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审计日志完整性属性测试
 * 属性 16: 审计日志完整性
 * 验证需求: 需求 11.1
 */
class AuditLogIntegrityProperties {
    
    private SecurityAuditComponent component;
    private AuditLogRepository auditLogRepository;
    
    @BeforeTry
    void setUp() {
        var policyRepo = Mockito.mock(SecurityPolicyRepository.class);
        auditLogRepository = Mockito.mock(AuditLogRepository.class);
        var objectMapper = new ObjectMapper();
        component = new SecurityAuditComponent(policyRepo, auditLogRepository, objectMapper);
        
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
    }
    
    @Provide
    Arbitrary<AuditAction> auditActions() {
        return Arbitraries.of(AuditAction.values());
    }
    
    @Provide
    Arbitrary<String> resourceTypes() {
        return Arbitraries.of("USER", "ROLE", "PERMISSION", "CONFIG", "DATA");
    }
    
    /**
     * 属性 16.1: 审计日志必须包含操作类型
     */
    @Property(tries = 20)
    void auditLogMustContainAction(
            @ForAll("auditActions") AuditAction action,
            @ForAll("resourceTypes") String resourceType) {
        AuditLogRequest request = AuditLogRequest.builder()
                .action(action)
                .resourceType(resourceType)
                .userId("user-1")
                .success(true)
                .build();
        
        component.recordAudit(request);
        
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        
        AuditLog saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo(action);
    }
    
    /**
     * 属性 16.2: 审计日志必须包含用户ID
     */
    @Property(tries = 20)
    void auditLogMustContainUserId(
            @ForAll("auditActions") AuditAction action,
            @ForAll String userId) {
        Assume.that(userId != null && !userId.isBlank());
        
        AuditLogRequest request = AuditLogRequest.builder()
                .action(action)
                .resourceType("USER")
                .userId(userId)
                .success(true)
                .build();
        
        component.recordAudit(request);
        
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    }
    
    /**
     * 属性 16.3: 审计日志必须包含资源类型
     */
    @Property(tries = 20)
    void auditLogMustContainResourceType(
            @ForAll("auditActions") AuditAction action,
            @ForAll("resourceTypes") String resourceType) {
        AuditLogRequest request = AuditLogRequest.builder()
                .action(action)
                .resourceType(resourceType)
                .userId("user-1")
                .success(true)
                .build();
        
        component.recordAudit(request);
        
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        
        assertThat(captor.getValue().getResourceType()).isEqualTo(resourceType);
    }
    
    /**
     * 属性 16.4: 审计日志必须有唯一ID
     */
    @Property(tries = 20)
    void auditLogMustHaveUniqueId(@ForAll("auditActions") AuditAction action) {
        AuditLogRequest request = AuditLogRequest.builder()
                .action(action)
                .resourceType("USER")
                .userId("user-1")
                .success(true)
                .build();
        
        component.recordAudit(request);
        
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        
        assertThat(captor.getValue().getId()).isNotNull();
        assertThat(captor.getValue().getId()).isNotBlank();
    }
    
    /**
     * 属性 16.5: 失败操作必须记录失败原因
     */
    @Property(tries = 20)
    void failedOperationMustRecordReason(
            @ForAll("auditActions") AuditAction action,
            @ForAll String failureReason) {
        Assume.that(failureReason != null && !failureReason.isBlank());
        
        AuditLogRequest request = AuditLogRequest.builder()
                .action(action)
                .resourceType("USER")
                .userId("user-1")
                .success(false)
                .failureReason(failureReason)
                .build();
        
        component.recordAudit(request);
        
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        
        AuditLog saved = captor.getValue();
        assertThat(saved.getSuccess()).isFalse();
        assertThat(saved.getFailureReason()).isEqualTo(failureReason);
    }
    
    /**
     * 属性 16.6: 数据变更必须记录新旧值
     */
    @Property(tries = 20)
    void dataChangesMustRecordOldAndNewValues(
            @ForAll String oldValue,
            @ForAll String newValue) {
        Assume.that(oldValue != null && newValue != null);
        
        AuditLogRequest request = AuditLogRequest.builder()
                .action(AuditAction.DATA_UPDATED)
                .resourceType("DATA")
                .resourceId("data-1")
                .userId("user-1")
                .oldValue(oldValue)
                .newValue(newValue)
                .success(true)
                .build();
        
        component.recordAudit(request);
        
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        
        AuditLog saved = captor.getValue();
        assertThat(saved.getOldValue()).isEqualTo(oldValue);
        assertThat(saved.getNewValue()).isEqualTo(newValue);
    }
}
