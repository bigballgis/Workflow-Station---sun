package com.developer.component.impl;

import com.developer.entity.FunctionUnit;
import com.developer.entity.MainTableViewAccess;
import com.developer.entity.MainTableViewConfig;
import com.developer.entity.MainTableViewField;
import com.developer.enums.MainTableViewAccessTargetType;
import com.developer.enums.MainTableViewStatus;
import com.developer.exception.DeveloperBusinessException;
import com.developer.repository.MainTableViewConfigRepository;
import com.developer.util.MainTableViewAccessRulesValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Export/import of "View Design" data — the per-function-unit Main Table views
 * ({@code dw_main_table_view_configs} + {@code dw_main_table_view_fields} + access rules).
 *
 * <p>Export serializes each view by its main table's NAME (ids don't survive across packages).
 * Access rules carry {@code targetCode} for cross-environment remap and {@code targetId} as fallback
 * for same-DB version rollback. Import validates paired BU+Role rules (see
 * {@link MainTableViewAccessRulesValidator}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MainTableViewPortability {

    private final MainTableViewConfigRepository mainTableViewConfigRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Serialize all main-table views of a function unit. Each view's main table is referenced by
     * {@code mainTableName} so it can be remapped on import.
     *
     * @param tableIdToName map of this FU's table id → table name (the exporter already builds it)
     */
    public List<Map<String, Object>> export(Long functionUnitId, Map<Long, String> tableIdToName) {
        return export(functionUnitId, tableIdToName, Map.of());
    }

    /**
     * @param formIdToName resolves the detail form to a name — database ids differ
     *                     per environment, so exporting the raw id would point the
     *                     imported view at an unrelated form or at nothing.
     */
    public List<Map<String, Object>> export(Long functionUnitId,
                                            Map<Long, String> tableIdToName,
                                            Map<Long, String> formIdToName) {
        List<MainTableViewConfig> views = mainTableViewConfigRepository.findByFunctionUnitIdWithFields(functionUnitId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (MainTableViewConfig view : views) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mainTableName", view.getMainTableId() != null ? tableIdToName.get(view.getMainTableId()) : null);
            m.put("detailFormName",
                    view.getDetailFormId() != null ? formIdToName.get(view.getDetailFormId()) : null);
            m.put("viewName", view.getViewName());
            m.put("isDefault", view.getIsDefault());
            m.put("status", view.getStatus() != null ? view.getStatus().name() : MainTableViewStatus.DRAFT.name());
            m.put("sortConfig", view.getSortConfig());
            m.put("filterConfig", view.getFilterConfig());
            m.put("restrictToInvolvedUsers", Boolean.TRUE.equals(view.getRestrictToInvolvedUsers()));
            // Access rules are not JOIN FETCHed on the entity — load via JDBC (same as designer Save path).
            m.put("accessRules", exportAccessRules(view.getId()));
            List<Map<String, Object>> fields = new ArrayList<>();
            if (view.getViewFields() != null) {
                for (MainTableViewField f : view.getViewFields()) {
                    Map<String, Object> fm = new LinkedHashMap<>();
                    fm.put("fieldName", f.getFieldName());
                    fm.put("displayLabel", f.getDisplayLabel());
                    fm.put("columnWidth", f.getColumnWidth());
                    fm.put("sortOrder", f.getSortOrder());
                    fm.put("visible", f.getVisible());
                    fm.put("systemField", f.getIsSystemField());
                    fm.put("columnType", f.getColumnType() != null ? f.getColumnType() : "field");
                    fm.put("lookupSourceField", f.getLookupSourceField());
                    fm.put("lookupDisplayField", f.getLookupDisplayField());
                    fields.add(fm);
                }
            }
            m.put("fields", fields);
            out.add(m);
        }
        return out;
    }

    /**
     * Recreate main-table views on the target function unit from serialized data.
     * Replaces any existing views on the FU first (idempotent for re-import).
     *
     * @param mainTableViews       serialized views (as produced by {@link #export})
     * @param functionUnit         target (managed) function unit
     * @param importedTableNameToId table name → newly-imported table id
     */
    public void importAll(List<Map<String, Object>> mainTableViews,
                          FunctionUnit functionUnit,
                          Map<String, Long> importedTableNameToId) {
        importAll(mainTableViews, functionUnit, importedTableNameToId, Map.of());
    }

    /**
     * @param importedFormNameToId form name → newly-imported form id. Must be populated
     *                             by the caller <em>before</em> views are imported;
     *                             an empty map leaves every detail form unset.
     */
    public void importAll(List<Map<String, Object>> mainTableViews,
                          FunctionUnit functionUnit,
                          Map<String, Long> importedTableNameToId,
                          Map<String, Long> importedFormNameToId) {
        if (mainTableViews == null || mainTableViews.isEmpty()) {
            return;
        }
        mainTableViewConfigRepository.deleteByFunctionUnitId(functionUnit.getId());
        mainTableViewConfigRepository.flush();

        for (Map<String, Object> v : mainTableViews) {
            String viewName = stringVal(v.get("viewName"));
            String mainTableName = (String) v.get("mainTableName");
            Long mainTableId = mainTableName != null ? importedTableNameToId.get(mainTableName) : null;
            if (mainTableId == null) {
                log.warn("Skipping main-table view '{}': main table '{}' not found in imported tables",
                        viewName, mainTableName);
                continue;
            }
            // A named detail form that did not come across is reported rather than
            // silently dropped: the view would otherwise look configured but open
            // nothing when a row is clicked.
            String detailFormName = (String) v.get("detailFormName");
            Long detailFormId = null;
            if (detailFormName != null && !detailFormName.isBlank()) {
                // MAIN-table rows open the request detail page, so they bind no detail form.
                // Packages exported before that rule still carry one; drop it with a warning
                // rather than throwing — a legacy package must remain importable.
                if (isMainTable(mainTableId)) {
                    log.warn("Main-table view '{}' is on a MAIN table and cannot bind detail form "
                            + "'{}'; its rows open the request detail page", viewName, detailFormName);
                } else {
                    detailFormId = importedFormNameToId.get(detailFormName);
                    if (detailFormId == null) {
                        log.warn("Main-table view '{}' references detail form '{}', which is not in the "
                                + "imported forms; the view will have no detail page", viewName, detailFormName);
                    }
                }
            }

            MainTableViewStatus status = parseStatus(v.get("status"));
            MainTableViewConfig config = MainTableViewConfig.builder()
                    .functionUnit(functionUnit)
                    .mainTableId(mainTableId)
                    .detailFormId(detailFormId)
                    .viewName(viewName)
                    .isDefault(v.get("isDefault") instanceof Boolean b ? b : false)
                    .sortConfig(asMapList(v.get("sortConfig")))
                    .filterConfig(asMap(v.get("filterConfig")))
                    .restrictToInvolvedUsers(v.get("restrictToInvolvedUsers") instanceof Boolean b && b)
                    .status(status)
                    .viewFields(new ArrayList<>())
                    .accessRules(new ArrayList<>())
                    .build();
            addFields(config, v.get("fields"));
            addAccessRules(config, viewName, v.get("accessRules"));
            MainTableViewAccessRulesValidator.validatePairedOrEmptyEntities(config.getAccessRules());
            mainTableViewConfigRepository.save(config);
        }
    }

    @SuppressWarnings("unchecked")
    private void addFields(MainTableViewConfig config, Object fieldsObj) {
        if (!(fieldsObj instanceof List<?> fields)) {
            return;
        }
        int order = 0;
        for (Object fo : fields) {
            if (!(fo instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> f = (Map<String, Object>) raw;
            Integer sortOrder = f.get("sortOrder") instanceof Number num ? num.intValue() : order;
            String columnType = f.get("columnType") instanceof String s && !s.isBlank() ? s : "field";
            config.getViewFields().add(MainTableViewField.builder()
                    .viewConfig(config)
                    .fieldName((String) f.get("fieldName"))
                    .displayLabel((String) f.get("displayLabel"))
                    .columnWidth(f.get("columnWidth") instanceof Number num ? num.intValue() : null)
                    .sortOrder(sortOrder)
                    .visible(f.get("visible") instanceof Boolean b ? b : true)
                    .isSystemField(f.get("systemField") instanceof Boolean b ? b : false)
                    .columnType(columnType)
                    .lookupSourceField(f.get("lookupSourceField") instanceof String s ? s : null)
                    .lookupDisplayField(f.get("lookupDisplayField") instanceof String s ? s : null)
                    .build());
            order++;
        }
    }

    private MainTableViewStatus parseStatus(Object v) {
        if (v instanceof String s && !s.isBlank()) {
            try {
                return MainTableViewStatus.valueOf(s);
            } catch (IllegalArgumentException e) {
                log.warn("Unknown main-table view status '{}', defaulting to DRAFT", s);
            }
        }
        return MainTableViewStatus.DRAFT;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object v) {
        return v instanceof List<?> list ? (List<Map<String, Object>>) list : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object v) {
        return v instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private List<Map<String, Object>> exportAccessRules(Long viewConfigId) {
        if (viewConfigId == null) {
            return List.of();
        }
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList("""
                    SELECT target_type, target_id
                    FROM dw_main_table_view_access
                    WHERE view_config_id = ?
                    ORDER BY target_type, target_id
                    """, viewConfigId);
        } catch (Exception e) {
            log.warn("Failed to load access rules for view {}: {}", viewConfigId, e.getMessage());
            return List.of();
        }
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String targetType = stringVal(row.get("target_type"));
            String targetId = stringVal(row.get("target_id"));
            if (targetType == null || targetId == null || targetId.isBlank()) {
                continue;
            }
            MainTableViewAccessTargetType type;
            try {
                type = MainTableViewAccessTargetType.valueOf(targetType.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("targetType", type.name());
            m.put("targetId", targetId);
            String targetCode = resolveTargetCode(type, targetId);
            if (targetCode != null && !targetCode.isBlank()) {
                m.put("targetCode", targetCode);
            }
            out.add(m);
        }
        return out;
    }

    /**
     * Whether the imported table is the function unit's MAIN table. Such views open the request
     * detail page in the portal and must not carry a detail form across an import.
     */
    private boolean isMainTable(Long tableId) {
        if (tableId == null) {
            return false;
        }
        try {
            List<String> types = jdbcTemplate.queryForList(
                    "SELECT table_type FROM dw_table_definitions WHERE id = ?", String.class, tableId);
            return !types.isEmpty() && "MAIN".equalsIgnoreCase(types.get(0));
        } catch (Exception e) {
            // FALLBACK(import): unknown table type — keep the legacy behaviour of importing the
            // binding rather than dropping design data on a transient lookup failure.
            log.warn("Could not resolve table type for table {}: {}", tableId, e.getMessage());
            return false;
        }
    }

    private String resolveTargetCode(MainTableViewAccessTargetType type, String targetId) {
        if (targetId == null || targetId.isBlank()) {
            return null;
        }
        try {
            if (type == MainTableViewAccessTargetType.ROLE) {
                return jdbcTemplate.queryForObject(
                        "SELECT code FROM sys_roles WHERE id = ?", String.class, targetId);
            }
            return jdbcTemplate.queryForObject(
                    "SELECT code FROM sys_business_units WHERE id = ?", String.class, targetId);
        } catch (Exception e) {
            log.warn("Could not resolve {} code for id {}: {}", type, targetId, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void addAccessRules(MainTableViewConfig config, String viewName, Object accessObj) {
        if (!(accessObj instanceof List<?> rules)) {
            return;
        }
        for (Object item : rules) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> rule = (Map<String, Object>) raw;
            String targetTypeRaw = rule.get("targetType") instanceof String s ? s : null;
            if (targetTypeRaw == null || targetTypeRaw.isBlank()) {
                continue;
            }
            MainTableViewAccessTargetType targetType;
            try {
                targetType = MainTableViewAccessTargetType.valueOf(targetTypeRaw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            String targetId = null;
            if (rule.get("targetId") instanceof String id && !id.isBlank()) {
                targetId = id.trim();
            }
            if (targetId == null && rule.get("targetCode") instanceof String code && !code.isBlank()) {
                targetId = resolveTargetId(targetType, code.trim());
                if (targetId == null) {
                    throw MainTableViewAccessRulesValidator.importUnresolved(viewName, targetType.name(), code);
                }
            }
            if (targetId == null || targetId.isBlank()) {
                throw MainTableViewAccessRulesValidator.importUnresolved(
                        viewName, targetType.name(), rule.get("targetCode"));
            }
            config.getAccessRules().add(MainTableViewAccess.builder()
                    .viewConfig(config)
                    .targetType(targetType)
                    .targetId(targetId)
                    .build());
        }
    }

    private String resolveTargetId(MainTableViewAccessTargetType type, String targetCode) {
        try {
            if (type == MainTableViewAccessTargetType.ROLE) {
                return jdbcTemplate.queryForObject(
                        "SELECT id FROM sys_roles WHERE code = ?", String.class, targetCode);
            }
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM sys_business_units WHERE code = ?", String.class, targetCode);
        } catch (Exception e) {
            return null;
        }
    }

    private static String stringVal(Object value) {
        return value != null ? String.valueOf(value) : null;
    }
}

