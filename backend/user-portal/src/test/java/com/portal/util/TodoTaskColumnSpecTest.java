package com.portal.util;

import com.portal.dto.PortalListColumnMeta;
import com.portal.dto.PortalListColumnMeta.Kind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TodoTaskColumnSpecTest {

    @Test
    void requestIdIsTextSearchableAndSortableWithoutGroup() {
        PortalListColumnMeta requestId = column("requestId");
        assertThat(requestId.kind()).isEqualTo(Kind.TEXT);
        assertThat(requestId.filterable()).isTrue();
        assertThat(requestId.sortable()).isTrue();
        assertThat(requestId.groupable()).isFalse();
        assertThat(requestId.operators()).contains("contains", "startsWith", "eq");
    }

    @Test
    void taskNameRemainsOrdinaryText() {
        PortalListColumnMeta taskName = column("taskName");
        assertThat(taskName.filterable()).isTrue();
        assertThat(taskName.sortable()).isTrue();
        assertThat(taskName.operators()).contains("contains");
    }

    private static PortalListColumnMeta column(String field) {
        return TodoTaskColumnSpec.columns().stream()
                .filter(c -> field.equals(c.field()))
                .findFirst()
                .orElseThrow();
    }
}
