package com.admin.list;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FunctionUnitDeploymentColumnSpecTest {

    @Test
    void onlyClosedValueColumnsGroup() {
        assertThat(FunctionUnitDeploymentColumnSpec.columns())
                .filteredOn(ListColumnMeta::groupable)
                .extracting(ListColumnMeta::field)
                .containsExactly("status", "deployedBy");
        assertThat(column("functionUnitName").kind()).isEqualTo(Kind.TEXT);
        assertThat(column("deployedBy").kind()).isEqualTo(Kind.USER);
        assertThat(column("deployedAt").kind()).isEqualTo(Kind.DATETIME);
    }

    @Test
    void groupingATextColumnIsRefused() {
        assertThatThrownBy(() -> FunctionUnitDeploymentColumnSpec.sql().groupByExpression("version"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ListColumnMeta column(String field) {
        return FunctionUnitDeploymentColumnSpec.columns().stream()
                .filter(c -> field.equals(c.field()))
                .findFirst()
                .orElseThrow();
    }
}
