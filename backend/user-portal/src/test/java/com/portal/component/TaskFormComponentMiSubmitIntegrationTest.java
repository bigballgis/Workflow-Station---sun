package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.client.WorkflowEngineClient;
import com.portal.entity.ProcessInstance;
import com.portal.exception.PortalException;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration coverage for {@link TaskFormComponent#submitTaskForm} through the actual production
 * call path (not just {@link MiSubTaskSubTableRowMerger} in isolation): MI row-level isolation
 * wiring, non-MI unchanged behavior, and the "PK resolution failure must abort with zero DB
 * mutation" guarantee — see docs/plans/mi-subtask-row-level-save-isolation.md's 【验证】 section,
 * which lists these three scenarios as required and previously untested at this level.
 */
class TaskFormComponentMiSubmitIntegrationTest {

    private static final String TASK_ID = "task-1";
    private static final String PROCESS_INSTANCE_ID = "process-1";
    private static final String STAGE_ID = "stage-1";

    private TaskFormComponent component(JdbcTemplate jdbcTemplate, WorkflowEngineClient workflowEngineClient) {
        return new TaskFormComponent(
                mock(ProcessFormComponent.class), mock(ChangeHistoryComponent.class),
                processInstanceRepository, workflowEngineClient,
                mock(RestTemplate.class), new ObjectMapper(), jdbcTemplate,
                com.portal.testsupport.PortalTransactionTestSupport.noopPlatformTransactionManager());
    }

    private final ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);

    /** No dw_form_stage_bindings row → fetchTaskFormByStageId returns null → every field passes through unfiltered. */
    private WorkflowEngineClient workflowEngineClientForTask() {
        WorkflowEngineClient client = mock(WorkflowEngineClient.class);
        when(client.isAvailable()).thenReturn(true);
        when(client.getTaskById(TASK_ID)).thenReturn(Optional.of(Map.of(
                "taskDefinitionKey", STAGE_ID,
                "processInstanceId", PROCESS_INSTANCE_ID)));
        return client;
    }

    private JdbcTemplate jdbcTemplateReturningNoFormBinding() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        // formDefinitionLoader().fetchTaskFormByStageId(...) queries dw_form_stage_bindings first;
        // an empty result (Mockito's default for an unstubbed List-returning call) makes it fall
        // through to null, so fieldPermissions is empty and filterEditableFields accepts everything.
        return jdbcTemplate;
    }

    private ProcessInstance processInstanceWithVariables(Map<String, Object> variables) {
        ProcessInstance pi = new ProcessInstance();
        pi.setId(PROCESS_INSTANCE_ID);
        pi.setFunctionUnitCode("fu-test");
        pi.setVariables(new HashMap<>(variables));
        return pi;
    }

    private static Map<String, Object> row(String idIdw, String name) {
        Map<String, Object> r = new HashMap<>();
        r.put("id_idw", idIdw);
        r.put("name", name);
        return r;
    }

    /**
     * Real MI loop-variable shape: the engine always writes a {@code rowKey} map built from the
     * designer-configured PK of the collection table (MiCollectionVariableBuilder /
     * SubTableDataInjector). The key set of that map is the authority on the PK columns — a bare
     * {@code rowId} is not accepted, since honouring it would mean guessing which column it names.
     */
    private static Map<String, Object> currentItem(String idIdw) {
        return Map.of("rowKey", Map.of("id_idw", idIdw));
    }

    private static Map<String, Object> currentItemWithRowKey(Map<String, Object> rowKey) {
        return Map.of("rowKey", rowKey);
    }

    @Test
    void submitTaskForm_twoMiParticipantsSaveSequentiallyWithoutClobberingEachOther() {
        JdbcTemplate jdbcTemplate = jdbcTemplateReturningNoFormBinding();
        TaskFormComponent taskFormComponent = component(jdbcTemplate, workflowEngineClientForTask());

        Map<String, Object> baseline = new HashMap<>();
        baseline.put("__subTables__", new HashMap<>(Map.of(
                "50539", new java.util.ArrayList<>(List.of(
                        row("Test-014", "A-original"),
                        row("Test-015", "B-original"))))));
        ProcessInstance processInstance = processInstanceWithVariables(baseline);
        when(processInstanceRepository.findById(PROCESS_INSTANCE_ID)).thenReturn(Optional.of(processInstance));

        // Participant A saves first: only sees its own row richly, B's row thinned to identity fields.
        Map<String, Object> formDataA = new HashMap<>();
        formDataA.put("_currentItem", currentItem("Test-014"));
        formDataA.put("__subTables__", new HashMap<>(Map.of(
                "50539", new java.util.ArrayList<>(List.of(
                        row("Test-014", "A-saved"),
                        Map.of("id_idw", "Test-015"))))));

        taskFormComponent.submitTaskForm(TASK_ID, "user-a", formDataA);

        assertThat(processInstance.getVariables()).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> afterA = (Map<String, Object>) processInstance.getVariables().get("__subTables__");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rowsAfterA = (List<Map<String, Object>>) afterA.get("50539");
        assertThat(rowsAfterA.stream().filter(r -> "Test-014".equals(r.get("id_idw"))).findFirst().orElseThrow().get("name"))
                .isEqualTo("A-saved");
        assertThat(rowsAfterA.stream().filter(r -> "Test-015".equals(r.get("id_idw"))).findFirst().orElseThrow().get("name"))
                .as("B's row must survive A's save untouched")
                .isEqualTo("B-original");

        // Participant B now saves, based on its own thin view — A's row here is stale/thinned.
        Map<String, Object> formDataB = new HashMap<>();
        formDataB.put("_currentItem", currentItem("Test-015"));
        formDataB.put("__subTables__", new HashMap<>(Map.of(
                "50539", new java.util.ArrayList<>(List.of(
                        Map.of("id_idw", "Test-014"),
                        row("Test-015", "B-saved"))))));

        taskFormComponent.submitTaskForm(TASK_ID, "user-b", formDataB);

        @SuppressWarnings("unchecked")
        Map<String, Object> afterB = (Map<String, Object>) processInstance.getVariables().get("__subTables__");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rowsAfterB = (List<Map<String, Object>>) afterB.get("50539");
        assertThat(rowsAfterB.stream().filter(r -> "Test-014".equals(r.get("id_idw"))).findFirst().orElseThrow().get("name"))
                .as("A's already-saved row must survive B's save, not be clobbered by B's thin/stale view of it")
                .isEqualTo("A-saved");
        assertThat(rowsAfterB.stream().filter(r -> "Test-015".equals(r.get("id_idw"))).findFirst().orElseThrow().get("name"))
                .isEqualTo("B-saved");

        verify(processInstanceRepository, org.mockito.Mockito.times(2)).save(processInstance);
    }

    @Test
    void submitTaskForm_miParticipantWithNonIdIdwPrimaryKeySavesInsteadOfBeingRejected() {
        // Regression for the reported "Unable to resolve this multi-instance sub-task's own row"
        // Save failure: the merger used to hardcode the MI collection PK as ["id_idw"], so a
        // sub-table whose Table Design PK is any other column could never resolve its own row and
        // every Save was refused — even though _currentItem.rowKey carried the correct key.
        JdbcTemplate jdbcTemplate = jdbcTemplateReturningNoFormBinding();
        TaskFormComponent taskFormComponent = component(jdbcTemplate, workflowEngineClientForTask());

        Map<String, Object> baseline = new HashMap<>();
        baseline.put("__subTables__", new HashMap<>(Map.of(
                "50539", new java.util.ArrayList<>(List.of(
                        new HashMap<>(Map.of("emp_no", "E-77", "name", "A-original")),
                        new HashMap<>(Map.of("emp_no", "E-88", "name", "B-original")))))));
        ProcessInstance processInstance = processInstanceWithVariables(baseline);
        when(processInstanceRepository.findById(PROCESS_INSTANCE_ID)).thenReturn(Optional.of(processInstance));

        Map<String, Object> formData = new HashMap<>();
        formData.put("_currentItem", currentItemWithRowKey(Map.of("emp_no", "E-88")));
        formData.put("__subTables__", new HashMap<>(Map.of(
                "50539", new java.util.ArrayList<>(List.of(
                        Map.of("emp_no", "E-77"),
                        new HashMap<>(Map.of("emp_no", "E-88", "name", "B-saved")))))));

        taskFormComponent.submitTaskForm(TASK_ID, "user-b", formData);

        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) processInstance.getVariables().get("__subTables__");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) after.get("50539");
        assertThat(rows.stream().filter(r -> "E-88".equals(r.get("emp_no"))).findFirst().orElseThrow().get("name"))
                .isEqualTo("B-saved");
        assertThat(rows.stream().filter(r -> "E-77".equals(r.get("emp_no"))).findFirst().orElseThrow().get("name"))
                .as("row isolation must still hold when the PK is not id_idw")
                .isEqualTo("A-original");
    }

    @Test
    void submitTaskForm_miSubmissionWithOnlyRowIdIsRejectedRatherThanGuessingThePkColumn() {
        JdbcTemplate jdbcTemplate = jdbcTemplateReturningNoFormBinding();
        TaskFormComponent taskFormComponent = component(jdbcTemplate, workflowEngineClientForTask());

        Map<String, Object> baseline = new HashMap<>();
        baseline.put("__subTables__", new HashMap<>(Map.of(
                "50539", new java.util.ArrayList<>(List.of(row("Test-014", "original"))))));
        ProcessInstance processInstance = processInstanceWithVariables(baseline);
        when(processInstanceRepository.findById(PROCESS_INSTANCE_ID)).thenReturn(Optional.of(processInstance));

        // A bare rowId does not say which column it is: treating it as id_idw is precisely the
        // guess that caused the original bug, so this fails closed instead.
        Map<String, Object> formData = new HashMap<>();
        formData.put("_currentItem", Map.of("rowId", "Test-014"));
        formData.put("__subTables__", new HashMap<>(Map.of(
                "50539", new java.util.ArrayList<>(List.of(row("Test-014", "SHOULD-NOT-BE-SAVED"))))));

        assertThatThrownBy(() -> taskFormComponent.submitTaskForm(TASK_ID, "user-a", formData))
                .isInstanceOf(PortalException.class)
                .satisfies(e -> assertThat(((PortalException) e).getCode()).isEqualTo("MI_ROW_KEY_UNRESOLVED"));

        assertThat(processInstance.getVariables()).isEqualTo(baseline);
        verify(processInstanceRepository, never()).save(any());
    }

    @Test
    void submitTaskForm_neverPersistsTheExecutionScopedMiLoopVariableProcessWide() {
        // Regression: _currentItem is EXECUTION-scoped (one per MI participant), but
        // up_process_instance.variables is a single process-wide blob. Persisting it there stamped
        // participant A's row identity onto the whole process, so participant B's task then loaded
        // A's row as "mine", edited the wrong row, and had its save rejected.
        JdbcTemplate jdbcTemplate = jdbcTemplateReturningNoFormBinding();
        TaskFormComponent taskFormComponent = component(jdbcTemplate, workflowEngineClientForTask());

        Map<String, Object> baseline = new HashMap<>();
        baseline.put("__subTables__", new HashMap<>(Map.of(
                "50539", new java.util.ArrayList<>(List.of(row("Test-014", "A-original"))))));
        ProcessInstance processInstance = processInstanceWithVariables(baseline);
        when(processInstanceRepository.findById(PROCESS_INSTANCE_ID)).thenReturn(Optional.of(processInstance));

        Map<String, Object> formData = new HashMap<>();
        formData.put("_currentItem", currentItem("Test-014"));
        formData.put("currentItem", currentItem("Test-014"));
        formData.put("__subTables__", new HashMap<>(Map.of(
                "50539", new java.util.ArrayList<>(List.of(row("Test-014", "A-saved"))))));

        taskFormComponent.submitTaskForm(TASK_ID, "user-a", formData);

        // Row isolation still applied (so the loop variable WAS honoured for this submission)...
        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) processInstance.getVariables().get("__subTables__");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) after.get("50539");
        assertThat(rows.get(0).get("name")).isEqualTo("A-saved");

        // ...but it must NOT be left behind in the shared process-wide variables.
        assertThat(processInstance.getVariables())
                .as("execution-scoped MI loop variable must never be persisted process-wide")
                .doesNotContainKey("_currentItem")
                .doesNotContainKey("currentItem");
    }

    @Test
    void submitTaskForm_nonMiSubmissionKeepsExistingWholeArrayReplaceBehavior() {
        JdbcTemplate jdbcTemplate = jdbcTemplateReturningNoFormBinding();
        TaskFormComponent taskFormComponent = component(jdbcTemplate, workflowEngineClientForTask());

        Map<String, Object> baseline = new HashMap<>();
        baseline.put("plainField", "old-value");
        baseline.put("__subTables__", new HashMap<>(Map.of(
                "60001", new java.util.ArrayList<>(List.of(row("R-1", "old-row"))))));
        ProcessInstance processInstance = processInstanceWithVariables(baseline);
        when(processInstanceRepository.findById(PROCESS_INSTANCE_ID)).thenReturn(Optional.of(processInstance));

        // No _currentItem/currentItem at all — a plain (non-MI) task submission.
        Map<String, Object> formData = new HashMap<>();
        formData.put("plainField", "new-value");
        formData.put("__subTables__", new HashMap<>(Map.of(
                "60001", new java.util.ArrayList<>(List.of(row("R-2", "whole-array-replaced"))))));

        taskFormComponent.submitTaskForm(TASK_ID, "user-x", formData);

        assertThat(processInstance.getVariables()).containsEntry("plainField", "new-value");
        @SuppressWarnings("unchecked")
        Map<String, Object> subTables = (Map<String, Object>) processInstance.getVariables().get("__subTables__");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) subTables.get("60001");
        // Legacy putAll semantics: the whole submitted array replaces the baseline outright,
        // R-1 is gone entirely — zero behavior change for non-MI submissions.
        assertThat(rows).extracting(r -> r.get("id_idw")).containsExactly("R-2");
    }

    @Test
    void submitTaskForm_unresolvableMiRowKeyAbortsWithZeroDbMutation() {
        JdbcTemplate jdbcTemplate = jdbcTemplateReturningNoFormBinding();
        TaskFormComponent taskFormComponent = component(jdbcTemplate, workflowEngineClientForTask());

        Map<String, Object> baseline = new HashMap<>();
        baseline.put("__subTables__", new HashMap<>(Map.of(
                "50539", new java.util.ArrayList<>(List.of(row("Test-014", "original"))))));
        ProcessInstance processInstance = processInstanceWithVariables(baseline);
        when(processInstanceRepository.findById(PROCESS_INSTANCE_ID)).thenReturn(Optional.of(processInstance));

        // _currentItem present (so this IS detected as an MI submission) but with no resolvable
        // row-key value at all — PK resolution must fail loudly, not fall back to whole-array replace.
        Map<String, Object> formData = new HashMap<>();
        formData.put("_currentItem", Map.of("someOtherField", "no-usable-pk"));
        formData.put("__subTables__", new HashMap<>(Map.of(
                "50539", new java.util.ArrayList<>(List.of(row("Test-014", "SHOULD-NOT-BE-SAVED"))))));

        assertThatThrownBy(() -> taskFormComponent.submitTaskForm(TASK_ID, "user-a", formData))
                .isInstanceOf(PortalException.class)
                .satisfies(e -> assertThat(((PortalException) e).getCode()).isEqualTo("MI_ROW_KEY_UNRESOLVED"));

        // Variables must be byte-for-byte unchanged — no partial write, no fallback putAll.
        assertThat(processInstance.getVariables()).isEqualTo(baseline);
        verify(processInstanceRepository, never()).save(any());
    }
}
