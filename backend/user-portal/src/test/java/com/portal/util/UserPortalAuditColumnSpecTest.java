package com.portal.util;

import com.platform.common.list.ListColumnFilter;
import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserPortalAuditColumnSpecTest {

    @Test
    void onlyClosedValueColumnsGroup() {
        assertThat(UserPortalAuditColumnSpec.columns())
                .filteredOn(ListColumnMeta::groupable)
                .extracting(ListColumnMeta::field)
                .containsExactly("changeType", "userName");
        assertThat(column("functionUnitCode").kind()).isEqualTo(Kind.TEXT);
        assertThat(column("timestamp").kind()).isEqualTo(Kind.DATETIME);
        assertThat(column("userName").kind()).isEqualTo(Kind.USER);
    }

    @Test
    void userNameCompilesToTheStoredUserId() {
        List<Object> params = new ArrayList<>();
        String where = UserPortalAuditColumnSpec.sql().whereClause(
                List.of(new ListColumnFilter("userName", "eq", "user-1", null)), params);
        assertThat(where).contains("ch.user_id");
        assertThat(params).contains("user-1");
    }

    @Test
    void groupingATextColumnIsRefused() {
        assertThatThrownBy(() -> UserPortalAuditColumnSpec.sql().groupByExpression("functionUnitCode"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ListColumnMeta column(String field) {
        return UserPortalAuditColumnSpec.columns().stream()
                .filter(c -> field.equals(c.field()))
                .findFirst()
                .orElseThrow();
    }
}
