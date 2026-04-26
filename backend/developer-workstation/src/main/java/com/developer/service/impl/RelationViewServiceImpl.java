package com.developer.service.impl;

import com.developer.entity.RelationViewConfig;
import com.developer.entity.RelationViewField;
import com.developer.repository.RelationViewConfigRepository;
import com.developer.repository.RelationViewFieldRepository;
import com.developer.service.RelationViewService;
import com.platform.common.dto.RelationFieldDTO;
import com.platform.common.enums.RelationDataType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Relation Table View 配置服务实现
 */
@Service
@RequiredArgsConstructor
public class RelationViewServiceImpl implements RelationViewService {

    private final RelationViewConfigRepository viewConfigRepository;
    private final RelationViewFieldRepository viewFieldRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public RelationViewConfig getViewConfig(Long bindingId) {
        return viewConfigRepository.findByBindingId(bindingId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "View config not found for binding: " + bindingId));
    }

    @Override
    @Transactional
    public RelationViewConfig saveViewConfig(Long bindingId, List<ViewFieldDTO> fields) {
        RelationViewConfig config = viewConfigRepository.findByBindingId(bindingId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "View config not found for binding: " + bindingId));

        // Clear existing fields and replace
        config.getViewFields().clear();

        for (ViewFieldDTO dto : fields) {
            RelationViewField field = RelationViewField.builder()
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
    @Transactional(readOnly = true)
    public List<RelationFieldDTO> getAvailableFields(Long tableId) {
        if (RelationTableBindingServiceImpl.SYSTEM_USER_TABLE_ID.equals(tableId)) {
            return systemUserFields();
        }
        String sql = "SELECT id, field_name, data_type, length, precision_value, scale, "
                + "nullable, is_primary_key, default_value, comment, sort_order "
                + "FROM rt_field_definitions WHERE table_id = ? ORDER BY sort_order ASC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> RelationFieldDTO.builder()
                .id(rs.getLong("id"))
                .fieldName(rs.getString("field_name"))
                .dataType(RelationDataType.valueOf(rs.getString("data_type")))
                .length(rs.getObject("length", Integer.class))
                .precision(rs.getObject("precision_value", Integer.class))
                .scale(rs.getObject("scale", Integer.class))
                .nullable(rs.getBoolean("nullable"))
                .isPrimaryKey(rs.getBoolean("is_primary_key"))
                .defaultValue(rs.getString("default_value"))
                .comment(rs.getString("comment"))
                .sortOrder(rs.getInt("sort_order"))
                .build(), tableId);
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
            boolean primaryKey, String comment) {
        return RelationFieldDTO.builder()
                .id((long) -sortOrder)
                .fieldName(fieldName)
                .dataType(dataType)
                .length(255)
                .nullable(!primaryKey)
                .isPrimaryKey(primaryKey)
                .comment(comment)
                .sortOrder(sortOrder)
                .build();
    }
}
