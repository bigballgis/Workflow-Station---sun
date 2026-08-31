package com.admin.list;

import com.platform.common.list.ListColumnFilter;
import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminAuditColumnSpecTest {

    @Test
    void resultCompilesToTheStoredSuccessFlag() {
        List<Object> params = new ArrayList<>();
        String where = AdminAuditColumnSpec.sql().whereClause(
                List.of(new ListColumnFilter("result", "eq", "SUCCESS", null)), params);
        assertThat(where).contains("CASE WHEN al.success THEN 'SUCCESS' ELSE 'FAILED' END");
        assertThat(params).containsExactly("SUCCESS");
    }

    @Test
    void actionFilterUsesTheLegacyCanonicalCase() {
        List<Object> params = new ArrayList<>();
        String where = AdminAuditColumnSpec.sql().whereClause(
                List.of(new ListColumnFilter("action", "eq", "QUERY", null)), params);
        assertThat(where).contains("DATA_QUERIED");
        assertThat(where).contains("ELSE al.action END");
        assertThat(params).containsExactly("QUERY");
    }

    private static ListColumnMeta column(String field) {
        return AdminAuditColumnSpec.columns().stream()
                .filter(c -> field.equals(c.field()))
                .findFirst()
                .orElseThrow();
    }
}
