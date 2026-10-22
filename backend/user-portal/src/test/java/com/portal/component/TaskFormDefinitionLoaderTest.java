package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.functionunit.CatalogSnapshotReader;
import com.platform.common.functionunit.FunctionUnitVersionException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskFormDefinitionLoaderTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RestTemplate http = mock(RestTemplate.class);
    private final CatalogSnapshotReader snapshots = mock(CatalogSnapshotReader.class);
    private final TaskFormDefinitionLoader loader = new TaskFormDefinitionLoader(http, new ObjectMapper(), jdbc);
    private void published(String process, String catalog, String name) {
        ReflectionTestUtils.setField(loader, "versionSnapshots", snapshots);
        when(jdbc.queryForList(contains("function_unit_catalog_id FROM up_process_instance"), eq(String.class), eq(process)))
                .thenReturn(List.of(catalog));
        when(jdbc.queryForList(contains("content_type = 'PROCESS'"), eq(String.class), eq(catalog)))
                .thenReturn(List.of(bpmn("42")));
        when(jdbc.query(contains("content_type = 'FORM'"), any(RowMapper.class), eq(catalog), eq("42")))
                .thenReturn(List.of(new java.util.HashMap<>(Map.of("formName", name, "configJson", Map.of()))));
        when(snapshots.form(catalog, "42")).thenReturn(Map.of("tableBindings", List.of()));
    }
    @Test void sharedStageIdsResolveWithinEachAssignedRelease() {
        published("old", "v1", "Old Approval");published("new", "v2", "New Approval");
        assertThat(loader.fetchTaskFormByStageId("Review", "old", "unused")).containsEntry("formName", "Old Approval");
        assertThat(loader.fetchTaskFormByStageId("Review", "new", "unused")).containsEntry("formName", "New Approval");
        verifyNoInteractions(http);
        verify(jdbc, never()).query(contains("dw_form_stage_bindings"), any(RowMapper.class), any());
        verify(jdbc, never()).query(contains("fu.code = ?"), any(RowMapper.class), any(), any());
    }
    @Test void missingPinCannotUseUnscopedDesignerOrHttpFallback() {
        assertThatThrownBy(() -> loader.fetchTaskFormByStageId("Review", "missing", "unused"))
                .isInstanceOf(FunctionUnitVersionException.class);
        verifyNoInteractions(http, snapshots);
    }
    @Test void nullProcessCannotResolveTheCurrentlyActiveForm() {
        assertThatThrownBy(() -> loader.fetchTaskFormByStageId("Review", null, "unused"))
                .isInstanceOf(FunctionUnitVersionException.class);
        verifyNoInteractions(http, snapshots);
    }
    @Test void missingPublishedBpmnFailsClosed() {
        when(jdbc.queryForList(contains("function_unit_catalog_id FROM up_process_instance"), eq(String.class), eq("old")))
                .thenReturn(List.of("v1"));
        assertThatThrownBy(() -> loader.fetchTaskFormByStageId("Review", "old", "unused"))
                .isInstanceOf(FunctionUnitVersionException.class);
        verifyNoInteractions(http, snapshots);
    }
    @Test void explicitlyFormlessNodeUsesReadOnlyProcessReference() {
        when(jdbc.queryForList(contains("function_unit_catalog_id FROM up_process_instance"), eq(String.class), eq("old")))
                .thenReturn(List.of("v1"));
        when(jdbc.queryForList(contains("content_type = 'PROCESS'"), eq(String.class), eq("v1")))
                .thenReturn(List.of(bpmn(null)));
        assertThat(loader.fetchTaskFormByStageId("Review", "old", "unused")).isNull();
        verifyNoInteractions(http, snapshots);
    }
    private static String bpmn(String form) {
        return "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:custom=\"http://workflow.platform/schema/custom\">"
                + "<process id=\"fixture\"><userTask id=\"Review\">"
                + (form == null ? "" : "<extensionElements><custom:properties><custom:property name=\"formId\" value=\""+form+"\"/></custom:properties></extensionElements>")
                + "</userTask></process></definitions>";
    }
}
