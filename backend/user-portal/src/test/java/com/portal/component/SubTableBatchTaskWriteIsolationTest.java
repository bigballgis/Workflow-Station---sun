package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.dto.SubTableBindingScope;
import com.portal.dto.TaskCompleteRequest;
import com.portal.dto.TaskInfo;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubTableBatchTaskWriteIsolationTest {
    private static final String STORE = "dw:details";
    private final WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
    private final ProcessInstanceRepository repository = mock(ProcessInstanceRepository.class);

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void completeRejectsSiblingClaimEvenWithForgedOrOmittedClientItem(boolean noFormData) {
        var completion = completion();
        Map<String, Object> variables = input("B", "forged");
        var request = TaskCompleteRequest.builder().taskId("task-one")
                .formData(noFormData ? null : variables).subTableBindingScopes(List.of(scope("B"))).build();
        assertThatThrownBy(() -> completion.isolateOutboundSubTables(task(), request, variables))
                .isInstanceOf(com.portal.exception.PortalException.class).hasMessageContaining("not allowed");
        verify(engine).requireTaskWriteContext("task-one");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void completeUsesActualExecutionAndPreservesSiblingDataWithoutBatchMarker(boolean noFormData) {
        var completion = completion();
        Map<String, Object> variables = input("A", "legitimate");
        var request = TaskCompleteRequest.builder().taskId("task-one")
                .formData(noFormData ? null : variables).subTableBindingScopes(List.of(scope("A"))).build();
        completion.isolateOutboundSubTables(task(), request, variables);
        assertThat(variables).doesNotContainKeys("_currentItem", "currentItem");
        List<?> actual = (List<?>) ((Map<?, ?>) variables.get("__subTables__")).get(STORE);
        assertThat(actual.toArray()).containsExactlyInAnyOrder(row("A", "legitimate", 3), row("B", "original-B", 2));
    }

    private TaskApprovalCompletionComponent completion() {
        Map<String, Object> baseline = Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of("schemaVersion", 1),
                "case_key", "case-one", "__subTables__", Map.of(STORE, List.of(row("A", "original-A", 2), row("B", "original-B", 2))));
        when(repository.findById("process-one")).thenReturn(Optional.of(ProcessInstance.builder().id("process-one")
                .functionUnitCode("synthetic-unit").functionUnitCatalogId("catalog-one").variables(baseline).build()));
        when(engine.requireTaskWriteContext("task-one")).thenReturn(Map.of("writeContext", Map.of(
                "taskId", "task-one", "processInstanceId", "process-one", "taskDefinitionKey", "review",
                "processDefinitionId", "deployed:1:original", "multiInstance", true,
                "currentItem", Map.of("rowKey", Map.of("participant_key", "A")))));
        var design = mock(SubTableWriteDesign.class);
        when(design.resolve("catalog-one", "review")).thenReturn(Map.of(
                "101", new SubTableWriteDesign.Binding("101", STORE, "party_ref", "SUB", "participants",
                        List.of("detail_key"), List.of("participant_key"), "structuralFk"),
                "102", new SubTableWriteDesign.Binding("102", "dw:participants", "case_ref", "MAIN", "cases",
                        List.of("participant_key"), List.of("case_key"), "miParticipantRow")));
        var jdbc = mock(JdbcTemplate.class);
        var isolation = new SubTableWriteIsolation(new MiSubTaskSubTableRowMerger(jdbc), new SubTableBindingScopeGuard(), design);
        var completion = new TaskApprovalCompletionComponent(engine, null, repository, null, null, null, null, null);
        ReflectionTestUtils.setField(completion, "subTableWriteIsolation", isolation);
        return completion;
    }

    private static TaskInfo task() {
        var task = new TaskInfo();
        task.setTaskId("task-one"); task.setTaskDefinitionKey("review"); task.setProcessInstanceId("process-one");
        return task;
    }

    private static Map<String, Object> input(String participant, String caption) {
        return new LinkedHashMap<>(Map.of("__subTables__", Map.of(STORE, List.of(row(participant, caption, 2))),
                "_currentItem", Map.of("rowKey", Map.of("participant_key", "B"))));
    }

    private static Map<String, Object> row(String participant, String caption, int version) {
        return Map.of("detail_key", "detail-" + participant, "party_ref", participant, "caption", caption, "_wsRowVersion", version);
    }

    private static SubTableBindingScope scope(String participant) {
        return new SubTableBindingScope("101", STORE, List.of(Map.of("detail_key", "detail-" + participant)), false);
    }
}
