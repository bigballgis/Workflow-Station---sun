package com.admin.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Admin ZIP import must parse {@code relation-tables/relation_tables.json} (including
 * computed-field formulas) and fail closed when that file is not valid JSON.
 */
class FunctionUnitPackageParserRelationTableTest {

    private final FunctionUnitPackageParser parser = new FunctionUnitPackageParser(new ObjectMapper());

    @Test
    void parseZipBytes_invalidRelationTablesJson_throwsIoException() throws Exception {
        byte[] zip = zipWithEntry(
                "relation-tables/relation_tables.json",
                "{not-valid-json".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> parser.parseZipBytes(zip))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("relation-tables/relation_tables.json");
    }

    @Test
    void parseZipBytes_validRelationTables_keepsComputedFieldMaps() throws Exception {
        String body = """
                [{"tableName":"prices","fields":[{"fieldName":"amount","dataType":"DECIMAL",\
                "isComputed":true,"computedField":{"source":"qty * price","scope":"row"}}]}]
                """;
        byte[] zip = zipWithEntry(
                "relation-tables/relation_tables.json",
                body.getBytes(StandardCharsets.UTF_8));

        FunctionUnitPackageParser.ParsedImportPackage parsed = parser.parseZipBytes(zip);

        assertThat(parsed.getRelationTables()).hasSize(1);
        assertThat(parsed.getRelationTables().get(0).get("tableName")).isEqualTo("prices");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields =
                (List<Map<String, Object>>) parsed.getRelationTables().get(0).get("fields");
        assertThat(fields.get(0).get("isComputed")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> formula = (Map<String, Object>) fields.get(0).get("computedField");
        assertThat(formula.get("source")).isEqualTo("qty * price");
    }

    private static byte[] zipWithEntry(String name, byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(name));
            zos.write(data);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
