package com.portal.component;

import com.portal.dto.TaskCompleteRequest;
import com.portal.dto.TaskInfo;
import com.portal.entity.ProcessInstance;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TaskApprovalPreparerBaselineTest {
    private static final String STORE = "dw:renamed_lines";
    private static final List<Map<String,Object>> SAVED = List.of(
            Map.of("code", "A", "node", "saved", "_wsRowVersion", 2));

    @Test void omittedStoreUsesPersistedRowsBeforeDisplayHydration() {
        prepare(Map.of("__subTables__", Map.of("dw:files", List.of())), SAVED);
    }
    @Test void completelyOmittedTablesAlsoUsePersistedRows() {
        prepare(Map.of(), SAVED);
    }
    @Test void explicitRowsRemainSubjectToTheWriteGuard() {
        List<Map<String,Object>> explicit = List.of(Map.of("code", "A", "node", "client-change"));
        prepare(Map.of("__subTables__", Map.of(STORE, explicit)), explicit);
    }
    private void prepare(Map<String,Object> input, List<Map<String,Object>> expected) {
        var owner = mock(TaskApprovalCompletionComponent.class, RETURNS_DEEP_STUBS);
        when(owner.engine().isAvailable()).thenReturn(true);
        when(owner.taskForms().prepareSubTableSubmission(anyString(), anyString(), anyMap(), anyString(), any(), any(), any()))
                .thenAnswer(inv -> new java.util.LinkedHashMap<>(inv.<Map<String,Object>>getArgument(2)));
        when(owner.taskForms().filterVersionedSubmission(anyString(), anyString(), anyMap()))
                .thenAnswer(inv -> new java.util.LinkedHashMap<>(inv.<Map<String,Object>>getArgument(2)));
        var instance = new ProcessInstance();
        instance.setVariables(Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of(),
                "__subTables__", Map.of(STORE, SAVED)));
        when(owner.instances().findById("process")).thenReturn(Optional.of(instance));
        var collection = spy(new MiCollectionVariableBuilder(null, null));
        doNothing().when(collection).injectMiCollectionFromBpmn(any(), any(), any(), anyMap());
        when(owner.miCollections()).thenReturn(collection);
        doAnswer(inv -> {
            Map<String,Object> variables = inv.getArgument(2);
            assertThat(((Map<?,?>)variables.get("__subTables__")).get(STORE)).isEqualTo(expected);
            return null;
        }).when(owner).isolateOutboundSubTables(any(), any(), anyMap(), anyBoolean());
        TaskInfo task = new TaskInfo();
        task.setTaskId("task"); task.setProcessInstanceId("process"); task.setTaskDefinitionKey("stage");
        task.setVariables(Map.of("__subTables__", Map.of(STORE,
                List.of(Map.of("code", "A", "node", "display-only", "_wsRowVersion", 2)))));
        TaskCompleteRequest request = new TaskCompleteRequest();
        request.setFormData(input); request.setAction("APPROVE");
        new TaskApprovalPreparer(owner, false).prepare(task, request, "actor", "actor");
        verify(owner).isolateOutboundSubTables(any(), any(), anyMap(), anyBoolean());
    }
}
