package com.portal.component;

import com.portal.exception.PortalException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
        // FK is now looked up by the canonical key's table name, not by a numeric binding id.
        when(jdbcTemplate.queryForList(any(String.class), eq(String.class), eq("attachment")))
                .thenReturn(List.of("main_id"));

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
}
