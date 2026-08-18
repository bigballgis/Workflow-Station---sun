package com.portal.util;

import com.portal.dto.ListColumnFilter;
import com.portal.dto.PortalListColumnMeta;
import com.portal.util.MainTableViewColumnSpec.FieldSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MainTableViewColumnSpecTest {

    private FieldSource designed(String name, String dataType) {
        return new FieldSource(name, name, false, "field", dataType);
    }

    private PortalListColumnMeta columnNamed(List<FieldSource> fields, String name) {
        return MainTableViewColumnSpec.columnsFor(fields).stream()
                .filter(c -> c.field().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void designedFieldsAreQueryableAccordingToTheirDeclaredType() {
        List<FieldSource> fields = List.of(
                designed("merchant_name", "VARCHAR"),
                designed("billing_amount", "DECIMAL"),
                designed("merchant_credit_date", "DATE"),
                designed("temporary_refund", "BOOLEAN"));

        assertThat(columnNamed(fields, "merchant_name").kind()).isEqualTo(PortalListColumnMeta.Kind.TEXT);
        assertThat(columnNamed(fields, "billing_amount").kind()).isEqualTo(PortalListColumnMeta.Kind.NUMBER);
        assertThat(columnNamed(fields, "merchant_credit_date").kind()).isEqualTo(PortalListColumnMeta.Kind.DATETIME);
        assertThat(columnNamed(fields, "temporary_refund").kind()).isEqualTo(PortalListColumnMeta.Kind.BOOLEAN);
        assertThat(columnNamed(fields, "billing_amount").operators()).contains("between", "gt");
        assertThat(MainTableViewColumnSpec.columnsFor(fields))
                .allMatch(PortalListColumnMeta::filterable)
                .allMatch(PortalListColumnMeta::sortable);
    }

    @Test
    void groupingFollowsTheFieldsMeaningRatherThanBeingOfferedEverywhere() {
        List<FieldSource> fields = List.of(
                designed("merchant_name", "VARCHAR"),
                designed("temporary_refund", "BOOLEAN"),
                designed("merchant_credit_date", "DATE"),
                new FieldSource("process_status", "Status", true, "field", null));

        assertThat(columnNamed(fields, "merchant_name").groupable())
                .as("free text repeats too rarely to group by")
                .isFalse();
        assertThat(columnNamed(fields, "merchant_credit_date").groupable())
                .as("a timestamp is unique per row")
                .isFalse();
        assertThat(columnNamed(fields, "temporary_refund").groupable()).isTrue();
        assertThat(columnNamed(fields, "process_status").groupable()).isTrue();
    }

    @Test
    void columnsWhoseValueIsResolvedInJavaAreDeclaredDisplayOnly() {
        List<FieldSource> fields = List.of(
                new FieldSource("customer_label", "Customer", false, "lookup_display", "VARCHAR"),
                new FieldSource("owner_label", "Owner", false, "fk_display", "VARCHAR"),
                designed("scan", "FILE"),
                designed("mystery", null));

        for (String field : List.of("customer_label", "owner_label", "scan", "mystery")) {
            PortalListColumnMeta column = columnNamed(fields, field);
            assertThat(column.filterable()).as(field + " filterable").isFalse();
            assertThat(column.sortable()).as(field + " sortable").isFalse();
            assertThat(column.operators()).as(field + " operators").isEmpty();
        }
    }

    @Test
    void systemColumnsCompileToRealColumnsAndDesignedFieldsToJsonMembers() {
        List<FieldSource> fields = List.of(
                new FieldSource("process_status", "Status", true, "field", null),
                new FieldSource("initiator", "Initiator", true, "field", null),
                designed("merchant_name", "VARCHAR"));
        ListFilterSql.ColumnRef ref = MainTableViewColumnSpec.columnRefFor(fields);

        assertThat(ref.sqlFor("process_status")).isEqualTo("pi.status");
        assertThat(ref.sqlFor("initiator"))
                .as("projection shows the display name and falls back to the id, so the filter must see both")
                .isEqualTo("COALESCE(pi.start_user_name, pi.start_user_id)");
        assertThat(ref.sqlFor("merchant_name")).isEqualTo("pi.variables->>'merchant_name'");
    }

    @Test
    void aCraftedFieldNameCannotReachSql() {
        ListFilterSql.ColumnRef ref = MainTableViewColumnSpec.columnRefFor(List.of());
        assertThatThrownBy(() -> ref.sqlFor("x'; DROP TABLE up_process_instance --"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withoutAUserSortTheViewKeepsTheOrderItsDesignerConfigured() {
        List<FieldSource> fields = List.of(
                designed("merchant_name", "VARCHAR"),
                designed("billing_amount", "DECIMAL"));
        ListFilterSql sql = MainTableViewColumnSpec.sqlFor(fields, List.of(
                Map.of("fieldName", "merchant_name", "direction", "ASC"),
                Map.of("fieldName", "billing_amount", "direction", "DESC")));

        assertThat(sql.orderBy(null, null))
                .isEqualTo(" ORDER BY pi.variables->>'merchant_name' ASC NULLS FIRST, "
                        + "(CASE WHEN pi.variables->>'billing_amount' ~ '^-?[0-9]+(\\.[0-9]+)?$' "
                        + "THEN (pi.variables->>'billing_amount')::numeric END) DESC NULLS LAST, "
                        + "pi.start_time DESC, pi.id");
    }

    @Test
    void aUserSortReplacesTheDesignerSortButKeepsTheTiebreak() {
        ListFilterSql sql = MainTableViewColumnSpec.sqlFor(
                List.of(designed("merchant_name", "VARCHAR")),
                List.of(Map.of("fieldName", "merchant_name", "direction", "ASC")));

        assertThat(sql.orderBy("merchant_name", "DESC"))
                .isEqualTo(" ORDER BY pi.variables->>'merchant_name' DESC NULLS LAST, pi.id");
    }

    @Test
    void aDesignerSortOnAFieldTheViewNoLongerShowsIsSkipped() {
        ListFilterSql sql = MainTableViewColumnSpec.sqlFor(
                List.of(designed("merchant_name", "VARCHAR")),
                List.of(Map.of("fieldName", "removed_field", "direction", "ASC")));

        assertThat(sql.orderBy(null, null)).isEqualTo(" ORDER BY pi.start_time DESC, pi.id");
    }

    @Test
    void filtersCompileAgainstTheViewsOwnColumns() {
        ListFilterSql sql = MainTableViewColumnSpec.sqlFor(
                List.of(designed("billing_amount", "DECIMAL"),
                        new FieldSource("customer_label", "Customer", false, "lookup_display", "VARCHAR")),
                List.of());
        List<Object> params = new ArrayList<>();

        assertThat(sql.whereClause(List.of(new ListColumnFilter("billing_amount", "gt", "80", null)), params))
                .contains("(pi.variables->>'billing_amount')::numeric > ?");
        assertThatThrownBy(() -> sql.whereClause(
                List.of(new ListColumnFilter("customer_label", "contains", "x", null)), new ArrayList<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not filterable");
    }
}
