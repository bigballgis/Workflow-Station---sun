package com.developer.controller;

import com.developer.component.FileUploadComponent;
import com.developer.dto.ApiResponse;
import com.developer.dto.ErrorResponse;
import com.developer.entity.UploadedFile;
import com.developer.exception.DeveloperBusinessException;
import com.developer.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * File upload controller.
 * Supports common formats: jpg/png/pdf/docx/xlsx.
 */
@RestController
@RequestMapping("/upload")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "File Upload", description = "File upload and download operations")
public class FileUploadController {

    private final FileUploadComponent fileUploadComponent;

    /**
     * Upload a single file.
     */
    @PostMapping
    @Operation(summary = "Upload file", description = "Supports jpg/png/pdf/docx/xlsx, max 10MB")
    public ResponseEntity<ApiResponse<Map<String, Object>>> upload(
            @RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(ApiResponse.success(fileUploadComponent.upload(file)));
        } catch (DeveloperBusinessException e) {
            return ResponseEntity.badRequest().body(errorResponse(e.getErrorCode(), e.getMessage()));
        } catch (IOException e) {
            log.error("File upload failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(errorResponse("UPLOAD_FAILED", "File upload failed, please retry"));
        }
    }

    /**
     * Access an uploaded file (supports inline preview).
     */
    @GetMapping("/files/{filename}")
    @Operation(summary = "Get file", description = "Access uploaded file by filename, supports inline preview")
    public ResponseEntity<Resource> getFile(
            @PathVariable String filename) {

        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return ResponseEntity.badRequest().build();
        }

        try {
            UploadedFile file = fileUploadComponent.getFile(filename);
            String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

            return ResponseEntity.ok()
                    .header("Content-Type", contentType)
                    .header("Content-Disposition", "inline; filename=\"" + filename + "\"")
                    .body(new ByteArrayResource(file.getContent()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Delete an uploaded file.
     */
    @DeleteMapping("/files/{filename}")
    @Operation(summary = "Delete file", description = "Delete an uploaded file")
    public ResponseEntity<ApiResponse<Void>> deleteFile(@PathVariable String filename) {
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return ResponseEntity.badRequest().build();
        }
        try {
            fileUploadComponent.deleteFile(filename);
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private <T> ApiResponse<T> errorResponse(String code, String message) {
        return ApiResponse.error(ErrorResponse.builder()
                .code(code)
                .message(message)
                .timestamp(Instant.now())
                .build());
    }
}
