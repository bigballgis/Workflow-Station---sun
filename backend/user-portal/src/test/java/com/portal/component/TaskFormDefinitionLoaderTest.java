package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The same BPMN node id legitimately exists in several function units — {@code dw_form_stage_bindings}
 * constrains only {@code UNIQUE(form_id, stage_id)}, and AI-generated processes reuse readable ids such as
 * {@code UserTask_Approve}. These tests pin the local (primary) resolution path to the function unit that
 * owns the process instance.
 */
class TaskFormDefinitionLoaderTest {

    private static final String SHARED_STAGE = "UserTask_Approve";
    private static final String PROCESS_INSTANCE = "proc-1";
    private static final String DW_URL = "http://developer-workstation:8082";

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final TaskFormDefinitionLoader loader =
            new TaskFormDefinitionLoader(restTemplate, new ObjectMapper(), jdbcTemplate);

    @Test
    void twoFunctionUnitsSharingStageId_eachResolvesItsOwnForm() {
        stubFunctionUnitCode("leave-proc", "fu-leave");
        stubFunctionUnitCode("expense-proc", "fu-expense");
        stubScopedBinding("fu-leave", form("Leave Approval"));
        stubScopedBinding("fu-expense", form("Expense Approval"));

        assertThat(loader.fetchTaskFormByStageId(SHARED_STAGE, "leave-proc", DW_URL))
                .containsEntry("formName", "Leave Approval");
        assertThat(loader.fetchTaskFormByStageId(SHARED_STAGE, "expense-proc", DW_URL))
                .containsEntry("formName", "Expense Approval");
    }

    @Test
    void resolvedFunctionUnitWithNoBinding_returnsNullInsteadOfAnotherUnitsForm() {
        stubFunctionUnitCode(PROCESS_INSTANCE, "fu-unbound");
        stubScopedBinding("fu-unbound");

        assertThat(loader.fetchTaskFormByStageId(SHARED_STAGE, PROCESS_INSTANCE, DW_URL)).isNull();
        // A resolved unit that binds no form is a definitive negative — no unscoped retry (the
        // single-argument query), no HTTP retry.
        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), eq(SHARED_STAGE));
        verify(restTemplate, never()).getForObject(anyString(), eq(Map.class));
    }

    @Test
    void unresolvableFunctionUnit_fallsBackToDeterministicUnscopedLookup() {
        // No up_process_instance row, or its code matches no dw_function_units row.
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(PROCESS_INSTANCE)))
                .thenReturn(List.of());
        when(jdbcTemplate.query(argThat(sql -> sql != null && !sql.contains("fu.code = ?")),
                any(RowMapper.class), eq(SHARED_STAGE)))
                .thenReturn(List.of(form("Newest Approval"), form("Older Approval")));

        assertThat(loader.fetchTaskFormByStageId(SHARED_STAGE, PROCESS_INSTANCE, DW_URL))
                .containsEntry("formName", "Newest Approval");
    }

    @Test
    void nullProcessInstance_staysUnscopedRatherThanFailing() {
        when(jdbcTemplate.query(argThat(sql -> sql != null && !sql.contains("fu.code = ?")),
                any(RowMapper.class), eq(SHARED_STAGE)))
                .thenReturn(List.of(form("Only Approval")));

        assertThat(loader.fetchTaskFormByStageId(SHARED_STAGE, null, DW_URL))
                .containsEntry("formName", "Only Approval");
    }

    @Test
    void httpFallback_carriesFunctionUnitCodeWhenLocalDbIsUnavailable() {
        stubFunctionUnitCode(PROCESS_INSTANCE, "fu-leave");
        when(jdbcTemplate.query(argThat(sql -> sql != null && sql.contains("fu.code = ?")),
                any(RowMapper.class), eq(SHARED_STAGE), eq("fu-leave")))
                .thenThrow(new IllegalStateException("relation does not exist"));
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("success", true,
                        "data", Map.of("form", Map.of("formName", "Leave Approval"))));

        assertThat(loader.fetchTaskFormByStageId(SHARED_STAGE, PROCESS_INSTANCE, DW_URL))
                .containsEntry("formName", "Leave Approval");
        verify(restTemplate).getForObject(
                argThat((String url) -> url.contains("stageId=" + SHARED_STAGE)
                        && url.contains("functionUnitCode=fu-leave")),
                eq(Map.class));
    }

    private void stubFunctionUnitCode(String processInstanceId, String functionUnitCode) {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(processInstanceId)))
                .thenReturn(List.of(functionUnitCode));
    }

    @SafeVarargs
    private void stubScopedBinding(String functionUnitCode, Map<String, Object>... rows) {
        when(jdbcTemplate.query(argThat(sql -> sql != null && sql.contains("fu.code = ?")),
                any(RowMapper.class), eq(SHARED_STAGE), eq(functionUnitCode)))
                .thenReturn(List.of(rows));
    }

    private Map<String, Object> form(String formName) {
        return Map.of("formName", formName, "configJson", Map.of(),
                "fieldPermissions", Map.of(), "readOnly", false);
    }
}
