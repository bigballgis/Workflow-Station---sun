package com.admin.list;

import com.platform.common.list.ListColumnFilter;
import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AutomationFlowRunColumnSpecTest {

    @Test
    void columnKinds() {
        assertThat(column("flowDisplayName").kind()).isEqualTo(Kind.TEXT);
        assertThat(column("status").kind()).isEqualTo(Kind.ENUM);
        assertThat(column("startTime").kind()).isEqualTo(Kind.DATETIME);
        assertThat(column("durationMs").kind()).isEqualTo(Kind.NUMBER);
    }

    /** 状态是封闭值集：选项必须逐值覆盖 AP FlowRunStatus，否则筛选会漏掉整类失败 */
    @Test
    void statusOptionsCoverEveryApRunStatus() {
        List<String> values = column("status").options().stream()
                .map(ListColumnMeta.Option::value)
                .toList();
        assertThat(values).containsExactlyInAnyOrder(
                "SUCCEEDED", "RUNNING", "QUEUED", "PAUSED", "FAILED", "TIMEOUT", "CANCELED",
                "INTERNAL_ERROR", "QUOTA_EXCEEDED", "MEMORY_LIMIT_EXCEEDED", "LOG_SIZE_EXCEEDED");
    }

    /**
     * 耗时是 NUMBER 列：{@code ListFilterSql} 先用正则守卫再 cast，故列表达式必须产出 text。
     * 若这里退回 bigint，PostgreSQL 会在 {@code ~} 运算符上直接报错（整页 500）。
     */
    @Test
    void durationFilterStaysTextSoTheNumericGuardApplies() {
        List<Object> params = new ArrayList<>();
        String where = AutomationFlowRunColumnSpec.sql().whereClause(
                List.of(new ListColumnFilter("durationMs", "gt", "1000", null)), params);
        assertThat(AutomationFlowRunColumnSpec.DURATION_SQL).endsWith("::text END");
        assertThat(where).contains("~ '^-?[0-9]+(\\.[0-9]+)?$'");
        assertThat(params).hasSize(1);
    }

    /** 失败步骤取 failedStep jsonb 的 displayName（AP 的 FailedStep 契约） */
    @Test
    void failedStepFiltersOnTheJsonbDisplayName() {
        List<Object> params = new ArrayList<>();
        String where = AutomationFlowRunColumnSpec.sql().whereClause(
                List.of(new ListColumnFilter("failedStepName", "contains", "http", null)), params);
        assertThat(where).contains("r.\"failedStep\"->>'displayName'");
    }

    /** 排队中的运行没有 startTime——默认排序必须走 created，否则最新的 QUEUED 沉到最底 */
    @Test
    void defaultOrderIsCreatedNotStartTime() {
        assertThat(AutomationFlowRunColumnSpec.sql().orderBy(null, null))
                .isEqualTo(" ORDER BY r.created DESC, r.id");
    }

    private static ListColumnMeta column(String field) {
        return AutomationFlowRunColumnSpec.columns().stream()
                .filter(c -> field.equals(c.field()))
                .findFirst()
                .orElseThrow();
    }
}
