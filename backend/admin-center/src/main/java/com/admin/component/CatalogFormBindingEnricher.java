package com.admin.component;

import com.admin.dto.response.TableBindingDTO;
import com.admin.dto.response.TableFieldDefinitionDTO;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Resolves catalog-snapshot binding table ids and filter-FK targets from live DW by
 * stable table / field names, without replacing the frozen binding list.
 */
final class CatalogFormBindingEnricher {

    private final JdbcTemplate jdbcTemplate;
    private final Consumer<List<TableBindingDTO>> fieldDefinitionEnricher;

    CatalogFormBindingEnricher(
            JdbcTemplate jdbcTemplate, Consumer<List<TableBindingDTO>> fieldDefinitionEnricher) {
        this.jdbcTemplate = jdbcTemplate;
        this.fieldDefinitionEnricher = fieldDefinitionEnricher;
    }

    void enrich(List<TableBindingDTO> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return;
        }
        Map<String, TableNameRow> byName = loadTablesByName(collectTableNames(bindings));
        applyTableNameRows(bindings, byName);
        fieldDefinitionEnricher.accept(bindings);
        fillFilterFkRefTableIdFromFields(bindings);
        fillFilterFkRefTableIdFromNames(bindings, byName);
    }

    private static List<String> collectTableNames(List<TableBindingDTO> bindings) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (TableBindingDTO binding : bindings) {
            addTableName(names, binding.getTableName());
            addTableName(names, binding.getFilterFkRefTableName());
        }
        return List.copyOf(names);
    }

    private static void addTableName(Set<String> names, String name) {
        if (name != null && !name.isBlank()) {
            names.add(name);
        }
    }

    private record TableNameRow(Long id, String displayName, String tableType, String description) {
    }

    private Map<String, TableNameRow> loadTablesByName(List<String> names) {
        if (names.isEmpty()) {
            return Map.of();
        }
        Map<String, TableNameRow> byName = new LinkedHashMap<>();
        String placeholders = names.stream().map(n -> "?").collect(Collectors.joining(","));
        queryTablesByName(
                "SELECT id, table_name, table_display_name, table_type::text AS table_type, display_name "
                        + "FROM dw_table_definitions WHERE table_name IN (" + placeholders + ")",
                names, byName);
        List<String> missing = names.stream().filter(n -> !byName.containsKey(n)).toList();
        if (!missing.isEmpty()) {
            String rtPlaceholders = missing.stream().map(n -> "?").collect(Collectors.joining(","));
            queryTablesByName(
                    "SELECT id, table_name, display_name AS table_display_name, 'RELATION' AS table_type, "
                            + "description AS display_name FROM rt_table_definitions WHERE table_name IN ("
                            + rtPlaceholders + ")",
                    missing, byName);
        }
        return byName;
    }

    private void queryTablesByName(String sql, List<String> names, Map<String, TableNameRow> into) {
        jdbcTemplate.query(sql, rs -> {
            String tableName = rs.getString("table_name");
            if (tableName == null || tableName.isBlank() || into.containsKey(tableName)) {
                return;
            }
            into.put(tableName, new TableNameRow(
                    readNullableLong(rs, "id"),
                    rs.getString("table_display_name"),
                    rs.getString("table_type"),
                    rs.getString("display_name")));
        }, names.toArray());
    }

    private void applyTableNameRows(List<TableBindingDTO> bindings, Map<String, TableNameRow> byName) {
        for (TableBindingDTO binding : bindings) {
            TableNameRow row = byName.get(binding.getTableName());
            if (row == null) {
                continue;
            }
            if (binding.getTableId() == null) {
                binding.setTableId(row.id());
            }
            if (binding.getTableDisplayName() == null) {
                binding.setTableDisplayName(row.displayName());
            }
            if (binding.getTableType() == null) {
                binding.setTableType(row.tableType());
            }
            if (binding.getTableDescription() == null) {
                binding.setTableDescription(row.description());
            }
        }
    }

    private void fillFilterFkRefTableIdFromFields(List<TableBindingDTO> bindings) {
        for (TableBindingDTO binding : bindings) {
            if (binding.getFilterFkRefTableId() != null) {
                continue;
            }
            String fieldName = binding.getFilterFkFieldName();
            if (fieldName == null || fieldName.isBlank() || binding.getFieldDefinitions() == null) {
                continue;
            }
            binding.getFieldDefinitions().stream()
                    .filter(f -> fieldName.equalsIgnoreCase(f.getFieldName()))
                    .map(TableFieldDefinitionDTO::getRefTableId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .ifPresent(binding::setFilterFkRefTableId);
        }
    }

    private void fillFilterFkRefTableIdFromNames(
            List<TableBindingDTO> bindings, Map<String, TableNameRow> byName) {
        for (TableBindingDTO binding : bindings) {
            if (binding.getFilterFkRefTableId() != null) {
                continue;
            }
            TableNameRow row = byName.get(binding.getFilterFkRefTableName());
            if (row != null) {
                binding.setFilterFkRefTableId(row.id());
            }
        }
    }

    private static Long readNullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Object v = rs.getObject(column);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
