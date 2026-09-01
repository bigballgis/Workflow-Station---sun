package com.admin.list;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import com.platform.common.list.ListFilterSql;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Automation Runs columns. Toolbar keyword stays outside this spec.
 * {@code status} is the AP {@code FlowRunStatus} ladder verbatim — the admin page must
 * not collapse states AP distinguishes (TIMEOUT vs FAILED vs INTERNAL_ERROR all mean
 * different things at triage time).
 */
public final class AutomationFlowRunColumnSpec {

    private AutomationFlowRunColumnSpec() {
    }

    /**
     * 未结束的运行没有耗时：SQL 里保持 NULL，前端显示 "—"。
     *
     * <p>结果转 text —— NUMBER 列的过滤/排序在 {@link ListFilterSql} 里先做正则守卫
     * （{@code ref ~ '^-?[0-9]+...'}）再 cast，守卫只能作用在文本表达式上。</p>
     */
    static final String DURATION_SQL = """
            CASE WHEN r."finishTime" IS NULL OR r."startTime" IS NULL THEN NULL \
            ELSE ((EXTRACT(EPOCH FROM (r."finishTime" - r."startTime")) * 1000)::bigint)::text END""";

    static final String FAILED_STEP_SQL = "r.\"failedStep\"->>'displayName'";

    public static List<ListColumnMeta> columns() {
        return List.of(
                ListColumnMeta.of("flowDisplayName", "automationRun.flow", Kind.TEXT),
                ListColumnMeta.withOptions("status", "automationRun.status", Kind.ENUM, statusOptions()),
                ListColumnMeta.of("startTime", "automationRun.started", Kind.DATETIME),
                ListColumnMeta.of("durationMs", "automationRun.duration", Kind.NUMBER),
                ListColumnMeta.of("failedStepName", "automationRun.failedStep", Kind.TEXT),
                ListColumnMeta.of("projectName", "automationRun.project", Kind.TEXT)
        );
    }

    public static ListFilterSql sql() {
        Map<String, ListColumnMeta> byField = new LinkedHashMap<>();
        for (ListColumnMeta column : columns()) {
            byField.put(column.field(), column);
        }
        // 默认按 created 倒序（不是 startTime）：排队中的运行还没有 startTime，
        // 按 startTime 排会把最新的 QUEUED 运行沉到最底；AP 自己的 Runs 页同样按 created。
        return new ListFilterSql(byField, AutomationFlowRunColumnSpec::sqlFor, "r.id",
                "r.created DESC");
    }

    static String sqlFor(String field) {
        return switch (field) {
            case "flowDisplayName" -> "fv.\"displayName\"";
            case "status" -> "r.status";
            case "startTime" -> "r.\"startTime\"::text";
            case "durationMs" -> DURATION_SQL;
            case "failedStepName" -> FAILED_STEP_SQL;
            case "projectName" -> "p.\"displayName\"";
            default -> throw new IllegalArgumentException("Unknown automation-run column: " + field);
        };
    }

    /** AP FlowRunStatus（automation/packages/core/execution flow-execution.ts）逐值对齐 */
    private static List<ListColumnMeta.Option> statusOptions() {
        return List.of(
                new ListColumnMeta.Option("SUCCEEDED", "automationRun.statusSucceeded"),
                new ListColumnMeta.Option("RUNNING", "automationRun.statusRunning"),
                new ListColumnMeta.Option("QUEUED", "automationRun.statusQueued"),
                new ListColumnMeta.Option("PAUSED", "automationRun.statusPaused"),
                new ListColumnMeta.Option("FAILED", "automationRun.statusFailed"),
                new ListColumnMeta.Option("TIMEOUT", "automationRun.statusTimeout"),
                new ListColumnMeta.Option("CANCELED", "automationRun.statusCanceled"),
                new ListColumnMeta.Option("INTERNAL_ERROR", "automationRun.statusInternalError"),
                new ListColumnMeta.Option("QUOTA_EXCEEDED", "automationRun.statusQuotaExceeded"),
                new ListColumnMeta.Option("MEMORY_LIMIT_EXCEEDED", "automationRun.statusMemoryExceeded"),
                new ListColumnMeta.Option("LOG_SIZE_EXCEEDED", "automationRun.statusLogSizeExceeded")
        );
    }
}
