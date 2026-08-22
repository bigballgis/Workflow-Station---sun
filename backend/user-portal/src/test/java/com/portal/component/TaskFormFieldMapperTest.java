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
