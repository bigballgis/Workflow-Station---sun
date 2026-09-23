package com.portal.component;

import com.portal.dto.SubTableBindingScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MiSubTableScopeAwareMergeTest {

    private static final String FU = "fu-test";

    @Test
    void classify_mixedWhenMainAndNestedFiltersShareATable() {
        assertThat(MiSubTableScopeAwareMerge.classify(List.of(
                new MiSubTableScopeAwareMerge.ScopeFilter("101", "main_id", "MAIN", "structuralFk"),
                new MiSubTableScopeAwareMerge.ScopeFilter("202", "sub_task_id", "SUB", "structuralFk"))))
                .isEqualTo(MiSubTableScopeAwareMerge.ScopeKind.MIXED);
    }

    @Test
    void classify_sharedWhenEveryScopeFilterTargetsMain() {
        assertThat(MiSubTableScopeAwareMerge.classify(List.of(
                new MiSubTableScopeAwareMerge.ScopeFilter("101", "main_id", "MAIN", "structuralFk"))))
                .isEqualTo(MiSubTableScopeAwareMerge.ScopeKind.SHARED);
    }

    @Test
    void classify_participantWhenEveryScopeFilterIsNested() {
        assertThat(MiSubTableScopeAwareMerge.classify(List.of(
                new MiSubTableScopeAwareMerge.ScopeFilter("202", "sub_task_id", "SUB", "structuralFk"))))
                .isEqualTo(MiSubTableScopeAwareMerge.ScopeKind.PARTICIPANT);
    }

    @Test
    void classify_throwsWhenFilterTargetMissing() {
        assertThatThrownBy(() -> MiSubTableScopeAwareMerge.classify(List.of(
                new MiSubTableScopeAwareMerge.ScopeFilter("101", "main_id", null, "structuralFk"))))
                .hasMessageContaining("no declared filter FK target");
    }

    @Test
    void mergeMixed_throwsWhenNestedFilterColumnMissing() {
        assertThatThrownBy(() -> MiSubTableScopeAwareMerge.mergeMixed(
                List.of(), List.of(), Map.of("id_idw", "Test-000002"),
                List.of(scope("101", List.of(), false), scope("202", List.of(), false)),
                List.of(
                        new MiSubTableScopeAwareMerge.ScopeFilter("101", "main_idva", "MAIN", "structuralFk"),
                        new MiSubTableScopeAwareMerge.ScopeFilter("202", null, "SUB", "structuralFk"))))
                .hasMessageContaining("nested filter FK");
    }

    @Test
    void mergeMixed_keepsSiblingParticipantRowAtBaseline() {
        Map<String, Object> shared = file("att-1", "Meeting-1", null, "notes.pdf");
        Map<String, Object> ours = file("att-2", "Meeting-1", "Test-000002", "mine.pdf");
        Map<String, Object> sibling = file("att-3", "Meeting-1", "Test-000001", "theirs.pdf");
        Map<String, Object> siblingThin = file("att-3", "Meeting-1", "Test-000001", "");

        List<Object> submitted = new ArrayList<>(List.of(shared, ours, siblingThin));
        List<Object> baseline = new ArrayList<>(List.of(
                file("att-1", "Meeting-1", null, "notes-old.pdf"), sibling));

        List<Object> merged = MiSubTableScopeAwareMerge.mergeMixed(
                submitted, baseline, Map.of("id_idw", "Test-000002"),
                List.of(
                        scope("101", List.of(Map.of("idfa", "att-1"), Map.of("idfa", "att-2"),
                                Map.of("idfa", "att-3")), false),
                        scope("202", List.of(Map.of("idfa", "att-2")), false)),
                List.of(
                        new MiSubTableScopeAwareMerge.ScopeFilter("101", "main_idva", "MAIN", "structuralFk"),
                        new MiSubTableScopeAwareMerge.ScopeFilter("202", "sub_task_id", "SUB", "structuralFk")));

        assertThat(merged).containsExactlyInAnyOrder(shared, ours, sibling);
        assertThat(merged).doesNotContain(siblingThin);
    }

    @Test
    void mergeMixed_appendsCurrentParticipantRowMissingFromBaseline() {
        Map<String, Object> ours = file("att-9", "Meeting-1", "Test-000002", "new.pdf");
        List<Object> merged = MiSubTableScopeAwareMerge.mergeMixed(
                List.of(ours), List.of(), Map.of("id_idw", "Test-000002"),
                List.of(scope("202", List.of(Map.of("idfa", "att-9")), false)),
                List.of(
                        new MiSubTableScopeAwareMerge.ScopeFilter("101", "main_idva", "MAIN", "structuralFk"),
                        new MiSubTableScopeAwareMerge.ScopeFilter("202", "sub_task_id", "SUB", "structuralFk")));

        assertThat(merged).containsExactly(ours);
    }

    @Test
    void mergeCurrentRowOnly_usesScopesInsteadOfFailingOnMixedTableFilters() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        stubScopeFilters(jdbc, 101L, "main_idva", "MAIN", 202L, "sub_task_id", "SUB");
        MiSubTaskSubTableRowMerger merger = new MiSubTaskSubTableRowMerger(jdbc);

        Map<String, Object> ours = file("att-2", "Meeting-1", "Test-000002", "mine.pdf");
        Map<String, Object> sibling = file("att-3", "Meeting-1", "Test-000001", "theirs.pdf");
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:attachment", new ArrayList<>(List.of(ours)));
        Map<String, Object> baseline = new HashMap<>();
        baseline.put("dw:attachment", new ArrayList<>(List.of(sibling)));

        Map<String, Object> merged = merger.mergeCurrentRowOnly(
                submitted, baseline, Map.of("id_idw", "Test-000002"), java.util.Set.of(),
                List.of(scope("101", List.of(), false), scope("202", List.of(Map.of("idfa", "att-2")), false)),
                FU);

        @SuppressWarnings("unchecked")
        List<Object> out = (List<Object>) merged.get("dw:attachment");
        assertThat(out).containsExactlyInAnyOrder(ours, sibling);
    }

    @Test
    void mergeCurrentRowOnly_withoutScopesStillRejectsMixedTableFilters() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("filter_fk_field_id"), eq(String.class), eq("attachment")))
                .thenReturn(List.of("MAIN", "SUB"));
        MiSubTaskSubTableRowMerger merger = new MiSubTaskSubTableRowMerger(jdbc);

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:attachment", new ArrayList<>(List.of(file("att-1", "Meeting-1", null, "notes.pdf"))));

        assertThatThrownBy(() ->
                merger.mergeCurrentRowOnly(submitted, Map.of(), Map.of("id_idw", "Test-000003")))
                .hasMessageContaining("own row");
    }

    @Test
    void mergeCurrentRowOnly_thisFormMainOnlyPassesThroughWithoutTableClassifier() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(contains("mi-scope-binding-filter"), any(RowMapper.class), eq(101L), eq(FU)))
                .thenAnswer(invocation -> {
                    try {
                        RowMapper<Object> mapper = invocation.getArgument(1);
                        return List.of(mapper.mapRow(filterRs("101", "main_idva", "MAIN"), 0));
                    } catch (java.sql.SQLException ex) {
                        throw new IllegalStateException(ex);
                    }
                });
        MiSubTaskSubTableRowMerger merger = new MiSubTaskSubTableRowMerger(jdbc);

        Map<String, Object> row = file("att-1", "Meeting-1", null, "notes.pdf");
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:attachment", new ArrayList<>(List.of(row)));

        Map<String, Object> merged = merger.mergeCurrentRowOnly(
                submitted, Map.of(), Map.of("id_idw", "Test-000003"), java.util.Set.of(),
                List.of(scope("101", List.of(Map.of("idfa", "att-1")), false)), FU);

        @SuppressWarnings("unchecked")
        List<Object> out = (List<Object>) merged.get("dw:attachment");
        assertThat(out).containsExactly(row);
        verify(jdbc, never()).queryForList(contains("filter_fk_field_id"), eq(String.class), eq("attachment"));
    }

    @Test
    void mergeCurrentRowOnly_scopesWithoutFunctionUnitFailLoud() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MiSubTaskSubTableRowMerger merger = new MiSubTaskSubTableRowMerger(jdbc);
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:attachment", new ArrayList<>(List.of(file("att-1", "Meeting-1", null, "notes.pdf"))));

        assertThatThrownBy(() -> merger.mergeCurrentRowOnly(
                submitted, Map.of(), Map.of("id_idw", "Test-000003"), java.util.Set.of(),
                List.of(scope("101", List.of(), false))))
                .hasMessageContaining("function unit");
    }

    private static SubTableBindingScope scope(
            String bindingId, List<Map<String, Object>> rowKeys, boolean emptied) {
        SubTableBindingScope s = new SubTableBindingScope();
        s.setBindingId(bindingId);
        s.setStoreKey("dw:attachment");
        s.setRowKeys(rowKeys);
        s.setEmptied(emptied);
        return s;
    }

    private static Map<String, Object> file(String id, String mainId, String participantId, String name) {
        Map<String, Object> r = new HashMap<>();
        r.put("idfa", id);
        r.put("main_idva", mainId);
        if (participantId != null) {
            r.put("sub_task_id", participantId);
        }
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
