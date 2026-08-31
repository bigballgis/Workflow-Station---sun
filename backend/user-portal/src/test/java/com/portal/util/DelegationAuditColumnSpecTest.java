package com.portal.util;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DelegationAuditColumnSpecTest {

    @Test
    void operationTypeAndCreatedAtKinds() {
        assertThat(column("operationType").kind()).isEqualTo(Kind.TEXT);
        assertThat(column("createdAt").kind()).isEqualTo(Kind.DATETIME);
    }

    private static ListColumnMeta column(String field) {
        return DelegationAuditColumnSpec.columns().stream()
                .filter(c -> field.equals(c.field()))
                .findFirst()
                .orElseThrow();
    }
}
