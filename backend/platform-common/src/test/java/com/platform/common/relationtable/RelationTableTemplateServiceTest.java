package com.platform.common.relationtable;

import com.platform.common.dto.RelationFieldDTO;
import com.platform.common.enums.RelationDataType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
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
}
