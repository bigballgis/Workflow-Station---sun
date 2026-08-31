package com.portal.util;

import com.platform.common.list.ListColumnFilter;
import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListFilterSql;
import com.portal.util.MainTableViewColumnSpec.FieldSource;
import com.portal.util.MainTableViewColumnSpec.SqlSource;
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

    private ListColumnMeta columnNamed(List<FieldSource> fields, String name) {
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

        assertThat(columnNamed(fields, "merchant_name").kind()).isEqualTo(ListColumnMeta.Kind.TEXT);
        assertThat(columnNamed(fields, "billing_amount").kind()).isEqualTo(ListColumnMeta.Kind.NUMBER);
        assertThat(columnNamed(fields, "merchant_credit_date").kind()).isEqualTo(ListColumnMeta.Kind.DATETIME);
        assertThat(columnNamed(fields, "temporary_refund").kind()).isEqualTo(ListColumnMeta.Kind.BOOLEAN);
        assertThat(columnNamed(fields, "temporary_refund").options())
                .extracting(ListColumnMeta.Option::value)
                .containsExactly("true", "false");
        assertThat(columnNamed(fields, "billing_amount").operators()).contains("between", "gt");
        assertThat(columnNamed(fields, "merchant_credit_date").operators().get(0)).isEqualTo("today");
        assertThat(MainTableViewColumnSpec.columnsFor(fields))
                .allMatch(ListColumnMeta::filterable)
                .allMatch(ListColumnMeta::sortable);
    }

    @Test
    void timeJsonAndUntypedDesignedFieldsAreQueryable() {
        List<FieldSource> fields = List.of(
                designed("shift_start", "TIME"),
                designed("payload", "JSON"),
                designed("notes", null));
        assertThat(columnNamed(fields, "shift_start").kind()).isEqualTo(ListColumnMeta.Kind.DATETIME);
        assertThat(columnNamed(fields, "payload").kind()).isEqualTo(ListColumnMeta.Kind.TEXT);
        assertThat(columnNamed(fields, "notes").kind()).isEqualTo(ListColumnMeta.Kind.TEXT);
        assertThat(MainTableViewColumnSpec.columnsFor(fields))
                .allMatch(ListColumnMeta::filterable)
                .allMatch(ListColumnMeta::sortable);
    }

    @Test
    void auditTimestampColumnsAreDatesEvenWhenTheTableDeclaredThemAsText() {
        List<FieldSource> fields = List.of(
                designed("created_at", "VARCHAR"),
                designed("updated_at", "VARCHAR"),
                designed("created_by", "VARCHAR"));

        ListColumnMeta created = columnNamed(fields, "created_at");
        assertThat(created.kind()).isEqualTo(ListColumnMeta.Kind.DATETIME);
        assertThat(created.filterable()).isTrue();
        assertThat(created.operators().get(0)).isEqualTo("today");
        assertThat(columnNamed(fields, "updated_at").kind()).isEqualTo(ListColumnMeta.Kind.DATETIME);
        assertThat(columnNamed(fields, "created_by").kind())
                .as("the person audit column is a USER picker, not free text")
                .isEqualTo(ListColumnMeta.Kind.USER);
    }

    @Test
    void lookupAndFkDisplayColumnsStayDisplayOnlyUntilMappedAndFileIsFilterableByName() {
        List<FieldSource> fields = List.of(
                new FieldSource("customer_label", "Customer", false, "lookup_display", "VARCHAR"),
                new FieldSource("owner_label", "Owner", false, "fk_display", "VARCHAR"),
                designed("scan", "FILE"));

        for (String field : List.of("customer_label", "owner_label")) {
            ListColumnMeta column = columnNamed(fields, field);
            assertThat(column.filterable()).as(field + " filterable").isFalse();
            assertThat(column.sortable()).as(field + " sortable").isFalse();
            assertThat(column.operators()).as(field + " operators").isEmpty();
        }
        ListColumnMeta file = columnNamed(fields, "scan");
        assertThat(file.kind()).isEqualTo(ListColumnMeta.Kind.FILE);
        assertThat(file.filterable()).isTrue();
        assertThat(file.sortable()).isTrue();
        assertThat(file.operators()).contains("contains", "eq", "isNull");
    }

    @Test
    void aFileColumnFilterComparesExtractedNamesNotTheStoredUrl() {
        ListFilterSql sql = MainTableViewColumnSpec.sqlFor(
                List.of(designed("scan", "FILE")), List.of());
        List<Object> params = new ArrayList<>();
        String where = sql.whereClause(
                List.of(new ListColumnFilter("scan", "contains", "report", null)), params);
        assertThat(where)
                .contains("pi.variables->'scan'")
                .contains("originalName")
                .doesNotContain("pi.variables->>'scan' ILIKE");
        assertThat(params).containsExactly("%report%");
    }

    @Test
    void lookupAndFkDisplayColumnsAreFilterableOnceTheStoredKeyMappingIsKnown() {
        List<FieldSource> fields = List.of(
                new FieldSource("customer_label", "Customer", false, "lookup_display", "VARCHAR",
                        "customer_id", "name", 42L, List.of()),
                new FieldSource("owner_label", "Owner", false, "fk_display", "VARCHAR",
                        "case_id", "legal_hold", null, List.of("case_number")));

        ListColumnMeta lookup = columnNamed(fields, "customer_label");
        assertThat(lookup.filterable()).isTrue();
        assertThat(lookup.sortable()).isFalse();
        assertThat(lookup.operators()).contains("contains", "eq");

        ListColumnMeta fk = columnNamed(fields, "owner_label");
        assertThat(fk.filterable()).isTrue();
        assertThat(fk.sortable()).isFalse();
    }

    @Test
    void systemColumnsCompileToRealColumnsAndDesignedFieldsToJsonMembers() {
        List<FieldSource> fields = List.of(
                new FieldSource("process_status", "Status", true, "field", null),
                new FieldSource("initiator", "Initiator", true, "field", null),
                designed("merchant_name", "VARCHAR"));
        ListFilterSql.ColumnRef ref = MainTableViewColumnSpec.columnRefFor(fields, SqlSource.INSTANCE);

        assertThat(ref.sqlFor("process_status")).isEqualTo("pi.status");
        assertThat(ref.sqlFor("initiator"))
                .as("projection shows the display name and falls back to the id, so the filter must see both")
                .isEqualTo("COALESCE(pi.start_user_name, pi.start_user_id)");
        assertThat(ref.sqlFor("merchant_name")).isEqualTo("pi.variables->>'merchant_name'");
    }

    @Test
    void aCraftedFieldNameCannotReachSql() {
        ListFilterSql.ColumnRef ref = MainTableViewColumnSpec.columnRefFor(List.of(), SqlSource.INSTANCE);
        assertThatThrownBy(() -> ref.sqlFor("x'; DROP TABLE up_process_instance --"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aSubViewReadsDesignedFieldsFromTheExpandedRowAndSystemColumnsFromTheInstance() {
        List<FieldSource> fields = List.of(
                new FieldSource("process_status", "Status", true, "field", null),
                designed("line_amount", "DECIMAL"));
        ListFilterSql.ColumnRef ref =
                MainTableViewColumnSpec.columnRefFor(fields, SqlSource.EXPANDED_SUB_ROW);

        assertThat(ref.sqlFor("line_amount")).isEqualTo("pi.sub_elem->>'line_amount'");
        assertThat(ref.sqlFor("process_status"))
                .as("a sub row still belongs to one instance, so status is the instance's")
                .isEqualTo("pi.status");
    }

    @Test
    void aSubViewFiltersSystemColumnsByTheSameKindAsMain() {
        List<FieldSource> fields = List.of(
                new FieldSource("process_status", "Status", true, "field", null),
                new FieldSource("start_time", "Start Time", true, "field", null),
                new FieldSource("initiator", "Initiator", true, "field", null),
                designed("line_amount", "DECIMAL"),
                designed("transaction_date", "DATE"));
        Map<String, ListColumnMeta> byField = new java.util.LinkedHashMap<>();
        MainTableViewColumnSpec.columnsFor(fields, SqlSource.EXPANDED_SUB_ROW)
                .forEach(c -> byField.put(c.field(), c));

        assertThat(byField.get("process_status").kind()).isEqualTo(ListColumnMeta.Kind.ENUM);
        assertThat(byField.get("process_status").filterable()).isTrue();
        assertThat(byField.get("start_time").kind()).isEqualTo(ListColumnMeta.Kind.DATETIME);
        assertThat(byField.get("start_time").filterable()).isTrue();
        assertThat(byField.get("initiator").kind()).isEqualTo(ListColumnMeta.Kind.USER);
        assertThat(byField.get("line_amount").kind()).isEqualTo(ListColumnMeta.Kind.NUMBER);
        assertThat(byField.get("transaction_date").kind()).isEqualTo(ListColumnMeta.Kind.DATETIME);
        assertThat(byField.get("transaction_date").operators().get(0)).isEqualTo("today");
    }

    @Test
    void aSubViewStatusFilterComparesTheOwningInstanceNotTheExpandedRow() {
        ListFilterSql sql = MainTableViewColumnSpec.sqlFor(
                List.of(
                        new FieldSource("process_status", "Status", true, "field", null),
                        designed("transaction_date", "DATE")),
                List.of(),
                SqlSource.EXPANDED_SUB_ROW, "pi.id, pi.row_identity");

        List<Object> params = new ArrayList<>();
        assertThat(sql.whereClause(
                List.of(new ListColumnFilter("process_status", "eq", "RUNNING", null)), params))
                .contains("pi.status")
                .doesNotContain("sub_elem->>'process_status'");

        params.clear();
        assertThat(sql.whereClause(
                List.of(new ListColumnFilter("transaction_date", "today", "", null)), params))
                .contains("pi.sub_elem->>'transaction_date'");
    }

    @Test
    void aSubViewPagesOnTheRowNotOnTheInstanceThatCarriesIt() {
        ListFilterSql sql = MainTableViewColumnSpec.sqlFor(
                List.of(designed("line_amount", "DECIMAL")), List.of(),
                SqlSource.EXPANDED_SUB_ROW, "pi.id, pi.row_identity");

        assertThat(sql.orderBy(null, null))
                .as("two rows of one instance must not be interchangeable, or a page boundary "
                        + "could show one of them twice and the other never")
                .isEqualTo(" ORDER BY pi.start_time DESC, pi.id, pi.row_identity");
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

    @Test
    void listFilterSqlRejectsAMappedDisplayFilterSoTheLabelCannotBeComparedToTheKey() {
        FieldSource mapped = new FieldSource("customer_label", "Customer", false, "lookup_display", "VARCHAR",
                "customer_id", "name", 42L, List.of());
        ListFilterSql sql = MainTableViewColumnSpec.sqlFor(List.of(mapped), List.of());

        assertThatThrownBy(() -> sql.whereClause(
                List.of(new ListColumnFilter("customer_label", "contains", "Acme", null)), new ArrayList<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not filterable");
    }
}
