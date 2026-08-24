package com.admin.list;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FunctionUnitColumnSpecTest {

    @Test
    void listOnlyClosedValueColumnsGroup() {
        assertThat(FunctionUnitColumnSpec.columns())
                .filteredOn(ListColumnMeta::groupable)
                .extracting(ListColumnMeta::field)
                .containsExactly("status", "enabled");
        assertThat(column(FunctionUnitColumnSpec.columns(), "name").kind()).isEqualTo(Kind.TEXT);
        assertThat(column(FunctionUnitColumnSpec.columns(), "enabled").kind()).isEqualTo(Kind.BOOLEAN);
    }

    @Test
    void archiveExposesUpdatedByAsUser() {
        assertThat(FunctionUnitColumnSpec.archiveColumns())
                .extracting(ListColumnMeta::field)
                .contains("updatedBy")
                .doesNotContain("enabled");
        assertThat(column(FunctionUnitColumnSpec.archiveColumns(), "updatedBy").kind()).isEqualTo(Kind.USER);
        assertThat(column(FunctionUnitColumnSpec.archiveColumns(), "updatedBy").groupable()).isTrue();
    }

    @Test
    void groupingATextColumnIsRefused() {
        assertThatThrownBy(() -> FunctionUnitColumnSpec.sql().groupByExpression("name"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FunctionUnitColumnSpec.archiveSql().groupByExpression("version"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void latestFromDedupesByCodeBeforePaging() {
        assertThat(FunctionUnitColumnSpec.latestFrom(false))
                .contains("DISTINCT ON (fu.code)")
                .contains("fu.status <> 'ARCHIVED'");
        assertThat(FunctionUnitColumnSpec.latestFrom(true))
                .contains("fu.status = 'ARCHIVED'");
    }

    private static ListColumnMeta column(java.util.List<ListColumnMeta> columns, String field) {
        return columns.stream()
                .filter(c -> field.equals(c.field()))
                .findFirst()
                .orElseThrow();
    }
}
