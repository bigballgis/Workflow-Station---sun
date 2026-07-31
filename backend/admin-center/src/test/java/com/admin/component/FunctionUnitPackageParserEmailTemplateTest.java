package com.admin.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Admin import must fail closed when email-templates/*.json is not valid JSON.
 */
class FunctionUnitPackageParserEmailTemplateTest {

    private final FunctionUnitPackageParser parser = new FunctionUnitPackageParser(new ObjectMapper());

    @Test
    void parseZipBytes_invalidEmailTemplateJson_throwsIoException() throws Exception {
        byte[] zip = zipWithEntry(
                "email-templates/template_1.json",
                "{not-valid-json".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> parser.parseZipBytes(zip))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("email-templates/template_1.json");
    }

    @Test
    void parseZipBytes_validEmailTemplate_addsEmailTemplateContent() throws Exception {
        String body = """
                {"templateId":12,"name":"Notice","subject":"Hi","bodyHtml":"<p>x</p>","enabled":true}
                """;
        byte[] zip = zipWithEntry(
                "email-templates/template_1.json",
                body.getBytes(StandardCharsets.UTF_8));

        FunctionUnitPackageParser.ParsedImportPackage parsed = parser.parseZipBytes(zip);

        List<FunctionUnitManagerComponent.ContentInfo> templates = parsed.getPackageContent().getContents()
                .stream()
                .filter(c -> c.getContentType() == com.admin.enums.ContentType.EMAIL_TEMPLATE)
                .toList();
        assertThat(templates).hasSize(1);
        assertThat(templates.get(0).getContentName()).isEqualTo("Notice");
        assertThat(templates.get(0).getSourceId()).isEqualTo("12");
        assertThat(templates.get(0).getContentData()).contains("bodyHtml");
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
