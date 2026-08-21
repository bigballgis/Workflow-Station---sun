package com.portal.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SubTableRowIdentityEnricherTest {

    private Map<String, Object> variablesWith(Object subTables) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("applicant", "user-1");
        variables.put("__subTables__", subTables);
        return variables;
    }

    private Map<String, Object> row(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return row;
    }

    private Map<String, Object> slices(String key, List<Map<String, Object>> rows) {
        Map<String, Object> subTables = new LinkedHashMap<>();
        subTables.put(key, rows);
        return subTables;
    }

    @Test
    void anonymousRowsGetAnIdentityAndIdentifiedRowsAreLeftAlone() {
        Map<String, Object> anonymous = row("card_number", "4111", "merchant_name", "ACME");
        Map<String, Object> allocated = row("id_idw", 5001, "card_number", "4222");
        Map<String, Object> variables = variablesWith(slices("50533", List.of(anonymous, allocated)));

        assertThat(SubTableRowIdentityEnricher.ensureRowIdentities(variables)).isEqualTo(1);
        assertThat(String.valueOf(anonymous.get("row_id"))).isNotBlank();
        assertThat(allocated).doesNotContainKey("row_id");
    }

    @Test
    void twoRowsWithIdenticalBusinessValuesGetDistinctIdentities() {
        Map<String, Object> first = row("amount", 100);
        Map<String, Object> second = row("amount", 100);
        Map<String, Object> variables = variablesWith(slices("b1", List.of(first, second)));

        assertThat(SubTableRowIdentityEnricher.ensureRowIdentities(variables)).isEqualTo(2);
        assertThat(first.get("row_id")).isNotEqualTo(second.get("row_id"));
    }

    @Test
    void theOneAllowedLevelOfNestedSubTablesIsCoveredToo() {
        Map<String, Object> child = row("line", "a");
        Map<String, Object> parent = row("header", "h");
        parent.put("__subTables__", slices("child-binding", List.of(child)));
        Map<String, Object> variables = variablesWith(slices("parent-binding", List.of(parent)));

        assertThat(SubTableRowIdentityEnricher.ensureRowIdentities(variables)).isEqualTo(2);
        assertThat(String.valueOf(child.get("row_id"))).isNotBlank();
    }

    @Test
    void runningTwiceAssignsNothingNew() {
        Map<String, Object> variables = variablesWith(slices("b1", List.of(row("amount", 1))));
        SubTableRowIdentityEnricher.ensureRowIdentities(variables);
        assertThat(SubTableRowIdentityEnricher.ensureRowIdentities(variables)).isZero();
    }

    @Test
    void payloadsWithoutSubTablesAreUntouched() {
        assertThat(SubTableRowIdentityEnricher.ensureRowIdentities(null)).isZero();
        assertThat(SubTableRowIdentityEnricher.ensureRowIdentities(new LinkedHashMap<>())).isZero();
        assertThat(SubTableRowIdentityEnricher.ensureRowIdentities(variablesWith(new LinkedHashMap<>()))).isZero();
        // A slice holding something other than a row list is left for the sanitizer/validator
        // to reject rather than silently reshaped here.
        assertThat(SubTableRowIdentityEnricher.ensureRowIdentities(variablesWith(slices("b1", null)))).isZero();
    }

}
