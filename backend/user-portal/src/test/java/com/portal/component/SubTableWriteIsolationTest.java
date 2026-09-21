package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.dto.ProcessStartRequest;
import com.portal.dto.SubTableBindingScope;
import com.portal.dto.TaskCompleteRequest;
import com.portal.dto.TaskInfo;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubTableWriteIsolationTest {

    private static final String FU = "fu-test";
    private static final String STORE = "dw:attachment";

    @Test
    void frozenMultiBindingCannotOptOutByOmittingScopes() {
        SubTableWriteDesign design = mock(SubTableWriteDesign.class);
        when(design.resolve("pin", "review")).thenReturn(Map.of(
                "101", new SubTableWriteDesign.Binding("101", STORE, "main_fk", "MAIN", "cases",
                        List.of("id"), List.of("id"), "structuralFk"),
                "202", new SubTableWriteDesign.Binding("202", STORE, "party_fk", "SUB", "parties",
                        List.of("id"), List.of("id"), "structuralFk")));
        var isolation = new SubTableWriteIsolation(mock(MiSubTaskSubTableRowMerger.class),
                mock(SubTableBindingScopeGuard.class), design);
        assertThatThrownBy(() -> isolation.apply(new SubTableWriteIsolation.Request(
                Map.of(), new HashMap<>(Map.of("__subTables__", Map.of(STORE, List.of()))),
                Map.of(), List.of(), List.of(), FU, "pin", "review")))
                .isInstanceOf(com.portal.exception.PortalException.class).hasMessageContaining("require binding scopes");
    }

    @Test
    @SuppressWarnings("unchecked")
    void apply_miThinCompletePayloadKeepsSiblingAtBaseline() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        stubScopeFilters(jdbc, 101L, "main_idva", "MAIN", 202L, "sub_task_id", "SUB");
        SubTableBindingScopeGuard guard = mock(SubTableBindingScopeGuard.class);
        SubTableWriteIsolation isolation = new SubTableWriteIsolation(
                new MiSubTaskSubTableRowMerger(jdbc), guard);

        Map<String, Object> ours = file("att-2", "Meeting-1", "Test-000002", "mine.pdf");
        Map<String, Object> sibling = file("att-3", "Meeting-1", "Test-000001", "theirs.pdf");
        Map<String, Object> inbound = new HashMap<>();
        inbound.put("__subTables__", new LinkedHashMap<>(Map.of(STORE, new ArrayList<>(List.of(ours)))));
        Map<String, Object> baselineVars = Map.of(
                "__subTables__", Map.of(STORE, new ArrayList<>(List.of(sibling))));
        List<SubTableBindingScope> scopes = List.of(
                scope("101", List.of(), false),
                scope("202", List.of(Map.of("idfa", "att-2")), false));

        isolation.apply(new SubTableWriteIsolation.Request(
                miForm(), inbound, baselineVars, List.of(), scopes, FU));

        List<Object> out = (List<Object>) ((Map<String, Object>) inbound.get("__subTables__")).get(STORE);
        assertThat(out).containsExactlyInAnyOrder(ours, sibling);
        verify(guard).assertAndApply(eq(scopes), eq(FU), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void apply_withoutCurrentItemLeavesThinSliceUnmerged() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SubTableBindingScopeGuard guard = mock(SubTableBindingScopeGuard.class);
        SubTableWriteIsolation isolation = new SubTableWriteIsolation(
                new MiSubTaskSubTableRowMerger(jdbc), guard);

        Map<String, Object> ours = file("att-2", "Meeting-1", "Test-000002", "mine.pdf");
        Map<String, Object> sibling = file("att-3", "Meeting-1", "Test-000001", "theirs.pdf");
        Map<String, Object> inbound = new HashMap<>();
        inbound.put("__subTables__", new LinkedHashMap<>(Map.of(STORE, new ArrayList<>(List.of(ours)))));
        Map<String, Object> baselineVars = Map.of(
                "__subTables__", Map.of(STORE, new ArrayList<>(List.of(sibling))));

        isolation.apply(new SubTableWriteIsolation.Request(
                Map.of("name", "plain"), inbound, baselineVars, List.of(), List.of(), FU));

        List<Object> out = (List<Object>) ((Map<String, Object>) inbound.get("__subTables__")).get(STORE);
        assertThat(out).containsExactly(ours);
        verify(guard, never()).assertAndApply(any(), any(), any(), any(), any());
        verify(jdbc, never()).query(any(String.class), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void apply_miBrokenRowKeyFailsLoud() {
        SubTableWriteIsolation isolation = new SubTableWriteIsolation(
                new MiSubTaskSubTableRowMerger(mock(JdbcTemplate.class)),
                mock(SubTableBindingScopeGuard.class));
        Map<String, Object> inbound = new HashMap<>();
        inbound.put("__subTables__", new LinkedHashMap<>(Map.of(STORE, List.of())));
        Map<String, Object> formData = new HashMap<>();
        formData.put("_currentItem", Map.of("rowKey", Map.of()));

        assertThatThrownBy(() -> isolation.apply(new SubTableWriteIsolation.Request(
                formData, inbound, Map.of(), List.of(), List.of(), FU)))
                .hasMessageContaining("Unable to resolve this multi-instance sub-task's own row");
    }

    @Test
    @SuppressWarnings("unchecked")
    void isolateOutboundSubTables_usesProcessBaselineBeforeCompletePutAll() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        stubScopeFilters(jdbc, 101L, "main_idva", "MAIN", 202L, "sub_task_id", "SUB");
        SubTableWriteIsolation isolation = new SubTableWriteIsolation(
                new MiSubTaskSubTableRowMerger(jdbc), mock(SubTableBindingScopeGuard.class));

        Map<String, Object> ours = file("att-2", "Meeting-1", "Test-000002", "mine.pdf");
        Map<String, Object> sibling = file("att-3", "Meeting-1", "Test-000001", "theirs.pdf");
        ProcessInstance process = new ProcessInstance();
        process.setFunctionUnitCode(FU);
        process.setVariables(Map.of("__subTables__", Map.of(STORE, new ArrayList<>(List.of(sibling)))));
        ProcessInstanceRepository repo = mock(ProcessInstanceRepository.class);
        when(repo.findById("pi-1")).thenReturn(Optional.of(process));

        TaskApprovalCompletionComponent completion = new TaskApprovalCompletionComponent(
                null, null, repo, null, null, null, null, null);
        ReflectionTestUtils.setField(completion, "subTableWriteIsolation", isolation);

        TaskInfo task = new TaskInfo();
        task.setProcessInstanceId("pi-1");
        Map<String, Object> variables = new HashMap<>();
        variables.put("__subTables__", new LinkedHashMap<>(Map.of(STORE, new ArrayList<>(List.of(ours)))));
        TaskCompleteRequest request = TaskCompleteRequest.builder()
                .taskId("t-1")
                .action("APPROVE")
                .formData(miFormWithSubTables(ours))
                .subTableBindingScopes(List.of(
                        scope("101", List.of(), false),
                        scope("202", List.of(Map.of("idfa", "att-2")), false)))
                .build();

        completion.isolateOutboundSubTables(task, request, variables);

        List<Object> out = (List<Object>) ((Map<String, Object>) variables.get("__subTables__")).get(STORE);
        assertThat(out).containsExactlyInAnyOrder(ours, sibling);
    }

    @Test
    void isolateStartSubTables_stripsTransportKeysAndGuardsAgainstEmptyBaseline() {
        SubTableBindingScopeGuard guard = mock(SubTableBindingScopeGuard.class);
        SubTableWriteIsolation isolation = new SubTableWriteIsolation(
                new MiSubTaskSubTableRowMerger(mock(JdbcTemplate.class)), guard);
        ProcessStartComponent start = newStartFacade();
        ReflectionTestUtils.setField(start, "subTableWriteIsolation", isolation);

        Map<String, Object> ours = file("att-2", "Meeting-1", "Test-000002", "mine.pdf");
        Map<String, Object> variables = new HashMap<>();
        variables.put("__subTables__", new LinkedHashMap<>(Map.of(STORE, new ArrayList<>(List.of(ours)))));
        variables.put("emptiedSubTableKeys", List.of("dw:leak"));
        variables.put("subTableBindingScopes", List.of("leak"));
        List<SubTableBindingScope> scopes = List.of(scope("101", List.of(Map.of("idfa", "att-2")), false));
        ProcessStartRequest request = ProcessStartRequest.builder()
                .subTableBindingScopes(scopes)
                .build();

        start.isolateStartSubTables(variables, request, FU);

        assertThat(variables).doesNotContainKeys("emptiedSubTableKeys", "subTableBindingScopes");
        verify(guard).assertAndApply(eq(scopes), eq(FU), eq(variables), any(), eq(Map.of()));
    }

    @Test
    void isolateStartSubTables_withoutScopesKeepsV1Path() {
        SubTableBindingScopeGuard guard = mock(SubTableBindingScopeGuard.class);
        SubTableWriteIsolation isolation = new SubTableWriteIsolation(
                new MiSubTaskSubTableRowMerger(mock(JdbcTemplate.class)), guard);
        ProcessStartComponent start = newStartFacade();
        ReflectionTestUtils.setField(start, "subTableWriteIsolation", isolation);

        Map<String, Object> variables = new HashMap<>();
        variables.put("__subTables__", new LinkedHashMap<>(Map.of(STORE, new ArrayList<>(List.of(
                file("att-2", "Meeting-1", "Test-000002", "mine.pdf"))))));

        start.isolateStartSubTables(variables, ProcessStartRequest.builder().build(), FU);

        verify(guard, never()).assertAndApply(any(), any(), any(), any(), any());
        assertThat(variables.get("__subTables__")).isInstanceOf(Map.class);
    }

    @Test
    void stripSubTableTransportMetadata_removesLeakedKeys() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("title", "keep");
        variables.put("emptiedSubTableKeys", List.of("dw:x"));
        variables.put("subTableBindingScopes", List.of());
        ProcessStartComponent.stripSubTableTransportMetadata(variables);
        assertThat(variables).containsOnlyKeys("title");
    }

    @Test
    void isolateProcessFormSubTables_usesProcessBaselineAndStripsNothingFromCallerCopy() {
        SubTableBindingScopeGuard guard = mock(SubTableBindingScopeGuard.class);
        SubTableWriteIsolation isolation = new SubTableWriteIsolation(
                new MiSubTaskSubTableRowMerger(mock(JdbcTemplate.class)), guard);
        ProcessFormComponent form = new ProcessFormComponent(
                null, null, null, new ObjectMapper(), null,
                com.portal.testsupport.PortalTransactionTestSupport.noopPlatformTransactionManager());
        ReflectionTestUtils.setField(form, "subTableWriteIsolation", isolation);

        Map<String, Object> ours = file("att-2", "Meeting-1", "Test-000002", "mine.pdf");
        Map<String, Object> sibling = file("att-3", "Meeting-1", "Test-000001", "theirs.pdf");
        Map<String, Object> inbound = new HashMap<>();
        inbound.put("__subTables__", new LinkedHashMap<>(Map.of(STORE, new ArrayList<>(List.of(ours)))));
        Map<String, Object> baselineVars = Map.of(
                "__subTables__", Map.of(STORE, new ArrayList<>(List.of(sibling))));
        List<SubTableBindingScope> scopes = List.of(
                scope("101", List.of(), false),
                scope("202", List.of(Map.of("idfa", "att-2")), false));

        form.isolateProcessFormSubTables(inbound, List.of(), scopes, baselineVars, FU);

        verify(guard).assertAndApply(eq(scopes), eq(FU), eq(inbound), any(), any());
    }

    @Test
    void takeBindingScopes_readsAndRemovesTransportKey() {
        ProcessFormComponent form = new ProcessFormComponent(
                null, null, null, new ObjectMapper(), null,
                com.portal.testsupport.PortalTransactionTestSupport.noopPlatformTransactionManager());
        Map<String, Object> inbound = new HashMap<>();
        inbound.put("title", "keep");
        inbound.put("subTableBindingScopes", List.of(Map.of(
                "bindingId", "101",
                "storeKey", STORE,
                "rowKeys", List.of(),
                "emptied", false)));
        List<SubTableBindingScope> scopes = form.takeBindingScopes(inbound);
        assertThat(scopes).hasSize(1);
        assertThat(scopes.get(0).getBindingId()).isEqualTo("101");
        assertThat(inbound).containsOnlyKeys("title");
    }

    private static ProcessStartComponent newStartFacade() {
        return new ProcessStartComponent(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static Map<String, Object> miForm() {
        Map<String, Object> form = new HashMap<>();
        form.put("_currentItem", Map.of("rowKey", Map.of("id_idw", "Test-000002")));
        return form;
    }

    private static Map<String, Object> miFormWithSubTables(Map<String, Object> ours) {
        Map<String, Object> form = miForm();
        form.put("__subTables__", new LinkedHashMap<>(Map.of(STORE, new ArrayList<>(List.of(ours)))));
        return form;
    }

    private static SubTableBindingScope scope(String bindingId, List<Map<String, Object>> rowKeys, boolean emptied) {
        SubTableBindingScope s = new SubTableBindingScope();
        s.setBindingId(bindingId);
        s.setStoreKey(STORE);
        s.setRowKeys(rowKeys);
        s.setEmptied(emptied);
        return s;
    }

    private static Map<String, Object> file(String id, String meeting, String participant, String name) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("idfa", id);
        r.put("main_idva", meeting);
        r.put("sub_task_id", participant);
        r.put("file", name);
        return r;
    }

    @SuppressWarnings("unchecked")
    private void stubScopeFilters(
            JdbcTemplate jdbc,
            long mainId, String mainField, String mainType,
            long nestedId, String nestedField, String nestedType) {
        when(jdbc.query(contains("mi-scope-binding-filter"), any(RowMapper.class), eq(mainId), eq(nestedId), eq(FU)))
                .thenAnswer(invocation -> {
                    try {
                        RowMapper<Object> mapper = invocation.getArgument(1);
                        return List.of(
                                mapper.mapRow(filterRs(String.valueOf(mainId), mainField, mainType), 0),
                                mapper.mapRow(filterRs(String.valueOf(nestedId), nestedField, nestedType), 1));
                    } catch (java.sql.SQLException ex) {
                        throw new IllegalStateException(ex);
                    }
                });
    }

    private java.sql.ResultSet filterRs(String bindingId, String field, String type)
            throws java.sql.SQLException {
        java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
        when(rs.getString("binding_id")).thenReturn(bindingId);
        when(rs.getString("filter_field")).thenReturn(field);
        when(rs.getString("ref_table_type")).thenReturn(type);
        when(rs.getString("link_mode")).thenReturn("structuralFk");
        when(rs.getString("table_name")).thenReturn("attachment");
        return rs;
    }
}
