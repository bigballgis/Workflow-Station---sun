package com.platform.common.fk;

import com.platform.common.dto.PkGenerationConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * DB-backed PK allocation using {@code dw_pk_sequences} / {@code rt_pk_sequences}.
 */
@Service
@RequiredArgsConstructor
public class JdbcPrimaryKeyAllocationService implements PrimaryKeyAllocationService {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public List<String> allocate(Long tableId, String fieldName, PkGenerationConfig config, int count, String scopeKey) {
        // No explicit counter table: fall back to the heuristic (DW table presence -> dw_pk_sequences).
        return allocate(tableId, fieldName, config, count, scopeKey, null);
    }

    @Override
    @Transactional
    public List<String> allocate(Long tableId, String fieldName, PkGenerationConfig config, int count,
                                 String scopeKey, String sequenceTable) {
        if (tableId == null || fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("tableId and fieldName are required");
        }
        int n = count <= 0 ? 1 : count;
        String strategy = config != null && config.getStrategy() != null
                ? config.getStrategy() : "manual";
        String table = resolveSequenceTable(tableId, sequenceTable);
        return switch (strategy) {
            case "uuid" -> allocateUuid(n);
            case "autoIncrement", "prefixedSequence" -> allocateSequence(table, tableId, fieldName, config, n, scopeKey);
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
            String sequenceTable, Long tableId, String fieldName, PkGenerationConfig config, int count, String scopeKey) {
        // Per-table counters only: scope_key is always empty so numbering is continuous per table+field.
        String scopeType = "perTable";
        String scope = "";
        String prefix = config != null && config.getPrefix() != null ? config.getPrefix() : "";
        int padWidth = config != null && config.getPadWidth() != null && config.getPadWidth() > 0
                ? config.getPadWidth() : 6;
        long startValue = config != null && config.getStartValue() != null ? config.getStartValue() : 1L;

        ensureSequenceRow(sequenceTable, tableId, fieldName, scopeType, scope, prefix, padWidth, startValue);

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
                scope);

        if (updated == null) {
            throw new IllegalStateException("Failed to allocate PK sequence");
        }
        long first = updated - count + 1;
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

    private void ensureSequenceRow(
            String sequenceTable,
            Long tableId,
            String fieldName,
            String scopeType,
            String scopeKey,
            String prefix,
            int padWidth,
            long startValue) {
        jdbcTemplate.update(
                """
                INSERT INTO %s (table_id, field_name, scope_type, scope_key, prefix, pad_width, current_value)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (table_id, field_name, scope_type, scope_key) DO NOTHING
                """.formatted(sequenceTable),
                tableId,
                fieldName,
                scopeType,
                scopeKey,
                prefix,
                padWidth,
                startValue - 1);
    }

    /**
     * Resolve the counter table. When the caller passes an explicit table (recommended), use it
     * verbatim — this avoids cross-domain collisions when a DW table id and an AC relation table id
     * happen to be equal. Only the legacy heuristic (DW table presence) is used when no override is
     * given. The override is whitelisted to the two known counter tables to keep it injection-safe.
     */
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
