package com.portal.controller;

import com.portal.component.FunctionUnitAccessComponent;
import com.portal.component.ProcessComponent;
import com.platform.common.dto.ApiResponse;
import com.portal.dto.ProcessInstanceInfo;
import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression for #1373: unauthenticated callers must not read process detail/history.
 */
@ExtendWith(MockitoExtension.class)
class ProcessControllerProcessDetailSecurityTest {

    @Mock
    private ProcessComponent processComponent;

    @Mock
    private I18nService i18nService;

    @Mock
    private FunctionUnitAccessComponent functionUnitAccessComponent;

    @Mock
    private com.portal.component.FunctionUnitAuditScopeComponent functionUnitAuditScopeComponent;

    @InjectMocks
    private ProcessController processController;

    @Test
    void getProcessDetail_rejectsNullUserIdWithoutLoadingProcess() {
        assertThatThrownBy(() -> processController.getProcessDetail(null, "proc-1"))
                .isInstanceOf(FunctionUnitAccessComponent.FunctionUnitAccessDeniedException.class)
                .hasMessageContaining("login");

        verify(processComponent, never()).getProcessDetail(any());
    }

    @Test
    void getProcessDetail_rejectsBlankUserIdWithoutLoadingProcess() {
        assertThatThrownBy(() -> processController.getProcessDetail("  ", "proc-1"))
                .isInstanceOf(FunctionUnitAccessComponent.FunctionUnitAccessDeniedException.class);

        verify(processComponent, never()).getProcessDetail(any());
    }

    @Test
    void getProcessDetail_rejectsNonParticipantWithoutViewAccess() {
        ProcessInstanceInfo detail = ProcessInstanceInfo.builder()
                .id("proc-1")
                .startUserId("other-user")
                .build();
        when(processComponent.getProcessDetail("proc-1")).thenReturn(detail);
        when(processComponent.canAuditProcessDetail("viewer", detail)).thenReturn(false);
        when(i18nService.getMessage("portal.process_detail_access_denied"))
                .thenReturn("You do not have permission to view this process");

        ApiResponse<ProcessInstanceInfo> response = processController.getProcessDetail("viewer", "proc-1");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError().getCode()).isEqualTo("403");
        assertThat(response.getError().getMessage())
                .isEqualTo("You do not have permission to view this process");
        verify(processComponent).getProcessDetail("proc-1");
    }

    @Test
    void getProcessDetail_allowsMainTableViewReader() {
        ProcessInstanceInfo detail = ProcessInstanceInfo.builder()
                .id("proc-1")
                .startUserId("system")
                .functionUnitCode("test-fu")
                .build();
        when(processComponent.getProcessDetail("proc-1")).thenReturn(detail);
        when(processComponent.canAuditProcessDetail("viewer", detail)).thenReturn(true);

        ApiResponse<ProcessInstanceInfo> response = processController.getProcessDetail("viewer", "proc-1");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(detail);
    }

    @Test
    void getProcessHistory_rejectsNullUserIdWithoutLoadingProcess() {
        assertThatThrownBy(() -> processController.getProcessHistory(null, "proc-1"))
                .isInstanceOf(FunctionUnitAccessComponent.FunctionUnitAccessDeniedException.class);

        verify(processComponent, never()).getProcessDetail(any());
        verify(processComponent, never()).getProcessHistory(any());
    }

    @Test
    void getProcessHistory_allowsParticipant() {
        ProcessInstanceInfo detail = ProcessInstanceInfo.builder()
                .id("proc-1")
                .startUserId("user-1")
                .build();
        when(processComponent.getProcessDetail("proc-1")).thenReturn(detail);
        when(processComponent.canAuditProcessDetail(eq("user-1"), eq(detail))).thenReturn(true);
        when(processComponent.getProcessHistory("proc-1")).thenReturn(java.util.List.of());

        ApiResponse<java.util.List<java.util.Map<String, Object>>> response =
                processController.getProcessHistory("user-1", "proc-1");

        assertThat(response.isSuccess()).isTrue();
        verify(processComponent).getProcessHistory("proc-1");
    }
}
