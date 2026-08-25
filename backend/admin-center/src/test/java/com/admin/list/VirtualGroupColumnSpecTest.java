package com.admin.list;

import com.platform.common.list.ListColumnFilter;
import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VirtualGroupColumnSpecTest {

    @Test
    void onlyClosedValueColumnsGroup() {
        assertThat(VirtualGroupColumnSpec.columns())
                .filteredOn(ListColumnMeta::groupable)
                .extracting(ListColumnMeta::field)
                .containsExactly("type", "boundRoleType", "status");
        assertThat(column("name").kind()).isEqualTo(Kind.TEXT);
        assertThat(column("memberCount").kind()).isEqualTo(Kind.NUMBER);
    }

    @Test
    void groupingATextColumnIsRefused() {
        assertThatThrownBy(() -> VirtualGroupColumnSpec.sql().groupByExpression("name"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void boundRoleNameFilterJoinsTheRoleTable() {
        List<Object> params = new ArrayList<>();
        String where = VirtualGroupColumnSpec.sql().whereClause(
                List.of(new ListColumnFilter("boundRoleName", "contains", "admin", null)), params);
        assertThat(where).contains("r.name ILIKE ?");
        assertThat(params).containsExactly("%admin%");
    }

    private static ListColumnMeta column(String field) {
        return VirtualGroupColumnSpec.columns().stream()
                .filter(c -> field.equals(c.field()))
                .findFirst()
                .orElseThrow();
    }
}
