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

    private void stubSiblingBindingIds(long bindingId, Long... siblingIds) {
        when(jdbcTemplate.queryForList(any(String.class), eq(Long.class), eq(bindingId)))
                .thenReturn(List.of(siblingIds));
    }

    private static Map<String, Object> row(String idIdw, String name) {
        Map<String, Object> r = new HashMap<>();
        r.put("id_idw", idIdw);
        r.put("name", name);
        r.put("task_status", "IN_PROGRESS");
        return r;
    }

    private static Map<String, Object> currentItem(String rowId) {
        return Map.of("rowId", rowId);
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
    void mergeCurrentRowOnly_patchesStaleSiblingBindingIdKeyAbsentFromSubmission() {
        // Real-world shape: the MI collection table is bound by 6 different Task/Process Design
        // forms (Assign Task, Sub task, Sub task (My Request), Main, ...), each getting its own
        // dw_form_table_bindings.id. The submitting sub-task's own binding is 50544; 50539 is a
        // sibling binding (e.g. "Assign Task") sharing the same designer table, stale because no
        // save has ever gone through it directly — only the string alias + the submitter's own
        // numeric key are present in the outbound payload.
        stubSiblingBindingIds(50544L, 50539L, 50627L);

        Map<String, Object> baseline = new HashMap<>();
        baseline.put("50544", new java.util.ArrayList<>(List.of(row("Test-014", "old"))));
        baseline.put("50539", new java.util.ArrayList<>(List.of(row("Test-014", "old"))));
        // 50627 never had this key in the baseline at all — must not be conjured out of nowhere.

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("50544", new java.util.ArrayList<>(List.of(row("Test-014", "new"))));

        Map<String, Object> merged = merger.mergeCurrentRowOnly(submitted, baseline, Map.of("id_idw", "Test-014"));

        assertThat(merged).containsKey("50539");
        assertThat(merged).doesNotContainKey("50627");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ownRows = (List<Map<String, Object>>) merged.get("50544");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> siblingRows = (List<Map<String, Object>>) merged.get("50539");
        assertThat(ownRows.get(0).get("name")).isEqualTo("new");
        assertThat(siblingRows.get(0).get("name")).as("stale sibling key must be patched to match, not left stale").isEqualTo("new");
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
        when(jdbcTemplate.queryForList(any(String.class), eq(String.class), eq(9001L)))
                .thenReturn(List.of("main_id"));

        Map<String, Object> attachmentRow = new HashMap<>();
        attachmentRow.put("id", "att-1");
        attachmentRow.put("file", "notes.pdf");

        Map<String, Object> baseline = new HashMap<>();
        baseline.put("50539", new java.util.ArrayList<>(List.of(row("Test-014", "old"))));
        baseline.put("9001", new java.util.ArrayList<>());

        Map<String, Object> submitted = new HashMap<>();
        submitted.put("50539", new java.util.ArrayList<>(List.of(row("Test-014", "new"))));
        List<Object> submittedAttachments = new java.util.ArrayList<>(List.of(attachmentRow));
        submitted.put("9001", submittedAttachments);

        Map<String, Object> merged = merger.mergeCurrentRowOnly(submitted, baseline, Map.of("id_idw", "Test-014"));

        // MI participant table: still row-merged as usual.
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> participantRows = (List<Map<String, Object>>) merged.get("50539");
        assertThat(participantRows.get(0).get("name")).isEqualTo("new");

        // Shared attachment table: passed through unchanged (not row-scoped, not silently dropped).
        assertThat(merged.get("9001")).isSameAs(submittedAttachments);
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
