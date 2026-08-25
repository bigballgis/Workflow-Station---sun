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
import com.platform.common.dto.ApiResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Automation flow / piece auditing: these controllers proxy the Activepieces API,
 * so there is no local entity to diff — the audit record must carry the operation
 * parameters explicitly, and a rejected (non-2xx) call must be recorded as FAILED.
 */
class AdminAuditAspectAutomationTest {

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
                // findAndRegisterModules() mirrors Spring Boot's auto-configured mapper
                // (JavaTimeModule); a bare ObjectMapper cannot serialise the
                // LocalDateTime on ApiResponse.
                new ObjectMapper().findAndRegisterModules());
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

    @Test
    void flowStatusChange_recordsEnabledFlagInNewValue() throws Throwable {
        ResponseEntity<?> ok = ResponseEntity.ok(
                ApiResponse.success(Map.of("flowId", "flow-1", "enabled", false)));

        aspect.auditAutomationFlow(joinPoint("setFlowStatus", ok, "flow-1", false));

        SecurityAuditComponent.AuditLogRequest req = captureRecorded();
        assertThat(req.getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(req.getResourceType()).isEqualTo("AUTOMATION_FLOW");
        assertThat(req.getResourceId()).isEqualTo("flow-1");
        assertThat(req.getSuccess()).isTrue();
        // The request path alone (/automation/flows/{id}/status) cannot tell
        // enable from disable — the flag must live in the record.
        assertThat(req.getNewValue()).contains("\"enabled\":false");
    }

    @Test
    void flowDelete_recordsForceFlag() throws Throwable {
        ResponseEntity<?> ok = ResponseEntity.ok(ApiResponse.success(Map.of("flowId", "flow-1")));

        aspect.auditAutomationFlow(joinPoint("deleteFlow", ok, "flow-1", true));

        SecurityAuditComponent.AuditLogRequest req = captureRecorded();
        assertThat(req.getAction()).isEqualTo(AuditAction.DELETE);
        assertThat(req.getResourceId()).isEqualTo("flow-1");
        assertThat(req.getNewValue()).contains("\"force\":true");
    }

    @Test
    void flowImport_recordsCreateFromResponseBody() throws Throwable {
        ResponseEntity<?> ok = ResponseEntity.ok(ApiResponse.success(Map.of(
                "flowId", "flow-9", "flowKey", "order-sync", "created", true, "published", true)));

        aspect.auditAutomationFlow(joinPoint("importFlow", ok, null, true));

        SecurityAuditComponent.AuditLogRequest req = captureRecorded();
        assertThat(req.getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(req.getResourceType()).isEqualTo("AUTOMATION_FLOW");
        assertThat(req.getNewValue()).contains("order-sync");
    }

    @Test
    void rejectedCall_isRecordedAsFailure() throws Throwable {
        ResponseEntity<?> forbidden = ResponseEntity.status(403)
                .body(ApiResponse.error("FORBIDDEN", "system:admin required"));

        aspect.auditAutomationFlow(joinPoint("deleteFlow", forbidden, "flow-1", false));

        SecurityAuditComponent.AuditLogRequest req = captureRecorded();
        assertThat(req.getSuccess()).isFalse();
        assertThat(req.getFailureReason()).contains("403").contains("FORBIDDEN");
    }

    @Test
    void pieceToggle_recordsDisabledFlag() throws Throwable {
        ResponseEntity<?> ok = ResponseEntity.ok(
                ApiResponse.success(Map.of("name", "@hermes/biz-calendar", "disabled", true)));

        aspect.auditAutomationPiece(joinPoint("togglePiece", ok, "@hermes/biz-calendar", true));

        SecurityAuditComponent.AuditLogRequest req = captureRecorded();
        assertThat(req.getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(req.getResourceType()).isEqualTo("AUTOMATION_PIECE");
        assertThat(req.getResourceId()).isEqualTo("@hermes/biz-calendar");
        assertThat(req.getNewValue()).contains("\"disabled\":true");
    }

    @Test
    void pieceDelete_recordsNameVersionAndForce() throws Throwable {
        ResponseEntity<?> ok = ResponseEntity.ok(
                ApiResponse.success(Map.of("name", "@hermes/biz-calendar", "version", "0.1.0")));

        aspect.auditAutomationPiece(
                joinPoint("deletePiece", ok, "@hermes/biz-calendar", "0.1.0", false));

        SecurityAuditComponent.AuditLogRequest req = captureRecorded();
        assertThat(req.getAction()).isEqualTo(AuditAction.DELETE);
        assertThat(req.getResourceId()).isEqualTo("@hermes/biz-calendar@0.1.0");
        assertThat(req.getNewValue()).contains("\"force\":false");
    }

    @Test
    void pieceExport_isRecordedAsQuery() throws Throwable {
        ResponseEntity<?> ok = ResponseEntity.ok(new byte[]{1, 2, 3});

        aspect.auditAutomationPiece(
                joinPoint("exportPiece", ok, "@hermes/biz-calendar", "0.1.0"));

        SecurityAuditComponent.AuditLogRequest req = captureRecorded();
        assertThat(req.getAction()).isEqualTo(AuditAction.QUERY);
        assertThat(req.getResourceId()).isEqualTo("@hermes/biz-calendar@0.1.0");
    }

    @Test
    void readOnlyListing_isNotRecorded() throws Throwable {
        ResponseEntity<?> ok = ResponseEntity.ok(ApiResponse.success(java.util.List.of()));

        aspect.auditAutomationPiece(joinPoint("listPieces", ok));
        aspect.auditAutomationPiece(joinPoint("queryPieces", ok));
        aspect.auditAutomationFlow(joinPoint("listFlows", ok));
        aspect.auditAutomationFlow(joinPoint("queryFlows", ok));

        verifyNoInteractions(securityAuditComponent);
    }

    @Test
    void unrecordedMethodStillProceeds() throws Throwable {
        ResponseEntity<?> ok = ResponseEntity.ok(ApiResponse.success(Map.of("flowId", "flow-1")));
        ProceedingJoinPoint pjp = joinPoint("resolveFlowRef", ok, "ref-1", "token");

        Object returned = aspect.auditAutomationFlow(pjp);

        assertThat(returned).isSameAs(ok);
        verify(securityAuditComponent, org.mockito.Mockito.never()).recordAudit(any());
    }
}
