package com.admin.list;

import com.platform.common.list.ListColumnFilter;
import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BiDashboardColumnSpecTest {

    @Test
    void onlyClosedValueColumnsGroup() {
        assertThat(BiDashboardColumnSpec.columns())
                .filteredOn(ListColumnMeta::groupable)
                .extracting(ListColumnMeta::field)
                .containsExactly("isDefaultLanding", "status");
        assertThat(column("dashboardTitle").groupable()).isFalse();
        assertThat(column("lastSyncedAt").kind()).isEqualTo(Kind.DATETIME);
        assertThat(column("isDefaultLanding").kind()).isEqualTo(Kind.BOOLEAN);
    }

    @Test
    void groupingATextColumnIsRefused() {
        assertThatThrownBy(() -> BiDashboardColumnSpec.sql().groupByExpression("dashboardTitle"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void titleFilterCompilesAgainstThePhysicalTable() {
        List<Object> params = new ArrayList<>();
        String where = BiDashboardColumnSpec.sql().whereClause(
                List.of(new ListColumnFilter("dashboardTitle", "contains", "sales", null)), params);
        assertThat(where).contains("d.dashboard_title ILIKE ?");
        assertThat(params).containsExactly("%sales%");
    }

    private static ListColumnMeta column(String field) {
        return BiDashboardColumnSpec.columns().stream()
                .filter(c -> field.equals(c.field()))
                .findFirst()
                .orElseThrow();
    }
}
