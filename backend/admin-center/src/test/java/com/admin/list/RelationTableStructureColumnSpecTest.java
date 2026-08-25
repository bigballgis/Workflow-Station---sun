package com.admin.list;

import com.platform.common.list.ListColumnFilter;
import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelationTableStructureColumnSpecTest {

    @Test
    void onlyClosedValueColumnsGroup() {
        assertThat(RelationTableStructureColumnSpec.columns())
                .filteredOn(ListColumnMeta::groupable)
                .extracting(ListColumnMeta::field)
                .containsExactly("status", "enabled", "portalVisible");
        assertThat(column("displayName").kind()).isEqualTo(Kind.TEXT);
        assertThat(column("currentVersion").kind()).isEqualTo(Kind.NUMBER);
    }

    @Test
    void groupingATextColumnIsRefused() {
        assertThatThrownBy(() -> RelationTableStructureColumnSpec.sql().groupByExpression("displayName"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void booleanFilterCastsThePhysicalColumnToText() {
        List<Object> params = new ArrayList<>();
        String where = RelationTableStructureColumnSpec.sql().whereClause(
                List.of(new ListColumnFilter("enabled", "eq", "true", null)), params);
        assertThat(where).contains("t.enabled::text");
        assertThat(params).containsExactly("true");
    }

    private static ListColumnMeta column(String field) {
        return RelationTableStructureColumnSpec.columns().stream()
                .filter(c -> field.equals(c.field()))
                .findFirst()
                .orElseThrow();
    }
}
