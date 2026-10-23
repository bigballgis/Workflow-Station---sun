package com.portal.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.functionunit.CatalogSnapshotReader;
import com.portal.component.ChangeHistorySubmissionFilter;
import com.portal.component.ProcessFormComponent;
import com.portal.component.RequestIdEnricher;
import com.portal.component.TaskFormComponent;
import com.portal.component.TaskFormDefinitionLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Published metadata fixtures for tests of unrelated form/save/transaction contracts. */
public final class PortalVersionTestSupport {
    private PortalVersionTestSupport() { }
    public static void task(TaskFormComponent component) { task(component, null); }
    public static void task(TaskFormComponent component, Map<String, Object> definition) {
        var loader = mock(TaskFormDefinitionLoader.class);
        lenient().when(loader.fetchTaskFormByStageId(any(), any(), any())).thenAnswer(invocation -> definition == null ? null : new java.util.LinkedHashMap<>(definition));
        ReflectionTestUtils.setField(component, "formDefinitionLoader", loader);
        wireAudit(component, Map.of());
    }
    public static void process(ProcessFormComponent component) {
        var definition = Map.<String,Object>of("formName", "Published process form", "configJson", Map.of(),
                "tableBindings", List.of(), "fieldPermissions", Map.of());
        var snapshots = snapshots(definition);
        ReflectionTestUtils.setField(component, "catalogSnapshots", snapshots);
        wireAudit(component, definition);
    }
    private static CatalogSnapshotReader snapshots(Map<String, Object> definition) {
        var snapshots = mock(CatalogSnapshotReader.class);
        lenient().when(snapshots.processForm(anyString(), anyBoolean())).thenAnswer(invocation -> new java.util.LinkedHashMap<>(definition));
        return snapshots;
    }
    private static void wireAudit(Object component, Map<String, Object> definition) {
        // These tests model explicitly formless task nodes; snapshot routing itself has dedicated tests.
        var metadata = mock(JdbcTemplate.class);
        lenient().when(metadata.queryForList(contains("function_unit_catalog_id FROM up_process_instance"),
                eq(String.class), any())).thenReturn(List.of("release-v1"));
        lenient().when(metadata.queryForList(contains("content_type = 'PROCESS'"), eq(String.class), any()))
                .thenReturn(List.of("<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"><process id=\"fixture\"/></definitions>"));
        var audit = new ChangeHistorySubmissionFilter(metadata, new ObjectMapper());
        ReflectionTestUtils.setField(audit, "versionSnapshots", snapshots(definition));
        ReflectionTestUtils.setField(component, "changeHistorySubmissionFilter", audit);
        if (ReflectionTestUtils.getField(component, "requestIdEnricher") == null)
            ReflectionTestUtils.setField(component, "requestIdEnricher", mock(RequestIdEnricher.class));
    }
    public static void publishedTask(Object component, JdbcTemplate jdbc, String process, String stage,
                                     String source, Map<String, Object> definition) {
        var mapper = new ObjectMapper();
        String xml = "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:custom=\"http://workflow.platform/schema/custom\"><process id=\"fixture\"><userTask id=\""
                +stage+"\"><extensionElements><custom:properties><custom:property name=\"formId\" value=\""
                +source+"\"/></custom:properties></extensionElements></userTask></process></definitions>";
        lenient().when(jdbc.queryForList(contains("function_unit_catalog_id FROM up_process_instance"), eq(String.class), eq(process)))
                .thenReturn(List.of("release-v1"));
        lenient().when(jdbc.queryForList(contains("content_type = 'PROCESS'"), eq(String.class), eq("release-v1")))
                .thenReturn(List.of(xml));
        lenient().when(jdbc.query(contains("content_type = 'FORM'"), any(org.springframework.jdbc.core.RowMapper.class),
                eq("release-v1"), eq(source))).thenAnswer(inv -> List.of(new java.util.LinkedHashMap<>(definition)));
        var snapshots = mock(CatalogSnapshotReader.class);
        lenient().when(snapshots.form("release-v1", source)).thenReturn(
                Map.of("tableBindings", definition.getOrDefault("tableBindings", List.of())));
        lenient().when(snapshots.formBindings(eq("release-v1"), anyMap())).thenReturn(
                (List<Map<String, Object>>) definition.getOrDefault("tableBindings", List.of()));
        if (component instanceof TaskFormComponent task) {
            var loader = new TaskFormDefinitionLoader(mock(org.springframework.web.client.RestTemplate.class), mapper, jdbc);
            ReflectionTestUtils.setField(loader, "versionSnapshots", snapshots);
            ReflectionTestUtils.setField(task, "formDefinitionLoader", loader);
            var audit = new ChangeHistorySubmissionFilter(jdbc, mapper);
            ReflectionTestUtils.setField(audit, "versionSnapshots", snapshots);
            ReflectionTestUtils.setField(task, "changeHistorySubmissionFilter", audit);
        } else {
            ReflectionTestUtils.setField(component, "versionSnapshots", snapshots);
        }
    }

}
