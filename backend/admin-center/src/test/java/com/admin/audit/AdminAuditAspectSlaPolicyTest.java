package com.admin.audit;

import com.admin.bi.repository.BiDashboardAssignmentRepository;
import com.admin.bi.repository.BiDashboardRegistryRepository;
import com.admin.bi.repository.BiRbacMappingRepository;
import com.admin.component.SecurityAuditComponent;
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
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SLA lead time changes and manual recalculations reach admin_audit_logs. */
class AdminAuditAspectSlaPolicyTest {

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
    void leadTimeChange_isRecordedAsUpdateOfTheFunctionUnit() throws Throwable {
        aspect.auditSlaPolicy(joinPoint("updatePolicy", ResponseEntity.ok().build(), "FU_CASES", new Object()));

        SecurityAuditComponent.AuditLogRequest req = captureRecorded();
        assertThat(req.getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(req.getResourceType()).isEqualTo("SLA_POLICY");
        assertThat(req.getResourceId()).isEqualTo("FU_CASES");
    }

    @Test
    void recalculate_recordsWhatWasReRun() throws Throwable {
        aspect.auditSlaPolicy(joinPoint("recalculate", ResponseEntity.ok().build(), "FU_CASES"));

        SecurityAuditComponent.AuditLogRequest req = captureRecorded();
        assertThat(req.getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(req.getResourceId()).isEqualTo("FU_CASES");
        assertThat(req.getNewValue()).contains("\"operation\":\"recalculate\"");
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
