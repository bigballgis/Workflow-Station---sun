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
import com.platform.common.dto.RelationFieldDTO;
import com.platform.common.dto.RelationTableDTO;
import com.platform.common.enums.RelationDataType;
import com.platform.common.enums.RelationTableStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Relation Table 绑定服务实现
 */
@Service
@RequiredArgsConstructor
public class RelationTableBindingServiceImpl implements RelationTableBindingService {

    public static final Long SYSTEM_USER_TABLE_ID = -1_000_000_001L;
    private static final String SYSTEM_USER_TABLE_NAME = "sys_users";
    private static final String SYSTEM_USER_DISPLAY_NAME = "User";

    private final FormTableBindingRepository formTableBindingRepository;
    private final FormDefinitionRepository formDefinitionRepository;
    private final RelationViewConfigRepository relationViewConfigRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public List<RelationTableDTO> getAvailableTables() {
        String sql = "SELECT id, table_name, display_name, description, status, enabled, "
                + "portal_visible, current_version FROM rt_table_definitions WHERE status = ?";
        List<RelationTableDTO> tables = new ArrayList<>(jdbcTemplate.query(sql, (rs, rowNum) -> RelationTableDTO.builder()
                .id(rs.getLong("id"))
                .tableName(rs.getString("table_name"))
                .displayName(rs.getString("display_name"))
                .description(rs.getString("description"))
                .status(RelationTableStatus.fromCode(rs.getString("status")))
                .enabled(rs.getBoolean("enabled"))
                .portalVisible(rs.getBoolean("portal_visible"))
                .currentVersion(rs.getInt("current_version"))
                .build(), RelationTableStatus.DEPLOYED.getCode()));

        // Load field definitions for each table
        String fieldSql = "SELECT id, field_name, data_type, length, precision_value, scale, "
                + "nullable, is_primary_key, default_value, display_name, sort_order "
                + "FROM rt_field_definitions WHERE table_id = ? ORDER BY sort_order ASC";
        for (RelationTableDTO table : tables) {
            List<RelationFieldDTO> fields = jdbcTemplate.query(fieldSql, (rs, rowNum) -> RelationFieldDTO.builder()
                    .id(rs.getLong("id"))
                    .fieldName(rs.getString("field_name"))
                    .dataType(RelationDataType.valueOf(rs.getString("data_type")))
                    .length(rs.getObject("length", Integer.class))
                    .precision(rs.getObject("precision_value", Integer.class))
                    .scale(rs.getObject("scale", Integer.class))
                    .nullable(rs.getBoolean("nullable"))
                    .isPrimaryKey(rs.getBoolean("is_primary_key"))
                    .defaultValue(rs.getString("default_value"))
                    .displayName(rs.getString("display_name"))
                    .sortOrder(rs.getInt("sort_order"))
                    .build(), table.getId());
            table.setFieldDefinitions(fields);
        }

        if (systemUserTableExists()) {
            tables.add(RelationTableDTO.builder()
                    .id(SYSTEM_USER_TABLE_ID)
                    .tableName(SYSTEM_USER_TABLE_NAME)
                    .displayName(SYSTEM_USER_DISPLAY_NAME)
                    .description("System user table")
                    .status(RelationTableStatus.DEPLOYED)
                    .enabled(true)
                    .portalVisible(false)
                    .currentVersion(1)
                    .fieldDefinitions(systemUserFields())
                    .build());
        }

        return tables;
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
        if (SYSTEM_USER_TABLE_ID.equals(tableId)) return SYSTEM_USER_DISPLAY_NAME;
        String sql = "SELECT display_name FROM rt_table_definitions WHERE id = ?";
        List<String> names = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("display_name"), tableId);
        return names.isEmpty() ? null : names.get(0);
    }

    private boolean systemUserTableExists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                Integer.class,
                SYSTEM_USER_TABLE_NAME);
        return count != null && count > 0;
    }

    private List<RelationFieldDTO> systemUserFields() {
        return List.of(
                systemUserField(1, "id", RelationDataType.VARCHAR, true, "User ID"),
                systemUserField(2, "username", RelationDataType.VARCHAR, false, "Username"),
                systemUserField(3, "display_name", RelationDataType.VARCHAR, false, "Display Name"),
                systemUserField(4, "full_name", RelationDataType.VARCHAR, false, "Full Name"),
                systemUserField(5, "email", RelationDataType.VARCHAR, false, "Email"),
                systemUserField(6, "employee_id", RelationDataType.VARCHAR, false, "Employee ID"),
                systemUserField(7, "status", RelationDataType.VARCHAR, false, "Status"),
                systemUserField(8, "language", RelationDataType.VARCHAR, false, "Language")
        );
    }

    private RelationFieldDTO systemUserField(int sortOrder, String fieldName, RelationDataType dataType,
            boolean primaryKey, String displayName) {
        return RelationFieldDTO.builder()
                .id((long) -sortOrder)
                .fieldName(fieldName)
                .dataType(dataType)
                .length(255)
                .nullable(!primaryKey)
                .isPrimaryKey(primaryKey)
                .displayName(displayName)
                .sortOrder(sortOrder)
                .build();
    }
}
