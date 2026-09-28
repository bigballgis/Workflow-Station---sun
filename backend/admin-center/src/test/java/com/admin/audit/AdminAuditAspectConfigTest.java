package com.admin.audit;

import com.admin.bi.repository.BiDashboardAssignmentRepository;
import com.admin.bi.repository.BiDashboardRegistryRepository;
import com.admin.bi.repository.BiRbacMappingRepository;
import com.admin.component.SecurityAuditComponent;
import com.admin.entity.SystemConfig;
import com.admin.enums.AuditAction;
import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.RelationTableDefinitionRepository;
import com.admin.repository.RoleRepository;
import com.admin.repository.UserRepository;
import com.admin.repository.VirtualGroupRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** System config writes reach admin_audit_logs, without leaking encrypted values. */
class AdminAuditAspectConfigTest {

    private SecurityAuditComponent securityAuditComponent;
    private AdminAuditAspect aspect;

    @BeforeEach
    void setUp() {
        securityAuditComponent = mock(SecurityAuditComponent.class);
        aspect = new AdminAuditAspect(
                securityAuditComponent,
                mock(UserRepository.class),
                mock(RoleRepository.class),
                mock(VirtualGroupRepository.class),
                mock(RelationTableDefinitionRepository.class),
                mock(BusinessUnitRepository.class),
                mock(BiDashboardRegistryRepository.class),
                mock(BiDashboardAssignmentRepository.class),
                mock(BiRbacMappingRepository.class),
                mock(PlatformTransactionManager.class),
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void createEncryptedConfig_isRecordedWithValueMasked() throws Throwable {
        SystemConfig created = SystemConfig.builder()
                .id("c-1").category("SYSTEM").configKey("smtp.password")
                .configValue("s3cr3t").defaultValue("d3fault").valueType("STRING")
                .encrypted(true).editable(true).version(1)
                .build();

        aspect.auditConfig(joinPoint("createConfig",
                ResponseEntity.status(HttpStatus.CREATED).body(created), new Object()));

        SecurityAuditComponent.AuditLogRequest req = captureRecorded();
        assertThat(req.getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(req.getResourceType()).isEqualTo("CONFIG");
        assertThat(req.getNewValue()).contains("smtp.password")
                .doesNotContain("s3cr3t").doesNotContain("d3fault");
    }

    @Test
    void sync_recordsEnvironmentsAndKeys() throws Throwable {
        aspect.auditConfig(joinPoint("syncConfigs", ResponseEntity.ok().build(),
                "dev", "prod", List.of("a.key", "b.key")));

        SecurityAuditComponent.AuditLogRequest req = captureRecorded();
        assertThat(req.getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(req.getNewValue())
                .contains("\"sourceEnv\":\"dev\"")
                .contains("\"targetEnv\":\"prod\"")
                .contains("a.key", "b.key");
    }

    private ProceedingJoinPoint joinPoint(String method, Object result, Object... args) throws Throwable {
        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn(method);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getArgs()).thenReturn(args);
        when(pjp.proceed()).thenReturn(result);
        return pjp;
    }

    private SecurityAuditComponent.AuditLogRequest captureRecorded() {
        ArgumentCaptor<SecurityAuditComponent.AuditLogRequest> captor =
                ArgumentCaptor.forClass(SecurityAuditComponent.AuditLogRequest.class);
        verify(securityAuditComponent).recordAudit(captor.capture());
        return captor.getValue();
    }
}
