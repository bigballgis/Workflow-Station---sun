package com.admin.list;

import com.platform.common.list.ListColumnFilter;
import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutomationFlowColumnSpecTest {

    @Test
    void displayNameAndUpdatedKinds() {
        assertThat(column("displayName").kind()).isEqualTo(Kind.TEXT);
        assertThat(column("updated").kind()).isEqualTo(Kind.DATETIME);
    }

    @Test
    void readinessFilterUsesTheSameCaseAsTheCatalogPage() {
        List<Object> params = new ArrayList<>();
        String where = AutomationFlowColumnSpec.sql().whereClause(
                List.of(new ListColumnFilter("readiness", "eq", "DRAFT", null)), params);
        assertThat(where).contains("WHEN f.\"publishedVersionId\" IS NULL THEN 'DRAFT'");
        assertThat(where).contains("WHEN f.status = 'ENABLED' THEN 'ENABLED'");
        assertThat(params).containsExactly("DRAFT");
    }

    private static ListColumnMeta column(String field) {
        return AutomationFlowColumnSpec.columns().stream()
                .filter(c -> field.equals(c.field()))
                .findFirst()
                .orElseThrow();
    }
}
