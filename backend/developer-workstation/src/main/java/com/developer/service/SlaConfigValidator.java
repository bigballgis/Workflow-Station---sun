package com.developer.service;

import com.developer.dto.FieldDefinitionRequest;
import com.developer.dto.SlaConfig;
import com.developer.dto.TableDefinitionRequest;
import com.developer.enums.DataType;
import com.developer.enums.TableType;
import com.developer.exception.DeveloperBusinessException;
import com.platform.common.i18n.I18nService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Validates the SLA due date mapping of a table design against the incoming field list, which is the
 * authority on what the table will contain after the save.
 */
@Service
@RequiredArgsConstructor
public class SlaConfigValidator {

    private final I18nService i18nService;

    /**
     * Rejects a mapping the portal could not apply. For {@code SUBMITTED_AT} a stale
     * {@code startDateField} is dropped rather than rejected, since it is never read.
     */
    public void validate(TableDefinitionRequest request) {
        SlaConfig config = request.getSlaConfig();
        if (config == null) {
            return;
        }
        if (request.getTableType() != TableType.MAIN) {
            throw fail("TABLE_SLA_MAIN_ONLY", "table.sla.main_only");
        }
        if (config.getStartDateSource() == null) {
            throw fail("TABLE_SLA_START_SOURCE_REQUIRED", "table.sla.start_source_required");
        }
        List<FieldDefinitionRequest> fields = request.getFields() == null ? List.of() : request.getFields();

        FieldDefinitionRequest due = requireField(fields, config.getDueDateField());
        if (due.getDataType() != DataType.DATE) {
            throw fail("TABLE_SLA_DUE_TYPE", "table.sla.due_type", due.getFieldName());
        }
        if (Boolean.TRUE.equals(due.getIsComputed())) {
            throw fail("TABLE_SLA_DUE_COMPUTED", "table.sla.due_computed", due.getFieldName());
        }

        if (config.getStartDateSource() == SlaConfig.StartDateSource.SUBMITTED_AT) {
            config.setStartDateField(null);
            return;
        }
        FieldDefinitionRequest start = requireField(fields, config.getStartDateField());
        if (start.getDataType() != DataType.DATE && start.getDataType() != DataType.TIMESTAMP) {
            throw fail("TABLE_SLA_START_TYPE", "table.sla.start_type", start.getFieldName());
        }
        if (Objects.equals(start.getFieldName(), due.getFieldName())) {
            throw fail("TABLE_SLA_SAME_FIELD", "table.sla.same_field");
        }
    }

    private FieldDefinitionRequest requireField(List<FieldDefinitionRequest> fields, String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            throw fail("TABLE_SLA_FIELD_MISSING", "table.sla.field_missing", "");
        }
        return fields.stream()
                .filter(f -> f != null && fieldName.equals(f.getFieldName()))
                .findFirst()
                .orElseThrow(() -> fail("TABLE_SLA_FIELD_MISSING", "table.sla.field_missing", fieldName));
    }

    private DeveloperBusinessException fail(String code, String messageKey, Object... args) {
        return new DeveloperBusinessException(code, i18nService.getMessage(messageKey, args));
    }
}
