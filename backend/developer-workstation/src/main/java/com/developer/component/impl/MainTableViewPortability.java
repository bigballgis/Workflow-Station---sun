package com.developer.component.impl;

import com.developer.entity.FunctionUnit;
import com.developer.entity.MainTableViewConfig;
import com.developer.entity.MainTableViewField;
import com.developer.enums.MainTableViewStatus;
import com.developer.repository.MainTableViewConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Export/import of "View Design" data — the per-function-unit Main Table views
 * ({@code dw_main_table_view_configs} + {@code dw_main_table_view_fields}).
 *
 * <p>Export serializes each view by its main table's NAME (ids don't survive across packages),
 * mirroring how FK {@code refTableName} and relation-table structures are carried. Import recreates
 * the views on the target function unit, remapping {@code mainTableName} → the freshly-imported
 * table id via the caller's {@code importedTableNameToId} map.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MainTableViewPortability {

    private final MainTableViewConfigRepository mainTableViewConfigRepository;

    /**
     * Serialize all main-table views of a function unit. Each view's main table is referenced by
     * {@code mainTableName} so it can be remapped on import.
     *
     * @param tableIdToName map of this FU's table id → table name (the exporter already builds it)
     */
    public List<Map<String, Object>> export(Long functionUnitId, Map<Long, String> tableIdToName) {
        List<MainTableViewConfig> views = mainTableViewConfigRepository.findByFunctionUnitIdWithFields(functionUnitId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (MainTableViewConfig view : views) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mainTableName", view.getMainTableId() != null ? tableIdToName.get(view.getMainTableId()) : null);
            m.put("viewName", view.getViewName());
            m.put("isDefault", view.getIsDefault());
            m.put("status", view.getStatus() != null ? view.getStatus().name() : MainTableViewStatus.DRAFT.name());
            m.put("sortConfig", view.getSortConfig());
            m.put("filterConfig", view.getFilterConfig());
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
        if (mainTableViews == null || mainTableViews.isEmpty()) {
            return;
        }
        // Replace existing views (e.g. a default seeded on FU create, or prior content on re-import).
        mainTableViewConfigRepository.deleteByFunctionUnitId(functionUnit.getId());
        mainTableViewConfigRepository.flush();

        for (Map<String, Object> v : mainTableViews) {
            String mainTableName = (String) v.get("mainTableName");
            Long mainTableId = mainTableName != null ? importedTableNameToId.get(mainTableName) : null;
            if (mainTableId == null) {
                log.warn("Skipping main-table view '{}': main table '{}' not found in imported tables",
                        v.get("viewName"), mainTableName);
                continue;
            }
            MainTableViewStatus status = parseStatus(v.get("status"));
            MainTableViewConfig config = MainTableViewConfig.builder()
                    .functionUnit(functionUnit)
                    .mainTableId(mainTableId)
                    .viewName((String) v.get("viewName"))
                    .isDefault(v.get("isDefault") instanceof Boolean b ? b : false)
                    .sortConfig(asMapList(v.get("sortConfig")))
                    .filterConfig(asMap(v.get("filterConfig")))
                    .status(status)
                    .viewFields(new ArrayList<>())
                    .build();
            addFields(config, v.get("fields"));
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
            config.getViewFields().add(MainTableViewField.builder()
                    .viewConfig(config)
                    .fieldName((String) f.get("fieldName"))
                    .displayLabel((String) f.get("displayLabel"))
                    .columnWidth(f.get("columnWidth") instanceof Number num ? num.intValue() : null)
                    .sortOrder(sortOrder)
                    .visible(f.get("visible") instanceof Boolean b ? b : true)
                    .isSystemField(f.get("systemField") instanceof Boolean b ? b : false)
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
}
