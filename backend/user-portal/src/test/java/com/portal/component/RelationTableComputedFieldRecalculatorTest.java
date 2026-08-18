package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.exception.PortalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Server-side recalculation of computed columns on Relation Table rows.
 *
 * <p>ASTs are written as JSON because that is the shape stored in {@code computed_field_json}.
 */
@DisplayName("RelationTableComputedFieldRecalculator")
class RelationTableComputedFieldRecalculatorTest {

    private static final Long TABLE_ID = 7L;

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    private RelationTableComputedFieldRecalculator recalculator;
    private final List<Object[]> fieldRows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        recalculator = new RelationTableComputedFieldRecalculator(jdbcTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(recalculator, "enabled", true);
        fieldRows.clear();
    }

    @Nested
    @DisplayName("does nothing")
    class NoOp {

        @Test
        @DisplayName("when no relation table in the deployment has a computed column")
        void noComputedFieldAnywhere() {
            existenceProbe(false);
            Map<String, Object> row = row("quantity", "2");

            recalculator.recalculate(TABLE_ID, row);

            assertThat(row).isEqualTo(row("quantity", "2"));
            // The existence probe must be the only query: every existing relation table writes
            // through this path and must not pay for metadata lookups.
            verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), any(Object[].class));
        }

        @Test
        @DisplayName("when the feature is switched off")
        void disabled() {
            ReflectionTestUtils.setField(recalculator, "enabled", false);
            Map<String, Object> row = row("quantity", "2");

            recalculator.recalculate(TABLE_ID, row);

            assertThat(row).isEqualTo(row("quantity", "2"));
            verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Boolean.class));
        }

        @Test
        @DisplayName("when this particular table has no computed column")
        void tableWithoutComputedFields() {
            existenceProbe(true);
            metadataQuery();
            Map<String, Object> row = row("quantity", "2");

            recalculator.recalculate(TABLE_ID, row);

            assertThat(row).isEqualTo(row("quantity", "2"));
        }

        @Test
        @DisplayName("for an empty row")
        void emptyRow() {
            Map<String, Object> row = new HashMap<>();

            recalculator.recalculate(TABLE_ID, row);

            assertThat(row).isEmpty();
        }
    }

    @Nested
    @DisplayName("recomputes")
    class Recomputes {

        @Test
        @DisplayName("a row formula from the row's own columns")
        void rowFormula() {
            existenceProbe(true);
            computedField("line_total", binary("*", field("quantity"), field("unit_price")), "fail");
            metadataQuery();

            Map<String, Object> row = row("quantity", "3", "unit_price", "1500.00");

            recalculator.recalculate(TABLE_ID, row);

            assertThat(row.get("line_total")).isEqualTo(new BigDecimal("4500.00"));
        }

        @Test
        @DisplayName("discarding whatever the client submitted for the computed column")
        void overwritesClientValue() {
            existenceProbe(true);
            computedField("line_total", binary("*", field("quantity"), field("unit_price")), "fail");
            metadataQuery();

            Map<String, Object> row = row("quantity", "3", "unit_price", "1500.00");
            row.put("line_total", "1");

            recalculator.recalculate(TABLE_ID, row);

            assertThat(row.get("line_total")).isEqualTo(new BigDecimal("4500.00"));
        }

        @Test
        @DisplayName("a formula that reads another formula's fresh result")
        void chainedFormulas() {
            existenceProbe(true);
            // Declared out of dependency order on purpose: ordering is the recalculator's job.
            computedField("grand_total", binary("+", field("subtotal"), number("10")), "fail");
            computedField("subtotal", binary("*", field("quantity"), number("100")), "fail");
            metadataQuery();

            Map<String, Object> row = row("quantity", "3");

            recalculator.recalculate(TABLE_ID, row);

            assertThat(row.get("subtotal")).isEqualTo(new BigDecimal("300"));
            assertThat(row.get("grand_total")).isEqualTo(new BigDecimal("310"));
        }
    }

    @Nested
    @DisplayName("on evaluation failure")
    class Errors {

        @Test
        @DisplayName("rejects the write when onError=fail")
        void failMode() {
            existenceProbe(true);
            computedField("ratio", binary("/", field("amount"), field("divisor")), "fail");
            metadataQuery();

            Map<String, Object> row = row("amount", "100", "divisor", "0");

            assertThatThrownBy(() -> recalculator.recalculate(TABLE_ID, row))
                    .isInstanceOf(PortalException.class)
                    .hasMessageContaining("ratio")
                    .hasMessageContaining("DIVISION_BY_ZERO");
        }

        @Test
        @DisplayName("stores a blank when onError=null")
        void nullMode() {
            existenceProbe(true);
            computedField("ratio", binary("/", field("amount"), field("divisor")), "null");
            metadataQuery();

            Map<String, Object> row = row("amount", "100", "divisor", "0");
            row.put("ratio", 999);

            recalculator.recalculate(TABLE_ID, row);

            assertThat(row).containsEntry("ratio", null);
        }

        @Test
        @DisplayName("rejects the write when a stored definition is unusable")
        void unusableDefinitionBlocksWrite() {
            existenceProbe(true);
            fieldRows.add(new Object[]{"broken", "{}"});
            computedField("line_total", binary("*", field("quantity"), field("unit_price")), "fail");
            metadataQuery();

            Map<String, Object> row = row("quantity", "3", "unit_price", "1500.00");
            row.put("line_total", "1");

            assertThatThrownBy(() -> recalculator.recalculate(TABLE_ID, row))
                    .isInstanceOf(PortalException.class)
                    .hasMessageContaining("broken");
            assertThat(row.get("line_total")).isEqualTo("1");
        }
    }

    private void existenceProbe(boolean exists) {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class))).thenReturn(exists);
    }

    private void computedField(String name, String astJson, String onError) {
        fieldRows.add(new Object[]{name,
                "{\"version\":1,\"scope\":\"row\",\"source\":\"x\",\"ast\":" + astJson
                        + ",\"onError\":\"" + onError + "\"}"});
    }

    @SuppressWarnings("unchecked")
    private void metadataQuery() {
        List<Object> mapped = new ArrayList<>();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(TABLE_ID)))
                .thenAnswer(invocation -> {
                    RowMapper<Object> mapper = invocation.getArgument(1);
                    mapped.clear();
                    for (Object[] fieldRow : fieldRows) {
                        java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                        when(rs.getString("field_name")).thenReturn((String) fieldRow[0]);
                        when(rs.getString("json")).thenReturn((String) fieldRow[1]);
                        mapped.add(mapper.mapRow(rs, mapped.size()));
                    }
                    return mapped;
                });
    }

    private static Map<String, Object> row(String... keyValues) {
        Map<String, Object> row = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put(keyValues[i], keyValues[i + 1]);
        }
        return row;
    }

    private static String number(String text) {
        return "{\"type\":\"number\",\"text\":\"" + text + "\"}";
    }

    private static String field(String name) {
        return "{\"type\":\"field\",\"name\":\"" + name + "\"}";
    }

    private static String binary(String op, String left, String right) {
        return "{\"type\":\"binary\",\"op\":\"" + op + "\",\"left\":" + left + ",\"right\":" + right + "}";
    }
}
