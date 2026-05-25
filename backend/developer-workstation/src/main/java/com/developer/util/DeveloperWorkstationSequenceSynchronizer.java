package com.developer.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 将 dw_* BIGSERIAL 序列与表中 MAX(id) 对齐。
 * init 脚本或导入若写入较大 id 而未推进序列，会导致 clone/import 主键冲突。
 *
 * <p>必须在独立连接/事务外执行（NOT_SUPPORTED），避免某表不存在时污染调用方的 PostgreSQL 事务。
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
     * 同步所有已知 dw_* 表的 id 序列（表不存在或无序列时跳过）。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void synchronizeAll() {
        for (String table : DW_TABLES_WITH_ID) {
            synchronizeTable(table);
        }
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
