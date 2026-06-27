package com.developer.service.impl;

import com.developer.dto.MainTableViewDtos.CreateMainTableViewRequest;
import com.developer.dto.MainTableViewDtos.MainTableViewDTO;
import com.developer.dto.MainTableViewDtos.MainTableViewFieldDTO;
import com.developer.dto.MainTableViewDtos.UpdateMainTableViewRequest;
import com.developer.entity.FieldDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.entity.FunctionUnit;
import com.developer.entity.MainTableViewConfig;
import com.developer.entity.TableDefinition;
import com.developer.entity.MainTableViewField;
import com.developer.entity.TableDefinition;
import com.developer.enums.MainTableViewStatus;
import com.developer.enums.TableType;
import com.developer.exception.DeveloperBusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.MainTableViewConfigRepository;
import com.developer.repository.TableDefinitionRepository;
import com.developer.service.MainTableViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MainTableViewServiceImpl implements MainTableViewService {

    private static final String DEFAULT_VIEW_NAME = "Main view";

    private final MainTableViewConfigRepository viewConfigRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final TableDefinitionRepository tableDefinitionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<MainTableViewDTO> listViews(Long functionUnitId) {
        assertFunctionUnitExists(functionUnitId);
        return viewConfigRepository.findByFunctionUnitIdWithFields(functionUnitId).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MainTableViewDTO getView(Long functionUnitId, Long viewId) {
        MainTableViewConfig config = loadView(functionUnitId, viewId);
        return toDto(config);
    }

    @Override
    @Transactional
    public MainTableViewDTO createView(Long functionUnitId, CreateMainTableViewRequest request) {
        FunctionUnit functionUnit = assertFunctionUnitExists(functionUnitId);
        TableDefinition table = resolveViewableTable(functionUnitId, request.tableId());

        // A manually-created view starts empty (no fields); the developer adds columns from the catalog.
        MainTableViewConfig created = MainTableViewConfig.builder()
                .functionUnit(functionUnit)
                .mainTableId(table.getId())
                .viewName(request.viewName().trim())
                .isDefault(false)
                .sortConfig(new ArrayList<>())
                .filterConfig(Map.of("conditions", List.of()))
                .status(MainTableViewStatus.DRAFT)
                .viewFields(new ArrayList<>())
                .build();

        return toDto(viewConfigRepository.save(created));
    }

    @Override
    @Transactional
    public MainTableViewDTO updateView(Long functionUnitId, Long viewId, UpdateMainTableViewRequest request) {
        MainTableViewConfig config = loadView(functionUnitId, viewId);

        if (request.viewName() != null && !request.viewName().isBlank()) {
            config.setViewName(request.viewName().trim());
        }
        if (request.sortConfig() != null) {
            config.setSortConfig(request.sortConfig());
        }
        if (request.filterConfig() != null) {
            config.setFilterConfig(request.filterConfig());
        }
        if (request.fields() != null) {
            config.getViewFields().clear();
            int order = 0;
            for (MainTableViewFieldDTO fieldDto : request.fields()) {
                config.getViewFields().add(MainTableViewField.builder()
                        .viewConfig(config)
                        .fieldName(fieldDto.fieldName())
                        .displayLabel(fieldDto.displayLabel())
                        .columnWidth(fieldDto.columnWidth())
                        .sortOrder(fieldDto.sortOrder() != null ? fieldDto.sortOrder() : order++)
                        .visible(fieldDto.visible() == null || fieldDto.visible())
                        .isSystemField(Boolean.TRUE.equals(fieldDto.systemField()))
                        .build());
            }
        }
        config.setStatus(MainTableViewStatus.DRAFT);
        return toDto(viewConfigRepository.save(config));
    }

    @Override
    @Transactional
    public void deleteView(Long functionUnitId, Long viewId) {
        MainTableViewConfig config = loadView(functionUnitId, viewId);
        if (Boolean.TRUE.equals(config.getIsDefault())) {
            throw new DeveloperBusinessException("BIZ_MAIN_VIEW_DELETE_DEFAULT",
                    "Default Main view cannot be deleted");
        }
        viewConfigRepository.delete(config);
    }

    @Override
    @Transactional
    public void seedDefaultViewIfAbsent(Long functionUnitId, Long tableId) {
        if (viewConfigRepository.existsByMainTableIdAndIsDefaultTrue(tableId)) {
            return;
        }
        FunctionUnit functionUnit = assertFunctionUnitExists(functionUnitId);
        TableDefinition table = tableDefinitionRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("TableDefinition", tableId));
        if (!isViewableTableType(table.getTableType())) {
            return;
        }
        // Defer SUB tables until they have at least one field (avoids empty default views).
        if (table.getTableType() == TableType.SUB
                && (table.getFieldDefinitions() == null || table.getFieldDefinitions().isEmpty())) {
            return;
        }
        viewConfigRepository.save(buildDefaultConfig(functionUnit, table));
    }

    @Override
    @Transactional
    public void seedDefaultViewsForFunctionUnit(Long functionUnitId) {
        FunctionUnit functionUnit = assertFunctionUnitExists(functionUnitId);
        for (TableDefinition table : tableDefinitionRepository.findByFunctionUnitIdWithFields(functionUnitId)) {
            if (!isViewableTableType(table.getTableType())) {
                continue;
            }
            if (table.getTableType() == TableType.SUB
                    && (table.getFieldDefinitions() == null || table.getFieldDefinitions().isEmpty())) {
                continue;
            }
            if (viewConfigRepository.existsByMainTableIdAndIsDefaultTrue(table.getId())) {
                continue;
            }
            viewConfigRepository.save(buildDefaultConfig(functionUnit, table));
        }
    }

    private boolean isViewableTableType(TableType type) {
        return type == TableType.MAIN || type == TableType.SUB;
    }

    @Override
    @Transactional
    public void publishViewsForFunctionUnit(Long functionUnitId) {
        List<MainTableViewConfig> views = viewConfigRepository.findByFunctionUnitIdWithFields(functionUnitId);
        for (MainTableViewConfig view : views) {
            view.setStatus(MainTableViewStatus.PUBLISHED);
        }
        viewConfigRepository.saveAll(views);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> snapshotViewsForFunctionUnit(Long functionUnitId) {
        return viewConfigRepository.findByFunctionUnitIdWithFields(functionUnitId).stream()
                .map(this::toSnapshotMap)
                .toList();
    }

    @Override
    @Transactional
    public void cloneViewsForFunctionUnit(Long sourceFunctionUnitId, FunctionUnit targetFunctionUnit,
                                          Map<Long, TableDefinition> tableIdMapping) {
        List<MainTableViewConfig> sourceViews = viewConfigRepository.findByFunctionUnitIdWithFields(sourceFunctionUnitId);
        for (MainTableViewConfig source : sourceViews) {
            Long clonedMainTableId = tableIdMapping.containsKey(source.getMainTableId())
                    ? tableIdMapping.get(source.getMainTableId()).getId()
                    : source.getMainTableId();
            MainTableViewConfig cloned = MainTableViewConfig.builder()
                    .functionUnit(targetFunctionUnit)
                    .mainTableId(clonedMainTableId)
                    .viewName(source.getViewName())
                    .isDefault(source.getIsDefault())
                    .sortConfig(copySortConfig(source.getSortConfig()))
                    .filterConfig(copyFilterConfig(source.getFilterConfig()))
                    .status(MainTableViewStatus.DRAFT)
                    .viewFields(new ArrayList<>())
                    .build();
            cloneFields(source, cloned);
            viewConfigRepository.save(cloned);
        }
    }

    private MainTableViewConfig buildDefaultConfig(FunctionUnit functionUnit, TableDefinition table) {
        boolean isMain = table.getTableType() == TableType.MAIN;
        MainTableViewConfig config = MainTableViewConfig.builder()
                .functionUnit(functionUnit)
                .mainTableId(table.getId())
                // System fields (process_status / start_time / …) only exist for the MAIN table's
                // workflow runtime, so the default start_time sort only applies there. SUB tables sort
                // by their own field order.
                .sortConfig(isMain ? defaultSortConfig() : new ArrayList<>())
                // Default view name mirrors the table's name (matches Table Design).
                .viewName(defaultViewName(table))
                .isDefault(true)
                .filterConfig(Map.of("conditions", List.of()))
                .status(MainTableViewStatus.DRAFT)
                .viewFields(new ArrayList<>())
                .build();

        // Default view includes ALL business fields (incl. PK and FK relationship fields).
        int order = 0;
        for (FieldDefinition field : table.getFieldDefinitions()) {
            config.getViewFields().add(MainTableViewField.builder()
                    .viewConfig(config)
                    .fieldName(field.getFieldName())
                    .displayLabel(field.getDisplayName() != null ? field.getDisplayName() : field.getFieldName())
                    .columnWidth(150)
                    .sortOrder(order++)
                    .visible(true)
                    .isSystemField(false)
                    .build());
        }
        if (isMain) {
            addDefaultSystemFields(config, order);
        }
        return config;
    }

    private String defaultViewName(TableDefinition table) {
        if (table.getTableDisplayName() != null && !table.getTableDisplayName().isBlank()) {
            return table.getTableDisplayName();
        }
        if (table.getTableName() != null && !table.getTableName().isBlank()) {
            return table.getTableName();
        }
        return DEFAULT_VIEW_NAME;
    }

    private void addDefaultSystemFields(MainTableViewConfig config, int startOrder) {
        int order = startOrder;
        config.getViewFields().add(systemField(config, "process_status", "Status", order++, 120));
        config.getViewFields().add(systemField(config, "start_time", "Start Time", order++, 160));
        config.getViewFields().add(systemField(config, "initiator", "Initiator", order++, 140));
        config.getViewFields().add(systemField(config, "current_step", "Current Step", order, 140));
    }

    private MainTableViewField systemField(MainTableViewConfig config, String name, String label, int order, int width) {
        return MainTableViewField.builder()
                .viewConfig(config)
                .fieldName(name)
                .displayLabel(label)
                .columnWidth(width)
                .sortOrder(order)
                .visible(true)
                .isSystemField(true)
                .build();
    }

    private List<Map<String, Object>> defaultSortConfig() {
        List<Map<String, Object>> sort = new ArrayList<>();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("fieldName", "start_time");
        entry.put("direction", "DESC");
        entry.put("systemField", true);
        sort.add(entry);
        return sort;
    }

    private void cloneFields(MainTableViewConfig source, MainTableViewConfig target) {
        for (MainTableViewField field : source.getViewFields()) {
            target.getViewFields().add(MainTableViewField.builder()
                    .viewConfig(target)
                    .fieldName(field.getFieldName())
                    .displayLabel(field.getDisplayLabel())
                    .columnWidth(field.getColumnWidth())
                    .sortOrder(field.getSortOrder())
                    .visible(field.getVisible())
                    .isSystemField(field.getIsSystemField())
                    .build());
        }
    }

    private List<Map<String, Object>> copySortConfig(List<Map<String, Object>> sortConfig) {
        if (sortConfig == null) {
            return defaultSortConfig();
        }
        return new ArrayList<>(sortConfig);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> copyFilterConfig(Map<String, Object> filterConfig) {
        if (filterConfig == null) {
            return Map.of("conditions", List.of());
        }
        return new HashMap<>(filterConfig);
    }

    private TableDefinition resolveViewableTable(Long functionUnitId, Long tableId) {
        TableDefinition table = tableDefinitionRepository.findByIdWithFields(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("TableDefinition", tableId));
        if (!table.getFunctionUnit().getId().equals(functionUnitId)) {
            throw new DeveloperBusinessException("BIZ_VIEW_TABLE_FU_MISMATCH",
                    "Table does not belong to this function unit");
        }
        if (!isViewableTableType(table.getTableType())) {
            throw new DeveloperBusinessException("BIZ_VIEW_TABLE_TYPE",
                    "Views can only be created for MAIN or SUB tables");
        }
        return table;
    }

    private MainTableViewConfig loadView(Long functionUnitId, Long viewId) {
        MainTableViewConfig config = viewConfigRepository.findByIdWithFields(viewId)
                .orElseThrow(() -> new ResourceNotFoundException("MainTableViewConfig", viewId));
        if (!config.getFunctionUnit().getId().equals(functionUnitId)) {
            throw new DeveloperBusinessException("BIZ_VIEW_FU_MISMATCH", "View does not belong to this function unit");
        }
        return config;
    }

    private FunctionUnit assertFunctionUnitExists(Long functionUnitId) {
        return functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));
    }

    private MainTableViewDTO toDto(MainTableViewConfig config) {
        // Enrich each view field with FK/PK metadata derived from the owning table's FieldDefinition.
        // Drives designer-internal FK navigation and Portal FK drill-down; not persisted on the view.
        Map<String, FieldDefinition> fieldMeta = tableDefinitionRepository.findByIdWithFields(config.getMainTableId())
                .map(t -> t.getFieldDefinitions().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                FieldDefinition::getFieldName, fd -> fd, (a, b) -> a)))
                .orElse(Map.of());

        List<MainTableViewFieldDTO> fields = config.getViewFields().stream()
                .map(f -> {
                    FieldDefinition fd = fieldMeta.get(f.getFieldName());
                    return MainTableViewFieldDTO.builder()
                            .fieldName(f.getFieldName())
                            .displayLabel(f.getDisplayLabel())
                            .columnWidth(f.getColumnWidth())
                            .sortOrder(f.getSortOrder())
                            .visible(f.getVisible())
                            .systemField(f.getIsSystemField())
                            .isPrimaryKey(fd != null ? fd.getIsPrimaryKey() : null)
                            .isForeignKey(fd != null ? fd.getIsForeignKey() : null)
                            .refTableId(fd != null ? fd.getRefTableId() : null)
                            .refPrimaryKeyFields(fd != null ? fd.getRefPrimaryKeyFields() : null)
                            .build();
                })
                .toList();

        return MainTableViewDTO.builder()
                .id(config.getId())
                .functionUnitId(config.getFunctionUnit().getId())
                .mainTableId(config.getMainTableId())
                .viewName(config.getViewName())
                .isDefault(config.getIsDefault())
                .status(config.getStatus() != null ? config.getStatus().name() : MainTableViewStatus.DRAFT.name())
                .sortConfig(config.getSortConfig())
                .filterConfig(config.getFilterConfig())
                .fields(fields)
                .build();
    }

    private Map<String, Object> toSnapshotMap(MainTableViewConfig config) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("id", config.getId());
        snap.put("mainTableId", config.getMainTableId());
        snap.put("viewName", config.getViewName());
        snap.put("isDefault", config.getIsDefault());
        snap.put("status", config.getStatus() != null ? config.getStatus().name() : MainTableViewStatus.DRAFT.name());
        snap.put("sortConfig", config.getSortConfig());
        snap.put("filterConfig", config.getFilterConfig());
        List<Map<String, Object>> fields = config.getViewFields().stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fieldName", f.getFieldName());
            m.put("displayLabel", f.getDisplayLabel());
            m.put("columnWidth", f.getColumnWidth());
            m.put("sortOrder", f.getSortOrder());
            m.put("visible", f.getVisible());
            m.put("systemField", f.getIsSystemField());
            return m;
        }).toList();
        snap.put("fields", fields);
        return snap;
    }
}
