package com.portal.component;

import com.portal.dto.ProcessInstanceInfo;
import com.portal.dto.ProcessStartFormPopupSubmitRequest;
import com.portal.entity.ActionDefinition;
import com.portal.entity.ProcessInstance;
import com.portal.exception.PortalException;
import com.portal.repository.ActionDefinitionRepository;
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
    private ActionDefinitionRepository actionDefinitionRepository;

    @InjectMocks
    private ProcessStartFormPopupComponent component;

    private static ActionDefinition formPopupAction() {
        ActionDefinition action = new ActionDefinition();
        action.setActionType("FORM_POPUP");
        return action;
    }

    private void stubFormPopupActionOnFu(String actionId, String processKey) {
        when(actionDefinitionRepository.findCatalogActionForFunctionUnit(actionId, processKey))
                .thenReturn(Optional.empty());
        when(actionDefinitionRepository.findDwActionForFunctionUnit(actionId, processKey))
                .thenReturn(Optional.of(formPopupAction()));
    }

    @Test
    void submit_startsProcessWhenNoInstanceId() {
        stubFormPopupActionOnFu("50384", "fu-x");
        ProcessInstanceInfo started = new ProcessInstanceInfo();
        started.setId("pi-1");
        when(processComponent.startProcess(eq("user-1"), eq("fu-x"), any())).thenReturn(started);

        ProcessStartFormPopupSubmitRequest req = ProcessStartFormPopupSubmitRequest.builder()
                .popupFormData(Map.of("remark_type", "a"))
                .formData(Map.of("title", "t"))
                .build();

        ProcessInstanceInfo result = component.submit("user-1", "fu-x", "50384", req);

        assertThat(result.getId()).isEqualTo("pi-1");
        verify(actionFormPopupSubmitComponent).submitForProcessInstance(
                "pi-1", "50384", req.getPopupFormData(), "user-1");
    }

    @Test
    void submit_reusesRunningInstanceForInitiator() {
        stubFormPopupActionOnFu("50384", "fu-x");
        ProcessInstance row = new ProcessInstance();
        row.setId("pi-2");
        row.setStartUserId("user-1");
        row.setStatus("RUNNING");
        row.setFunctionUnitCode("fu-x");
        when(processInstanceRepository.findById("pi-2")).thenReturn(Optional.of(row));

        ProcessStartFormPopupSubmitRequest req = ProcessStartFormPopupSubmitRequest.builder()
                .processInstanceId("pi-2")
                .popupFormData(Map.of("remark_type", "b"))
                .build();

        component.submit("user-1", "fu-x", "50384", req);

        verify(processComponent, never()).startProcess(any(), any(), any());
        verify(actionFormPopupSubmitComponent).submitForProcessInstance(
                "pi-2", "50384", req.getPopupFormData(), "user-1");
    }

    @Test
    void submit_rejectsNonInitiator() {
        stubFormPopupActionOnFu("50384", "fu-x");
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
        when(actionDefinitionRepository.findCatalogActionForFunctionUnit("99", "fu-x"))
                .thenReturn(Optional.empty());
        when(actionDefinitionRepository.findDwActionForFunctionUnit("99", "fu-x"))
                .thenReturn(Optional.empty());

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
}
