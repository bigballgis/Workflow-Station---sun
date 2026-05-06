package com.developer.service.impl;

import com.developer.entity.FormTableBinding;
import com.developer.entity.SubTableViewConfig;
import com.developer.entity.SubTableViewField;
import com.developer.entity.TableDefinition;
import com.developer.enums.DataType;
import com.developer.repository.FormTableBindingRepository;
import com.developer.repository.SubTableViewConfigRepository;
import com.developer.repository.TableDefinitionRepository;
import com.developer.service.SubTableViewService;
import com.platform.common.dto.RelationFieldDTO;
import com.platform.common.enums.RelationDataType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Sub-Table View 配置服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubTableViewServiceImpl implements SubTableViewService {

    private final SubTableViewConfigRepository viewConfigRepository;
    private final FormTableBindingRepository bindingRepository;
    private final TableDefinitionRepository tableDefinitionRepository;

    @Override
    @Transactional(readOnly = true)
    public SubTableViewConfig getViewConfig(Long bindingId) {
        return viewConfigRepository.findByBindingId(bindingId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "View config not found for binding: " + bindingId));
    }

    @Override
    @Transactional
    public SubTableViewConfig getOrCreateViewConfig(Long bindingId) {
        return viewConfigRepository.findByBindingId(bindingId)
                .orElseGet(() -> createDefaultViewConfig(bindingId));
    }

    @Override
    @Transactional
    public SubTableViewConfig saveViewConfig(Long bindingId, List<ViewFieldDTO> fields) {
        SubTableViewConfig config = viewConfigRepository.findByBindingId(bindingId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "View config not found for binding: " + bindingId));

        // Clear existing fields and replace
        config.getViewFields().clear();

        for (ViewFieldDTO dto : fields) {
            SubTableViewField field = SubTableViewField.builder()
                    .viewConfig(config)
                    .fieldName(dto.fieldName())
                    .displayLabel(dto.displayLabel())
                    .columnWidth(dto.columnWidth())
                    .sortOrder(dto.sortOrder())
                    .visible(dto.visible() != null ? dto.visible() : true)
                    .build();
            config.getViewFields().add(field);
        }

        return viewConfigRepository.save(config);
    }

    @Override
    @Transactional
    public SubTableViewConfig createDefaultViewConfig(Long bindingId) {
        FormTableBinding binding = bindingRepository.findByIdWithTable(bindingId)
                .orElseThrow(() -> new IllegalArgumentException("Binding not found: " + bindingId));

        // Check if config already exists
        if (viewConfigRepository.existsByBindingId(bindingId)) {
            return viewConfigRepository.findByBindingId(bindingId).orElseThrow();
        }

        // Get table ID from binding
        Long tableId = binding.getTableId();
        if (tableId == null) {
            throw new IllegalArgumentException("Binding has no table: " + bindingId);
        }

        // Get all fields from the table using JPA
        TableDefinition table = tableDefinitionRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));
        List<RelationFieldDTO> availableFields = table.getFieldDefinitions().stream()
                .map(f -> RelationFieldDTO.builder()
                        .id(f.getId())
                        .fieldName(f.getFieldName())
                        .dataType(toRelationDataType(f.getDataType()))
                        .length(f.getLength())
                        .precision(f.getPrecision())
                        .scale(f.getScale())
                        .nullable(f.getNullable())
                        .isPrimaryKey(f.getIsPrimaryKey())
                        .defaultValue(f.getDefaultValue())
                        .comment(f.getDescription())
                        .sortOrder(f.getSortOrder())
                        .build())
                .collect(java.util.stream.Collectors.toList());

        // Create view config
        SubTableViewConfig config = SubTableViewConfig.builder()
                .binding(binding)
                .viewFields(new ArrayList<>())
                .build();

        // Add all fields to the view
        int sortOrder = 0;
        for (RelationFieldDTO field : availableFields) {
            SubTableViewField viewField = SubTableViewField.builder()
                    .viewConfig(config)
                    .fieldName(field.getFieldName())
                    .displayLabel(field.getComment() != null ? field.getComment() : field.getFieldName())
                    .columnWidth(150)
                    .sortOrder(sortOrder++)
                    .visible(true)
                    .build();
            config.getViewFields().add(viewField);
        }

        config = viewConfigRepository.save(config);

        // Update binding with subListViewId
        binding.setSubListViewId(config.getId());
        bindingRepository.save(binding);

        return config;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RelationFieldDTO> getAvailableFields(Long tableId) {
        // Query from dw_field_definitions for sub-table fields
        // Note: SUB type tables may not have entries in dw_field_definitions,
        // so we use JPA to get fields from the TableDefinition entity instead
        return List.of(); // Fields will be populated by the caller using JPA relationship
    }

    @Override
    @Transactional(readOnly = true)
    public List<RelationFieldDTO> getAvailableFieldsByBinding(FormTableBinding binding) {
        Long tableId = binding.getTableId();
        if (tableId == null) {
            return new ArrayList<>();
        }
        // Use JPA to get fields from TableDefinition
        return tableDefinitionRepository.findById(tableId)
                .map(table -> table.getFieldDefinitions().stream()
                        .map(f -> RelationFieldDTO.builder()
                                .id(f.getId())
                                .fieldName(f.getFieldName())
                                .dataType(toRelationDataType(f.getDataType()))
                                .length(f.getLength())
                                .precision(f.getPrecision())
                                .scale(f.getScale())
                                .nullable(f.getNullable())
                                .isPrimaryKey(f.getIsPrimaryKey())
                                .defaultValue(f.getDefaultValue())
                                .comment(f.getDescription())
                                .sortOrder(f.getSortOrder())
                                .build())
                        .collect(java.util.stream.Collectors.toList()))
                .orElse(new ArrayList<>());
    }

    @Override
    @Transactional(readOnly = true)
    public ViewConfigDTO getViewConfigDTO(Long bindingId) {
        SubTableViewConfig config = getOrCreateViewConfig(bindingId);
        FormTableBinding binding = config.getBinding();
        Long tableId = binding != null ? binding.getTableId() : null;

        List<ViewFieldDTO> viewFieldDTOs = config.getViewFields().stream()
                .map(f -> new ViewFieldDTO(
                        f.getFieldName(),
                        f.getDisplayLabel(),
                        f.getColumnWidth(),
                        f.getSortOrder(),
                        f.getVisible()
                ))
                .toList();

        return new ViewConfigDTO(config.getId(), bindingId, tableId, viewFieldDTOs);
    }

    /**
     * Maps designer {@link DataType} to shared {@link RelationDataType}. Unchecked
     * failures here run inside {@code @Transactional} and would mark the transaction
     * rollback-only even if the caller catches — so unknown types fall back to VARCHAR.
     */
    private static RelationDataType toRelationDataType(DataType dt) {
        if (dt == null) {
            return RelationDataType.VARCHAR;
        }
        try {
            return RelationDataType.valueOf(dt.name());
        } catch (IllegalArgumentException ex) {
            return RelationDataType.VARCHAR;
        }
    }
}
