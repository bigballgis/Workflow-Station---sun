package com.portal.component;

import com.portal.exception.PortalException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MI (multi-instance) sub-task row-level save isolation: two participants' sub-tasks saving in
 * sequence must never overwrite each other's row, and a submission whose own row can't be
 * resolved must fail outright rather than silently falling back to a whole-array replace.
 */
class MiSubTaskSubTableRowMergerTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final MiSubTaskSubTableRowMerger merger = new MiSubTaskSubTableRowMerger(jdbcTemplate);

    private static Map<String, Object> row(String idIdw, String name) {
        Map<String, Object> r = new HashMap<>();
        r.put("id_idw", idIdw);
        r.put("name", name);
        r.put("task_status", "IN_PROGRESS");
        return r;
    }

    /** Real MI loop-variable shape: the engine always writes a {@code rowKey} map built from the designer PK. */
    private static Map<String, Object> currentItem(String idIdw) {
        return Map.of("rowKey", Map.of("id_idw", idIdw));
    }

    private static Map<String, Object> miFormData(Object currentItemValue) {
        Map<String, Object> formData = new HashMap<>();
        formData.put("_currentItem", currentItemValue);
        return formData;
    }

    @Test
    void isMiSubTaskSubmission_trueWhenCurrentItemPresent() {
        assertThat(merger.isMiSubTaskSubmission(Map.of("_currentItem", currentItem("Test-1")))).isTrue();
        assertThat(merger.isMiSubTaskSubmission(Map.of("currentItem", currentItem("Test-1")))).isTrue();
    }

    @Test
    void isMiSubTaskSubmission_falseWhenAbsent() {
        assertThat(merger.isMiSubTaskSubmission(Map.of("name", "plain form"))).isFalse();
        assertThat(merger.isMiSubTaskSubmission(new HashMap<>())).isFalse();
    }

    @Test
    void requireResolvedRowKey_throwsWhenNullOrEmpty() {
        assertThatThrownBy(() -> merger.requireResolvedRowKey(null))
                .isInstanceOf(PortalException.class);
        assertThatThrownBy(() -> merger.requireResolvedRowKey(Map.of()))
                .isInstanceOf(PortalException.class);
    }

    @Test
    void requireResolvedRowKey_passesWhenResolved() {
        merger.requireResolvedRowKey(Map.of("id_idw", "Test-1"));
    }

    // --- resolveCurrentItemRowKey: rowKey is its own authority on the PK columns ---

    @Test
    void resolveCurrentItemRowKey_readsWhateverPkColumnTheDesignerConfigured() {
        // Regression: a hardcoded ["id_idw"] PK list made every sub-table whose PK is named
        // anything else resolve to null, failing the save with MI_ROW_KEY_UNRESOLVED even though
        // the correct key was present in rowKey all along.
        assertThat(merger.resolveCurrentItemRowKey(miFormData(Map.of("rowKey", Map.of("emp_no", "E-77")))))
                .containsExactly(Map.entry("emp_no", "E-77"));
        assertThat(merger.resolveCurrentItemRowKey(
                miFormData(Map.of("rowKey", Map.of("uuid_col", "3f2a-9c1b")))))
                .containsExactly(Map.entry("uuid_col", "3f2a-9c1b"));
    }

    @Test
    void resolveCurrentItemRowKey_keepsEveryColumnOfACompositeKey() {
        Map<String, Object> resolved = merger.resolveCurrentItemRowKey(miFormData(
                Map.of("rowKey", new java.util.LinkedHashMap<>(Map.of("bu_code", "FIN", "emp_no", "E-77")))));

        assertThat(resolved).containsOnly(Map.entry("bu_code", "FIN"), Map.entry("emp_no", "E-77"));
    }

    @Test
    void resolveCurrentItemRowKey_stillReadsPlainIdIdwKeys() {
        assertThat(merger.resolveCurrentItemRowKey(miFormData(currentItem("Test-014"))))
                .containsExactly(Map.entry("id_idw", "Test-014"));
        assertThat(merger.resolveCurrentItemRowKey(miFormData(Map.of("rowKey", Map.of("id", 8778)))))
                .containsExactly(Map.entry("id", 8778));
    }

    @Test
    void resolveCurrentItemRowKey_acceptsCurrentItemWithoutUnderscore() {
        Map<String, Object> formData = new HashMap<>();
        formData.put("currentItem", currentItem("Test-014"));

        assertThat(merger.resolveCurrentItemRowKey(formData))
                .containsExactly(Map.entry("id_idw", "Test-014"));
    }

    // --- resolveCurrentItemRowKey: no fallbacks — unresolvable shapes fail loud ---

    @Test
    void resolveCurrentItemRowKey_nullWhenRowKeyMapAbsentOrUnusable() {
        // rowId alone is NOT accepted: guessing which column it belongs to is exactly what broke.
        assertThat(merger.resolveCurrentItemRowKey(miFormData(Map.of("rowId", "Test-014")))).isNull();
        assertThat(merger.resolveCurrentItemRowKey(miFormData(Map.of("rowKey", Map.of())))).isNull();
        assertThat(merger.resolveCurrentItemRowKey(miFormData(Map.of("rowKey", "Test-014")))).isNull();
        assertThat(merger.resolveCurrentItemRowKey(miFormData("not-a-map"))).isNull();
        assertThat(merger.resolveCurrentItemRowKey(new HashMap<>())).isNull();
    }

    @Test
    void resolveCurrentItemRowKey_nullWhenAnyPkValueIsNullOrBlank() {
        Map<String, Object> nullValued = new HashMap<>();
        nullValued.put("emp_no", null);
        assertThat(merger.resolveCurrentItemRowKey(miFormData(Map.of("rowKey", nullValued)))).isNull();

        assertThat(merger.resolveCurrentItemRowKey(miFormData(Map.of("rowKey", Map.of("emp_no", "   "))))).isNull();

        Map<String, Object> partialComposite = new HashMap<>();
        partialComposite.put("bu_code", "FIN");
        partialComposite.put("emp_no", "");
        assertThat(merger.resolveCurrentItemRowKey(miFormData(Map.of("rowKey", partialComposite))))
                .as("a partial composite key would merge into the wrong participant's row")
                .isNull();
    }

    @Test
    void mergeCurrentRowOnly_isolatesRowsByANonIdIdwPrimaryKey() {
        Map<String, Object> rowA = new HashMap<>(Map.of("emp_no", "E-77", "name", "A-saved-value"));
        Map<String, Object> rowB = new HashMap<>(Map.of("emp_no", "E-88", "name", "B-old-value"));
        Map<String, Object> baseline = new HashMap<>();
        baseline.put("50539", new java.util.ArrayList<>(List.of(rowA, rowB)));

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("50539", new java.util.ArrayList<>(List.of(
                new HashMap<>(Map.of("emp_no", "E-77", "name", "STALE-should-be-ignored")),
                new HashMap<>(Map.of("emp_no", "E-88", "name", "B-new-value")))));

        Map<String, Object> merged = merger.mergeCurrentRowOnly(submitted, baseline, Map.of("emp_no", "E-88"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) merged.get("50539");
        assertThat(rows).hasSize(2);
        assertThat(rows.stream().filter(r -> "E-77".equals(r.get("emp_no"))).findFirst().orElseThrow().get("name"))
                .isEqualTo("A-saved-value");
        assertThat(rows.stream().filter(r -> "E-88".equals(r.get("emp_no"))).findFirst().orElseThrow().get("name"))
                .isEqualTo("B-new-value");
    }

    @Test
    void mergeCurrentRowOnly_isolatesRowsByACompositePrimaryKey() {
        Map<String, Object> baseline = new HashMap<>();
        baseline.put("50539", new java.util.ArrayList<>(List.of(
                new HashMap<>(Map.of("bu_code", "FIN", "emp_no", "E-77", "name", "A-saved-value")),
                new HashMap<>(Map.of("bu_code", "OPS", "emp_no", "E-77", "name", "B-old-value")))));

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("50539", new java.util.ArrayList<>(List.of(
                new HashMap<>(Map.of("bu_code", "FIN", "emp_no", "E-77", "name", "STALE-should-be-ignored")),
                new HashMap<>(Map.of("bu_code", "OPS", "emp_no", "E-77", "name", "B-new-value")))));

        Map<String, Object> rowKey = new java.util.LinkedHashMap<>(Map.of("bu_code", "OPS", "emp_no", "E-77"));
        Map<String, Object> merged = merger.mergeCurrentRowOnly(submitted, baseline, rowKey);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) merged.get("50539");
        assertThat(rows).hasSize(2);
        assertThat(rows.stream().filter(r -> "FIN".equals(r.get("bu_code"))).findFirst().orElseThrow().get("name"))
                .as("the row sharing emp_no but not bu_code must not be touched")
                .isEqualTo("A-saved-value");
        assertThat(rows.stream().filter(r -> "OPS".equals(r.get("bu_code"))).findFirst().orElseThrow().get("name"))
                .isEqualTo("B-new-value");
    }

    @Test
    void mergeCurrentRowOnly_emptySubmittedSliceLeavesBaselineUntouchedInsteadOfFailing() {
        // An empty slice makes no claim about any row — the task form simply renders no rows for
        // this alias key (MI isolation rebuilds the payload per binding, so sibling alias keys can
        // legitimately arrive empty). Treating that as "my row vanished" broke Save for every MI
        // sub-task whose form does not render the participants binding it was keyed under.
        Map<String, Object> baseline = new HashMap<>();
        baseline.put("50539", new java.util.ArrayList<>(List.of(
                row("Test-014", "A-saved"), row("Test-015", "B-saved"))));

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("50539", new java.util.ArrayList<>());

        Map<String, Object> merged = merger.mergeCurrentRowOnly(submitted, baseline, Map.of("id_idw", "Test-015"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) merged.get("50539");
        assertThat(rows).as("baseline stands untouched, nothing lost").hasSize(2);
        assertThat(rows.stream().map(r -> r.get("name")))
                .containsExactlyInAnyOrder("A-saved", "B-saved");
    }

    @Test
    void mergeCurrentRowOnly_throwsWhenOwnRowMissingFromSubmissionInsteadOfDiscardingTheSave() {
        Map<String, Object> baseline = new HashMap<>();
        baseline.put("50539", new java.util.ArrayList<>(List.of(row("Test-014", "old"))));

        // Submitted rows carry no id_idw at all — the own row cannot be located. Returning the
        // baseline here would silently drop everything the user typed and report success.
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("50539", new java.util.ArrayList<>(List.of(new HashMap<>(Map.of("name", "typed-but-lost")))));

        assertThatThrownBy(() -> merger.mergeCurrentRowOnly(submitted, baseline, Map.of("id_idw", "Test-014")))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining("refusing to save");
    }

    @Test
    void mergeCurrentRowOnly_secondParticipantSaveDoesNotOverwriteFirst() {
        // Baseline: participant A already saved a fresh name; participant B is still on the
        // pre-edit value.
        Map<String, Object> baseline = new HashMap<>();
        baseline.put("50539", new java.util.ArrayList<>(List.of(
                row("Test-014", "A-saved-value"),
                row("Test-015", "B-old-value"))));

        // B's own MI sub-task page only ever hydrates its own row richly; A's row is whatever the
        // page happened to carry (could be thin/stale — must be ignored either way).
        Map<String, Object> submittedByB = new HashMap<>();
        submittedByB.put("50539", new java.util.ArrayList<>(List.of(
                row("Test-014", "STALE-should-be-ignored"),
                row("Test-015", "B-new-value"))));

        Map<String, Object> rowKeyB = Map.of("id_idw", "Test-015");

        Map<String, Object> merged = merger.mergeCurrentRowOnly(submittedByB, baseline, rowKeyB);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) merged.get("50539");
        assertThat(rows).hasSize(2);
        Map<String, Object> rowA = rows.stream().filter(r -> "Test-014".equals(r.get("id_idw"))).findFirst().orElseThrow();
        Map<String, Object> rowB = rows.stream().filter(r -> "Test-015".equals(r.get("id_idw"))).findFirst().orElseThrow();
        assertThat(rowA.get("name")).isEqualTo("A-saved-value");
        assertThat(rowB.get("name")).isEqualTo("B-new-value");
    }

    @Test
    void mergeCurrentRowOnly_appendsNewParticipantRowNotYetInBaseline() {
        Map<String, Object> baseline = new HashMap<>();
        baseline.put("50539", new java.util.ArrayList<>(List.of(row("Test-014", "existing"))));

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("50539", new java.util.ArrayList<>(List.of(row("Test-999", "brand-new-participant"))));

        Map<String, Object> merged = merger.mergeCurrentRowOnly(submitted, baseline, Map.of("id_idw", "Test-999"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) merged.get("50539");
        assertThat(rows).hasSize(2);
        assertThat(rows.stream().map(r -> r.get("id_idw"))).containsExactlyInAnyOrder("Test-014", "Test-999");
    }

    @Test
    void mergeCurrentRowOnly_appliesSameMergeToEveryAliasKey() {
        Map<String, Object> baseline = new HashMap<>();
        baseline.put("50539", new java.util.ArrayList<>(List.of(row("Test-014", "old"), row("Test-015", "sibling-old"))));
        baseline.put("Participants", new java.util.ArrayList<>(List.of(row("Test-014", "old"), row("Test-015", "sibling-old"))));

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("50539", new java.util.ArrayList<>(List.of(row("Test-014", "new"), row("Test-015", "STALE"))));
        submitted.put("Participants", new java.util.ArrayList<>(List.of(row("Test-014", "new"), row("Test-015", "STALE"))));

        Map<String, Object> merged = merger.mergeCurrentRowOnly(submitted, baseline, Map.of("id_idw", "Test-014"));

        for (String key : List.of("50539", "Participants")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) merged.get(key);
            Map<String, Object> ownRow = rows.stream().filter(r -> "Test-014".equals(r.get("id_idw"))).findFirst().orElseThrow();
            Map<String, Object> siblingRow = rows.stream().filter(r -> "Test-015".equals(r.get("id_idw"))).findFirst().orElseThrow();
            assertThat(ownRow.get("name")).as("key=%s own row", key).isEqualTo("new");
            assertThat(siblingRow.get("name")).as("key=%s sibling row untouched", key).isEqualTo("sibling-old");
        }
    }

    @Test
    void mergeCurrentRowOnly_canonicalKeyNeedsNoSiblingPatching() {
        // Replaces the former "patchesStaleSiblingBindingIdKey..." test. A designer table used to get
        // one numeric key per binding (Assign Task / Sub task / Main / My Request variants) plus
        // table-name aliases — 9 keys for one table — so a submission carrying only some of them left
        // the rest stale, and this class had to patch the siblings back into line.
        //
        // With canonical keys a table has exactly ONE key (dw:<name>), so there is no sibling to
        // patch and no way for two copies of the same row to disagree. The merge is a plain
        // row-level merge on that single key.
        Map<String, Object> baseline = new HashMap<>();
        baseline.put("dw:subtable", new java.util.ArrayList<>(List.of(
                row("Test-014", "old"), row("Test-015", "sibling-old"))));

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:subtable", new java.util.ArrayList<>(List.of(
                row("Test-014", "new"), row("Test-015", "STALE-should-be-ignored"))));

        Map<String, Object> merged = merger.mergeCurrentRowOnly(submitted, baseline, Map.of("id_idw", "Test-014"));

        assertThat(merged).containsOnlyKeys("dw:subtable");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) merged.get("dw:subtable");
        assertThat(rows.stream().filter(r -> "Test-014".equals(r.get("id_idw"))).findFirst().orElseThrow().get("name"))
                .isEqualTo("new");
        assertThat(rows.stream().filter(r -> "Test-015".equals(r.get("id_idw"))).findFirst().orElseThrow().get("name"))
                .as("other participants' rows still come from the baseline, never the thin submission")
                .isEqualTo("sibling-old");
    }

    @Test
    void mergeCurrentRowOnly_stringAliasKeyHasNoSiblingLookup() {
        // Table-name aliases ("Participants") carry no binding id to resolve siblings from — the
        // sibling patch is keyed off numeric binding ids only. jdbcTemplate must not even be asked.
        Map<String, Object> baseline = new HashMap<>();
        baseline.put("Participants", new java.util.ArrayList<>(List.of(row("Test-014", "old"))));

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("Participants", new java.util.ArrayList<>(List.of(row("Test-014", "new"))));

        Map<String, Object> merged = merger.mergeCurrentRowOnly(submitted, baseline, Map.of("id_idw", "Test-014"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) merged.get("Participants");
        assertThat(rows.get(0).get("name")).isEqualTo("new");
    }

    @Test
    void mergeCurrentRowOnly_sharedProcessScopedBindingPassesThroughUnchangedNotRowMerged() {
        // Real-world shape: an MI sub-task's form also carries a shared attachment table (FK'd by
        // main_id to the whole request, not per-participant) alongside the MI participants table.
        // Row-scoping the attachment table by the current participant's PK would never find a
        // matching row and would silently drop every submitted attachment row (the exact bug this
        // classification exists to prevent).
        // 共享与否按**结构**判定：附件表的外键指向 MAIN 表（列名叫什么无关）。
        when(jdbcTemplate.queryForList(contains("ref.table_type"), eq(String.class), eq("attachment")))
                .thenReturn(List.of("MAIN"));

        Map<String, Object> attachmentRow = new HashMap<>();
        attachmentRow.put("id", "att-1");
        attachmentRow.put("file", "notes.pdf");

        Map<String, Object> baseline = new HashMap<>();
        baseline.put("dw:subtable", new java.util.ArrayList<>(List.of(row("Test-014", "old"))));
        baseline.put("dw:attachment", new java.util.ArrayList<>());

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:subtable", new java.util.ArrayList<>(List.of(row("Test-014", "new"))));
        List<Object> submittedAttachments = new java.util.ArrayList<>(List.of(attachmentRow));
        submitted.put("dw:attachment", submittedAttachments);

        Map<String, Object> merged = merger.mergeCurrentRowOnly(submitted, baseline, Map.of("id_idw", "Test-014"));

        // MI participant table: still row-merged as usual.
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> participantRows = (List<Map<String, Object>>) merged.get("dw:subtable");
        assertThat(participantRows.get(0).get("name")).isEqualTo("new");

        // Shared attachment table: passed through unchanged (not row-scoped, not silently dropped).
        assertThat(merged.get("dw:attachment")).isSameAs(submittedAttachments);
    }

    @Test
    void mergeCurrentRowOnly_nonArrayValuesPassThroughUnchanged() {
        Map<String, Object> baseline = new HashMap<>();
        Map<String, Object> submitted = new HashMap<>();
        Map<String, Object> nestedShape = Map.of("nested", "value");
        submitted.put("someRow", nestedShape);

        Map<String, Object> merged = merger.mergeCurrentRowOnly(submitted, baseline, Map.of("id_idw", "Test-014"));

        assertThat(merged.get("someRow")).isSameAs(nestedShape);
    }

    /**
     * Relation-table slice (`rt:<name>`) must pass through untouched.
     *
     * <p>Real incident: a Function Unit's MI task form also binds a relation table (`test`) and the
     * platform's virtual `sys_users` table. Relation tables are not designer sub-tables — no
     * participant FK, and legitimately no primary key. Treating their slice as "one MI participant's
     * rows" finds no row matching the participant PK and, because the slice is non-empty, fails the
     * whole submission: every Save on that task returned 500, even though the sub-task table's own
     * PK (`id_idwnn`) was configured and submitted correctly.
     */
    @Test
    void mergeCurrentRowOnly_relationTableSlicePassesThroughUnchanged() {
        Map<String, Object> relationRow = new HashMap<>();
        relationRow.put("id_idwnn", "Test-000002");   // a DIFFERENT row than the current participant
        relationRow.put("test", "r");

        // 该 FU 的子任务主键叫 id_idwnn（用户实际改过的名字），不是 helper 里的 id_idw
        Map<String, Object> ownOld = new HashMap<>();
        ownOld.put("id_idwnn", "Test-000001");
        ownOld.put("name", "old");
        Map<String, Object> ownNew = new HashMap<>();
        ownNew.put("id_idwnn", "Test-000001");
        ownNew.put("name", "new");

        Map<String, Object> baseline = new HashMap<>();
        baseline.put("dw:subtable", new java.util.ArrayList<>(List.of(ownOld)));

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:subtable", new java.util.ArrayList<>(List.of(ownNew)));
        submitted.put("rt:test", new java.util.ArrayList<>(List.of(relationRow)));

        Map<String, Object> merged =
                merger.mergeCurrentRowOnly(submitted, baseline, Map.of("id_idwnn", "Test-000001"));

        // Relation table: untouched, and it must NOT fail the save.
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rel = (List<Map<String, Object>>) merged.get("rt:test");
        assertThat(rel).hasSize(1);
        assertThat(rel.get(0).get("id_idwnn")).isEqualTo("Test-000002");

        // The MI sub-task table is still row-merged as usual.
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> own = (List<Map<String, Object>>) merged.get("dw:subtable");
        assertThat(own.get(0).get("name")).isEqualTo("new");
    }

    /** The MI sub-task table itself keeps its guard: a slice without our own row still fails loud. */
    @Test
    void mergeCurrentRowOnly_miSubTaskTableStillRejectsSliceMissingOwnRow() {
        when(jdbcTemplate.queryForList(contains("b.foreign_key_field"), eq(String.class), eq("subtable")))
                .thenReturn(List.of("id_idwnn"));

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:subtable", new java.util.ArrayList<>(List.of(row("Test-000002", "theirs"))));

        assertThatThrownBy(() ->
                merger.mergeCurrentRowOnly(submitted, Map.of(), Map.of("id_idwnn", "Test-000001")))
                .hasMessageContaining("own row");
    }

    /**
     * A relation table filed under a `dw:` key must still pass through.
     *
     * <p>The task-form API does not send `relationTableId`, so the frontend cannot distinguish a
     * relation table from a designer table and writes BOTH as `dw:<name>`. Measured on the live
     * incident: binding 50550 is relation table `test` yet its slice arrived as `dw:test`, and the
     * participant guard rejected it — failing every Save on that task.
     */
    @Test
    void mergeCurrentRowOnly_relationTableUnderDwKeyPassesThroughUnchanged() {
        // Not present in dw_table_definitions → not a designer sub-table.
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), eq("test")))
                .thenReturn(0);

        Map<String, Object> relationRow = new HashMap<>();
        relationRow.put("id_idwnn", "Test-000002");
        relationRow.put("test", "r");

        Map<String, Object> ownNew = new HashMap<>();
        ownNew.put("id_idwnn", "Test-000001");
        ownNew.put("name", "new");

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:subtable", new java.util.ArrayList<>(List.of(ownNew)));
        submitted.put("dw:test", new java.util.ArrayList<>(List.of(relationRow)));

        Map<String, Object> merged =
                merger.mergeCurrentRowOnly(submitted, Map.of(), Map.of("id_idwnn", "Test-000001"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rel = (List<Map<String, Object>>) merged.get("dw:test");
        assertThat(rel).hasSize(1);
        assertThat(rel.get(0).get("id_idwnn")).isEqualTo("Test-000002");
    }

    /**
     * A link-child (People-style) row belongs to its participant through a STRUCTURAL FK, not the
     * collection's primary key.
     *
     * <p>Live incident: `people` rows are {@code {id=<uuid>, sub_task_id=Test-000002, sex, age}} while
     * the collection key is {@code {id_idwnn: Test-000002}}. Matching the slice by pkCols never found
     * a row, so the "own row missing" guard rejected every Save on that sub-task.
     */
    @Test
    void mergeCurrentRowOnly_linkChildRowMatchedByStructuralForeignKey() {
        when(jdbcTemplate.queryForList(contains("b.foreign_key_field"), eq(String.class), eq("people")))
                .thenReturn(List.of("id"));
        // 设计器声明的外键列：判「这行属于哪个参与者」用它，不猜列名
        when(jdbcTemplate.queryForList(contains("f.is_foreign_key"), eq(String.class), eq("people")))
                .thenReturn(List.of("sub_task_id"));

        Map<String, Object> mine = new HashMap<>();
        mine.put("id", "d9f73b1a-fea2-45e0-af77-ca75edd984c0");
        mine.put("sub_task_id", "Test-000002");
        mine.put("age", "a");

        Map<String, Object> theirs = new HashMap<>();
        theirs.put("id", "0000aaaa-0000-0000-0000-00000000ffff");
        theirs.put("sub_task_id", "Test-000001");
        theirs.put("age", "keep-me");

        Map<String, Object> baseline = new HashMap<>();
        baseline.put("dw:people", new java.util.ArrayList<>(List.of(theirs)));

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:people", new java.util.ArrayList<>(List.of(mine)));

        Map<String, Object> merged =
                merger.mergeCurrentRowOnly(submitted, baseline, Map.of("id_idwnn", "Test-000002"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) merged.get("dw:people");
        // Our own row is merged in...
        assertThat(rows).anySatisfy(r -> assertThat(r.get("age")).isEqualTo("a"));
        // ...and the other participant's row is preserved untouched.
        assertThat(rows).anySatisfy(r -> assertThat(r.get("age")).isEqualTo("keep-me"));
    }

    /**
     * A row pointing at ANOTHER participant is never merged in as ours.
     *
     * <p>It is also not an error: the slice simply carries a sibling's row while this participant has
     * none yet (see {@code sliceWithOnlyOtherParticipantsRowsLeavesBaselineAlone}). What must hold is
     * that the foreign row is never adopted — the baseline is returned unchanged.
     */
    @Test
    void mergeCurrentRowOnly_structuralForeignKeyOfAnotherParticipantIsNotAdopted() {
        when(jdbcTemplate.queryForList(contains("b.foreign_key_field"), eq(String.class), eq("people")))
                .thenReturn(List.of("id"));
        // 设计器声明的外键列：判「这行属于哪个参与者」用它，不猜列名
        when(jdbcTemplate.queryForList(contains("f.is_foreign_key"), eq(String.class), eq("people")))
                .thenReturn(List.of("sub_task_id"));

        Map<String, Object> foreign = new HashMap<>();
        foreign.put("id", "0000aaaa-0000-0000-0000-00000000ffff");
        foreign.put("sub_task_id", "Test-000001");

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:people", new java.util.ArrayList<>(List.of(foreign)));

        Map<String, Object> merged =
                merger.mergeCurrentRowOnly(submitted, Map.of(), Map.of("id_idwnn", "Test-000002"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) merged.get("dw:people");
        // Baseline was empty and the foreign row must NOT be adopted as ours.
        assertThat(rows).isEmpty();
    }

    /**
     * A participant may own SEVERAL link-child rows — all of them must survive the save.
     *
     * <p>A link-child table is not one-row-per-participant. Routing it through the single-row replace
     * kept only the first match and silently dropped the rest (data loss, worse than the original
     * rejection). This participant's whole set is replaced instead, and other participants' rows stay.
     */
    @Test
    void mergeCurrentRowOnly_participantOwningSeveralLinkChildRowsKeepsAllOfThem() {
        when(jdbcTemplate.queryForList(contains("b.foreign_key_field"), eq(String.class), eq("people")))
                .thenReturn(List.of("id"));
        // 设计器声明的外键列：判「这行属于哪个参与者」用它，不猜列名
        when(jdbcTemplate.queryForList(contains("f.is_foreign_key"), eq(String.class), eq("people")))
                .thenReturn(List.of("sub_task_id"));

        Map<String, Object> first = new HashMap<>();
        first.put("id", "aaaaaaaa-0000-0000-0000-000000000001");
        first.put("sub_task_id", "Test-000002");
        first.put("age", "first");

        Map<String, Object> second = new HashMap<>();
        second.put("id", "bbbbbbbb-0000-0000-0000-000000000002");
        second.put("sub_task_id", "Test-000002");
        second.put("age", "second");

        Map<String, Object> otherParticipant = new HashMap<>();
        otherParticipant.put("id", "cccccccc-0000-0000-0000-000000000003");
        otherParticipant.put("sub_task_id", "Test-000001");
        otherParticipant.put("age", "keep-me");

        Map<String, Object> staleOwn = new HashMap<>();
        staleOwn.put("id", "aaaaaaaa-0000-0000-0000-000000000001");
        staleOwn.put("sub_task_id", "Test-000002");
        staleOwn.put("age", "stale");

        Map<String, Object> baseline = new HashMap<>();
        baseline.put("dw:people", new java.util.ArrayList<>(List.of(otherParticipant, staleOwn)));

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:people", new java.util.ArrayList<>(List.of(first, second)));

        Map<String, Object> merged =
                merger.mergeCurrentRowOnly(submitted, baseline, Map.of("id_idwnn", "Test-000002"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) merged.get("dw:people");
        assertThat(rows).hasSize(3);
        assertThat(rows).anySatisfy(r -> assertThat(r.get("age")).isEqualTo("first"));
        assertThat(rows).anySatisfy(r -> assertThat(r.get("age")).isEqualTo("second"));
        // Another participant's row is untouched...
        assertThat(rows).anySatisfy(r -> assertThat(r.get("age")).isEqualTo("keep-me"));
        // ...and our own stale row is gone, not duplicated.
        assertThat(rows).noneSatisfy(r -> assertThat(r.get("age")).isEqualTo("stale"));
    }

    /**
     * Link-child rows are matched by whichever structural FK column the table actually uses —
     * {@code sub_task_id}, {@code participant_id}, … — including a mix within one slice.
     */
    @Test
    void mergeCurrentRowOnly_linkChildMatchedAcrossDifferentStructuralFkColumns() {
        when(jdbcTemplate.queryForList(contains("b.foreign_key_field"), eq(String.class), eq("people")))
                .thenReturn(List.of("id"));
        // 设计器声明的外键列：判「这行属于哪个参与者」用它，不猜列名
        // 本用例的表在设计器里声明了两个外键列，两条行各用其中之一指向同一参与者
        when(jdbcTemplate.queryForList(contains("f.is_foreign_key"), eq(String.class), eq("people")))
                .thenReturn(List.of("sub_task_id", "participant_id"));

        Map<String, Object> viaSubTask = new HashMap<>();
        viaSubTask.put("id", "aaaaaaaa-0000-0000-0000-000000000001");
        viaSubTask.put("sub_task_id", "Test-000002");
        viaSubTask.put("age", "a");

        Map<String, Object> viaParticipant = new HashMap<>();
        viaParticipant.put("id", "bbbbbbbb-0000-0000-0000-000000000002");
        viaParticipant.put("participant_id", "Test-000002");
        viaParticipant.put("age", "b");

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:people", new java.util.ArrayList<>(List.of(viaSubTask, viaParticipant)));

        Map<String, Object> merged =
                merger.mergeCurrentRowOnly(submitted, Map.of(), Map.of("id_idwnn", "Test-000002"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) merged.get("dw:people");
        assertThat(rows).hasSize(2);
    }

    /**
     * An UNDECLARED empty slice leaves the baseline untouched — including for link-child tables.
     *
     * <p>An empty slice is ambiguous on its own: it can mean "the user deleted their rows" OR "this
     * binding was not rendered / MI isolation rebuilt the payload without it". Deleting on that
     * signal alone would wipe rows a form never showed, so the baseline stands.
     *
     * <p>The user's actual intent is carried separately, by {@code explicitlyEmptiedKeys} — see
     * {@link #mergeCurrentRowOnly_declaredEmptyLinkChildSliceDeletesOnlyMyRows()}.
     */
    @Test
    void mergeCurrentRowOnly_emptyLinkChildSliceKeepsBaselineRatherThanDeleting() {
        when(jdbcTemplate.queryForList(contains("b.foreign_key_field"), eq(String.class), eq("people")))
                .thenReturn(List.of("id"));
        // 设计器声明的外键列：判「这行属于哪个参与者」用它，不猜列名
        when(jdbcTemplate.queryForList(contains("f.is_foreign_key"), eq(String.class), eq("people")))
                .thenReturn(List.of("sub_task_id"));

        Map<String, Object> mineOld = new HashMap<>();
        mineOld.put("id", "aaaaaaaa-0000-0000-0000-000000000001");
        mineOld.put("sub_task_id", "Test-000002");
        mineOld.put("age", "old");

        Map<String, Object> other = new HashMap<>();
        other.put("id", "cccccccc-0000-0000-0000-000000000003");
        other.put("sub_task_id", "Test-000001");
        other.put("age", "keep");

        Map<String, Object> baseline = new HashMap<>();
        baseline.put("dw:people", new java.util.ArrayList<>(List.of(other, mineOld)));

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:people", new java.util.ArrayList<>());

        Map<String, Object> merged =
                merger.mergeCurrentRowOnly(submitted, baseline, Map.of("id_idwnn", "Test-000002"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) merged.get("dw:people");
        assertThat(rows).hasSize(2);
        assertThat(rows).anySatisfy(r -> assertThat(r.get("age")).isEqualTo("keep"));
        assertThat(rows).anySatisfy(r -> assertThat(r.get("age")).isEqualTo("old"));
    }

    /**
     * Deleting the LAST row a participant owns persists once the frontend declares that intent.
     *
     * <p>Measured before this fix: deleting one of several People rows persisted, deleting the only
     * one silently came back on reload — the empty slice fell into the ambiguous branch above and
     * the baseline (still holding the row) was returned untouched.
     *
     * <p>Only the declaring participant's rows are cleared; a sibling's row in the same shared slice
     * must survive.
     */
    @Test
    void mergeCurrentRowOnly_declaredEmptyLinkChildSliceDeletesOnlyMyRows() {
        when(jdbcTemplate.queryForList(contains("b.foreign_key_field"), eq(String.class), eq("people")))
                .thenReturn(List.of("id"));
        when(jdbcTemplate.queryForList(contains("f.is_foreign_key"), eq(String.class), eq("people")))
                .thenReturn(List.of("sub_task_id"));

        Map<String, Object> mineOnly = new HashMap<>();
        mineOnly.put("id", "aaaaaaaa-0000-0000-0000-000000000001");
        mineOnly.put("sub_task_id", "Test-000002");
        mineOnly.put("age", "delete-me");

        Map<String, Object> other = new HashMap<>();
        other.put("id", "cccccccc-0000-0000-0000-000000000003");
        other.put("sub_task_id", "Test-000001");
        other.put("age", "keep");

        Map<String, Object> baseline = new HashMap<>();
        baseline.put("dw:people", new java.util.ArrayList<>(List.of(other, mineOnly)));

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:people", new java.util.ArrayList<>());

        Map<String, Object> merged = merger.mergeCurrentRowOnly(
                submitted, baseline, Map.of("id_idwnn", "Test-000002"), Set.of("dw:people"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) merged.get("dw:people");
        // 我的最后一行被删掉
        assertThat(rows).hasSize(1);
        // peer 的行原样保留
        assertThat(rows.get(0).get("age")).isEqualTo("keep");
        assertThat(rows.get(0).get("sub_task_id")).isEqualTo("Test-000001");
    }

    /**
     * The REAL delete-last-row shape: the submitted slice is NOT empty — it still carries the other
     * participants' rows — it just has none of mine.
     *
     * <p>Measured on task 072f18ed (2026-09-03): submitted=3 peers, baseline=4. An
     * {@code isEmpty()}-only guard therefore never fired, and delete-last-row silently kept the
     * baseline even with the declaration present.
     */
    @Test
    void mergeCurrentRowOnly_declaredDeleteOfMyLastRowWhilePeerRowsRideAlong() {
        when(jdbcTemplate.queryForList(contains("b.foreign_key_field"), eq(String.class), eq("people")))
                .thenReturn(List.of("id"));
        when(jdbcTemplate.queryForList(contains("f.is_foreign_key"), eq(String.class), eq("people")))
                .thenReturn(List.of("sub_task_id"));

        Map<String, Object> mine = new HashMap<>();
        mine.put("id", "aaaaaaaa-0000-0000-0000-000000000001");
        mine.put("sub_task_id", "Test-000002");

        Map<String, Object> peerA = new HashMap<>();
        peerA.put("id", "cccccccc-0000-0000-0000-000000000003");
        peerA.put("sub_task_id", "Test-000001");

        Map<String, Object> peerB = new HashMap<>();
        peerB.put("id", "dddddddd-0000-0000-0000-000000000004");
        peerB.put("sub_task_id", "Test-000003");

        Map<String, Object> baseline = new HashMap<>();
        baseline.put("dw:people", new java.util.ArrayList<>(List.of(peerA, peerB, mine)));

        // 界面删掉了我唯一一行，但提交的切片里仍带着两个 peer 的行 —— 非空！
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:people", new java.util.ArrayList<>(List.of(peerA, peerB)));

        Map<String, Object> merged = merger.mergeCurrentRowOnly(
                submitted, baseline, Map.of("id_idwnn", "Test-000002"), Set.of("dw:people"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) merged.get("dw:people");
        assertThat(rows).hasSize(2);
        assertThat(rows).noneSatisfy(r -> assertThat(r.get("sub_task_id")).isEqualTo("Test-000002"));
        assertThat(rows).anySatisfy(r -> assertThat(r.get("sub_task_id")).isEqualTo("Test-000001"));
        assertThat(rows).anySatisfy(r -> assertThat(r.get("sub_task_id")).isEqualTo("Test-000003"));
    }

    /**
     * Without the declaration, the same peers-only slice must NOT delete my row — that is the
     * long-standing "this participant has no row yet" case.
     */
    @Test
    void mergeCurrentRowOnly_peersOnlySliceWithoutDeclarationStillKeepsMyRow() {
        when(jdbcTemplate.queryForList(contains("b.foreign_key_field"), eq(String.class), eq("people")))
                .thenReturn(List.of("id"));
        when(jdbcTemplate.queryForList(contains("f.is_foreign_key"), eq(String.class), eq("people")))
                .thenReturn(List.of("sub_task_id"));

        Map<String, Object> mine = new HashMap<>();
        mine.put("id", "aaaaaaaa-0000-0000-0000-000000000001");
        mine.put("sub_task_id", "Test-000002");

        Map<String, Object> peerA = new HashMap<>();
        peerA.put("id", "cccccccc-0000-0000-0000-000000000003");
        peerA.put("sub_task_id", "Test-000001");

        Map<String, Object> baseline = new HashMap<>();
        baseline.put("dw:people", new java.util.ArrayList<>(List.of(peerA, mine)));

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:people", new java.util.ArrayList<>(List.of(peerA)));

        Map<String, Object> merged =
                merger.mergeCurrentRowOnly(submitted, baseline, Map.of("id_idwnn", "Test-000002"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) merged.get("dw:people");
        assertThat(rows).hasSize(2);
        assertThat(rows).anySatisfy(r -> assertThat(r.get("sub_task_id")).isEqualTo("Test-000002"));
    }

    /**
     * The declaration must NOT be able to swallow the MI_ROW_KEY_UNRESOLVED guard.
     *
     * <p>The declared-delete branch is checked before that guard, so it must first prove it can
     * even express a delete: with no resolvable FK columns, "drop the rows whose FK names me" is a
     * constant false. Silently returning a no-op there would hide a genuinely corrupt submission
     * (rows that disagree with rowKey about what identifies a row) that the guard is meant to
     * reject loudly.
     */
    @Test
    void mergeCurrentRowOnly_declarationWithoutFkMetadataStillFailsLoud() {
        when(jdbcTemplate.queryForList(contains("b.foreign_key_field"), eq(String.class), eq("people")))
                .thenReturn(List.of("id"));
        // 没有任何字段级 FK —— 归属判不出来
        when(jdbcTemplate.queryForList(contains("f.is_foreign_key"), eq(String.class), eq("people")))
                .thenReturn(List.of());

        Map<String, Object> stranger = new HashMap<>();
        stranger.put("id", "eeeeeeee-0000-0000-0000-000000000009");
        stranger.put("name", "typed-but-would-be-lost");

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:people", new java.util.ArrayList<>(List.of(stranger)));

        assertThatThrownBy(() -> merger.mergeCurrentRowOnly(
                submitted, Map.of(), Map.of("id_idwnn", "Test-000002"), Set.of("dw:people")))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining("missing from the");
    }

    /**
     * A declaration for ONE key must not clear a different key's slice.
     */
    @Test
    void mergeCurrentRowOnly_declarationIsScopedToTheDeclaredKeyOnly() {
        when(jdbcTemplate.queryForList(contains("b.foreign_key_field"), eq(String.class), eq("people")))
                .thenReturn(List.of("id"));
        when(jdbcTemplate.queryForList(contains("f.is_foreign_key"), eq(String.class), eq("people")))
                .thenReturn(List.of("sub_task_id"));

        Map<String, Object> mine = new HashMap<>();
        mine.put("id", "aaaaaaaa-0000-0000-0000-000000000001");
        mine.put("sub_task_id", "Test-000002");

        Map<String, Object> baseline = new HashMap<>();
        baseline.put("dw:people", new java.util.ArrayList<>(List.of(mine)));

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:people", new java.util.ArrayList<>());

        // 声明的是另一张表 —— people 不受影响
        Map<String, Object> merged = merger.mergeCurrentRowOnly(
                submitted, baseline, Map.of("id_idwnn", "Test-000002"), Set.of("dw:somethingelse"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) merged.get("dw:people");
        assertThat(rows).hasSize(1);
    }

    /**
     * A link-child slice holding ONLY other participants' rows is not corruption.
     *
     * <p>This participant simply has no row on that table yet, while a sibling's row rides along in
     * the shared slice. Measured: participant {@code Test-000002} opened a task whose `people` slice
     * held only {@code {sub_task_id: Test-000001}} — failing there rejected every Save although
     * nothing of ours was at stake.
     */
    @Test
    void mergeCurrentRowOnly_sliceWithOnlyOtherParticipantsRowsLeavesBaselineAlone() {
        when(jdbcTemplate.queryForList(contains("b.foreign_key_field"), eq(String.class), eq("people")))
                .thenReturn(List.of("id"));
        // 设计器声明的外键列：判「这行属于哪个参与者」用它，不猜列名
        when(jdbcTemplate.queryForList(contains("f.is_foreign_key"), eq(String.class), eq("people")))
                .thenReturn(List.of("sub_task_id"));

        Map<String, Object> foreign = new HashMap<>();
        foreign.put("id", "bad0d243-7e3c-4c67-9137-69ea487980fd");
        foreign.put("sub_task_id", "Test-000001");
        foreign.put("age", "q");

        Map<String, Object> baseline = new HashMap<>();
        baseline.put("dw:people", new java.util.ArrayList<>(List.of(foreign)));

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:people", new java.util.ArrayList<>(List.of(foreign)));

        Map<String, Object> merged =
                merger.mergeCurrentRowOnly(submitted, baseline, Map.of("id_idwnn", "Test-000002"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) merged.get("dw:people");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("sub_task_id")).isEqualTo("Test-000001");
    }

    /**
     * The corruption guard still fires when rows carry NO structural FK and no matching PK — i.e.
     * the submission genuinely disagrees about what identifies a row.
     */
    @Test
    void mergeCurrentRowOnly_rowsWithoutStructuralFkStillFailLoud() {
        when(jdbcTemplate.queryForList(contains("b.foreign_key_field"), eq(String.class), eq("subtable")))
                .thenReturn(List.of("id_idwnn"));

        Map<String, Object> mismatched = new HashMap<>();
        mismatched.put("id_idwnn", "Test-000009");
        mismatched.put("name", "someone else");

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:subtable", new java.util.ArrayList<>(List.of(mismatched)));

        assertThatThrownBy(() ->
                merger.mergeCurrentRowOnly(submitted, Map.of(), Map.of("id_idwnn", "Test-000002")))
                .hasMessageContaining("own row");
    }

    /**
     * 现场回归：Multi-Instance Subtask Demo 把主外键改名后（people.sub_task_id → sub_task_idqc、
     * subtable 主键 → id_idwxwc），保存直接被 MI_ROW_KEY_UNRESOLVED 拒掉。
     *
     * <p>根因是本类曾用一张写死的列名表（sub_task_id / participant_id / …）判「这行属于哪个参与者」。
     * 改名后那张表一个都命中不了：既找不到本参与者的行，也无法证明提交的行属于别人，于是落到
     * "own row missing" 的硬失败分支 —— 而实际上 payload 完全正确。
     *
     * <p>现在外键列来自 dw_field_definitions.is_foreign_key，列叫什么都不影响。
     */
    @Test
    void mergeCurrentRowOnly_renamedForeignKeyColumnStillMatchesParticipant() {
        when(jdbcTemplate.queryForList(contains("b.foreign_key_field"), eq(String.class), eq("people")))
                .thenReturn(List.of("idqc"));
        when(jdbcTemplate.queryForList(contains("f.is_foreign_key"), eq(String.class), eq("people")))
                .thenReturn(List.of("sub_task_idqc"));

        Map<String, Object> mine = new HashMap<>();
        mine.put("idqc", "000c88a8-dcd0-4d7b-b35b-3fb684e2ab4d");
        mine.put("sub_task_idqc", "Test-000003");
        mine.put("age", "q");

        Map<String, Object> mine2 = new HashMap<>();
        mine2.put("idqc", "892cc8c0-8b75-40c1-a2ca-3037c5b93a48");
        mine2.put("sub_task_idqc", "Test-000003");
        mine2.put("age", "w");

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:people", new java.util.ArrayList<>(List.of(mine, mine2)));

        // 改名后的 collection 主键；改动前这里会抛 MI_ROW_KEY_UNRESOLVED
        Map<String, Object> merged = merger.mergeCurrentRowOnly(
                submitted, Map.of(), Map.of("id_idwxwc", "Test-000003"));

        @SuppressWarnings("unchecked")
        List<Object> people = (List<Object>) merged.get("dw:people");
        org.assertj.core.api.Assertions.assertThat(people).hasSize(2);
    }

    /**
     * 现场回归：共享表（附件）的外键改名后，不能再被当成参与者子表逐行隔离。
     *
     * <p>改名前外键叫 main_id，命中写死的 SHARED_PROCESS_SUB_TABLE_FK；改名成 main_idva 后一个
     * 都命中不了，附件表被判成「参与者子表」，于是拿子任务 PK 去逐行匹配 —— 匹配不上就整单
     * 拒绝保存（或静默丢掉全部附件行）。现在按结构判定：外键指向 MAIN 表即为共享表。
     */
    @Test
    void mergeCurrentRowOnly_sharedTableWithRenamedForeignKeyStillPassesThrough() {
        when(jdbcTemplate.queryForList(contains("ref.table_type"), eq(String.class), eq("attachment")))
                .thenReturn(List.of("MAIN"));

        Map<String, Object> attachmentRow = new HashMap<>();
        attachmentRow.put("idfa", "att-1");
        attachmentRow.put("main_idva", "Meeting-000002");
        attachmentRow.put("file", "notes.pdf");

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:attachment", new java.util.ArrayList<>(List.of(attachmentRow)));

        // 改动前这里会抛 MI_ROW_KEY_UNRESOLVED（附件行里没有 id_idwxwc）
        Map<String, Object> merged = merger.mergeCurrentRowOnly(
                submitted, Map.of(), Map.of("id_idwxwc", "Test-000003"));

        @SuppressWarnings("unchecked")
        List<Object> out = (List<Object>) merged.get("dw:attachment");
        org.assertj.core.api.Assertions.assertThat(out).containsExactly(attachmentRow);
    }

    /**
     * Code review 抓到的 Blocker 回归：**MI collection 表自己的外键也指向主表**
     * （demo FU：{@code subtable.main_idaaz -> main}）。
     *
     * <p>只按「FK 指向 MAIN = 共享表」判定，会把 collection 判成共享 → 整片跳过行合并 →
     * 当前参与者提交里那份「兄弟参与者被 MI 隔离削薄成 thin stub」的行原样落库，
     * 把别人已经保存好的字段覆盖成空。实测：Test-000003 的 name 从 "THEIR SAVED DATA"
     * 变成 ""。这比它要修的原 bug 更严重 —— 丢的是别人已存的数据。
     *
     * <p>修法：`binding_link_mode = 'miParticipantRow'` 是设计器的**显式声明**，
     * 优先级高于任何结构判据。
     */
    @Test
    void mergeCurrentRowOnly_collectionTableWhoseFkAlsoPointsAtMainStillRowIsolates() {
        // collection 表：设计器显式声明 miParticipantRow，同时它的 FK 也指向 MAIN
        when(jdbcTemplate.queryForObject(contains("binding_link_mode"), eq(Integer.class), eq("subtable")))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(contains("ref.table_type"), eq(String.class), eq("subtable")))
                .thenReturn(List.of("MAIN"));

        Map<String, Object> mine = new HashMap<>();
        mine.put("id_idwxwc", "Test-000004");
        mine.put("name", "MY NEW VALUE");

        // MI 隔离后，兄弟参与者的行只剩身份字段，业务字段是空的
        Map<String, Object> siblingThinStub = new HashMap<>();
        siblingThinStub.put("id_idwxwc", "Test-000003");
        siblingThinStub.put("name", "");

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:subtable", new java.util.ArrayList<>(List.of(mine, siblingThinStub)));

        Map<String, Object> siblingSaved = new HashMap<>();
        siblingSaved.put("id_idwxwc", "Test-000003");
        siblingSaved.put("name", "THEIR SAVED DATA");

        Map<String, Object> baseline = new HashMap<>();
        baseline.put("dw:subtable", new java.util.ArrayList<>(List.of(siblingSaved)));

        Map<String, Object> merged = merger.mergeCurrentRowOnly(
                submitted, baseline, Map.of("id_idwxwc", "Test-000004"));

        @SuppressWarnings("unchecked")
        List<Object> out = (List<Object>) merged.get("dw:subtable");
        // 兄弟参与者已保存的数据必须原封不动；当前参与者的新值要写进去
        org.assertj.core.api.Assertions.assertThat(out)
                .containsExactlyInAnyOrder(siblingSaved, mine);
    }
}
