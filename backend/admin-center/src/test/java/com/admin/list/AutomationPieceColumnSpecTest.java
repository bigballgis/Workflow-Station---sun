package com.admin.list;

import com.platform.common.list.ListColumnFilter;
import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutomationPieceColumnSpecTest {

    @Test
    void onlyClosedValueColumnsGroup() {
        assertThat(AutomationPieceColumnSpec.columns())
                .filteredOn(ListColumnMeta::groupable)
                .extracting(ListColumnMeta::field)
                .containsExactly("pieceType", "disabled");
        assertThat(column("displayName").groupable()).isFalse();
        assertThat(column("actionCount").kind()).isEqualTo(Kind.NUMBER);
    }

    @Test
    void groupingATextColumnIsRefused() {
        assertThatThrownBy(() -> AutomationPieceColumnSpec.sql().groupByExpression("name"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void displayNameFilterUsesQuotedCamelCaseColumn() {
        List<Object> params = new ArrayList<>();
        String where = AutomationPieceColumnSpec.sql().whereClause(
                List.of(new ListColumnFilter("displayName", "contains", "slack", null)), params);
        assertThat(where).contains("pm.\"displayName\" ILIKE ?");
        assertThat(params).containsExactly("%slack%");
    }

    private static ListColumnMeta column(String field) {
        return AutomationPieceColumnSpec.columns().stream()
                .filter(c -> field.equals(c.field()))
                .findFirst()
                .orElseThrow();
    }
}
