package com.admin.list;

import com.platform.common.list.ListColumnFilter;
import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BiRbacColumnSpecTest {

    @Test
    void onlyClosedValueColumnsGroup() {
        assertThat(BiRbacColumnSpec.columns())
                .filteredOn(ListColumnMeta::groupable)
                .extracting(ListColumnMeta::field)
                .containsExactly("sysRoleType");
        assertThat(column("sysRoleName").groupable()).isFalse();
        assertThat(column("supersetRoles").kind()).isEqualTo(Kind.TEXT);
        assertThat(column("lastUpdatedAt").kind()).isEqualTo(Kind.DATETIME);
    }

    @Test
    void groupingATextColumnIsRefused() {
        assertThatThrownBy(() -> BiRbacColumnSpec.sql().groupByExpression("sysRoleName"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void supersetRoleFilterAggregatesMappedNames() {
        List<Object> params = new ArrayList<>();
        String where = BiRbacColumnSpec.sql().whereClause(
                List.of(new ListColumnFilter("supersetRoles", "contains", "gamma", null)), params);
        assertThat(where).contains("string_agg");
        assertThat(where).contains("bi_superset_role");
        assertThat(params).containsExactly("%gamma%");
    }

    private static ListColumnMeta column(String field) {
        return BiRbacColumnSpec.columns().stream()
                .filter(c -> field.equals(c.field()))
                .findFirst()
                .orElseThrow();
    }
}
