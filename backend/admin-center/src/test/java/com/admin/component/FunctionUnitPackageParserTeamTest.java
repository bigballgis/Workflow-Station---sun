package com.admin.component;

import com.admin.service.FunctionUnitTeamService.PackageTeam;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** DW export packages name the unit's team by code and name (platform access model §8.1). */
class FunctionUnitPackageParserTeamTest {

    private final FunctionUnitPackageParser parser = new FunctionUnitPackageParser(new ObjectMapper());

    @Test
    void theManifestTeamIsRead() throws Exception {
        var parsed = parser.parseZipBytes(manifest("""
                {"code":"fu-x","name":"FU X","version":"1.0.2","team":{"code":"PAYMENT_OPS","name":"Payment Ops"}}
                """));

        assertThat(parsed.getTeam()).isEqualTo(new PackageTeam("PAYMENT_OPS", "Payment Ops"));
    }

    @Test
    void aPackageFromBeforeTeamsHasNone() throws Exception {
        assertThat(parser.parseZipBytes(manifest("{\"code\":\"fu-x\",\"name\":\"FU X\"}")).getTeam()).isNull();
        assertThat(parser.parseZipBytes(manifest("{\"code\":\"fu-x\",\"team\":{\"name\":\"No code\"}}")).getTeam())
                .isNull();
    }

    private static byte[] manifest(String json) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(json.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
