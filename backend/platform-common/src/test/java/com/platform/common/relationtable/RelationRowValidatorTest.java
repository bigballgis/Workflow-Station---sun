package com.platform.common.relationtable;

import com.platform.common.dto.RelationFieldDTO;
import com.platform.common.enums.RelationDataType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RelationRowValidatorTest {

    private RelationFieldDTO field(String name, RelationDataType type, boolean nullable) {
        return RelationFieldDTO.builder().fieldName(name).dataType(type).nullable(nullable).build();
    }

    @Test
    void requiredFieldEmpty_isError() {
        var fields = List.of(field("name", RelationDataType.VARCHAR, false));
        var r = RelationRowValidator.validateRow(1, Map.of("name", ""), fields);
        assertThat(r.isValid()).isFalse();
        assertThat(r.getErrors()).hasSize(1);
        assertThat(r.getErrors().get(0).getField()).isEqualTo("name");
    }

    @Test
    void optionalEmpty_isValidAndUnset() {
        var fields = List.of(field("note", RelationDataType.VARCHAR, true));
        var r = RelationRowValidator.validateRow(1, Map.of("note", ""), fields);
        assertThat(r.isValid()).isTrue();
        assertThat(r.getValues()).doesNotContainKey("note");
    }

    @Test
    void varcharLengthExceeded_isError() {
        var f = RelationFieldDTO.builder().fieldName("code").dataType(RelationDataType.VARCHAR)
                .nullable(true).length(3).build();
        var r = RelationRowValidator.validateRow(1, Map.of("code", "ABCD"), List.of(f));
        assertThat(r.isValid()).isFalse();
        assertThat(r.getErrors().get(0).getMessage()).contains("max length 3");
    }

    @Test
    void integerCoercion_validAndInvalid() {
        var f = field("qty", RelationDataType.INTEGER, true);
        assertThat(RelationRowValidator.validateRow(1, Map.of("qty", "42"), List.of(f)).getValues())
                .containsEntry("qty", 42L);
        assertThat(RelationRowValidator.validateRow(1, Map.of("qty", "x"), List.of(f)).isValid()).isFalse();
    }

    @Test
    void decimalScaleExceeded_isError() {
        var f = RelationFieldDTO.builder().fieldName("amt").dataType(RelationDataType.DECIMAL)
                .nullable(true).scale(2).build();
        var ok = RelationRowValidator.validateRow(1, Map.of("amt", "10.50"), List.of(f));
        assertThat(ok.isValid()).isTrue();
        var bad = RelationRowValidator.validateRow(1, Map.of("amt", "10.555"), List.of(f));
        assertThat(bad.isValid()).isFalse();
    }

    @Test
    void booleanParsing() {
        var f = field("flag", RelationDataType.BOOLEAN, true);
        assertThat(RelationRowValidator.validateRow(1, Map.of("flag", "yes"), List.of(f)).getValues())
                .containsEntry("flag", Boolean.TRUE);
        assertThat(RelationRowValidator.validateRow(1, Map.of("flag", "maybe"), List.of(f)).isValid()).isFalse();
    }

    @Test
    void dateParsing() {
        var f = field("d", RelationDataType.DATE, true);
        assertThat(RelationRowValidator.validateRow(1, Map.of("d", "2026-06-27"), List.of(f)).isValid()).isTrue();
        assertThat(RelationRowValidator.validateRow(1, Map.of("d", "27/06/2026"), List.of(f)).isValid()).isFalse();
    }

    @Test
    void dateImport_normalizesUnambiguousYearFirstDatesAndMidnight() {
        for (String name : List.of("effective_on", "renewal_day")) {
            var fields = List.of(field(name, RelationDataType.DATE, true));
            for (String input : List.of("2025-02-24", "2025/2/24", "2025/02/24",
                    "2025-02-24T00:00", "2025-02-24T00:00:00")) {
                var result = RelationRowValidator.validateRow(1, Map.of(name, input), fields);
                assertThat(result.isValid()).as("%s: %s", name, input).isTrue();
                assertThat(result.getValues()).containsEntry(name, "2025-02-24");
            }
        }
    }

    @Test
    void dateImport_rejectsInvalidDatesAmbiguousOrderAndTimeLoss() {
        var fields = List.of(field("effective_on", RelationDataType.DATE, true));
        for (String input : List.of("2025-02-30", "2025-02-29", "2025/2/30",
                "02/03/2025", "2025-13-01", "2025-02-24T13:45", "2025-02-24T00:00:00.001",
                "2025-02-30T00:00", "2025-02-24T00:00:00+08:00")) {
            var result = RelationRowValidator.validateRow(1, Map.of("effective_on", input), fields);
            assertThat(result.isValid()).as(input).isFalse();
            assertThat(result.getValues()).doesNotContainKey("effective_on");
            assertThat(result.getErrors()).hasSize(1);
        }
        assertThat(RelationRowValidator.validateRow(1, Map.of("effective_on", "2024/2/29"), fields)
                .getValues()).containsEntry("effective_on", "2024-02-29");
    }

    @Test
    void timestampImport_acceptsExcelMinutePrecisionAndKeepsFractionalSeconds() {
        var fields = List.of(field("recorded_at", RelationDataType.TIMESTAMP, true));
        for (String input : List.of("2025-02-24 13:45:00", "2025-02-24 13:45",
                "2025-02-24T13:45:00", "2025-02-24T13:45")) {
            var result = RelationRowValidator.validateRow(1, Map.of("recorded_at", input), fields);
            assertThat(result.isValid()).as(input).isTrue();
            assertThat(result.getValues()).containsEntry("recorded_at", "2025-02-24T13:45");
        }
        var fractional = RelationRowValidator.validateRow(1,
                Map.of("recorded_at", "2025-02-24T13:45:12.123"), fields);
        assertThat(fractional.isValid()).isTrue();
        assertThat(fractional.getValues()).containsEntry("recorded_at", "2025-02-24T13:45:12.123");
    }

    @Test
    void timestampImport_rejectsInvalidDatesAndUnsupportedZones() {
        var fields = List.of(field("recorded_at", RelationDataType.TIMESTAMP, true));
        for (String input : List.of("2025-02-30 13:45:00", "2025-02-29T13:45:12",
                "2025-02-24T24:00", "2025-02-24T13:45:00Z", "2025-02-24T13:45:00+08:00")) {
            assertThat(RelationRowValidator.validateRow(1, Map.of("recorded_at", input), fields).isValid())
                    .as(input).isFalse();
        }
    }

    @Test
    void manualPrimaryKeyRequired() {
        var pk = RelationFieldDTO.builder().fieldName("id").dataType(RelationDataType.VARCHAR)
                .nullable(true).isPrimaryKey(true)
                .pkGeneration(Map.of("strategy", "manual")).build();
        var r = RelationRowValidator.validateRow(1, Map.of("id", ""), List.of(pk));
        assertThat(r.isValid()).isFalse();
    }

    @Test
    void autoPrimaryKeyNotRequiredOnImport() {
        // uuid / sequence / unspecified strategy => generated server-side, not required from file
        var uuidPk = RelationFieldDTO.builder().fieldName("id").dataType(RelationDataType.VARCHAR)
                .nullable(true).isPrimaryKey(true).pkGeneration(Map.of("strategy", "uuid")).build();
        assertThat(RelationRowValidator.validateRow(1, Map.of("id", ""), List.of(uuidPk)).isValid()).isTrue();

        var defaultPk = RelationFieldDTO.builder().fieldName("id").dataType(RelationDataType.VARCHAR)
                .nullable(true).isPrimaryKey(true).build();
        assertThat(RelationRowValidator.validateRow(1, Map.of("id", ""), List.of(defaultPk)).isValid()).isTrue();
    }

    @Test
    void autoPrimaryKeyExcludedFromImportableAndIgnoredWhenFilled() {
        // An auto-generated PK must not appear in the template...
        var autoPk = RelationFieldDTO.builder().fieldName("id").dataType(RelationDataType.INTEGER)
                .nullable(true).isPrimaryKey(true).pkGeneration(Map.of("strategy", "prefixedSequence")).build();
        var name = field("name", RelationDataType.VARCHAR, true);
        assertThat(RelationRowValidator.importableFieldNames(List.of(autoPk, name)))
                .containsExactly("name");

        // ...and a stale file that still carries a (non-integer) value for it must NOT fail validation.
        var r = RelationRowValidator.validateRow(1, Map.of("id", "dd", "name", "x"), List.of(autoPk, name));
        assertThat(r.isValid()).isTrue();
        assertThat(r.getValues()).doesNotContainKey("id").containsEntry("name", "x");
    }

    @Test
    void manualPrimaryKeyStillIncludedInImportable() {
        var manualPk = RelationFieldDTO.builder().fieldName("id").dataType(RelationDataType.VARCHAR)
                .nullable(true).isPrimaryKey(true).pkGeneration(Map.of("strategy", "manual")).build();
        assertThat(RelationRowValidator.importableFieldNames(List.of(manualPk))).containsExactly("id");
    }

    @Test
    void systemFieldsExcludedFromImportable() {
        var fields = List.of(
                field("name", RelationDataType.VARCHAR, true),
                field("created_at", RelationDataType.TIMESTAMP, true),
                field("status", RelationDataType.VARCHAR, true));
        assertThat(RelationRowValidator.importableFieldNames(fields)).containsExactly("name");
    }

    @Test
    void unknownColumn_isError() {
        var fields = List.of(field("name", RelationDataType.VARCHAR, true));
        var r = RelationRowValidator.validateRow(1, Map.of("nope", "x"), fields);
        assertThat(r.isValid()).isFalse();
        assertThat(r.getErrors().get(0).getField()).isEqualTo("nope");
    }
}
