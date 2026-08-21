package com.portal.controller;

import com.portal.component.ProcessComponent;
import com.portal.component.ProcessFormComponent;
import com.platform.common.dto.ApiResponse;
import com.portal.dto.ProcessFormData;
import com.portal.dto.ProcessInstanceInfo;
import com.portal.exception.PortalException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The form layout carries the submitted values of a request, so reading it took
 * the same gate as the request detail. Previously this endpoint took no user id.
 */
@ExtendWith(MockitoExtension.class)
class ProcessFormControllerSecurityTest {

    @Mock
    private ProcessFormComponent processFormComponent;

    @Mock
    private ProcessComponent processComponent;

    @InjectMocks
    private ProcessFormController controller;

    private static ProcessInstanceInfo otherUsersRequest() {
        return ProcessInstanceInfo.builder()
                .id("proc-1")
                .startUserId("someone-else")
                .build();
    }

    @Test
    void getProcessFormData_deniesNonParticipantAndDoesNotLoadForm() {
        ProcessInstanceInfo detail = otherUsersRequest();
        when(processComponent.getProcessDetail("proc-1")).thenReturn(detail);
        when(processComponent.canAuditProcessDetail("outsider", detail)).thenReturn(false);

        assertThatThrownBy(() -> controller.getProcessFormData("outsider", "proc-1"))
                .isInstanceOf(PortalException.class)
                .satisfies(e -> assertThat(((PortalException) e).getCode()).isEqualTo("403"));

        verify(processFormComponent, never()).getProcessFormData(any());
    }

    @Test
    void getProcessFormData_deniesAnonymousCallerWithoutLoadingProcess() {
        assertThatThrownBy(() -> controller.getProcessFormData(null, "proc-1"))
                .isInstanceOf(PortalException.class)
                .satisfies(e -> assertThat(((PortalException) e).getCode()).isEqualTo("403"));

        verify(processComponent, never()).getProcessDetail(any());
        verify(processFormComponent, never()).getProcessFormData(any());
    }

    @Test
    void getProcessFormData_allowsParticipant() {
        ProcessInstanceInfo detail = otherUsersRequest();
        ProcessFormData data = ProcessFormData.builder().processInstanceId("proc-1").build();
        when(processComponent.getProcessDetail("proc-1")).thenReturn(detail);
        when(processComponent.canAuditProcessDetail("participant", detail)).thenReturn(true);
        when(processFormComponent.getProcessFormData("proc-1")).thenReturn(data);

        ApiResponse<ProcessFormData> response = controller.getProcessFormData("participant", "proc-1");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isSameAs(data);
    }

    /**
     * A reviewer holding only a Function Unit audit grant (not a participant, not the
     * initiator) must be able to load the process form — this is the primary form-data
     * source for the audit detail page, so canAuditProcessDetail must gate it, not the
     * narrower canAccessProcessDetail.
     */
    @Test
    void getProcessFormData_allowsAuditorWithoutParticipantAccess() {
        ProcessInstanceInfo detail = otherUsersRequest();
        ProcessFormData data = ProcessFormData.builder().processInstanceId("proc-1").build();
        when(processComponent.getProcessDetail("proc-1")).thenReturn(detail);
        when(processComponent.canAuditProcessDetail("auditor", detail)).thenReturn(true);
        when(processFormComponent.getProcessFormData("proc-1")).thenReturn(data);

        ApiResponse<ProcessFormData> response = controller.getProcessFormData("auditor", "proc-1");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isSameAs(data);
    }

    /**
     * An unresolvable instance falls through to the component, which owns the
     * not-found response — a missing request must not read as a denied one.
     */
    @Test
    void getProcessFormData_allowsUnresolvableInstanceToReachComponent() {
        ProcessFormData data = ProcessFormData.builder().processInstanceId("proc-1").build();
        when(processComponent.getProcessDetail("ghost")).thenReturn(null);
        when(processFormComponent.getProcessFormData("ghost")).thenReturn(data);

        ApiResponse<ProcessFormData> response = controller.getProcessFormData("viewer", "ghost");

        assertThat(response.isSuccess()).isTrue();
    }
}
