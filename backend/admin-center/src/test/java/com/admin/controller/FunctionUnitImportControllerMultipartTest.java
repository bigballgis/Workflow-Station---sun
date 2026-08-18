package com.admin.controller;

import com.admin.component.DeploymentManagerComponent;
import com.admin.component.EmailConnectionSyncComponent;
import com.admin.component.EmailMonitorSyncComponent;
import com.admin.component.FunctionUnitManagerComponent;
import com.admin.component.ProcessDeploymentComponent;
import com.admin.dto.request.FunctionUnitImportRequest;
import com.admin.dto.response.FunctionUnitInfo;
import com.admin.dto.response.ImportResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.UserPrincipal;
import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Multipart ZIP import must hand the ZIP bytes (Base64) to {@code importFunctionPackage}.
 * Putting the extracted BPMN text in {@code fileContent} while {@code fileName} still ends in
 * {@code .zip} made parseBase64Zip fail and dropped relation-table computed fields.
 */
@ExtendWith(MockitoExtension.class)
class FunctionUnitImportControllerMultipartTest {

    @Mock private FunctionUnitManagerComponent functionUnitManager;
    @Mock private DeploymentManagerComponent deploymentManager;
    @Mock private ProcessDeploymentComponent processDeploymentComponent;
    @Mock private EmailConnectionSyncComponent emailConnectionSyncComponent;
    @Mock private EmailMonitorSyncComponent emailMonitorSyncComponent;
    @Mock private I18nService i18nService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        lenient().when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        UserPrincipal principal = UserPrincipal.builder()
                .userId("test-user-id")
                .username("test-user")
                .roles(Collections.emptyList())
                .permissions(Collections.emptyList())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList()));

        FunctionUnitImportController controller = new FunctionUnitImportController(
                functionUnitManager,
                deploymentManager,
                processDeploymentComponent,
                emailConnectionSyncComponent,
                emailMonitorSyncComponent,
                new ObjectMapper(),
                i18nService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void import_passesZipBytesNotBpmnText() throws Exception {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <process id="orders" name="Orders"/>
                </definitions>
                """;
        byte[] zipBytes = zip(
                "manifest.json",
                "{\"name\":\"Orders\",\"code\":\"orders\",\"version\":\"1.0.0\"}".getBytes(StandardCharsets.UTF_8),
                "process/process.bpmn",
                bpmn.getBytes(StandardCharsets.UTF_8),
                "relation-tables/relation_tables.json",
                """
                [{"tableName":"prices","fields":[{"fieldName":"amount","isComputed":true,\
                "computedField":{"source":"qty * price","scope":"row"}}]}]
                """.getBytes(StandardCharsets.UTF_8));

        when(functionUnitManager.importFunctionPackage(any(), eq("test-user-id")))
                .thenReturn(ImportResult.success(FunctionUnitInfo.builder()
                        .id("fu-1")
                        .name("Orders")
                        .version("1.0.0")
                        .build()));

        mockMvc.perform(multipart("/function-units-import/import")
                        .file(new MockMultipartFile(
                                "file", "orders.zip", MediaType.APPLICATION_OCTET_STREAM_VALUE, zipBytes)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.functionUnitId").value("fu-1"));

        ArgumentCaptor<FunctionUnitImportRequest> captor =
                ArgumentCaptor.forClass(FunctionUnitImportRequest.class);
        verify(functionUnitManager).importFunctionPackage(captor.capture(), eq("test-user-id"));
        FunctionUnitImportRequest sent = captor.getValue();
        assertThat(sent.getFileName()).isEqualTo("orders.zip");
        byte[] decoded = Base64.getDecoder().decode(sent.getFileContent());
        assertThat(decoded).startsWith(new byte[] {0x50, 0x4B, 0x03, 0x04});
        assertThat(new String(decoded, StandardCharsets.ISO_8859_1)).doesNotStartWith("<?xml");
        assertThat(unzippedEntry(decoded, "relation-tables/relation_tables.json"))
                .contains("isComputed")
                .contains("computedField");
        assertThat(unzippedEntry(decoded, "process/process.bpmn")).contains("<process");
    }

    private static String unzippedEntry(byte[] zipBytes, String name) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (name.equals(entry.getName())) {
                    return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("ZIP has no entry " + name);
    }

    private static byte[] zip(String n1, byte[] d1, String n2, byte[] d2, String n3, byte[] d3)
            throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            put(zos, n1, d1);
            put(zos, n2, d2);
            put(zos, n3, d3);
        }
        return baos.toByteArray();
    }

    private static void put(ZipOutputStream zos, String name, byte[] data) throws Exception {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(data);
        zos.closeEntry();
    }
}
