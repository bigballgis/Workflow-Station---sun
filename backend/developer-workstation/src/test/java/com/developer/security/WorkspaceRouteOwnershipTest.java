package com.developer.security;

import com.developer.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A path id that names a form, table, action, … belongs to the unit (or form) the path puts it under, or the request
 * is answered as if it did not exist (platform access model §6.1, §6.2).
 */
class WorkspaceRouteOwnershipTest {

    private static final String FORM_SQL = "SELECT function_unit_id FROM dw_form_definitions WHERE id = ?";
    private static final String BINDING_SQL = "SELECT form_id FROM dw_form_table_bindings WHERE id = ?";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final WorkspaceRouteOwnership ownership = new WorkspaceRouteOwnership(jdbc);

    @Test
    void aFormUnderItsOwnUnitIsAuthorizedThroughThePathUnit() {
        owner(FORM_SQL, 11L, 7L);

        assertThat(ownership.functionUnitOf("/function-units/7/forms/11")).isEmpty();
        assertThat(ownership.functionUnitOf("/function-units/7/forms/11/validate")).isEmpty();
    }

    @Test
    void aFormOfAnotherUnitIsNotFoundUnderThisOne() {
        owner(FORM_SQL, 1L, 1L);

        assertThatThrownBy(() -> ownership.functionUnitOf("/function-units/50054/forms/1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Form");
    }

    @Test
    void everyUnitChildIsCheckedAgainstItsOwnTable() {
        when(jdbc.queryForList(eq("SELECT function_unit_id FROM dw_action_definitions WHERE id = ?"),
                eq(Long.class), eq(5L))).thenReturn(List.of(2L));

        assertThatThrownBy(() -> ownership.functionUnitOf("/function-units/7/actions/5/test"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aMissingSubResourceIsNotFound() {
        assertThatThrownBy(() -> ownership.functionUnitOf("/function-units/7/tables/99"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aBindingMustBelongToTheFormInThePath() {
        owner(FORM_SQL, 11L, 7L);
        owner(FORM_SQL, 12L, 7L);
        owner(BINDING_SQL, 19L, 11L);

        assertThat(ownership.functionUnitOf("/function-units/7/forms/11/bindings/19")).isEmpty();
        assertThatThrownBy(() -> ownership.functionUnitOf("/function-units/7/forms/12/bindings/19"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> ownership.functionUnitOf("/api/forms/12/relation-bindings/19"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aBindingIdFromABodyMustBeABindingOfThatForm() {
        owner(BINDING_SQL, 19L, 11L);

        ownership.requireBindingOfForm(19L, 11L);
        ownership.requireBindingOfForm(null, 11L);
        assertThatThrownBy(() -> ownership.requireBindingOfForm(19L, 12L))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> ownership.requireBindingOfForm(20L, 11L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void routesWithoutAUnitAreAuthorizedAgainstTheOwnerFound() {
        owner(FORM_SQL, 11L, 7L);
        owner("SELECT function_unit_id FROM dw_action_definitions WHERE id = ?", 5L, 3L);

        assertThat(ownership.functionUnitOf("/api/forms/11/lookup-config/lookup_1")).isEqualTo(Optional.of(7L));
        assertThat(ownership.functionUnitOf("/actions/5")).isEqualTo(Optional.of(3L));
    }

    @Test
    void aSessionIsFoundByItsUuidAndAMalformedOneDoesNotExist() {
        UUID session = UUID.randomUUID();
        owner("SELECT function_unit_id FROM dw_ai_sessions WHERE session_id = ?", session, 4L);

        assertThat(ownership.functionUnitOf("/ai-generation/sessions/" + session + "/messages")).contains(4L);
        assertThatThrownBy(() -> ownership.functionUnitOf("/ai-generation/sessions/not-a-uuid/phase"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aDeploymentStatusIsReadInItsUnitsWorkspace() {
        owner("SELECT function_unit_id FROM dw_deployment_jobs WHERE id = ?", "job-1", 6L);

        assertThat(ownership.functionUnitOf("/function-units/deployments/job-1/status")).contains(6L);
    }

    @Test
    void pathsWithoutSubResourcesNeedNoLookup() {
        assertThat(ownership.functionUnitOf("/function-units/7")).isEmpty();
        assertThat(ownership.functionUnitOf("/function-units/7/forms")).isEmpty();
        assertThat(ownership.functionUnitOf("/function-units/7/email-monitors/templates")).isEmpty();
        assertThat(ownership.functionUnitOf("/actions/batch")).isEmpty();
        verifyNoInteractions(jdbc);
    }

    private void owner(String sql, Object id, Long owner) {
        when(jdbc.queryForList(eq(sql), eq(Long.class), eq(id))).thenReturn(List.of(owner));
    }
}
