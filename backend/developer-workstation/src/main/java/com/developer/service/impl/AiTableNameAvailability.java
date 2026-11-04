package com.developer.service.impl;

import com.developer.exception.AiGenerationException;
import com.developer.repository.TableDefinitionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AI 产出的表名在全平台是否还可用。
 *
 * <p>表名全平台唯一（{@code uk_dw_table_name} / {@code ux_dw_table_name_lower} 跨所有 FU，大小写不敏感，Relation Table 的
 * {@code rt_table_definitions} 同在一个命名空间——与设计器 {@code TableDesignComponentImpl#isTableNameAvailable} 同一口径）。
 * 本 FU 自己的表不算占用：AI 写入在写表前先清掉本 FU 的表图谱，重新生成沿用旧名是正常的。</p>
 *
 * <p>只报占用的名字，不说被哪个 FU 占用——那个 FU 可能在别的工作区。</p>
 */
@Component
public class AiTableNameAvailability {

    public static final String TABLE_NAME_TAKEN = "AI_TABLE_NAME_TAKEN";

    private final TableDefinitionRepository tableDefinitionRepository;
    private final JdbcTemplate jdbcTemplate;

    public AiTableNameAvailability(TableDefinitionRepository tableDefinitionRepository, JdbcTemplate jdbcTemplate) {
        this.tableDefinitionRepository = tableDefinitionRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** {@code tableDefinitions} 里已被别的 FU 或 Relation Table 占用的表名，按出现顺序。 */
    public List<String> takenNames(Long functionUnitId, List<Map<String, Object>> tableDefinitions) {
        // null 会让 "function_unit_id <> NULL" 永不成立、占用检查静默放行
        Objects.requireNonNull(functionUnitId, "functionUnitId");
        List<String> taken = new ArrayList<>();
        if (tableDefinitions == null) return taken;
        for (Map<String, Object> table : tableDefinitions) {
            if (table != null && table.get("tableName") instanceof String name && !name.isBlank()
                    && isTaken(functionUnitId, name.trim())) {
                taken.add(name.trim());
            }
        }
        return taken;
    }

    /**
     * 生成期检查：{@code generatedData.tableDefinitions} 有被占用的名字就抛可修违规（{@link #TABLE_NAME_TAKEN}），
     * 让生成链路带着这段话重问模型一次。
     */
    public void requireAvailable(Long functionUnitId, Object generatedData) {
        if (!(generatedData instanceof Map<?, ?> data) || !(data.get("tableDefinitions") instanceof List<?> tables)) {
            return;
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Object t : tables) {
            if (t instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> entry = (Map<String, Object>) m;
                entries.add(entry);
            }
        }
        List<String> taken = takenNames(functionUnitId, entries);
        if (!taken.isEmpty()) {
            throw new AiGenerationException(TABLE_NAME_TAKEN, describe(taken));
        }
    }

    /** 给模型和用户看的同一句话。 */
    public static String describe(List<String> taken) {
        return "Table name(s) " + taken + " are already used by another function unit. Table names are unique across "
                + "the whole platform (case-insensitive), so these tables cannot be saved. Rename each of them to a "
                + "name specific to this function unit's business and use the new name in every reference to it "
                + "(refTableName, tableRelations, form tableBindings, and subTableName / "
                + "multiInstance_{subTableName}_collection in the process).";
    }

    private boolean isTaken(Long functionUnitId, String name) {
        if (tableDefinitionRepository.existsByTableNameIgnoreCaseAndFunctionUnitIdNot(name, functionUnitId)) {
            return true;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rt_table_definitions WHERE lower(table_name) = lower(?)", Integer.class, name);
        return count != null && count > 0;
    }
}
