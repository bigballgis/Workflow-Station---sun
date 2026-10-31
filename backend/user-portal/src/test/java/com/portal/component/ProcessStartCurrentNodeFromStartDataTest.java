package com.portal.component;

import com.portal.dto.ProcessStartRequest;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProcessStartComponent — persist current_node from engine start")
class ProcessStartCurrentNodeFromStartDataTest {

    @Test
    @DisplayName("nextTaskName on start data becomes the initial current_node")
    void readsNextTaskName() {
        Map<String, Object> data = new HashMap<>();
        data.put("processInstanceId", "p-1");
        data.put("nextTaskName", "Case Submission");

        assertThat(ProcessStartComponent.currentNodeFromStartData(data)).isEqualTo("Case Submission");
    }

    @Test
    @DisplayName("blank, missing, or non-string nextTaskName is not invented")
    void blankOrMissingIsNull() {
        assertThat(ProcessStartComponent.currentNodeFromStartData(null)).isNull();
        assertThat(ProcessStartComponent.currentNodeFromStartData(Map.of())).isNull();
        assertThat(ProcessStartComponent.currentNodeFromStartData(Map.of("nextTaskName", "  "))).isNull();
        assertThat(ProcessStartComponent.currentNodeFromStartData(Map.of("nextTaskName", 12))).isNull();
    }

    @Test
    @DisplayName("auto-complete must not write a null current_node over the start-time value")
    void nullOutcomeMustNotOverwrite() {
        assertThat(ProcessStartComponent.shouldWriteOutcomeCurrentNode(null)).isFalse();
        assertThat(ProcessStartComponent.shouldWriteOutcomeCurrentNode("  ")).isFalse();
        assertThat(ProcessStartComponent.shouldWriteOutcomeCurrentNode("Review")).isTrue();
    }

    @Test
    @DisplayName("persistRunningProcessInstance writes engine nextTaskName onto current_node")
    void persistWritesEngineNextTaskName() {
        ProcessStartComponent component = mock(ProcessStartComponent.class, CALLS_REAL_METHODS);
        ProcessInstanceRepository repo = mock(ProcessInstanceRepository.class);
        ReflectionTestUtils.setField(component, "processInstanceRepository", repo);
        ArgumentCaptor<ProcessInstance> saved = ArgumentCaptor.forClass(ProcessInstance.class);
        when(repo.save(saved.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> data = new HashMap<>();
        data.put("processDefinitionId", "synthetic:1");
        data.put("nextTaskName", "Case Submission");
        ProcessStartRequest request = new ProcessStartRequest();
        request.setBusinessKey("synthetic-business");

        ReflectionTestUtils.invokeMethod(
                component,
                "persistRunningProcessInstance",
                "synthetic-instance",
                data,
                "synthetic-key",
                "synthetic-name",
                request,
                "synthetic-user",
                "Synthetic User",
                Map.of(),
                new ProcessStartComponent.ActiveCatalogPin("synthetic-catalog", "synthetic-code", "1.0"));

        verify(repo).save(saved.getValue());
        assertThat(saved.getValue().getCurrentNode()).isEqualTo("Case Submission");
    }
}
