package com.admin.list;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleColumnSpecTest {

    @Test
    void onlyClosedValueColumnsGroup() {
        assertThat(RoleColumnSpec.columns())
                .filteredOn(ListColumnMeta::groupable)
                .extracting(ListColumnMeta::field)
                .containsExactly("type", "status", "isSystem");
        assertThat(column("name").kind()).isEqualTo(Kind.TEXT);
        assertThat(column("isSystem").kind()).isEqualTo(Kind.BOOLEAN);
    }

    @Test
    void groupingATextColumnIsRefused() {
        assertThatThrownBy(() -> RoleColumnSpec.sql().groupByExpression("name"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void systemTabCodesExcludeFuViewer() {
        assertThat(RoleColumnSpec.SYSTEM_ROLE_LIST_CODES).doesNotContain("FU_VIEWER");
        assertThat(RoleColumnSpec.SYSTEM_ROLE_LIST_CODES).contains("SYS_ADMIN", "MANAGER");
    }

    private static ListColumnMeta column(String field) {
        return RoleColumnSpec.columns().stream()
                .filter(c -> field.equals(c.field()))
                .findFirst()
                .orElseThrow();
    }
}
