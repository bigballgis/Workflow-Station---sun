package com.portal.component;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DwFunctionUnitIdLookupTest {

    private static final String SQL = "SELECT id FROM dw_function_units WHERE code = ?";

    @Test
    @DisplayName("a unique Designer code resolves to dw_function_units.id")
    void uniqueCodeResolvesToId() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(eq(SQL), eq(Long.class), eq("FU-MCY"))).thenReturn(List.of(48L));

        assertThat(DwFunctionUnitIdLookup.findIdByCode(jdbc, "FU-MCY")).isEqualTo(48L);
    }

    @Test
    @DisplayName("unknown or blank code stays null rather than inventing an id")
    void unknownOrBlankStaysNull() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(eq(SQL), eq(Long.class), eq("missing"))).thenReturn(List.of());

        assertThat(DwFunctionUnitIdLookup.findIdByCode(jdbc, "missing")).isNull();
        assertThat(DwFunctionUnitIdLookup.findIdByCode(jdbc, "  ")).isNull();
        assertThat(DwFunctionUnitIdLookup.findIdByCode(jdbc, null)).isNull();
        assertThat(DwFunctionUnitIdLookup.findIdByCode(null, "FU-MCY")).isNull();
        verify(jdbc, never()).queryForList(eq(SQL), eq(Long.class), eq("  "));
    }
}
