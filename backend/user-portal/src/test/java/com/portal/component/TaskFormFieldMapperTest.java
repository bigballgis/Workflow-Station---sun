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

    /**
     * Regression: a field absent from a NON-EMPTY permission map is editable.
     *
     * <p>Reproduces FU {@code atm-20260623-gaevus} form 321, whose {@code field_permissions} holds
     * 21 entries that are all READONLY and no {@code "EDITABLE"} value at all — the Form Designer
     * persists the map sparsely, writing only the read-only fields. Requiring an exact
     * {@code "EDITABLE"} match discarded every ordinary field (e.g. {@code case_status}) while the
     * request still returned 200, so the portal reported success and the value never changed.
     */
    @Test
    void fieldAbsentFromNonEmptyPermissionsIsEditable() {
        Map<String, Object> formData = Map.of(
                "case_status", "Closed",
                "card_number", "55",
                "case_number", "ATM-DC-PW-000009");
        // Sparse map exactly as the designer writes it: read-only keys only.
        Map<String, String> permissions = Map.of(
                "case_number", "READONLY",
                "row_id", "READONLY",
                "created_by", "READONLY");

        Map<String, Object> accepted = mapper.filterEditableFields(formData, permissions);

        assertThat(accepted).containsOnlyKeys("case_status", "card_number");
        assertThat(accepted.get("case_status")).isEqualTo("Closed");
        // Explicit READONLY is still enforced.
        assertThat(accepted).doesNotContainKey("case_number");
    }

    /**
     * Regression: clearing an optional field submits a null value, which must be accepted (and
     * persisted as null), not blow up.
     *
     * <p>{@code Collectors.toMap} routes through {@code HashMap.merge}, which throws
     * {@link NullPointerException} on a null value. While the filter still required an exact
     * {@code "EDITABLE"} match, a cleared field was dropped before reaching the collector, so the
     * hazard was latent; accepting absent-key-means-editable exposed it. Symptom: emptying Case
     * Status in the UI and pressing Save returned 500 "Internal server error".
     *
     * <p>{@code Map.of()} rejects null values, so the payload is built with a HashMap on purpose —
     * that is also what Jackson produces for {@code {"case_status": null}}.
     */
    @Test
    void nullFieldValueIsAcceptedRatherThanThrowing() {
        Map<String, Object> formData = new java.util.HashMap<>();
        formData.put("case_status", null);
        formData.put("card_number", "55");
        Map<String, String> permissions = Map.of("case_number", "READONLY");

        Map<String, Object> accepted = mapper.filterEditableFields(formData, permissions);

        assertThat(accepted).containsOnlyKeys("case_status", "card_number");
        assertThat(accepted.get("case_status")).isNull();
    }

    /** The empty-permissions branch must handle nulls identically (it always did — lock it in). */
    @Test
    void nullFieldValueIsAcceptedWithEmptyPermissions() {
        Map<String, Object> formData = new java.util.HashMap<>();
        formData.put("case_status", null);

        Map<String, Object> accepted = mapper.filterEditableFields(formData, Map.of());

        assertThat(accepted).containsKey("case_status");
        assertThat(accepted.get("case_status")).isNull();
    }

    /** {@code __subTables__} keeps its unconditional pass-through (filtered later, binding-aware). */
    @Test
    void subTablesPassThroughEvenWhenPermissionsAreNonEmpty() {
        Map<String, Object> formData = Map.of(
                "__subTables__", Map.of("dw:atm_transaction", java.util.List.of()),
                "case_number", "ATM-DC-PW-000009");
        Map<String, String> permissions = Map.of("case_number", "READONLY");

        Map<String, Object> accepted = mapper.filterEditableFields(formData, permissions);

        assertThat(accepted).containsOnlyKeys("__subTables__");
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
