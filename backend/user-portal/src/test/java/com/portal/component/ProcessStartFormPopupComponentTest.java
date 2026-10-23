package com.portal.component;

import com.portal.dto.ProcessInstanceInfo;
import com.portal.dto.ProcessStartFormPopupSubmitRequest;
import com.portal.entity.ActionDefinition;
import com.portal.entity.ProcessInstance;
import com.portal.exception.PortalException;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessStartFormPopupComponentTest {

    @Mock
    private ProcessComponent processComponent;
    @Mock
    private ActionFormPopupSubmitComponent actionFormPopupSubmitComponent;
    @Mock
    private ProcessInstanceRepository processInstanceRepository;
    @Mock
    private ActionDefinitionLookupComponent actionDefinitionLookupComponent;
    @Mock
    private ProcessStartComponent processStartComponent;

    @InjectMocks
    private ProcessStartFormPopupComponent component;

    private static ActionDefinition formPopupAction() {
        ActionDefinition action = new ActionDefinition();
        action.setActionType("FORM_POPUP");
        return action;
    }

    /** A new request starts on the unit's active release, so its popup action must be one of that release. */
    private void stubFormPopupActionOnActiveRelease(String actionId, String processKey) {
        when(processStartComponent.activeCatalogIdForStart("user-1", processKey)).thenReturn("rel-active");
        when(actionDefinitionLookupComponent.findPinned("rel-active", actionId))
                .thenReturn(Optional.of(formPopupAction()));
    }

    @Test
    void submit_startsProcessWhenNoInstanceId() {
        stubFormPopupActionOnActiveRelease("50384", "fu-x");
        ProcessInstanceInfo started = new ProcessInstanceInfo();
        started.setId("pi-1");
        when(processComponent.startProcess(eq("user-1"), eq("fu-x"), any())).thenReturn(started);

        ProcessStartFormPopupSubmitRequest req = ProcessStartFormPopupSubmitRequest.builder()
                .popupFormData(Map.of("remark_type", "a"))
                .formData(Map.of("title", "t"))
                .build();

        ActionTableRowRef row = new ActionTableRowRef("row-1");
        when(actionFormPopupSubmitComponent.submitForProcessInstance(
                "pi-1", "50384", req.getPopupFormData(), "user-1")).thenReturn(row);

        ProcessStartFormPopupOutcome result = component.submit("user-1", "fu-x", "50384", req);

        assertThat(result.instance().getId()).isEqualTo("pi-1");
        assertThat(result.actionRow()).isEqualTo(row);
        verify(actionFormPopupSubmitComponent).submitForProcessInstance(
                "pi-1", "50384", req.getPopupFormData(), "user-1");
    }

    /**
     * A started request's popup action is checked against the release that request was started
     * on (by the shared write path), never against the live design or the unit's latest release.
     */
    @Test
    void submit_reusesRunningInstanceForInitiator() {
        ProcessInstance row = new ProcessInstance();
        row.setId("pi-2");
        row.setStartUserId("user-1");
        row.setStatus("RUNNING");
        row.setFunctionUnitCode("fu-x");
        row.setFunctionUnitCatalogId("rel-1");
        when(processInstanceRepository.findById("pi-2")).thenReturn(Optional.of(row));
        when(actionDefinitionLookupComponent.findPinned("rel-1", "50384")).thenReturn(Optional.of(formPopupAction()));

        ProcessStartFormPopupSubmitRequest req = ProcessStartFormPopupSubmitRequest.builder()
                .processInstanceId("pi-2")
                .popupFormData(Map.of("remark_type", "b"))
                .build();

        component.submit("user-1", "fu-x", "50384", req);

        verify(processComponent, never()).startProcess(any(), any(), any());
        verify(actionFormPopupSubmitComponent).submitForProcessInstance(
                "pi-2", "50384", req.getPopupFormData(), "user-1");
        org.mockito.Mockito.verifyNoInteractions(processStartComponent);
    }

    @Test
    void submit_rejectsNonInitiator() {
        ProcessInstance row = new ProcessInstance();
        row.setStartUserId("other");
        row.setStatus("RUNNING");
        when(processInstanceRepository.findById("pi-3")).thenReturn(Optional.of(row));

        ProcessStartFormPopupSubmitRequest req = ProcessStartFormPopupSubmitRequest.builder()
                .processInstanceId("pi-3")
                .popupFormData(Map.of())
                .build();

        assertThatThrownBy(() -> component.submit("user-1", "fu-x", "50384", req))
                .isInstanceOf(PortalException.class);
    }

    @Test
    void submit_rejectsActionNotBoundToProcessKey() {
        when(processStartComponent.activeCatalogIdForStart("user-1", "fu-x")).thenReturn("rel-active");
        when(actionDefinitionLookupComponent.findPinned("rel-active", "99")).thenReturn(Optional.empty());

        ProcessStartFormPopupSubmitRequest req = ProcessStartFormPopupSubmitRequest.builder()
                .popupFormData(Map.of())
                .formData(Map.of("title", "t"))
                .build();

        assertThatThrownBy(() -> component.submit("user-1", "fu-x", "99", req))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining("Action not found for this process");
        verify(processComponent, never()).startProcess(any(), any(), any());
        verify(actionFormPopupSubmitComponent, never()).submitForProcessInstance(any(), any(), any(), any());
    }

    private ProcessStartFormPopupSubmitRequest runningRequestOnRelease1() {
        ProcessInstance row = new ProcessInstance();
        row.setId("pi-2");
        row.setStartUserId("user-1");
        row.setStatus("RUNNING");
        row.setFunctionUnitCode("fu-x");
        row.setFunctionUnitCatalogId("rel-1");
        when(processInstanceRepository.findById("pi-2")).thenReturn(Optional.of(row));
        return ProcessStartFormPopupSubmitRequest.builder().processInstanceId("pi-2").popupFormData(Map.of()).build();
    }

    /** An action that is not part of the request's release is "not found", not a broken release. */
    @Test
    void submit_actionMissingFromTheRequestsReleaseIsNotFound() {
        ProcessStartFormPopupSubmitRequest req = runningRequestOnRelease1();
        when(actionDefinitionLookupComponent.findPinned("rel-1", "99")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> component.submit("user-1", "fu-x", "99", req))
                .isInstanceOf(PortalException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((PortalException) e).getCode()).isEqualTo("404"));
        verify(actionFormPopupSubmitComponent, never()).submitForProcessInstance(any(), any(), any(), any());
    }

    @Test
    void submit_releaseActionThatIsNotAFormPopupIsRejected() {
        ProcessStartFormPopupSubmitRequest req = runningRequestOnRelease1();
        ActionDefinition approve = new ActionDefinition();
        approve.setActionType("APPROVE");
        when(actionDefinitionLookupComponent.findPinned("rel-1", "50380")).thenReturn(Optional.of(approve));

        assertThatThrownBy(() -> component.submit("user-1", "fu-x", "50380", req))
                .isInstanceOf(PortalException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((PortalException) e).getCode()).isEqualTo("400"));
        verify(actionFormPopupSubmitComponent, never()).submitForProcessInstance(any(), any(), any(), any());
    }
}
