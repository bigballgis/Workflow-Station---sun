package com.developer.component.impl;

import com.developer.entity.UploadedFile;
import com.developer.exception.DeveloperBusinessException;
import com.developer.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileUploadComponentImplTest {

    @Mock
    private FileStorageService fileStorageService;

    private FileUploadComponentImpl component;

    @BeforeEach
    void setUp() {
        component = new FileUploadComponentImpl(fileStorageService);
        ReflectionTestUtils.setField(component, "baseUrl", "/api/v1/upload/files");
    }

    @Test
    void upload_shouldPersistBytesAndReturnCompatibleResponse() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invoice.pdf",
                "application/pdf",
                "hello".getBytes()
        );

        when(fileStorageService.store(anyString(), anyString(), anyString(), anyLong(), any(byte[].class)))
                .thenAnswer(invocation -> UploadedFile.builder()
                        .storedName(invocation.getArgument(0, String.class))
                        .originalName(invocation.getArgument(1, String.class))
                        .contentType(invocation.getArgument(2, String.class))
                        .fileSize(invocation.getArgument(3, Long.class))
                        .content(invocation.getArgument(4, byte[].class))
                        .build());

        var result = component.upload(file);

        ArgumentCaptor<String> storedNameCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileStorageService).store(
                storedNameCaptor.capture(),
                anyString(),
                anyString(),
                anyLong(),
                any(byte[].class)
        );

        String storedName = storedNameCaptor.getValue();
        assertThat(storedName).endsWith(".pdf");
        assertThat(result).containsEntry("id", storedName);
        assertThat(result).containsEntry("name", "invoice.pdf");
        assertThat(result).containsEntry("size", 5L);
        assertThat(result).containsEntry("type", "application/pdf");
        assertThat((String) result.get("url"))
                .isEqualTo("/api/v1/upload/files/" + storedName + "?originalName=invoice.pdf");
    }

    @Test
    void upload_shouldRejectEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> component.upload(file))
                .isInstanceOf(DeveloperBusinessException.class)
                .hasMessage("File must not be empty");
    }

    @Test
    void upload_shouldRejectOversizedFile() {
        byte[] content = new byte[(10 * 1024 * 1024) + 1];
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf", "application/pdf", content);

        assertThatThrownBy(() -> component.upload(file))
                .isInstanceOf(DeveloperBusinessException.class)
                .hasMessage("File size must not exceed 10MB");
    }

    @Test
    void upload_shouldRejectUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hi".getBytes());

        assertThatThrownBy(() -> component.upload(file))
                .isInstanceOf(DeveloperBusinessException.class)
                .hasMessageContaining("Unsupported file type");
    }
}
