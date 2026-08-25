package com.portal.util;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PermissionRequestColumnSpecTest {

    @Test
    void closedValueColumnsAreGroupable() {
        assertThat(PermissionRequestColumnSpec.columns())
                .filteredOn(ListColumnMeta::groupable)
                .extracting(ListColumnMeta::field)
                .containsExactlyInAnyOrder("requestType", "status", "applicantId", "submittedByUserId");
    }

    @Test
    void targetNameIsFilterableText() {
        assertThat(column("targetName").kind()).isEqualTo(Kind.TEXT);
        assertThat(column("targetName").filterable()).isTrue();
        assertThat(column("createdAt").kind()).isEqualTo(Kind.DATETIME);
        assertThat(column("requestType").kind()).isEqualTo(Kind.ENUM);
    }

    private static ListColumnMeta column(String field) {
        return PermissionRequestColumnSpec.columns().stream()
                .filter(c -> field.equals(c.field()))
                .findFirst()
                .orElseThrow();
    }
}
