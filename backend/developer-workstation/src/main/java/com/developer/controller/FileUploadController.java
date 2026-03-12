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
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传控制器
 * 支持常见文件格式：jpg/png/pdf/docx/xlsx
 */
@RestController
@RequestMapping("/upload")
@Slf4j
@Tag(name = "文件上传", description = "文件上传、下载相关操作")
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
    @Operation(summary = "上传文件", description = "支持 jpg/png/pdf/docx/xlsx 格式，最大 10MB")
    public ResponseEntity<ApiResponse<Map<String, Object>>> upload(
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(errorResponse("FILE_EMPTY", "文件不能为空"));
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            return ResponseEntity.badRequest().body(errorResponse("FILE_TOO_LARGE", "文件大小不能超过 10MB"));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            return ResponseEntity.badRequest().body(errorResponse("INVALID_FILENAME", "文件名不合法"));
        }

        String extension = getExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return ResponseEntity.badRequest().body(
                    errorResponse("UNSUPPORTED_TYPE", "不支持的文件类型，仅允许：" + String.join(", ", ALLOWED_EXTENSIONS)));
        }

        try {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            String storedName = UUID.randomUUID() + extension;
            Path targetPath = uploadPath.resolve(storedName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            String fileUrl = baseUrl + "/" + storedName;

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
                    .body(errorResponse("UPLOAD_FAILED", "文件上传失败，请重试"));
        }
    }

    /**
     * 访问已上传的文件（支持内联预览）
     */
    @GetMapping("/files/{filename}")
    @Operation(summary = "获取文件", description = "通过文件名访问已上传的文件，支持图片内联预览")
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
    @Operation(summary = "删除文件", description = "删除已上传的文件")
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
                    .body(errorResponse("DELETE_FAILED", "文件删除失败"));
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
