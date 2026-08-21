package com.portal.controller;

import com.portal.component.ChangeHistoryComponent;
import com.portal.component.ChangeHistorySensitiveMaskResolver;
import com.portal.component.ProcessComponent;
import com.platform.common.dto.ApiResponse;
import com.platform.common.i18n.I18nService;
import com.portal.dto.ChangeHistoryRecord;
import com.portal.dto.ProcessInstanceInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Change history and its sensitive-mask companion previously took no user id at
 * all, so any authenticated caller could read another user's request by pasting
 * that request's id. Both now carry the same gate as {@code GET /processes/{id}}.
 */
@ExtendWith(MockitoExtension.class)
class ChangeHistoryControllerSecurityTest {

    @Mock
    private ChangeHistoryComponent changeHistoryComponent;

    @Mock
    private ChangeHistorySensitiveMaskResolver sensitiveMaskResolver;

    @Mock
    private ProcessComponent processComponent;

    @Mock
    private I18nService i18nService;

    @InjectMocks
    private ChangeHistoryController controller;

    private static ProcessInstanceInfo otherUsersRequest() {
        return ProcessInstanceInfo.builder()
                .id("proc-1")
                .startUserId("someone-else")
                .build();
    }

    @Test
    void changeHistory_deniesNonParticipantAndDoesNotQueryHistory() {
        ProcessInstanceInfo detail = otherUsersRequest();
        when(processComponent.getProcessDetail("proc-1")).thenReturn(detail);
        when(processComponent.canAuditProcessDetail("outsider", detail)).thenReturn(false);
        when(i18nService.getMessage("portal.process_detail_access_denied"))
                .thenReturn("You do not have permission to view this process");

        ApiResponse<List<ChangeHistoryRecord>> response =
                controller.getChangeHistory("outsider", "proc-1", null, null);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError().getCode()).isEqualTo("403");
        verify(changeHistoryComponent, never()).getChangeHistory(any(), any(), any());
    }

    @Test
    void changeHistory_deniesAnonymousCaller() {
        ApiResponse<List<ChangeHistoryRecord>> response =
                controller.getChangeHistory(null, "proc-1", null, null);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError().getCode()).isEqualTo("403");
        verify(processComponent, never()).getProcessDetail(any());
        verify(changeHistoryComponent, never()).getChangeHistory(any(), any(), any());
    }

    /**
     * A reviewer holding only a Function Unit audit grant (not a participant, not the
     * initiator) must be able to read change history — canAuditProcessDetail is a
     * strict superset of canAccessProcessDetail for exactly this reason.
     */
    @Test
    void changeHistory_allowsAuditorWithoutParticipantAccess() {
        ProcessInstanceInfo detail = otherUsersRequest();
        when(processComponent.getProcessDetail("proc-1")).thenReturn(detail);
        when(processComponent.canAuditProcessDetail("auditor", detail)).thenReturn(true);
        when(changeHistoryComponent.getChangeHistory("proc-1", null, null)).thenReturn(List.of());

        ApiResponse<List<ChangeHistoryRecord>> response =
                controller.getChangeHistory("auditor", "proc-1", null, null);

        assertThat(response.isSuccess()).isTrue();
        verify(changeHistoryComponent).getChangeHistory("proc-1", null, null);
    }

    @Test
    void changeHistory_allowsParticipantAndPassesRowAndTaskFilters() {
        ProcessInstanceInfo detail = otherUsersRequest();
        when(processComponent.getProcessDetail("proc-1")).thenReturn(detail);
        when(processComponent.canAuditProcessDetail("participant", detail)).thenReturn(true);
        when(changeHistoryComponent.getChangeHistory("proc-1", "row-9", "task-3"))
                .thenReturn(List.of());

        ApiResponse<List<ChangeHistoryRecord>> response =
                controller.getChangeHistory("participant", "proc-1", "row-9", "task-3");

        assertThat(response.isSuccess()).isTrue();
        verify(changeHistoryComponent).getChangeHistory("proc-1", "row-9", "task-3");
    }

    /**
     * A history query for an instance the portal cannot resolve is a data gap, not
     * a permission failure — keep it readable so callers get an empty list rather
     * than a misleading 403.
     */
    @Test
    void changeHistory_allowsUnresolvableInstance() {
        when(processComponent.getProcessDetail("ghost")).thenReturn(null);
        when(changeHistoryComponent.getChangeHistory("ghost", null, null)).thenReturn(List.of());

        ApiResponse<List<ChangeHistoryRecord>> response =
                controller.getChangeHistory("viewer", "ghost", null, null);

        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void sensitiveMasks_deniesNonParticipantAndDoesNotResolveMasks() {
        ProcessInstanceInfo detail = otherUsersRequest();
        when(processComponent.getProcessDetail("proc-1")).thenReturn(detail);
        when(processComponent.canAuditProcessDetail("outsider", detail)).thenReturn(false);
        when(i18nService.getMessage("portal.process_detail_access_denied"))
                .thenReturn("You do not have permission to view this process");

        ApiResponse<Map<String, Map<String, Object>>> response =
                controller.getChangeHistorySensitiveMasks("outsider", "proc-1");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError().getCode()).isEqualTo("403");
        verify(sensitiveMaskResolver, never()).resolveByProcessInstanceId(any());
    }

    @Test
    void sensitiveMasks_allowsParticipant() {
        ProcessInstanceInfo detail = otherUsersRequest();
        when(processComponent.getProcessDetail("proc-1")).thenReturn(detail);
        when(processComponent.canAuditProcessDetail("participant", detail)).thenReturn(true);
        when(sensitiveMaskResolver.resolveByProcessInstanceId("proc-1")).thenReturn(Map.of());

        ApiResponse<Map<String, Map<String, Object>>> response =
                controller.getChangeHistorySensitiveMasks("participant", "proc-1");

        assertThat(response.isSuccess()).isTrue();
        verify(sensitiveMaskResolver).resolveByProcessInstanceId("proc-1");
    }
}
