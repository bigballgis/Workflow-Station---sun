package com.portal.util;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class AuditApplicationColumnSpecTest {

    @Test
    void initiatorIsAUserColumnBetweenTitleAndAssignee() {
        assertThat(AuditApplicationColumnSpec.columns())
                .extracting(ListColumnMeta::field)
                .containsExactly(
                        "requestId", "businessKey", "startUserName",
                        "currentAssignee", "startTime", "status");
        assertThat(column("startUserName").kind()).isEqualTo(Kind.USER);
        assertThat(column("startUserName").filterable()).isTrue();
        assertThat(column("startUserName").sortable()).isTrue();
    }

    @Test
    void searchableFieldsAreTheVisibleColumns() {
        assertThat(AuditApplicationColumnSpec.searchableFields())
                .containsExactly(
                        "requestId", "businessKey", "startUserName",
                        "currentAssignee", "startTime", "status");
    }

    @Test
    void keywordSearchIsPlainTextOfPaintedCells() {
        List<Object> params = new ArrayList<>();
        String where = AuditApplicationColumnSpec.textSearchClause("请假", params);
        assertThat(where).contains("pi.variables->>'__request_id' ILIKE ?");
        assertThat(where).contains("COALESCE(NULLIF(BTRIM(pi.business_key), ''), pi.process_definition_name) ILIKE ?");
        assertThat(where).contains("COALESCE(pi.start_user_name, pi.start_user_id) ILIKE ?");
        assertThat(where).contains(ProcessAssigneeStoredSql.EXPRESSION + " ILIKE ?");
        assertThat(where).contains("FROM sys_users u");
        assertThat(where).contains("to_char(pi.start_time, 'YYYY-MM-DD HH24:MI') ILIKE ?");
        assertThat(where).contains("pi.status ILIKE ?");
        assertThat(where).doesNotContain("pi.current_node");
        assertThat(where).doesNotContain("pi.function_unit_code ILIKE");
        assertThat(where).doesNotContain("pi.start_time::text");
        assertThat(params).hasSize(7).allMatch("%请假%"::equals);
    }

    @Test
    void blankKeywordAddsNoPredicate() {
        List<Object> params = new ArrayList<>();
        assertThat(AuditApplicationColumnSpec.textSearchClause("  ", params)).isEmpty();
        assertThat(params).isEmpty();
    }

    @Test
    void currentAssigneeContainsLooksAtTheClaimedUserAndTheCandidatePool() {
        List<Object> params = new ArrayList<>();
        String where = AuditApplicationColumnSpec.sql().whereClause(
                List.of(new com.platform.common.list.ListColumnFilter(
                        "currentAssignee", "contains", "id-a", null)),
                params);
        assertThat(where).contains("pi.current_assignee");
        assertThat(where).contains("pi.candidate_users");
        assertThat(where).contains("concat_ws");
        assertThat(where).contains("regexp_split_to_array");
        assertThat(params).containsExactly("id-a");
    }

    @Test
    void initiatorFilterLooksAtDisplayNameAndId() {
        List<Object> params = new ArrayList<>();
        String where = AuditApplicationColumnSpec.sql().whereClause(
                List.of(new com.platform.common.list.ListColumnFilter(
                        "startUserName", "eq", "u1", null)),
                params);
        assertThat(where).contains("COALESCE(pi.start_user_name, pi.start_user_id)");
        assertThat(params).containsExactly("u1");
    }

    private static ListColumnMeta column(String field) {
        return AuditApplicationColumnSpec.columns().stream()
                .filter(c -> field.equals(c.field()))
                .findFirst()
                .orElseThrow();
    }
}
