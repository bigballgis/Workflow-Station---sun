package com.portal.util;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TodoTaskColumnSpecTest {

    @Test
    void requestIdIsTextSearchableAndSortable() {
        ListColumnMeta requestId = column("requestId");
        assertThat(requestId.kind()).isEqualTo(Kind.TEXT);
        assertThat(requestId.filterable()).isTrue();
        assertThat(requestId.sortable()).isTrue();
        assertThat(requestId.operators()).contains("contains", "startsWith", "eq");
    }

    @Test
    void taskNameRemainsOrdinaryText() {
        ListColumnMeta taskName = column("taskName");
        assertThat(taskName.filterable()).isTrue();
        assertThat(taskName.sortable()).isTrue();
        assertThat(taskName.operators()).contains("contains");
    }

    private static ListColumnMeta column(String field) {
        return TodoTaskColumnSpec.columns().stream()
                .filter(c -> field.equals(c.field()))
                .findFirst()
                .orElseThrow();
    }
}
