package com.platform.common.fk;

import com.platform.common.dto.PkGenerationConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * DB-backed PK allocation using {@code dw_pk_sequences} / {@code rt_pk_sequences}.
 *
 * <p>{@code autoIncrement} / {@code prefixedSequence} counters are global per (table_id, field_name).
 * {@code dailyDateSequence} / {@code monthlyDateSequence} use one row per calendar period
 * ({@code perDay} / {@code perMonth}) and must not be merged by {@link #consolidateAndEnsureSequenceRow}.
 */
@Service
public class JdbcPrimaryKeyAllocationService implements PrimaryKeyAllocationService {

    private static final String SCOPE_TYPE_PER_TABLE = "perTable";
    private static final String SCOPE_KEY_GLOBAL = "";

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Autowired
    public JdbcPrimaryKeyAllocationService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, CalendarDateSequence.systemClock());
    }

    JdbcPrimaryKeyAllocationService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<String> allocate(Long tableId, String fieldName, PkGenerationConfig config, int count, String scopeKey) {
        return allocate(tableId, fieldName, config, count, scopeKey, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<String> allocate(Long tableId, String fieldName, PkGenerationConfig config, int count,
                                 String scopeKey, String sequenceTable) {
        if (tableId == null || fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("tableId and fieldName are required");
        }
        int n = count <= 0 ? 1 : count;
        String strategy = config != null && config.getStrategy() != null
                ? config.getStrategy() : "manual";
        String table = resolveSequenceTable(tableId, sequenceTable);
        if (CalendarDateSequence.Period.forStrategy(strategy) != null) {
            return allocateCalendarDateSequence(table, tableId, fieldName, config, n);
        }
        return switch (strategy) {
            case "uuid" -> allocateUuid(n);
            case "autoIncrement", "prefixedSequence" -> allocateSequence(table, tableId, fieldName, config, n);
            default -> throw new IllegalArgumentException("PK strategy does not support allocation: " + strategy);
        };
    }

    private List<String> allocateUuid(int count) {
        List<String> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(UUID.randomUUID().toString());
        }
        return out;
    }

    private List<String> allocateSequence(
            String sequenceTable, Long tableId, String fieldName, PkGenerationConfig config, int count) {
        String prefix = config != null && config.getPrefix() != null ? config.getPrefix() : "";
        int padWidth = config != null && config.getPadWidth() != null && config.getPadWidth() > 0
                ? config.getPadWidth() : 6;
        long startValue = config != null && config.getStartValue() != null ? config.getStartValue() : 1L;

        consolidateAndEnsureSequenceRow(
                sequenceTable, tableId, fieldName, prefix, padWidth, startValue);

        long first = incrementCounter(sequenceTable, tableId, fieldName, SCOPE_TYPE_PER_TABLE, SCOPE_KEY_GLOBAL, count);
        List<String> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long val = first + i;
            if ("prefixedSequence".equalsIgnoreCase(config != null ? config.getStrategy() : "")) {
                out.add(prefix + String.format(Locale.ROOT, "%0" + padWidth + "d", val));
            } else {
                out.add(String.valueOf(val));
            }
        }
        return out;
    }

    private List<String> allocateCalendarDateSequence(
            String sequenceTable, Long tableId, String fieldName, PkGenerationConfig config, int count) {
        CalendarDateSequence.Period period = CalendarDateSequence.Period.forStrategy(
                config != null ? config.getStrategy() : null);
        if (period == null) {
            throw new IllegalArgumentException("PK strategy does not support allocation: "
                    + (config != null ? config.getStrategy() : null));
        }
        int padWidth = CalendarDateSequence.resolvePadWidth(config != null ? config.getPadWidth() : null);
        long startValue = config != null && config.getStartValue() != null ? config.getStartValue() : 1L;
        String periodKey = CalendarDateSequence.periodKey(clock, period);
        ensurePeriodSequenceRow(sequenceTable, tableId, fieldName, period, padWidth, startValue);
        long first = incrementCounter(
                sequenceTable, tableId, fieldName, period.scopeType(), periodKey, count);
        List<String> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(CalendarDateSequence.format(periodKey, first + i, padWidth));
        }
        return out;
    }

    /**
     * Insert this period's counter row if missing. Does not reset {@code current_value} on conflict
     * and does not delete other periods' rows (unlike {@link #consolidateAndEnsureSequenceRow}).
     */
    private void ensurePeriodSequenceRow(
            String sequenceTable, Long tableId, String fieldName, CalendarDateSequence.Period period,
            int padWidth, long startValue) {
        String periodKey = CalendarDateSequence.periodKey(clock, period);
        long floor = startValue - 1;
        jdbcTemplate.update(
                """
                INSERT INTO %s (table_id, field_name, scope_type, scope_key, prefix, pad_width, current_value)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (table_id, field_name, scope_type, scope_key)
                DO UPDATE SET prefix = EXCLUDED.prefix,
                              pad_width = EXCLUDED.pad_width,
                              updated_at = NOW()
                """.formatted(sequenceTable),
                tableId,
                fieldName,
                period.scopeType(),
                periodKey,
                periodKey,
                padWidth,
                floor);
    }

    private long incrementCounter(
            String sequenceTable, Long tableId, String fieldName, String scopeType, String scopeKey, int count) {
        Long updated = jdbcTemplate.queryForObject(
                """
                UPDATE %s
                SET current_value = current_value + ?,
                    updated_at = NOW()
                WHERE table_id = ? AND field_name = ? AND scope_type = ? AND scope_key = ?
                RETURNING current_value
                """.formatted(sequenceTable),
                Long.class,
                count,
                tableId,
                fieldName,
                scopeType,
                scopeKey);
        if (updated == null) {
            throw new IllegalStateException("Failed to allocate PK sequence for table "
                    + tableId + " field " + fieldName);
        }
        return updated - count + 1;
    }

    /**
     * Merge legacy per-scope counters into the canonical perTable row, then upsert that row.
     * Ensures every user/process shares one continuous sequence for the table+field.
     * Must not be used for calendar-period strategies (would collapse per-day / per-month rows).
     */
    private void consolidateAndEnsureSequenceRow(
            String sequenceTable,
            Long tableId,
            String fieldName,
            String prefix,
            int padWidth,
            long startValue) {
        long floor = startValue - 1;
        Long maxLegacy = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(MAX(current_value), ?) FROM %s
                WHERE table_id = ? AND field_name = ?
                """.formatted(sequenceTable),
                Long.class,
                floor,
                tableId,
                fieldName);
        long seed = Math.max(floor, maxLegacy != null ? maxLegacy : floor);

        jdbcTemplate.update(
                """
                INSERT INTO %s (table_id, field_name, scope_type, scope_key, prefix, pad_width, current_value)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (table_id, field_name, scope_type, scope_key)
                DO UPDATE SET current_value = GREATEST(%s.current_value, EXCLUDED.current_value),
                              prefix = EXCLUDED.prefix,
                              pad_width = EXCLUDED.pad_width,
                              updated_at = NOW()
                """.formatted(sequenceTable, sequenceTable),
                tableId,
                fieldName,
                SCOPE_TYPE_PER_TABLE,
                SCOPE_KEY_GLOBAL,
                prefix,
                padWidth,
                seed);

        jdbcTemplate.update(
                """
                DELETE FROM %s
                WHERE table_id = ? AND field_name = ?
                  AND NOT (scope_type = ? AND scope_key = ?)
                """.formatted(sequenceTable),
                tableId,
                fieldName,
                SCOPE_TYPE_PER_TABLE,
                SCOPE_KEY_GLOBAL);
    }

    private String resolveSequenceTable(Long tableId, String override) {
        if ("dw_pk_sequences".equals(override) || "rt_pk_sequences".equals(override)) {
            return override;
        }
        Integer dw = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dw_table_definitions WHERE id = ?",
                Integer.class,
                tableId);
        if (dw != null && dw > 0) {
            return "dw_pk_sequences";
        }
        return "rt_pk_sequences";
    }
}
