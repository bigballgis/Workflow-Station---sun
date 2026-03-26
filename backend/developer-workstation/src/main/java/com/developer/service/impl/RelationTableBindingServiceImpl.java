package com.developer.service.impl;

import com.developer.entity.FormDefinition;
import com.developer.entity.FormTableBinding;
import com.developer.entity.RelationViewConfig;
import com.developer.enums.BindingMode;
import com.developer.enums.BindingType;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FormTableBindingRepository;
import com.developer.repository.RelationViewConfigRepository;
import com.developer.service.RelationTableBindingService;
import com.platform.common.dto.RelationTableDTO;
import com.platform.common.enums.RelationTableStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Relation Table 绑定服务实现
 */
@Service
@RequiredArgsConstructor
public class RelationTableBindingServiceImpl implements RelationTableBindingService {

    private final FormTableBindingRepository formTableBindingRepository;
    private final FormDefinitionRepository formDefinitionRepository;
    private final RelationViewConfigRepository relationViewConfigRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public List<RelationTableDTO> getAvailableTables() {
        String sql = "SELECT id, table_name, display_name, description, status, enabled, "
                + "portal_visible, current_version FROM rt_table_definitions WHERE status = ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> RelationTableDTO.builder()
                .id(rs.getLong("id"))
                .tableName(rs.getString("table_name"))
                .displayName(rs.getString("display_name"))
                .description(rs.getString("description"))
                .status(RelationTableStatus.fromCode(rs.getString("status")))
                .enabled(rs.getBoolean("enabled"))
                .portalVisible(rs.getBoolean("portal_visible"))
                .currentVersion(rs.getInt("current_version"))
                .build(), RelationTableStatus.DEPLOYED.getCode());
    }

    @Override
    @Transactional
    public Long bindRelationTable(Long formId, Long tableId) {
        FormDefinition form = formDefinitionRepository.findById(formId)
                .orElseThrow(() -> new IllegalArgumentException("Form not found: " + formId));

        if (formTableBindingRepository.existsByFormIdAndRelationTableId(formId, tableId)) {
            throw new IllegalStateException("Relation table already bound to this form");
        }

        FormTableBinding binding = FormTableBinding.builder()
                .form(form)
                .relationTableId(tableId)
                .bindingType(BindingType.RELATED)
                .bindingMode(BindingMode.READONLY)
                .sortOrder((int) formTableBindingRepository.countByFormId(formId))
                .build();
        binding = formTableBindingRepository.save(binding);

        // Auto-create RelationViewConfig
        RelationViewConfig viewConfig = RelationViewConfig.builder()
                .bindingId(binding.getId())
                .tableId(tableId)
                .build();
        relationViewConfigRepository.save(viewConfig);

        return binding.getId();
    }

    @Override
    @Transactional
    public void unbindRelationTable(Long formId, Long bindingId) {
        FormTableBinding binding = formTableBindingRepository.findById(bindingId)
                .orElseThrow(() -> new IllegalArgumentException("Binding not found: " + bindingId));

        if (!binding.getFormId().equals(formId)) {
            throw new IllegalArgumentException("Binding does not belong to form: " + formId);
        }

        // Delete ViewConfig (cascade deletes ViewFields)
        relationViewConfigRepository.findByBindingId(bindingId)
                .ifPresent(relationViewConfigRepository::delete);

        formTableBindingRepository.delete(binding);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RelationTableBindingDTO> getBindings(Long formId) {
        List<FormTableBinding> bindings = formTableBindingRepository
                .findByFormIdAndBindingTypeList(formId, BindingType.RELATED);
        return bindings.stream()
                .map(b -> {
                    Long viewConfigId = relationViewConfigRepository.findByBindingId(b.getId())
                            .map(RelationViewConfig::getId)
                            .orElse(null);
                    // Query display name from rt_table_definitions
                    String displayName = getRelationTableDisplayName(b.getRelationTableId());
                    return new RelationTableBindingDTO(
                            b.getId(),
                            b.getRelationTableId(),
                            displayName != null ? displayName : "Unknown",
                            displayName,
                            b.getBindingType().name(),
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
}
