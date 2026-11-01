package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.dto.TaskCompleteRequest;
import com.portal.dto.TaskInfo;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskRestoredMultiBindingTest {
    private static final String STORE = "dw:journal";
    private static final List<Map<String,Object>> SAVED = List.of(
            Map.of("journal_key", "N1", "caption", "saved", "_wsRowVersion", 2));

    @Test void omittedMultiBindingStoreIsPreservedWithoutBecomingAClientClaim() {
        Map<String,Object> submitted = input(Map.of());
        assertThatCode(() -> completion().isolateOutboundSubTables(task(), request(submitted), submitted))
                .doesNotThrowAnyException();
        assertThat(((Map<?,?>)submitted.get("__subTables__")).get(STORE)).isEqualTo(SAVED);
    }

    @Test void explicitlySubmittingEvenUnchangedMultiBindingRowsStillRequiresScopes() {
        Map<String,Object> submitted = input(Map.of(STORE, SAVED));
        assertThatThrownBy(() -> completion().isolateOutboundSubTables(task(), request(submitted), submitted))
                .hasMessageContaining("require binding scopes");
    }

    @Test void anOmittedStoreCannotBeChangedByDisplayHydrationWithoutScopes() {
        Map<String,Object> hydrated = input(Map.of(STORE, List.of(
                Map.of("journal_key", "N1", "caption", "changed", "_wsRowVersion", 2))));
        assertThatThrownBy(() -> completion().isolateOutboundSubTables(task(), request(input(Map.of())), hydrated))
                .hasMessageContaining("require binding scopes");
    }

    @Test void legacyBindingIdClaimsAreStillExplicitAfterStoreNormalization() {
        Map<String,Object> normalized = input(Map.of(STORE, SAVED));
        assertThatThrownBy(() -> completion().isolateOutboundSubTables(task(), request(input(Map.of("r1", SAVED))), normalized))
                .hasMessageContaining("require binding scopes");
    }

    private TaskApprovalCompletionComponent completion() {
        var engine = mock(WorkflowEngineClient.class);
        when(engine.requireTaskWriteContext("task")).thenReturn(Map.of("writeContext", Map.of(
                "taskId", "task", "processInstanceId", "process", "taskDefinitionKey", "review",
                "processDefinitionId", "deployed:1:original", "multiInstance", false)));
        var repository = mock(ProcessInstanceRepository.class);
        Map<String,Object> baseline = Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of("schemaVersion", 1),
                "__subTables__", Map.of(STORE, SAVED, "dw:empty", List.of()));
        when(repository.findById("process")).thenReturn(Optional.of(ProcessInstance.builder().id("process")
                .functionUnitCode("synthetic").functionUnitCatalogId("catalog").variables(baseline).build()));
        var design = mock(SubTableWriteDesign.class);
        when(design.resolve("catalog", "review")).thenReturn(Map.of(
                "r1", new SubTableWriteDesign.Binding("r1", STORE, "root_ref", "MAIN", "roots",
                        List.of("journal_key"), List.of("root_key"), "structuralFk"),
                "r2", new SubTableWriteDesign.Binding("r2", STORE, "line_ref", "SUB", "lines",
                        List.of("journal_key"), List.of("line_key"), "structuralFk")));
        var isolation = new SubTableWriteIsolation(mock(MiSubTaskSubTableRowMerger.class),
                mock(SubTableBindingScopeGuard.class), design);
        var completion = new TaskApprovalCompletionComponent(engine, null, repository, null, null, null, null, null);
        ReflectionTestUtils.setField(completion, "subTableWriteIsolation", isolation);
        return completion;
    }
    private static TaskInfo task() {
        var task = new TaskInfo(); task.setTaskId("task"); task.setProcessInstanceId("process");
        task.setTaskDefinitionKey("review"); return task;
    }
    private static TaskCompleteRequest request(Map<String,Object> values) {
        return TaskCompleteRequest.builder().taskId("task").formData(values).build();
    }
    private static Map<String,Object> input(Map<String,?> tables) {
        return new LinkedHashMap<>(Map.of("__subTables__", tables));
    }
}
