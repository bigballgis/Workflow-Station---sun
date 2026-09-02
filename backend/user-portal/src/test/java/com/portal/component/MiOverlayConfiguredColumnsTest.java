package com.portal.component;

import com.portal.component.MiOverlaySupport.MiRowProgress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MI mirror columns are written under the names this Function Unit configured in Sub-Task Config
 * ({@code miTaskStatusField} / {@code miTaskCurrentNodeField}), with the platform defaults applying
 * only when it configured none.
 *
 * <p>Regression: {@code applyMiOverlayToVariableRow} used to write the configured column AND the
 * literals {@code task_status} / {@code task_current_node}. A Function Unit that named its own
 * columns therefore got BOTH stamped on every row — the portal rendered two status columns and
 * which one a reader picked was a coin flip. Worse, the two disagreed: the configured column got
 * the raw engine status while the literal got the mapped portal status.
 */
class MiOverlayConfiguredColumnsTest {

    private static Map<String, Object> row() {
        return new HashMap<>();
    }

    @Test
    @DisplayName("configured column names win, and the platform literals are NOT also written")
    void configuredColumnsOnly() {
        Map<String, Object> r = row();
        MiOverlaySupport.applyMiOverlayToVariableRow(
                r, new MiRowProgress("review_state", "review_step", "IN_PROGRESS", "sub form1"));

        assertThat(r).containsEntry("review_state", "IN_PROGRESS");
        assertThat(r).containsEntry("review_step", "sub form1");
        // The bug: these used to be stamped alongside the configured names.
        assertThat(r).doesNotContainKey("task_status");
        assertThat(r).doesNotContainKey("task_current_node");
    }

    @Test
    @DisplayName("no configuration → platform defaults (the live path for every deployed FU today)")
    void platformDefaultsWhenUnconfigured() {
        Map<String, Object> r = row();
        MiOverlaySupport.applyMiOverlayToVariableRow(r, new MiRowProgress(null, null, "COMPLETED", "end"));

        assertThat(r).containsEntry("task_status", "COMPLETED");
        assertThat(r).containsEntry("task_current_node", "end");
    }

    @Test
    @DisplayName("engine status is mapped to the portal vocabulary in the configured column too")
    void engineStatusMappedIntoConfiguredColumn() {
        Map<String, Object> r = row();
        // The frontend renders only COMPLETED / IN_PROGRESS / ASSIGNED (see useSubTableStatusColumns),
        // so the configured column must carry the MAPPED value, not the raw engine one.
        MiOverlaySupport.applyMiOverlayToVariableRow(
                r, new MiRowProgress("review_state", "review_step", "CREATED", "sub form1"));

        assertThat(r).containsEntry("review_state", "IN_PROGRESS");
    }

    @Test
    @DisplayName("blank current node renders as '-' under the configured name")
    void blankNodePlaceholder() {
        Map<String, Object> r = row();
        MiOverlaySupport.applyMiOverlayToVariableRow(
                r, new MiRowProgress("review_state", "review_step", "IN_PROGRESS", "  "));

        assertThat(r).containsEntry("review_step", "-");
    }

    @Test
    @DisplayName("stuck-row repair uses the configured column names")
    void stuckRowRepairUsesConfiguredColumns() {
        Map<String, Object> r = row();
        r.put("assignee_user_id", "u1");
        r.put("review_step", "sub form1");
        r.put("review_state", "PENDING");

        MiOverlaySupport.normalizeStuckMiParticipantRowForCompletedProcess(
                r, new MiRowProgress("review_state", "review_step", "IN_PROGRESS", "sub form1"));

        assertThat(r).containsEntry("review_state", "COMPLETED");
        assertThat(r).containsEntry("review_step", "end");
        // Must not invent the platform-default columns on a FU that renamed them.
        assertThat(r).doesNotContainKey("task_status");
    }

    @Test
    @DisplayName("stuck-row repair leaves an already terminal row alone")
    void stuckRowRepairSkipsTerminalRow() {
        Map<String, Object> r = row();
        r.put("assignee_user_id", "u1");
        r.put("review_state", "COMPLETED");
        r.put("review_step", "end");

        MiOverlaySupport.normalizeStuckMiParticipantRowForCompletedProcess(
                r, new MiRowProgress("review_state", "review_step", "COMPLETED", "end"));

        assertThat(r).containsEntry("review_step", "end");
    }

    @Test
    @DisplayName("miColumnNamesFor: first configured name wins, else platform defaults")
    void miColumnNamesResolution() {
        assertThat(MiOverlaySupport.miColumnNamesFor(null))
                .containsExactly("task_status", "task_current_node");

        Map<String, MiRowProgress> progress = new LinkedHashMap<>();
        progress.put("r1", new MiRowProgress("review_state", "review_step", "IN_PROGRESS", "n"));
        assertThat(MiOverlaySupport.miColumnNamesFor(progress))
                .containsExactly("review_state", "review_step");
    }

    @Test
    @DisplayName("protected columns include both the defaults and this FU's configured names")
    void protectedColumnsCoverConfiguredNames() {
        Map<String, MiRowProgress> progress = new LinkedHashMap<>();
        progress.put("r1", new MiRowProgress("review_state", "review_step", "IN_PROGRESS", "n"));

        assertThat(MiOverlaySupport.miDashboardColumnsToProtect(progress))
                .contains("task_status", "task_current_node", "review_state", "review_step");
    }
}
