package com.workflow.service;

import com.platform.common.i18n.I18nService;
import com.workflow.client.DeveloperWorkstationFileClient;
import org.flowable.engine.delegate.BpmnError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailAttachmentResolverTest {

    @Mock
    private DeveloperWorkstationFileClient fileClient;

    @Mock
    private I18nService i18nService;

    private EmailAttachmentResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new EmailAttachmentResolver(fileClient, i18nService);
    }

    @Test
    void resolve_mainFileField_downloadsAndEncodes() {
        String url = "/api/v1/upload/files/abc.pdf?originalName=report.pdf";
        when(fileClient.downloadByStoredUrl(url)).thenReturn(Optional.of(
                new DeveloperWorkstationFileClient.DownloadedFile(
                        "report.pdf", "hello".getBytes(StandardCharsets.UTF_8))));

        List<EmailSendOptions.EmailAttachmentPart> parts = resolver.resolve(
                "[{\"source\":\"main\",\"fieldName\":\"invoice_file\"}]",
                Map.of("invoice_file", url));

        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).name()).isEqualTo("report.pdf");
        assertThat(parts.get(0).content())
                .isEqualTo(Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void resolve_lookupTargetFile_readsEmbeddedRow() {
        String url = "/api/v1/upload/files/att-1.bin?originalName=a.bin";
        when(fileClient.downloadByStoredUrl(url)).thenReturn(Optional.of(
                new DeveloperWorkstationFileClient.DownloadedFile(
                        "a.bin", new byte[] {1, 2, 3})));

        Map<String, Object> customer = Map.of(
                "id", "c1",
                "contract_file", url);
        List<EmailSendOptions.EmailAttachmentPart> parts = resolver.resolve(
                "[{\"source\":\"lookup\",\"lookupField\":\"customer\",\"targetField\":\"contract_file\"}]",
                Map.of("customer", customer));

        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).name()).isEqualTo("a.bin");
    }

    @Test
    void resolve_ignoresLegacyNameContent() {
        List<EmailSendOptions.EmailAttachmentPart> parts = resolver.resolve(
                "[{\"name\":\"old.pdf\",\"content\":\"AAAA\"}]",
                Map.of());

        assertThat(parts).isEmpty();
    }

    @Test
    void resolve_skipsWhenFieldEmpty() {
        List<EmailSendOptions.EmailAttachmentPart> parts = resolver.resolve(
                "[{\"source\":\"main\",\"fieldName\":\"invoice_file\"}]",
                Map.of());

        assertThat(parts).isEmpty();
    }

    @Test
    void resolve_subTableFileField_readsRowsFromSubTables() {
        String url = "/api/v1/upload/files/sub-1.pdf?originalName=row.pdf";
        when(fileClient.downloadByStoredUrl(url)).thenReturn(Optional.of(
                new DeveloperWorkstationFileClient.DownloadedFile(
                        "row.pdf", "sub".getBytes(StandardCharsets.UTF_8))));

        Map<String, Object> variables = Map.of(
                "__subTables__", Map.of(
                        "271", List.of(Map.of("file", url))));

        List<EmailSendOptions.EmailAttachmentPart> parts = resolver.resolve(
                "[{\"source\":\"sub\",\"bindingId\":271,\"fieldName\":\"file\"}]",
                variables);

        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).name()).isEqualTo("row.pdf");
    }

    @Test
    void resolve_downloadFailure_throwsBpmnError() {
        String url = "/api/v1/upload/files/missing.pdf?originalName=missing.pdf";
        when(fileClient.downloadByStoredUrl(url)).thenReturn(Optional.empty());
        when(i18nService.getMessage("email.send_task.attachment_download_failed"))
                .thenReturn("attachment download failed");

        assertThatThrownBy(() -> resolver.resolve(
                "[{\"source\":\"main\",\"fieldName\":\"invoice_file\"}]",
                Map.of("invoice_file", url)))
                .isInstanceOf(BpmnError.class)
                .extracting(ex -> ((BpmnError) ex).getErrorCode())
                .isEqualTo("EMAIL_ATTACHMENT_FAILED");
    }

    @Test
    void resolve_emptyContent_throwsBpmnError() {
        String url = "/api/v1/upload/files/empty.pdf?originalName=empty.pdf";
        when(fileClient.downloadByStoredUrl(url)).thenReturn(Optional.of(
                new DeveloperWorkstationFileClient.DownloadedFile("empty.pdf", new byte[0])));
        when(i18nService.getMessage("email.send_task.attachment_download_failed"))
                .thenReturn("attachment download failed");

        assertThatThrownBy(() -> resolver.resolve(
                "[{\"source\":\"main\",\"fieldName\":\"invoice_file\"}]",
                Map.of("invoice_file", url)))
                .isInstanceOf(BpmnError.class)
                .extracting(ex -> ((BpmnError) ex).getErrorCode())
                .isEqualTo("EMAIL_ATTACHMENT_FAILED");
    }
}
