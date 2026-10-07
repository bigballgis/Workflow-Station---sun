package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.computedfield.ComputedFieldDates;
import com.platform.common.functionunit.ProcessStartForm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Derives a case's SLA due date: start date + the Function Unit's lead time (calendar days).
 *
 * <p>Which fields hold the dates is design-time config on the PRIMARY main table
 * ({@code dw_table_definitions.sla_config}); the lead time is environment data maintained in Admin
 * Center ({@code ac_sla_policies}). The due date is server-owned like the Request ID: every write
 * path overwrites whatever the client sent, so a stale value never outlives the lead time it came from.
 *
 * <p>The mapping is cached per Function Unit (it only changes on redeploy); the lead time is read on
 * every call so a change takes effect immediately.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlaDueDateEnricher {

    private static final long SPEC_TTL_MS = 5 * 60 * 1000L;
    private static final int MAX_CACHED_SPECS = 128;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedSpec> specCache = Collections.synchronizedMap(
            new LinkedHashMap<>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedSpec> eldest) {
                    return size() > MAX_CACHED_SPECS;
                }
            });

    /** Parsed {@code sla_config}; {@code startDateField == null} means "submission time". */
    record SlaSpec(String startDateField, String dueDateField) {
        static final SlaSpec NONE = new SlaSpec(null, null);

        boolean isConfigured() {
            return dueDateField != null && !dueDateField.isBlank();
        }
    }

    private record CachedSpec(SlaSpec spec, long cachedAt) {
    }

    /** What {@link #compute} decided for one case. */
    public record Result(Kind kind, String dueDateField, String oldDueDate, String newDueDate, String reason) {
        public enum Kind { NOT_CONFIGURED, NO_LEAD_TIME, START_MISSING, COMPUTED }
    }

    /**
     * Overwrites the due date field for a write path. Call after computed-field recalculation, next to
     * {@code stampRequestId}.
     *
     * <p>Only a computed date is written. Without a lead time or a start date the field is left as it
     * is: the mapped due date field may be an existing business field that already holds data, and
     * removing it with nothing to replace it would destroy that data. The recalculation job skips such
     * cases the same way.
     *
     * @param submittedDate the case's submission date (today for a new case); used when the mapping
     *                      takes the start date from the submission time
     */
    public void stamp(String functionUnitCode, Map<String, Object> variables, LocalDate submittedDate) {
        if (variables == null) {
            return;
        }
        Result result = compute(functionUnitCode, variables, submittedDate);
        if (result.kind() == Result.Kind.COMPUTED) {
            variables.put(result.dueDateField(), result.newDueDate());
        }
    }

    /** Computes the due date without touching {@code variables}, reading the current lead time. */
    public Result compute(String functionUnitCode, Map<String, Object> variables, LocalDate submittedDate) {
        SlaSpec spec = resolveSpec(functionUnitCode);
        if (!spec.isConfigured()) {
            return new Result(Result.Kind.NOT_CONFIGURED, null, null, null, null);
        }
        return compute(spec, functionUnitCode, variables, submittedDate, loadLeadTimeDays(functionUnitCode));
    }

    /**
     * Same as {@link #compute(String, Map, LocalDate)} with a lead time the caller already read, so a
     * recalculation job does not re-query it for every case.
     */
    public Result compute(String functionUnitCode, Map<String, Object> variables, LocalDate submittedDate,
                          Integer leadTimeDays) {
        SlaSpec spec = resolveSpec(functionUnitCode);
        if (!spec.isConfigured()) {
            return new Result(Result.Kind.NOT_CONFIGURED, null, null, null, null);
        }
        return compute(spec, functionUnitCode, variables, submittedDate, leadTimeDays);
    }

    private Result compute(SlaSpec spec, String functionUnitCode, Map<String, Object> variables,
                           LocalDate submittedDate, Integer leadTimeDays) {
        String oldDue = variables.get(spec.dueDateField()) == null
                ? null : String.valueOf(variables.get(spec.dueDateField()));
        if (leadTimeDays == null) {
            return new Result(Result.Kind.NO_LEAD_TIME, spec.dueDateField(), oldDue, null,
                    "No SLA lead time is set for " + functionUnitCode);
        }
        Long startEpochDay = spec.startDateField() == null
                ? (submittedDate == null ? null : submittedDate.toEpochDay())
                : parseStart(variables.get(spec.startDateField()));
        if (startEpochDay == null) {
            Object raw = spec.startDateField() == null ? null : variables.get(spec.startDateField());
            return new Result(Result.Kind.START_MISSING, spec.dueDateField(), oldDue, null,
                    raw == null
                            ? "Start date " + Objects.requireNonNullElse(spec.startDateField(), "(submission time)") + " is empty"
                            : "Start date " + spec.startDateField() + " is not a date: " + raw);
        }
        String newDue = LocalDate.ofEpochDay(startEpochDay + leadTimeDays).toString();
        return new Result(Result.Kind.COMPUTED, spec.dueDateField(), oldDue, newDue, null);
    }

    private static Long parseStart(Object raw) {
        return raw == null ? null : ComputedFieldDates.parseEpochDay(String.valueOf(raw));
    }

    private Integer loadLeadTimeDays(String functionUnitCode) {
        List<Integer> rows = jdbcTemplate.query(
                "SELECT lead_time_days FROM ac_sla_policies WHERE function_unit_code = ?",
                (rs, i) -> rs.getInt(1), functionUnitCode);
        return rows.isEmpty() ? null : rows.get(0);
    }

    SlaSpec resolveSpec(String functionUnitCode) {
        if (functionUnitCode == null || functionUnitCode.isBlank()) {
            return SlaSpec.NONE;
        }
        long now = System.currentTimeMillis();
        CachedSpec cached = specCache.get(functionUnitCode);
        if (cached != null && now - cached.cachedAt() < SPEC_TTL_MS) {
            return cached.spec();
        }
        SlaSpec spec;
        try {
            spec = loadSpec(functionUnitCode);
        } catch (BadSqlGrammarException e) {
            // Schema not migrated yet (89-dw-table-sla-config.sql): every Function Unit is unmapped,
            // and blocking all case writes over an optional feature would be worse. Not cached, so the
            // next call sees the column as soon as the migration runs.
            log.error("SLA due date disabled: dw_table_definitions.sla_config is not readable ({})", e.getMessage());
            return SlaSpec.NONE;
        }
        specCache.put(functionUnitCode, new CachedSpec(spec, now));
        return spec;
    }

    /** Same PRIMARY-binding resolution as {@link RequestIdEnricher} and Admin's SlaPolicyComponent. */
    private SlaSpec loadSpec(String functionUnitCode) {
        List<String> rows = jdbcTemplate.query(
                """
                        SELECT td.sla_config::text AS cfg
                        FROM dw_function_units fu
                        INNER JOIN dw_form_definitions fd
                            ON fd.function_unit_id = fu.id AND %s
                        INNER JOIN dw_form_table_bindings ftb
                            ON ftb.form_id = fd.id AND ftb.binding_type = 'PRIMARY'
                        INNER JOIN dw_table_definitions td
                            ON td.id = ftb.table_id
                        WHERE fu.code = ?
                        ORDER BY ftb.sort_order NULLS LAST, ftb.id
                        LIMIT 1
                        """.formatted(ProcessStartForm.SQL_PREDICATE),
                (rs, rowNum) -> rs.getString("cfg"),
                functionUnitCode);
        if (rows.isEmpty() || rows.get(0) == null || rows.get(0).isBlank()) {
            return SlaSpec.NONE;
        }
        try {
            Map<?, ?> cfg = objectMapper.readValue(rows.get(0), Map.class);
            String due = cfg.get("dueDateField") instanceof String s && !s.isBlank() ? s : null;
            String start = "FIELD".equals(cfg.get("startDateSource")) && cfg.get("startDateField") instanceof String s
                    && !s.isBlank() ? s : null;
            if (due == null || ("FIELD".equals(cfg.get("startDateSource")) && start == null)) {
                throw new IllegalStateException("incomplete sla_config " + rows.get(0));
            }
            return new SlaSpec(start, due);
        } catch (Exception e) {
            // Saved configs are validated by the designer; a broken one must not silently drop due dates.
            throw new IllegalStateException("Invalid SLA config for function unit " + functionUnitCode, e);
        }
    }
}
