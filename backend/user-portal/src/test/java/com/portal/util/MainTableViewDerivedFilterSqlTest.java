package com.portal.util;

import com.platform.common.list.ListColumnFilter;
import com.portal.util.MainTableViewColumnSpec.FieldSource;
import com.portal.util.MainTableViewColumnSpec.SqlSource;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MainTableViewDerivedFilterSqlTest {

    private static final FieldSource LOOKUP = new FieldSource(
            "customer_label", "Customer", false, "lookup_display", "VARCHAR",
            "customer_id", "name", 42L, List.of());
    private static final FieldSource USER_LOOKUP = new FieldSource(
            "owner_label", "Owner", false, "lookup_display", "VARCHAR",
            "owner_id", "full_name", MainTableViewDerivedFilterSql.SYSTEM_USER_TABLE_ID, List.of());
    private static final FieldSource FK = new FieldSource(
            "case_title", "Case", false, "fk_display", "VARCHAR",
            "case_id", "legal_hold", null, List.of("case_number"));
    private static final FieldSource AMOUNT = new FieldSource(
            "billing_amount", "Amount", false, "field", "DECIMAL");

    @Test
    void plainFiltersDropDisplayMappedColumnsAndKeepDesignedFields() {
        List<ListColumnFilter> filters = List.of(
                new ListColumnFilter("customer_label", "contains", "Acme", null),
                new ListColumnFilter("billing_amount", "gt", "80", null));

        assertThat(MainTableViewDerivedFilterSql.plainFilters(filters, List.of(LOOKUP, AMOUNT)))
                .containsExactly(new ListColumnFilter("billing_amount", "gt", "80", null));
    }

    @Test
    void lookupContainsJoinsTheRelationTableOnTheStoredKeyNotTheTypedLabel() {
        SqlFragment sql = MainTableViewDerivedFilterSql.whereClause(
                List.of(new ListColumnFilter("customer_label", "contains", "Acme", null)),
                List.of(LOOKUP),
                SqlSource.INSTANCE);

        assertThat(sql.sql())
                .contains("EXISTS")
                .contains("rt_table_data_rows")
                .contains("rt.table_id = ?")
                .contains("rt.data->>'name' ILIKE ?")
                .doesNotContain("pi.variables->>'customer_id' ILIKE");
        assertThat(sql.params()).containsExactly(42L, "%Acme%");
    }

    @Test
    void lookupEqUsesTheSystemUserTableWhenTheSourceIsSysUsers() {
        SqlFragment sql = MainTableViewDerivedFilterSql.whereClause(
                List.of(new ListColumnFilter("owner_label", "eq", "张三", null)),
                List.of(USER_LOOKUP),
                SqlSource.INSTANCE);

        assertThat(sql.sql())
                .contains("sys_users")
                .contains("u.full_name::text = ?")
                .contains("u.id::text")
                .doesNotContain("rt_table_data_rows");
        assertThat(sql.params()).containsExactly("张三");
    }

    @Test
    void notContainsIsNotExistsOnTheMatchingLookupRow() {
        SqlFragment sql = MainTableViewDerivedFilterSql.whereClause(
                List.of(new ListColumnFilter("customer_label", "notContains", "Acme", null)),
                List.of(LOOKUP),
                SqlSource.INSTANCE);

        assertThat(sql.sql()).contains("AND NOT EXISTS");
        assertThat(sql.sql()).contains("rt.data->>'name' ILIKE ?");
    }

    @Test
    void fkContainsComparesTheResolvedMainLabelNotTheFkScalar() {
        SqlFragment sql = MainTableViewDerivedFilterSql.whereClause(
                List.of(new ListColumnFilter("case_title", "contains", "Yes", null)),
                List.of(FK),
                SqlSource.EXPANDED_SUB_ROW);

        assertThat(sql.sql())
                .contains("pi.sub_elem->>'case_id'")
                .contains("(pi.variables)::jsonb->>'legal_hold'")
                .contains("(pi.variables)::jsonb->>'case_number'")
                .contains("ILIKE ?")
                .doesNotContain("pi.sub_elem->>'case_id' ILIKE");
        assertThat(sql.params()).containsExactly("%Yes%");
    }

    @Test
    void isNullLooksAtTheStoredKeyNotTheLookupTable() {
        SqlFragment sql = MainTableViewDerivedFilterSql.whereClause(
                List.of(new ListColumnFilter("customer_label", "isNull", null, null)),
                List.of(LOOKUP),
                SqlSource.INSTANCE);

        assertThat(sql.sql()).contains("customer_id").doesNotContain("rt_table_data_rows");
        assertThat(sql.params()).isEmpty();
    }

    @Test
    void unknownOperatorIsRejected() {
        assertThatThrownBy(() -> MainTableViewDerivedFilterSql.whereClause(
                List.of(new ListColumnFilter("customer_label", "gt", "1", null)),
                List.of(LOOKUP),
                SqlSource.INSTANCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void craftedDisplayFieldCannotReachSql() {
        FieldSource crafted = new FieldSource(
                "customer_label", "Customer", false, "lookup_display", "VARCHAR",
                "customer_id", "name'; DROP TABLE rt_table_data_rows --", 42L, List.of());
        assertThatThrownBy(() -> MainTableViewDerivedFilterSql.whereClause(
                List.of(new ListColumnFilter("customer_label", "contains", "x", null)),
                List.of(crafted),
                SqlSource.INSTANCE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
