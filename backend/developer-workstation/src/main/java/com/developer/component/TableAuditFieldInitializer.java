package com.developer.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-shot startup initializer that adds standard audit fields
 * ({@code created_at}, {@code created_by}, {@code updated_at}, {@code updated_by})
 * to any existing {@code dw_table_definitions} that are missing them.
 * <p>
 * New tables get audit fields via {@code TableDesignComponentImpl.create()};
 * this component backfills tables that were created before that logic shipped.
 * Each field INSERT is idempotent ({@code WHERE NOT EXISTS}) so repeated runs are safe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TableAuditFieldInitializer {

    private final JdbcTemplate jdbc;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfillMissingAuditFields() {
        try {
            int added = 0;

            // created_at
            added += jdbc.update("""
                INSERT INTO dw_field_definitions
                    (table_id, field_name, data_type, nullable, is_primary_key, display_name, sort_order)
                SELECT t.id, 'created_at', 'TIMESTAMP', true, false, 'Created At',
                       COALESCE((SELECT MAX(f.sort_order) FROM dw_field_definitions f WHERE f.table_id = t.id), -1) + 1
                FROM dw_table_definitions t
                WHERE NOT EXISTS (
                    SELECT 1 FROM dw_field_definitions f
                    WHERE f.table_id = t.id AND lower(f.field_name) = 'created_at'
                )
                """);

            // created_by
            added += jdbc.update("""
                INSERT INTO dw_field_definitions
                    (table_id, field_name, data_type, length, nullable, is_primary_key, display_name, sort_order)
                SELECT t.id, 'created_by', 'VARCHAR', 64, true, false, 'Created By',
                       COALESCE((SELECT MAX(f.sort_order) FROM dw_field_definitions f WHERE f.table_id = t.id), -1) + 1
                FROM dw_table_definitions t
                WHERE NOT EXISTS (
                    SELECT 1 FROM dw_field_definitions f
                    WHERE f.table_id = t.id AND lower(f.field_name) = 'created_by'
                )
                """);

            // updated_at
            added += jdbc.update("""
                INSERT INTO dw_field_definitions
                    (table_id, field_name, data_type, nullable, is_primary_key, display_name, sort_order)
                SELECT t.id, 'updated_at', 'TIMESTAMP', true, false, 'Updated At',
                       COALESCE((SELECT MAX(f.sort_order) FROM dw_field_definitions f WHERE f.table_id = t.id), -1) + 1
                FROM dw_table_definitions t
                WHERE NOT EXISTS (
                    SELECT 1 FROM dw_field_definitions f
                    WHERE f.table_id = t.id AND lower(f.field_name) = 'updated_at'
                )
                """);

            // updated_by
            added += jdbc.update("""
                INSERT INTO dw_field_definitions
                    (table_id, field_name, data_type, length, nullable, is_primary_key, display_name, sort_order)
                SELECT t.id, 'updated_by', 'VARCHAR', 64, true, false, 'Updated By',
                       COALESCE((SELECT MAX(f.sort_order) FROM dw_field_definitions f WHERE f.table_id = t.id), -1) + 1
                FROM dw_table_definitions t
                WHERE NOT EXISTS (
                    SELECT 1 FROM dw_field_definitions f
                    WHERE f.table_id = t.id AND lower(f.field_name) = 'updated_by'
                )
                """);

            if (added > 0) {
                log.info("Backfilled {} missing audit field(s) across existing tables", added);
            }
        } catch (Exception e) {
            // Best-effort; table or schema may not exist in test / fresh environments.
            log.debug("Audit field backfill skipped: {}", e.getMessage());
        }
    }
}
