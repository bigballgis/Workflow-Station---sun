package com.workflow.component;

import com.platform.common.functionunit.FunctionUnitVersionException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PinnedReleaseLookupTest {
    private static final String PIN_QUERY = "SELECT function_unit_catalog_id FROM up_process_instance WHERE id = ?";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PinnedReleaseLookup lookup = new PinnedReleaseLookup(jdbc);

    @Test void releaseInTheEngineVariablesIsTheRequestsOwn() {
        assertThat(lookup.catalogIdOf("rel-engine", "pi-1")).isEqualTo("rel-engine");
        verifyNoInteractions(jdbc);
    }

    @Test void requestWithoutTheVariableUsesThePortalPin() {
        when(jdbc.queryForList(PIN_QUERY, String.class, "pi-1")).thenReturn(List.of("rel-portal"));
        assertThat(lookup.catalogIdOf(" ", "pi-1")).isEqualTo("rel-portal");
    }

    @Test void requestWithNeitherNeedsRecovery() {
        when(jdbc.queryForList(PIN_QUERY, String.class, "pi-1")).thenReturn(java.util.Collections.singletonList(null));
        assertThatThrownBy(() -> lookup.catalogIdOf(null, "pi-1"))
                .isInstanceOf(FunctionUnitVersionException.class)
                .satisfies(error -> assertThat(((FunctionUnitVersionException) error).getDetails())
                        .containsEntry("reason", "VERSION_RECOVERY_REQUIRED"));
    }

    @Test void unrecordedRequestNeedsRecovery() {
        when(jdbc.queryForList(PIN_QUERY, String.class, "pi-1")).thenReturn(List.of());
        assertThatThrownBy(() -> lookup.catalogIdOf(null, "pi-1")).isInstanceOf(FunctionUnitVersionException.class);
    }
}
