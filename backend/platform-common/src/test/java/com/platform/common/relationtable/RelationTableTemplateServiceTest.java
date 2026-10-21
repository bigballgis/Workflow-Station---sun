package com.platform.common.relationtable;

import com.platform.common.dto.RelationFieldDTO;
import com.platform.common.enums.RelationDataType;
import org.junit.jupiter.api.Test;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RelationTableTemplateServiceTest {

    private final RelationTableTemplateService svc = new RelationTableTemplateService();

    private RelationFieldDTO field(String name, RelationDataType type, Integer length, boolean nullable) {
        return RelationFieldDTO.builder().fieldName(name).dataType(type).length(length).nullable(nullable).build();
    }

    @Test
    void csvTemplate_hasSingleHeaderRowWithTypeHintInHeader_noSeparateHintRow() {
        var fields = List.of(field("name", RelationDataType.VARCHAR, 255, true));
        String csv = new String(svc.generateTemplate(fields, "csv"), StandardCharsets.UTF_8);
        String[] lines = csv.replace("\r\n", "\n").split("\n");

        // Header carries the type/length annotation...
        assertThat(lines[0]).isEqualTo("name (VARCHAR(255))");
        // ...and there is no legacy "#VARCHAR(255)" hint row.
        assertThat(csv).doesNotContain("#VARCHAR");
        boolean hasHintRow = java.util.Arrays.stream(lines).anyMatch(l -> l.startsWith("#"));
        assertThat(hasHintRow).isFalse();
    }

    @Test
    void requiredFieldHeaderMarksRequired() {
        var fields = List.of(field("code", RelationDataType.VARCHAR, 10, false));
        String csv = new String(svc.generateTemplate(fields, "csv"), StandardCharsets.UTF_8);
        assertThat(csv.split("\n")[0]).isEqualTo("code (VARCHAR(10) *required)");
    }

    @Test
    void parseImport_recoversBareFieldNameFromAnnotatedHeader() {
        // Simulate a user filling in the generated template (annotated header + one data row).
        String csv = "name (VARCHAR(255))\n333ee\n";
        List<Map<String, Object>> rows = svc.parseImport(csv.getBytes(StandardCharsets.UTF_8), "csv");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("name", "333ee");
    }

    @Test
    void parseImport_stillAcceptsPlainHeader() {
        String csv = "name\nhello\n";
        List<Map<String, Object>> rows = svc.parseImport(csv.getBytes(StandardCharsets.UTF_8), "csv");
        assertThat(rows.get(0)).containsEntry("name", "hello");
    }

    @Test
    void templates_describeDateFormatsAndSetExcelColumnStylesWithoutDataRows() throws Exception {
        var fields = List.of(field("effective_on", RelationDataType.DATE, null, true),
                field("recorded_at", RelationDataType.TIMESTAMP, null, true));
        String csv = new String(svc.generateTemplate(fields, "csv"), StandardCharsets.UTF_8);
        assertThat(csv).contains("effective_on (DATE yyyy-MM-dd)",
                "recorded_at (TIMESTAMP yyyy-MM-dd HH:mm:ss)");
        assertThat(csv.lines()).hasSize(1);
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(svc.generateTemplate(fields, "xlsx")))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(1);
            assertThat(sheet.getColumnStyle(0).getDataFormatString()).isEqualTo("yyyy-mm-dd");
            assertThat(sheet.getColumnStyle(1).getDataFormatString()).isEqualTo("yyyy-mm-dd hh:mm:ss");
        }
        var parsed = svc.parseImport((csv + "2025/2/24,2025-02-24 13:45:00\n")
                .getBytes(StandardCharsets.UTF_8), "csv");
        assertThat(RelationRowValidator.validateRows(parsed, fields).get(0).isValid()).isTrue();
    }

    @Test
    void nativeExcelDates_areValidatedByFieldTypeRegardlessOfNamesAndDisplayFormat() throws Exception {
        for (String dateName : List.of("effective_on", "renewal_day")) {
            var fields = List.of(field(dateName, RelationDataType.DATE, null, true),
                    field("recorded_at", RelationDataType.TIMESTAMP, null, true));
            for (String displayFormat : List.of("yyyy-mm-dd", "yyyy/m/d h:mm")) {
                var results = RelationRowValidator.validateRows(svc.parseImport(
                        nativeDateWorkbook(dateName, displayFormat, LocalDateTime.of(2025, 2, 24, 0, 0)),
                        "xlsx"), fields);
                assertThat(results).hasSize(1);
                assertThat(results.get(0).isValid()).as("%s: %s", dateName, displayFormat).isTrue();
                assertThat(results.get(0).getValues()).containsEntry(dateName, "2025-02-24")
                        .containsEntry("recorded_at", "2025-02-24T13:45");
            }
        }
    }

    @Test
    void nativeExcelDateWithNonzeroTime_isRejectedWithoutTruncation() throws Exception {
        var fields = List.of(field("effective_on", RelationDataType.DATE, null, true),
                field("recorded_at", RelationDataType.TIMESTAMP, null, true));
        var results = RelationRowValidator.validateRows(svc.parseImport(nativeDateWorkbook(
                "effective_on", "yyyy-mm-dd hh:mm:ss", LocalDateTime.of(2025, 2, 24, 13, 45)), "xlsx"), fields);
        assertThat(results.get(0).isValid()).isFalse();
        assertThat(results.get(0).getErrors()).hasSize(1);
        assertThat(results.get(0).getErrors().get(0).getField()).isEqualTo("effective_on");
        assertThat(results.get(0).getValues()).doesNotContainKey("effective_on")
                .containsEntry("recorded_at", "2025-02-24T13:45");
    }

    private byte[] nativeDateWorkbook(String dateName, String format, LocalDateTime date) throws Exception {
        try (var wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("Import");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue(dateName);
            header.createCell(1).setCellValue("recorded_at");
            CellStyle style = wb.createCellStyle();
            style.setDataFormat(wb.createDataFormat().getFormat(format));
            var row = sheet.createRow(1);
            var dateCell = row.createCell(0);
            dateCell.setCellValue(date);
            dateCell.setCellStyle(style);
            var timestampCell = row.createCell(1);
            timestampCell.setCellValue(LocalDateTime.of(2025, 2, 24, 13, 45));
            timestampCell.setCellStyle(style);
            wb.write(out);
            return out.toByteArray();
        }
    }
}
