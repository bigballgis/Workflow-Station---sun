package com.portal.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MainTableViewDesignerFilterSqlTest {

    /** Every field is a JSON member except {@code label}, which stands in for a resolved column. */
    private final MainTableViewDesignerFilterSql compiler = new MainTableViewDesignerFilterSql(
            field -> "label".equals(field) ? null : "pi.variables->>'" + field + "'",
            "View 7 'Open requests'");

    private static Map<String, Object> condition(String field, String operator, Object value) {
        Map<String, Object> condition = new java.util.LinkedHashMap<>();
        condition.put("fieldName", field);
        condition.put("operator", operator);
        condition.put("value", value);
        return condition;
    }

    private static Map<String, Object> node(String logic, Object... children) {
        List<Map<String, Object>> conditions = new ArrayList<>();
        List<Map<String, Object>> groups = new ArrayList<>();
        for (Object child : children) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) child;
            (map.containsKey("fieldName") ? conditions : groups).add(map);
        }
        return Map.of("logic", logic, "conditions", conditions, "groups", groups);
    }

    @Test
    void aViewThatFiltersNothingConstrainsNothing() {
        assertThat(compiler.whereClause(null, new ArrayList<>())).isEmpty();
        assertThat(compiler.whereClause(Map.of(), new ArrayList<>())).isEmpty();
        assertThat(compiler.whereClause(node("and"), new ArrayList<>()))
                .as("a filter with no conditions left in it must not exclude every row")
                .isEmpty();
    }

    @Test
    void conditionsAreCaseInsensitiveJustAsTheyWereInMemory() {
        List<Object> params = new ArrayList<>();
        String sql = compiler.whereClause(node("and", condition("status", "eq", "Open")), params);

        assertThat(sql).isEqualTo(
                " AND ((pi.variables->>'status' IS NOT NULL"
                        + " AND lower(pi.variables->>'status') = lower(?)))");
        assertThat(params).containsExactly("Open");
    }

    @Test
    void aRowWithoutAValueFailsEvenTheNegativeConditions() {
        List<Object> params = new ArrayList<>();
        String sql = compiler.whereClause(node("and", condition("status", "ne", "Open")), params);

        assertThat(sql)
                .as("in memory a missing value failed every operator but the null checks; SQL's own"
                        + " three-valued logic would agree here, but only by accident")
                .contains("pi.variables->>'status' IS NOT NULL");
    }

    @Test
    void wildcardsInTheDesignedValueAreLiteralText() {
        List<Object> params = new ArrayList<>();
        compiler.whereClause(node("and", condition("title", "contains", "100%_net")), params);

        assertThat(params).containsExactly("%100\\%\\_net%");
    }

    @Test
    void orMeansOrAndNestedGroupsKeepTheirOwnLogic() {
        List<Object> params = new ArrayList<>();
        String sql = compiler.whereClause(node("or",
                condition("status", "eq", "Open"),
                node("and", condition("status", "eq", "Draft"), condition("amount", "gt", "100"))), params);

        assertThat(sql).contains(" OR ");
        assertThat(sql).contains(" AND (");
        assertThat(params).containsExactly("Open", "Draft", new BigDecimal("100"), "100");
    }

    @Test
    void aNumericBoundComparesAsANumberWhereTheRowHoldsOneAndAsTextWhereItDoesNot() {
        List<Object> params = new ArrayList<>();
        String sql = compiler.whereClause(node("and", condition("amount", "gt", "100")), params);

        assertThat(sql)
                .as("'20' is above '100' as text and below it as a number; which one applies was a"
                        + " per-row decision in memory and has to stay one")
                .contains("CASE WHEN pi.variables->>'amount' ~ ")
                .contains("(pi.variables->>'amount')::numeric > ?")
                .contains("ELSE lower(pi.variables->>'amount') > lower(?)");
        assertThat(params).containsExactly(new BigDecimal("100"), "100");
    }

    @Test
    void aNonNumericBoundNeverTakesTheNumericBranch() {
        List<Object> params = new ArrayList<>();
        String sql = compiler.whereClause(node("and", condition("title", "lt", "M")), params);

        assertThat(sql).doesNotContain("::numeric");
        assertThat(params).containsExactly("M");
    }

    @Test
    void inAcceptsBothACommaSeparatedStringAndAList() {
        List<Object> fromString = new ArrayList<>();
        compiler.whereClause(node("and", condition("status", "in", "Open, Draft")), fromString);
        List<Object> fromList = new ArrayList<>();
        compiler.whereClause(node("and", condition("status", "in", List.of("Open", "Draft"))), fromList);

        assertThat(fromString).containsExactly("Open", "Draft");
        assertThat(fromList).containsExactly("Open", "Draft");
    }

    @Test
    void anEmptyValueOnlyMatchesTheNullChecks() {
        List<Object> params = new ArrayList<>();
        String sql = compiler.whereClause(node("and", condition("status", "isNull", null)), params);

        assertThat(sql).isEqualTo(
                " AND ((pi.variables->>'status' IS NULL OR btrim(pi.variables->>'status') = ''))");
        assertThat(params).isEmpty();
    }

    @Test
    void aFilterOnALabelResolvedAfterTheReadIsReportedRatherThanApproximated() {
        assertThatThrownBy(() ->
                compiler.whereClause(node("and", condition("label", "eq", "ACME")), new ArrayList<>()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("View 7 'Open requests'")
                .hasMessageContaining("label");
    }

    @Test
    void anOperatorWithNoMeaningHereIsReportedRatherThanIgnored() {
        assertThatThrownBy(() ->
                compiler.whereClause(node("and", condition("status", "sortOf", "Open")), new ArrayList<>()))
                .as("ignoring it would widen the view silently, which is how a narrowed view leaks rows")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sortOf");
    }
}
