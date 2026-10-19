package com.portal.component;

import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcessStartBatchFirstCollectionTest {
    private final ProcessStartComponent component = mock(ProcessStartComponent.class, CALLS_REAL_METHODS);
    private final MiCollectionVariableBuilder builder = mock(MiCollectionVariableBuilder.class);
    private final ProcessInstanceRepository instances = mock(ProcessInstanceRepository.class);
    private static final List<String> SERVER_ROWS = List.of("server-validated-row");

    private void baseline(Map<String, Object> variables) {
        ProcessInstance instance = new ProcessInstance(); instance.setVariables(variables); instance.setProcessDefinitionId("frozen-deployment");
        when(instances.findById("synthetic-instance")).thenReturn(Optional.of(instance));
        ReflectionTestUtils.setField(component, "processInstanceRepository", instances);
        ReflectionTestUtils.setField(component, "miCollectionVariableBuilder", builder);
    }
    private SubTableBatchInitialMiOutcome.Plan inject(Map<String, Object> variables) {
        return ReflectionTestUtils.invokeMethod(component, "injectMiCollectionsForFirstTask", Map.of("processDefinitionKey", "synthetic-process"),
                "intake", "synthetic-instance", new ProcessStartComponent.ActiveCatalogPin("catalog", "synthetic-process", "1"), variables);
    }
    private void legacyBuild() {
        doAnswer(call -> { ((Map<String, Object>) call.getArgument(3)).put("miRows", SERVER_ROWS); return null; })
                .when(builder).injectMiCollectionFromBpmn(anyString(), anyString(), anyString(), anyMap());
    }
    @Test void enhancedFirstTaskUsesPersistedSnapshotAndReplacesAnUntrustedPreexistingCollection() {
        Map<String, Object> frozen = Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of("schemaVersion", 1));
        baseline(frozen); legacyBuild();
        var expected = List.of(new SubTableBatchInitialMiOutcome.Expected("child", true, "synthetic_lines", List.of(Map.of("key", "one")), List.of(), List.of()));
        doAnswer(call -> { ((Map<String, Object>) call.getArgument(3)).put("miRows", SERVER_ROWS); return expected; })
                .when(builder).injectMiCollectionForFirstTask(anyString(), anyString(), anyString(), anyMap(), eq(frozen));
        Map<String, Object> variables = new HashMap<>(Map.of("miRows", List.of("untrusted-client-collection")));
        assertThat(inject(variables)).isEqualTo(new SubTableBatchInitialMiOutcome.Plan("synthetic-instance", "frozen-deployment", expected));
        assertThat(variables.get("miRows")).isEqualTo(SERVER_ROWS);
        assertThat(variables).doesNotContainKey(SubTableWriteIsolation.BATCH_RUNTIME_FIELD);
        verify(builder).injectMiCollectionForFirstTask(eq("synthetic-process"), eq("intake"), eq("synthetic-instance"), anyMap(), same(frozen));
        verify(builder, never()).injectMiCollectionFromBpmn(anyString(), anyString(), anyString(), anyMap());
    }
    @Test void legacyFirstTaskKeepsItsPreparedCollectionAndTheOriginalBuilderPath() {
        baseline(Map.of()); legacyBuild();
        Map<String, Object> variables = new HashMap<>(Map.of("miRows", List.of("legacy-prepared-rows")));
        assertThat(inject(variables)).isNull();
        assertThat(variables.get("miRows")).isEqualTo(List.of("legacy-prepared-rows"));
        verify(builder).injectMiCollectionFromBpmn(anyString(), anyString(), anyString(), anyMap());
        verify(builder, never()).injectMiCollectionFromBpmn(anyString(), anyString(), anyString(), anyMap(), anyMap());
        verify(builder, never()).injectMiCollectionForFirstTask(anyString(), anyString(), anyString(), anyMap(), anyMap());
    }
    @Test void frozenValidationFailureCannotPublishPartOfACollection() {
        Map<String, Object> frozen = Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of("schemaVersion", 1));
        baseline(frozen);
        doAnswer(call -> { ((Map<String, Object>) call.getArgument(3)).put("miRows", SERVER_ROWS); throw new IllegalStateException("synthetic target rejected"); })
                .when(builder).injectMiCollectionForFirstTask(anyString(), anyString(), anyString(), anyMap(), anyMap());
        Map<String, Object> variables = new HashMap<>(Map.of("businessField", "unchanged"));
        assertThatThrownBy(() -> inject(variables)).hasMessage("synthetic target rejected");
        assertThat(variables).containsOnlyKeys("businessField");
    }
    @Test void enhancedFirstTaskWithoutReachableAssignmentsProducesNoObservationPlan() {
        baseline(Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of("schemaVersion", 1)));
        when(builder.injectMiCollectionForFirstTask(anyString(), anyString(), anyString(), anyMap(), anyMap())).thenReturn(List.of());
        assertThat(inject(new HashMap<>())).isNull();
    }
}
