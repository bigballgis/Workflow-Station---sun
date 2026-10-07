package com.admin.list;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import com.platform.common.list.ListFilterSql;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SLA policy list columns: one row per Function Unit whose main table declares an SLA due date
 * mapping. Outer aliases: {@code fu} (dw_function_units), {@code p} (ac_sla_policies, may be null).
 */
public final class SlaPolicyColumnSpec {

    private SlaPolicyColumnSpec() {
    }

    /** ISO-8601 UTC ("...Z") so the browser renders every SLA timestamp in the viewer's own zone. */
    public static String isoUtc(String timestamptzColumn) {
        return "to_char(" + timestamptzColumn + " AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"')";
    }

    static final String LATEST_JOB_STATUS_SQL = """
            (SELECT j.status FROM up_sla_recalc_jobs j \
            WHERE j.function_unit_code = fu.code ORDER BY j.submitted_at DESC LIMIT 1)""";

    public static List<ListColumnMeta> columns() {
        return List.of(
                ListColumnMeta.of("functionUnitCode", "sla.colFunctionUnitCode", Kind.TEXT),
                ListColumnMeta.of("functionUnitName", "sla.colFunctionUnitName", Kind.TEXT),
                ListColumnMeta.of("leadTimeDays", "sla.colLeadTimeDays", Kind.NUMBER),
                ListColumnMeta.of("version", "sla.colVersion", Kind.NUMBER),
                ListColumnMeta.of("updatedBy", "sla.colUpdatedBy", Kind.TEXT),
                ListColumnMeta.of("updatedAt", "sla.colUpdatedAt", Kind.DATETIME),
                ListColumnMeta.withOptions("latestJobStatus", "sla.colLatestJob", Kind.ENUM, jobStatusOptions())
        );
    }

    public static ListFilterSql sql() {
        Map<String, ListColumnMeta> byField = new LinkedHashMap<>();
        for (ListColumnMeta column : columns()) {
            byField.put(column.field(), column);
        }
        return new ListFilterSql(byField, SlaPolicyColumnSpec::sqlFor, "fu.code", "fu.code ASC");
    }

    static String sqlFor(String field) {
        return switch (field) {
            case "functionUnitCode" -> "fu.code";
            case "functionUnitName" -> "fu.name";
            case "leadTimeDays" -> "p.lead_time_days::text";
            case "version" -> "p.version::text";
            case "updatedBy" -> "p.updated_by";
            case "updatedAt" -> isoUtc("p.updated_at");
            case "latestJobStatus" -> LATEST_JOB_STATUS_SQL;
            default -> throw new IllegalArgumentException("Unknown sla-policy column: " + field);
        };
    }

    private static List<ListColumnMeta.Option> jobStatusOptions() {
        return List.of(
                new ListColumnMeta.Option("PENDING", "sla.job.PENDING"),
                new ListColumnMeta.Option("RUNNING", "sla.job.RUNNING"),
                new ListColumnMeta.Option("SUCCEEDED", "sla.job.SUCCEEDED"),
                new ListColumnMeta.Option("PARTIAL", "sla.job.PARTIAL"),
                new ListColumnMeta.Option("FAILED", "sla.job.FAILED"),
                new ListColumnMeta.Option("SUPERSEDED", "sla.job.SUPERSEDED")
        );
    }
}
