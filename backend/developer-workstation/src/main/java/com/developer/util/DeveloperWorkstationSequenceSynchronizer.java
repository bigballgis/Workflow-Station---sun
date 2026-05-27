package com.developer.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Aligns dw_* BIGSERIAL sequences with MAX(id) in each table.
 * If init scripts or imports insert large IDs without advancing the sequence, clone/import may hit PK conflicts.
 *
 * <p>Runs outside the caller transaction (NOT_SUPPORTED) so missing tables do not poison the caller's PostgreSQL transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeveloperWorkstationSequenceSynchronizer {

    private static final List<String> DW_TABLES_WITH_ID = List.of(
            "dw_icons",
            "dw_function_units",
            "dw_process_definitions",
            "dw_table_definitions",
            "dw_field_definitions",
            "dw_foreign_keys",
            "dw_form_definitions",
            "dw_form_table_bindings",
            "dw_form_stage_bindings",
            "dw_action_definitions",
            "dw_decision_definitions",
            "dw_table_relations",
            "dw_versions",
            "dw_sub_table_view_configs",
            "dw_sub_table_view_fields",
            "dw_link_form_components"
    );

    private final JdbcTemplate jdbcTemplate;

    /**
     * Syncs id sequences for all known dw_* tables (skips if table missing or no serial sequence).
     * Runs on a separate connection; suitable for import/clone before uncommitted writes in the current transaction.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void synchronizeAll() {
        for (String table : DW_TABLES_WITH_ID) {
            synchronizeTable(table);
        }
    }

    /**
     * Aligns sequences within the same JDBC transaction as the caller, so flushed-but-uncommitted rows are visible.
     * Rollback flows that delete/reinsert before restore must use this; otherwise NOT_SUPPORTED would reset the sequence to stale MAX.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void synchronizeAllInTransaction() {
        for (String table : DW_TABLES_WITH_ID) {
            synchronizeTable(table);
        }
    }

    /** Aligns dw_field_definitions sequence with MAX(id) (call before table save delete+reinsert). */
    public void synchronizeFieldDefinitions() {
        synchronizeTable("dw_field_definitions");
    }

    /** Aligns dw_versions sequence with MAX(id). */
    public void synchronizeVersions() {
        synchronizeTable("dw_versions");
    }

    void synchronizeTable(String tableName) {
        try {
            Boolean exists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM information_schema.tables "
                            + "WHERE table_schema = 'public' AND table_name = ?)",
                    Boolean.class,
                    tableName);
            if (!Boolean.TRUE.equals(exists)) {
                log.debug("Table {} does not exist, skipping sequence sync", tableName);
                return;
            }

            String sequenceName = jdbcTemplate.queryForObject(
                    "SELECT pg_get_serial_sequence(?, 'id')",
                    String.class,
                    tableName);
            if (sequenceName == null || sequenceName.isBlank()) {
                log.debug("No serial sequence for table {}", tableName);
                return;
            }

            Long maxId = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(MAX(id), 0) FROM " + tableName,
                    Long.class);
            long nextVal = maxId != null ? maxId : 0L;
            jdbcTemplate.queryForObject(
                    "SELECT setval(CAST(? AS regclass), GREATEST(?, 1))",
                    Long.class,
                    sequenceName,
                    nextVal);
            log.debug("Synchronized sequence {} for {} to {}", sequenceName, tableName, nextVal);
        } catch (Exception e) {
            log.debug("Skipped sequence sync for {}: {}", tableName, e.getMessage());
        }
    }
}
