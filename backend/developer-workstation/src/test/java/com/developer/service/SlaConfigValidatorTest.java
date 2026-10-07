package com.developer.service;

import com.developer.dto.FieldDefinitionRequest;
import com.developer.dto.SlaConfig;
import com.developer.dto.TableDefinitionRequest;
import com.developer.enums.DataType;
import com.developer.enums.TableType;
import com.developer.exception.DeveloperBusinessException;
import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class SlaConfigValidatorTest {

    private final SlaConfigValidator validator = new SlaConfigValidator(mock(I18nService.class));

    @Test
    void acceptsFieldSourcedMappingOnMainTable() {
        assertDoesNotThrow(() -> validator.validate(request(TableType.MAIN,
                fieldConfig("received", "due"), field("received", DataType.TIMESTAMP), field("due", DataType.DATE))));
    }

    @Test
    void nullConfigIsAlwaysAccepted() {
        assertDoesNotThrow(() -> validator.validate(request(TableType.SUB, null)));
    }

    @Test
    void submittedAtDropsStaleStartField() {
        SlaConfig config = SlaConfig.builder()
                .startDateSource(SlaConfig.StartDateSource.SUBMITTED_AT)
                .startDateField("gone")
                .dueDateField("due")
                .build();
        validator.validate(request(TableType.MAIN, config, field("due", DataType.DATE)));
        assertNull(config.getStartDateField());
    }

    @Test
    void rejectsNonMainTable() {
        assertCode("TABLE_SLA_MAIN_ONLY", request(TableType.SUB,
                fieldConfig("received", "due"), field("received", DataType.DATE), field("due", DataType.DATE)));
    }

    @Test
    void rejectsMissingStartSource() {
        SlaConfig config = SlaConfig.builder().dueDateField("due").build();
        assertCode("TABLE_SLA_START_SOURCE_REQUIRED", request(TableType.MAIN, config, field("due", DataType.DATE)));
    }

    @Test
    void rejectsDueFieldNotOnTable() {
        assertCode("TABLE_SLA_FIELD_MISSING", request(TableType.MAIN,
                fieldConfig("received", "due"), field("received", DataType.DATE)));
    }

    @Test
    void rejectsTimestampDueField() {
        assertCode("TABLE_SLA_DUE_TYPE", request(TableType.MAIN,
                fieldConfig("received", "due"), field("received", DataType.DATE), field("due", DataType.TIMESTAMP)));
    }

    @Test
    void rejectsComputedDueField() {
        FieldDefinitionRequest due = field("due", DataType.DATE);
        due.setIsComputed(true);
        assertCode("TABLE_SLA_DUE_COMPUTED", request(TableType.MAIN,
                fieldConfig("received", "due"), field("received", DataType.DATE), due));
    }

    @Test
    void rejectsNonDateStartField() {
        assertCode("TABLE_SLA_START_TYPE", request(TableType.MAIN,
                fieldConfig("received", "due"), field("received", DataType.VARCHAR), field("due", DataType.DATE)));
    }

    @Test
    void rejectsSameStartAndDueField() {
        assertCode("TABLE_SLA_DUE_TYPE", request(TableType.MAIN,
                fieldConfig("due", "due"), field("due", DataType.TIMESTAMP)));
        assertCode("TABLE_SLA_SAME_FIELD", request(TableType.MAIN,
                fieldConfig("due", "due"), field("due", DataType.DATE)));
    }

    private void assertCode(String code, TableDefinitionRequest request) {
        DeveloperBusinessException ex = assertThrows(DeveloperBusinessException.class,
                () -> validator.validate(request));
        assertEquals(code, ex.getErrorCode());
    }

    private static SlaConfig fieldConfig(String start, String due) {
        return SlaConfig.builder()
                .startDateSource(SlaConfig.StartDateSource.FIELD)
                .startDateField(start)
                .dueDateField(due)
                .build();
    }

    private static FieldDefinitionRequest field(String name, DataType type) {
        FieldDefinitionRequest field = new FieldDefinitionRequest();
        field.setFieldName(name);
        field.setDataType(type);
        return field;
    }

    private static TableDefinitionRequest request(TableType type, SlaConfig config, FieldDefinitionRequest... fields) {
        TableDefinitionRequest request = new TableDefinitionRequest();
        request.setTableName("t");
        request.setTableType(type);
        request.setSlaConfig(config);
        request.setFields(List.of(fields));
        return request;
    }
}
