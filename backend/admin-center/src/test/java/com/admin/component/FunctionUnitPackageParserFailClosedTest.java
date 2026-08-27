package com.admin.component;

import com.admin.enums.ContentType;
import com.admin.exception.AdminBusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FunctionUnitPackageParserFailClosedTest {

    private final FunctionUnitPackageParser parser = new FunctionUnitPackageParser(new ObjectMapper());

    @Test
    void invalidFormJson_throws() throws Exception {
        byte[] zip = zipWithEntry("forms/form_1.json", "{not-json".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> parser.parseZipBytes(zip))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("forms/form_1.json");
    }

    @Test
    void decisionDmn_isCatalogued() throws Exception {
        byte[] zip = zipWithEntry("decisions/decision_1.dmn", "<definitions/>".getBytes(StandardCharsets.UTF_8));
        FunctionUnitPackageParser.ParsedImportPackage parsed = parser.parseZipBytes(zip);
        assertThat(parsed.getPackageContent().getContents())
                .anyMatch(c -> c.getContentType() == ContentType.DECISION
                        && c.getContentName().equals("decision_1.dmn"));
    }

    @Test
    void zipSlipPath_rejected() throws Exception {
        byte[] zip = zipWithEntry("../secret.json", "{}".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> parser.parseZipBytes(zip))
                .isInstanceOf(AdminBusinessException.class)
                .extracting(ex -> ((AdminBusinessException) ex).getErrorCode())
                .isEqualTo("FU_IMPORT_INVALID_PACKAGE");
    }

    @Test
    void iconScript_stripped() throws Exception {
        String manifest = """
                {"code":"x","name":"X","icon":{"svgContent":"<svg><script>alert(1)</script></svg>"}}
                """;
        byte[] zip = zipWithEntry("manifest.json", manifest.getBytes(StandardCharsets.UTF_8));
        FunctionUnitPackageParser.ParsedImportPackage parsed = parser.parseZipBytes(zip);
        assertThat(parsed.getIconSvg()).doesNotContain("script").contains("svg");
    }

    @Test
    void invalidTableJson_throws() throws Exception {
        byte[] zip = zipWithEntry("tables/table_0.json", "{not-json".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> parser.parseZipBytes(zip))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("tables/table_0.json");
    }

    @Test
    void invalidViewJson_throws() throws Exception {
        byte[] zip = zipWithEntry("views/main_table_views.json", "{not-json".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> parser.parseZipBytes(zip))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("views/main_table_views.json");
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
