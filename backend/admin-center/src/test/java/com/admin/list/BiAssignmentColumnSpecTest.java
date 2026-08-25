package com.admin.list;

import com.platform.common.list.ListColumnFilter;
import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BiAssignmentColumnSpecTest {

    @Test
    void onlyClosedValueColumnsGroup() {
        assertThat(BiAssignmentColumnSpec.columns())
                .filteredOn(ListColumnMeta::groupable)
                .extracting(ListColumnMeta::field)
                .containsExactly("targetType", "layoutMode", "isDefault");
        assertThat(column("dashboardTitle").groupable()).isFalse();
        assertThat(column("targetName").kind()).isEqualTo(Kind.TEXT);
        assertThat(column("displayOrder").kind()).isEqualTo(Kind.NUMBER);
    }

    @Test
    void groupingATextColumnIsRefused() {
        assertThatThrownBy(() -> BiAssignmentColumnSpec.sql().groupByExpression("dashboardTitle"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void targetNameFilterResolvesFromTheTargetTables() {
        List<Object> params = new ArrayList<>();
        String where = BiAssignmentColumnSpec.sql().whereClause(
                List.of(new ListColumnFilter("targetName", "contains", "ann", null)), params);
        assertThat(where).contains("sys_users");
        assertThat(where).contains("sys_roles");
        assertThat(where).contains("sys_business_units");
        assertThat(params).containsExactly("%ann%");
    }

    private static ListColumnMeta column(String field) {
        return BiAssignmentColumnSpec.columns().stream()
                .filter(c -> field.equals(c.field()))
                .findFirst()
                .orElseThrow();
    }
}
