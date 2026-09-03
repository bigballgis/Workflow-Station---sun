package com.portal.component;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TaskFormFieldMapperTest {

    private final TaskFormFieldMapper mapper = new TaskFormFieldMapper();

    @Test
    void emptyPermissionsStillRejectsPlatformAuditFields() {
        Map<String, Object> formData = Map.of(
                "title", "x",
                "created_at", "forged",
                "updated_by", "forged");

        Map<String, Object> accepted = mapper.filterEditableFields(formData, Map.of());

        assertThat(accepted).containsOnlyKeys("title");
        assertThat(accepted.get("title")).isEqualTo("x");
    }

    @Test
    void editablePermissionOnAuditFieldIsStillRejected() {
        Map<String, Object> formData = Map.of(
                "title", "x",
                "created_at", "forged",
                "notes", "y");
        Map<String, String> permissions = Map.of(
                "title", "EDITABLE",
                "created_at", "EDITABLE",
                "notes", "EDITABLE");

        Map<String, Object> accepted = mapper.filterEditableFields(formData, permissions);

        assertThat(accepted).containsOnlyKeys("title", "notes");
    }

    /**
     * Documents WHY {@code TaskFormComponent} must re-attach {@code _currentItem} (and
     * {@code __subTables__}) after this call: the subset keeps only DESIGNER form fields, so MI
     * runtime state is filtered out here by design.
     *
     * <p>Measured on task 506809ee (Test-000009): the task-scoped {@code _currentItem} was resolved
     * correctly and then dropped by this filter, which silently switched MI row isolation off —
     * server-loaded People rows could not be deleted at all.
     */
    @Test
    void extractFieldSubsetDropsMiRuntimeStateSoCallersMustReattachIt() {
        Map<String, Object> variables = Map.of(
                "title", "x",
                "_currentItem", Map.of("rowId", "Test-000009"),
                "__subTables__", Map.of("dw:people", java.util.List.of()));

        Map<String, Object> subset = mapper.extractFieldSubset(variables, Set.of("title"));

        assertThat(subset).containsOnlyKeys("title");
    }

    @Test
    void extractFieldSubsetCopiesOwnerDisplayCompanion() {
        Map<String, Object> variables = Map.of(
                "creator", "user:user-dev",
                "creator__display", "Developer Tester",
                "owner", "user:user-e2e-lina",
                "owner__display", "李娜",
                "unrelated", "skip");

        Map<String, Object> subset = mapper.extractFieldSubset(variables, Set.of("creator", "owner"));

        assertThat(subset).containsEntry("creator", "user:user-dev")
                .containsEntry("creator__display", "Developer Tester")
                .containsEntry("owner", "user:user-e2e-lina")
                .containsEntry("owner__display", "李娜")
                .doesNotContainKey("unrelated");
    }
}
