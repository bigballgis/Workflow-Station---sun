package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.exception.PortalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Server-side recalculation of computed fields.
 *
 * <p>The JDBC layer is stubbed rather than hitting a database because what needs proving here is
 * evaluation behaviour — ordering, alias de-duplication, error policy, and above all that a Function
 * Unit with no computed fields is left untouched.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ComputedFieldRecalculator")
class ComputedFieldRecalculatorTest {

    private static final String FU = "purchase-request";
    private static final long MAIN_TABLE_ID = 1L;
    private static final long ITEMS_TABLE_ID = 2L;

    private static final String FIELDS_SQL = "dw_field_definitions fd";
    private static final String BINDINGS_SQL = "dw_form_table_bindings";

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PortalPrimaryKeyAllocationComponent primaryKeyAllocationComponent;

    private ComputedFieldRecalculator recalculator;

    /** Rows the stubbed field-metadata query returns. */
    private final List<Object[]> fieldRows = new ArrayList<>();

    /** binding id -> {tableId, tableName}, as the stubbed bindings query returns. */
    private final List<Object[]> bindingRows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        recalculator = new ComputedFieldRecalculator(
                jdbcTemplate, new ObjectMapper(), primaryKeyAllocationComponent);
        ReflectionTestUtils.setField(recalculator, "enabled", true);
        when(primaryKeyAllocationComponent.resolveFunctionUnitIdForAllocation(anyString()))
                .thenReturn(10L);
        stubExistenceProbe(true);
        stubFieldQuery();
        stubBindingQuery();
    }

    @Nested
    @DisplayName("leaves records alone")
    class NoOp {

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("when the deployment has no computed field at all")
        void noComputedFieldAnywhere() {
            // This is the guarantee that matters for every existing Function Unit and for the AI
            // write path: not one further query, not one map mutation.
            stubExistenceProbe(false);
            Map<String, Object> variables = existingRecord();
            Map<String, Object> before = deepCopy(variables);

            recalculator.recalculate(FU, variables);

            assertThat(variables).isEqualTo(before);
            verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), any(Object[].class));
            verify(primaryKeyAllocationComponent, never())
                    .resolveFunctionUnitIdForAllocation(anyString());
        }

        @Test
        @DisplayName("when this Function Unit has no computed field")
        void otherFunctionUnitHasThem() {
            // Existence is global; this FU's own metadata is empty, so its records are untouched.
            Map<String, Object> variables = existingRecord();
            Map<String, Object> before = deepCopy(variables);

            recalculator.recalculate(FU, variables);

            assertThat(variables).isEqualTo(before);
        }

        @Test
        @DisplayName("when the feature is switched off")
        void disabled() {
            ReflectionTestUtils.setField(recalculator, "enabled", false);
            mainField("total_price", aggregate("SUM", "request_items", "amount"), "fail");
            Map<String, Object> variables = existingRecord();
            Map<String, Object> before = deepCopy(variables);

            recalculator.recalculate(FU, variables);

            assertThat(variables).isEqualTo(before);
            verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Boolean.class));
        }

        @Test
        @DisplayName("when the variable map is empty")
        void emptyVariables() {
            Map<String, Object> variables = new HashMap<>();

            recalculator.recalculate(FU, variables);

            assertThat(variables).isEmpty();
        }
    }

    @Nested
    @DisplayName("recomputes")
    class Recomputes {

        @Test
        @DisplayName("a row formula on the main table")
        void rowFormula() {
            mainField("total_with_tax",
                    binary("*", field("subtotal"), binary("+", number("1"), field("tax_rate"))),
                    "fail");
            Map<String, Object> variables = new HashMap<>();
            variables.put("subtotal", "100.00");
            variables.put("tax_rate", "0.05");

            recalculator.recalculate(FU, variables);

            assertThat(variables.get("total_with_tax")).isEqualTo(new BigDecimal("105.0000"));
        }

        @Test
        @DisplayName("an aggregate over a sub-table")
        void aggregateFormula() {
            mainField("total_price", aggregate("SUM", "request_items", "amount"), "fail");
            binding(42L, ITEMS_TABLE_ID, "request_items");
            Map<String, Object> variables = new HashMap<>();
            variables.put(subTablesKey(), slices(Map.of("request_items",
                    rows(Map.of("amount", "4000.00"), Map.of("amount", "8000.00")))));

            recalculator.recalculate(FU, variables);

            assertThat(variables.get("total_price")).isEqualTo(new BigDecimal("12000.00"));
        }

        @Test
        @DisplayName("sub-table row formulas before the main aggregate that sums them")
        void twoLevelOrdering() {
            // amount is itself a formula; if the aggregate ran first it would sum blanks.
            subField(ITEMS_TABLE_ID, "amount",
                    binary("*", field("quantity"), field("unit_price")), "fail");
            mainField("total_price", aggregate("SUM", "request_items", "amount"), "fail");
            binding(42L, ITEMS_TABLE_ID, "request_items");
            Map<String, Object> variables = new HashMap<>();
            variables.put(subTablesKey(), slices(Map.of("request_items", rows(
                    row("quantity", "2", "unit_price", "1500.00"),
                    row("quantity", "3", "unit_price", "3000.00")))));

            recalculator.recalculate(FU, variables);

            // Scales are asserted exactly, not via compareTo: quantity (scale 0) times unit_price
            // (scale 2) must yield scale 2, and the sum must not silently widen it.
            List<Map<String, Object>> items = sliceOf(variables, "request_items");
            assertThat(items.get(0).get("amount")).isEqualTo(new BigDecimal("3000.00"));
            assertThat(items.get(1).get("amount")).isEqualTo(new BigDecimal("9000.00"));
            assertThat(variables.get("total_price")).isEqualTo(new BigDecimal("12000.00"));
        }

        @Test
        @DisplayName("a formula that reads another computed field, whatever order they are stored in")
        void chainedFields() {
            // Deliberately registered so the dependent field comes first in sort order.
            mainField("grand_total", binary("+", field("subtotal_calc"), number("10")), "fail");
            mainField("subtotal_calc", binary("*", field("qty"), number("100")), "fail");
            Map<String, Object> variables = new HashMap<>();
            variables.put("qty", "3");

            recalculator.recalculate(FU, variables);

            assertThat(variables.get("subtotal_calc")).isEqualTo(new BigDecimal("300"));
            assertThat(variables.get("grand_total")).isEqualTo(new BigDecimal("310"));
        }

        @Test
        @DisplayName("overwriting a value the client tried to tamper with")
        void overwritesSubmittedValue() {
            mainField("total_price", aggregate("SUM", "request_items", "amount"), "fail");
            binding(42L, ITEMS_TABLE_ID, "request_items");
            Map<String, Object> variables = new HashMap<>();
            variables.put("total_price", 9000);
            variables.put(subTablesKey(), slices(Map.of("request_items",
                    rows(Map.of("amount", "12000.00")))));

            recalculator.recalculate(FU, variables);

            assertThat(variables.get("total_price")).isEqualTo(new BigDecimal("12000.00"));
        }
    }

    @Nested
    @DisplayName("aggregate de-duplication")
    class Deduplication {

        @Test
        @DisplayName("counts one slice once even when it is present under three alias keys")
        void threeAliasesDoNotTriple() {
            // __subTables__ carries the same rows under the binding id, the table name and the
            // display name. Summing every key would triple an approval amount while looking
            // entirely plausible — the single worst failure mode this feature can have.
            mainField("total_price", aggregate("SUM", "request_items", "amount"), "fail");
            binding(42L, ITEMS_TABLE_ID, "request_items");
            Map<String, Object> subTables = new LinkedHashMap<>();
            subTables.put("42", rows(Map.of("amount", "4000.00"), Map.of("amount", "8000.00")));
            subTables.put("request_items", rows(Map.of("amount", "4000.00"), Map.of("amount", "8000.00")));
            subTables.put("Request Items", rows(Map.of("amount", "4000.00"), Map.of("amount", "8000.00")));
            Map<String, Object> variables = new HashMap<>();
            variables.put(subTablesKey(), subTables);

            recalculator.recalculate(FU, variables);

            assertThat(variables.get("total_price")).isEqualTo(new BigDecimal("12000.00"));
        }

        @Test
        @DisplayName("refreshes row formulas in every alias copy, not just the canonical one")
        void everyAliasIsRecomputed() {
            // Aliases hold separate copies of the same logical rows. A formula is a pure function
            // of the row, so recomputing it in each copy is safe and avoids leaving a stale value
            // behind in whichever alias the aggregate happened not to read.
            subField(ITEMS_TABLE_ID, "amount",
                    binary("*", field("quantity"), field("unit_price")), "fail");
            binding(42L, ITEMS_TABLE_ID, "request_items");
            Map<String, Object> subTables = new LinkedHashMap<>();
            subTables.put("42", rows(row("quantity", "2", "unit_price", "50.00")));
            subTables.put("request_items", rows(row("quantity", "2", "unit_price", "50.00")));
            Map<String, Object> variables = new HashMap<>();
            variables.put(subTablesKey(), subTables);

            recalculator.recalculate(FU, variables);

            assertThat(sliceOf(variables, "42").get(0).get("amount"))
                    .isEqualTo(new BigDecimal("100.00"));
            assertThat(sliceOf(variables, "request_items").get(0).get("amount"))
                    .isEqualTo(new BigDecimal("100.00"));
        }
    }

    @Nested
    @DisplayName("error policy")
    class Errors {

        @Test
        @DisplayName("blocks the write when onError is fail")
        void failMode() {
            mainField("ratio", binary("/", field("amount"), field("divisor")), "fail");
            Map<String, Object> variables = new HashMap<>();
            variables.put("amount", "100");
            variables.put("divisor", "0");

            assertThatThrownBy(() -> recalculator.recalculate(FU, variables))
                    .isInstanceOf(PortalException.class)
                    .hasMessageContaining("ratio")
                    .hasMessageContaining("DIVISION_BY_ZERO");
        }

        @Test
        @DisplayName("stores blank when onError is null")
        void nullMode() {
            mainField("ratio", binary("/", field("amount"), field("divisor")), "null");
            Map<String, Object> variables = new HashMap<>();
            variables.put("amount", "100");
            variables.put("divisor", "0");
            variables.put("ratio", 999);

            recalculator.recalculate(FU, variables);

            assertThat(variables.get("ratio")).isNull();
        }

        @Test
        @DisplayName("never leaves a stale client value behind on failure")
        void staleValueIsNotKept() {
            // Keeping the submitted number would be worse than blanking it: the record would look
            // successfully computed while resting on a value the server never verified.
            mainField("ratio", binary("/", field("amount"), field("divisor")), "null");
            Map<String, Object> variables = new HashMap<>();
            variables.put("amount", "abc");
            variables.put("divisor", "2");
            variables.put("ratio", 12345);

            recalculator.recalculate(FU, variables);

            assertThat(variables).containsEntry("ratio", null);
        }

        @Test
        @DisplayName("reports a dependency cycle instead of looping")
        void cycleIsReported() {
            // The design-time validator rejects cycles, so reaching here means the stored rows were
            // edited around it. Recursing forever would take the whole service down.
            mainField("a", field("b"), "fail");
            mainField("b", field("a"), "fail");
            Map<String, Object> variables = existingRecord();

            assertThatThrownBy(() -> recalculator.recalculate(FU, variables))
                    .isInstanceOf(PortalException.class)
                    .hasMessageContaining("cycle");
        }

        @Test
        @DisplayName("skips a field whose stored definition is unusable")
        void unusableDefinitionIsSkipped() {
            fieldRows.add(new Object[]{MAIN_TABLE_ID, "purchase_request", "MAIN", "broken", "{}"});
            mainField("total", binary("+", number("1"), number("2")), "fail");
            Map<String, Object> variables = existingRecord();

            recalculator.recalculate(FU, variables);

            assertThat(variables).doesNotContainKey("broken");
            assertThat(variables.get("total")).isEqualTo(new BigDecimal("3"));
        }
    }

    @Nested
    @DisplayName("existence probe")
    class ExistenceProbe {

        @Test
        @DisplayName("is cached so repeated writes do not re-query")
        void cached() {
            recalculator.hasAnyComputedField();
            recalculator.hasAnyComputedField();
            recalculator.hasAnyComputedField();

            verify(jdbcTemplate, org.mockito.Mockito.times(1))
                    .queryForObject(anyString(), eq(Boolean.class));
        }
    }

    // ----- stubbing ------------------------------------------------------------------------

    private void stubExistenceProbe(boolean exists) {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class))).thenReturn(exists);
    }

    @SuppressWarnings("unchecked")
    private void stubFieldQuery() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    if (!sql.contains(FIELDS_SQL)) {
                        return List.of();
                    }
                    RowMapper<Object> mapper = invocation.getArgument(1);
                    List<Object> mapped = new ArrayList<>();
                    for (Object[] row : fieldRows) {
                        mapped.add(mapper.mapRow(fieldResultSet(row), mapped.size()));
                    }
                    return mapped;
                });
    }

    private void stubBindingQuery() {
        org.mockito.Mockito.doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (!sql.contains(BINDINGS_SQL)) {
                return null;
            }
            RowCallbackHandler handler = invocation.getArgument(1);
            for (Object[] row : bindingRows) {
                handler.processRow(bindingResultSet(row));
            }
            return null;
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
    }

    /** Minimal ResultSet over {tableId, tableName, tableType, fieldName, json}. */
    private ResultSet fieldResultSet(Object[] row) throws Exception {
        ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        when(rs.getLong("table_id")).thenReturn((Long) row[0]);
        when(rs.getString("table_name")).thenReturn((String) row[1]);
        when(rs.getString("table_type")).thenReturn((String) row[2]);
        when(rs.getString("field_name")).thenReturn((String) row[3]);
        when(rs.getString("json")).thenReturn((String) row[4]);
        return rs;
    }

    /** Minimal ResultSet over {bindingId, tableId, tableName}. */
    private ResultSet bindingResultSet(Object[] row) throws Exception {
        ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        when(rs.getLong("binding_id")).thenReturn((Long) row[0]);
        when(rs.getLong("table_id")).thenReturn((Long) row[1]);
        when(rs.getString("table_name")).thenReturn((String) row[2]);
        return rs;
    }

    // ----- fixtures ------------------------------------------------------------------------

    private void mainField(String name, String ast, String onError) {
        fieldRows.add(new Object[]{
                MAIN_TABLE_ID, "purchase_request", "MAIN", name, definition(ast, onError)});
    }

    private void subField(long tableId, String name, String ast, String onError) {
        fieldRows.add(new Object[]{
                tableId, "request_items", "SUB", name, definition(ast, onError)});
    }

    private void binding(long bindingId, long tableId, String tableName) {
        bindingRows.add(new Object[]{bindingId, tableId, tableName});
    }

    private static String definition(String ast, String onError) {
        return "{\"version\":1,\"scope\":\"row\",\"source\":\"x\",\"onError\":\"" + onError
                + "\",\"ast\":" + ast + "}";
    }

    // ASTs are written as JSON because that is the exact shape stored in computed_field_json and
    // produced by the designer's parser; building them as nested maps would test something else.

    private static String number(String text) {
        return "{\"type\":\"number\",\"text\":\"" + text + "\"}";
    }

    private static String field(String name) {
        return "{\"type\":\"field\",\"name\":\"" + name + "\"}";
    }

    private static String binary(String op, String left, String right) {
        return "{\"type\":\"binary\",\"op\":\"" + op + "\",\"left\":" + left
                + ",\"right\":" + right + "}";
    }

    private static String aggregate(String fn, String table, String column) {
        return "{\"type\":\"aggregate\",\"fn\":\"" + fn + "\",\"table\":\"" + table
                + "\",\"column\":\"" + column + "\"}";
    }

    /** A record of an existing Function Unit: plain fields only, nothing computed. */
    private static Map<String, Object> existingRecord() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("title", "Laptop refresh");
        variables.put("amount", "1200.00");
        variables.put("initiator", "alice");
        variables.put("created_at", "2026-08-13T00:00:00");
        variables.put(subTablesKey(), slices(Map.of("request_items",
                rows(row("quantity", "2", "unit_price", "600.00")))));
        return variables;
    }

    private static String subTablesKey() {
        return "__subTables__";
    }

    private static Map<String, Object> slices(Map<String, List<Map<String, Object>>> raw) {
        return new LinkedHashMap<>(raw);
    }

    @SafeVarargs
    private static List<Map<String, Object>> rows(Map<String, Object>... rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(new HashMap<>(row));
        }
        return result;
    }

    private static Map<String, Object> row(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> row = new HashMap<>();
        row.put(k1, v1);
        row.put(k2, v2);
        return row;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> sliceOf(Map<String, Object> variables, String key) {
        Map<String, Object> subTables = (Map<String, Object>) variables.get(subTablesKey());
        return (List<Map<String, Object>>) subTables.get(key);
    }

    /** Structural snapshot used to assert "not one byte changed". */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> copy = new HashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> map) {
                copy.put(entry.getKey(), deepCopy((Map<String, Object>) map));
            } else if (value instanceof List<?> list) {
                List<Object> copied = new ArrayList<>();
                for (Object item : list) {
                    copied.add(item instanceof Map<?, ?> m
                            ? deepCopy((Map<String, Object>) m) : item);
                }
                copy.put(entry.getKey(), copied);
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }
}
