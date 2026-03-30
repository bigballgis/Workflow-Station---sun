package com.developer.service.impl;

import com.developer.entity.FormTableBinding;
import com.developer.entity.RelationLookupConfig;
import com.developer.entity.RelationViewConfig;
import com.developer.enums.BindingType;
import com.developer.repository.FormTableBindingRepository;
import com.developer.repository.RelationLookupConfigRepository;
import com.developer.repository.RelationViewConfigRepository;
import com.developer.service.RelationLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Relation Table Lookup 配置服务实现
 */
@Service
@RequiredArgsConstructor
public class RelationLookupServiceImpl implements RelationLookupService {

    private final RelationLookupConfigRepository lookupConfigRepository;
    private final FormTableBindingRepository formTableBindingRepository;
    private final RelationViewConfigRepository viewConfigRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public RelationLookupConfig getLookupConfig(Long formId, String componentId) {
        return lookupConfigRepository.findByFormIdAndComponentId(formId, componentId)
                .orElse(null);
    }

    @Override
    @Transactional
    public RelationLookupConfig saveLookupConfig(Long formId, String componentId, LookupConfigDTO config) {
        // Validate that the table is bound to this form
        List<FormTableBinding> bindings = formTableBindingRepository
                .findByFormIdAndBindingTypeList(formId, BindingType.RELATED);
        boolean isBound = bindings.stream()
                .anyMatch(b -> b.getRelationTableId() != null && b.getRelationTableId().equals(config.tableId()));
        if (!isBound) {
            throw new IllegalArgumentException(
                    "Table " + config.tableId() + " is not bound to form " + formId);
        }

        RelationLookupConfig entity = lookupConfigRepository
                .findByFormIdAndComponentId(formId, componentId)
                .orElse(RelationLookupConfig.builder()
                        .formId(formId)
                        .componentId(componentId)
                        .build());

        entity.setViewConfigId(config.viewConfigId());
        entity.setTableId(config.tableId());
        entity.setSearchFields(config.searchFields());
        entity.setDisplayField(config.displayField());

        return lookupConfigRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BoundViewDTO> getBoundViews(Long formId) {
        List<FormTableBinding> bindings = formTableBindingRepository
                .findByFormIdAndBindingTypeList(formId, BindingType.RELATED);

        return bindings.stream()
                .map(b -> {
                    Long viewConfigId = viewConfigRepository.findByBindingId(b.getId())
                            .map(RelationViewConfig::getId)
                            .orElse(null);
                    String displayName = getRelationTableDisplayName(b.getRelationTableId());
                    String tableName = getRelationTableName(b.getRelationTableId());
                    return new BoundViewDTO(
                            b.getId(),
                            b.getRelationTableId(),
                            tableName,
                            displayName,
                            viewConfigId
                    );
                })
                .toList();
    }

    private String getRelationTableDisplayName(Long tableId) {
        if (tableId == null) return null;
        String sql = "SELECT display_name FROM rt_table_definitions WHERE id = ?";
        List<String> names = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("display_name"), tableId);
        return names.isEmpty() ? null : names.get(0);
    }

    private String getRelationTableName(Long tableId) {
        if (tableId == null) return null;
        String sql = "SELECT table_name FROM rt_table_definitions WHERE id = ?";
        List<String> names = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("table_name"), tableId);
        return names.isEmpty() ? null : names.get(0);
    }
}
