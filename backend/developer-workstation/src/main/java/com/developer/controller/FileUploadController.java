package com.developer.controller;

import com.developer.dto.ApiResponse;
import com.developer.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.net.URLEncoder;

/**
 * 文件上传控制器
 * 支持常见文件格式：jpg/png/pdf/docx/xlsx
 */
@RestController
@RequestMapping("/upload")
@Slf4j
@Tag(name = "File Upload", description = "File upload and download operations")
public class FileUploadController {

    private static final List<String> ALLOWED_EXTENSIONS =
            Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".pdf", ".doc", ".docx", ".xls", ".xlsx");

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10MB

    @Value("${file.upload.dir:uploads}")
    private String uploadDir;

    @Value("${file.upload.base-url:/api/v1/upload/files}")
    private String baseUrl;

    /**
     * 上传单个文件
     */
    @PostMapping
    @Operation(summary = "Upload file", description = "Supports jpg/png/pdf/docx/xlsx, max 10MB")
    public ResponseEntity<ApiResponse<Map<String, Object>>> upload(
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(errorResponse("FILE_EMPTY", "File must not be empty"));
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            return ResponseEntity.badRequest().body(errorResponse("FILE_TOO_LARGE", "File size must not exceed 10MB"));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            return ResponseEntity.badRequest().body(errorResponse("INVALID_FILENAME", "Invalid filename"));
        }

        String extension = getExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return ResponseEntity.badRequest().body(
                    errorResponse("UNSUPPORTED_TYPE", "Unsupported file type, allowed: " + String.join(", ", ALLOWED_EXTENSIONS)));
        }

        try {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            String storedName = UUID.randomUUID() + extension;
            Path targetPath = uploadPath.resolve(storedName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // Preserve original filename for UI display/download while keeping storage name opaque.
            String encodedOriginalName = URLEncoder.encode(originalFilename, StandardCharsets.UTF_8);
            String fileUrl = baseUrl + "/" + storedName + "?originalName=" + encodedOriginalName;

            Map<String, Object> result = new HashMap<>();
            result.put("id", storedName);
            result.put("name", originalFilename);
            result.put("url", fileUrl);
            result.put("size", file.getSize());
            result.put("type", file.getContentType());

            log.info("File uploaded: {} -> {}", originalFilename, storedName);
            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (IOException e) {
            log.error("File upload failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(errorResponse("UPLOAD_FAILED", "File upload failed, please retry"));
        }
    }

    /**
     * 访问已上传的文件（支持内联预览）
     */
    @GetMapping("/files/{filename}")
    @Operation(summary = "Get file", description = "Access uploaded file by filename, supports inline preview")
    public ResponseEntity<org.springframework.core.io.Resource> getFile(
            @PathVariable String filename) {

        try {
            Path basePath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path filePath = basePath.resolve(filename).normalize();

            // 防止路径遍历攻击
            if (!filePath.startsWith(basePath)) {
                return ResponseEntity.badRequest().build();
            }

            UrlResource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .header("Content-Type", contentType)
                    .header("Content-Disposition", "inline; filename=\"" + filename + "\"")
                    .body(resource);

        } catch (IOException e) {
            log.error("File retrieval failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 删除已上传的文件
     */
    @DeleteMapping("/files/{filename}")
    @Operation(summary = "Delete file", description = "Delete an uploaded file")
    public ResponseEntity<ApiResponse<Void>> deleteFile(@PathVariable String filename) {
        try {
            Path basePath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path filePath = basePath.resolve(filename).normalize();

            if (!filePath.startsWith(basePath)) {
                return ResponseEntity.badRequest().build();
            }

            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("File deleted: {}", filename);
                return ResponseEntity.ok(ApiResponse.success(null));
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (IOException e) {
            log.error("File deletion failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(errorResponse("DELETE_FAILED", "File deletion failed"));
        }
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex >= 0 ? filename.substring(dotIndex) : "";
    }

    private <T> ApiResponse<T> errorResponse(String code, String message) {
        return ApiResponse.error(ErrorResponse.builder()
                .code(code)
                .message(message)
                .timestamp(Instant.now())
                .build());
    }
}
