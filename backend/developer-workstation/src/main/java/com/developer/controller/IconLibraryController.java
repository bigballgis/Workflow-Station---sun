package com.developer.controller;

import com.developer.component.IconLibraryComponent;
import com.developer.dto.ApiResponse;
import com.developer.entity.Icon;
import com.developer.enums.IconCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 图标库控制器
 */
@RestController
@RequestMapping("/api/v1/icons")
@RequiredArgsConstructor
@Tag(name = "图标库", description = "图标管理相关操作")
public class IconLibraryController {
    
    private final IconLibraryComponent iconLibraryComponent;
    
    @GetMapping
    @Operation(summary = "分页查询图标")
    public ResponseEntity<ApiResponse<Page<Icon>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) IconCategory category,
            Pageable pageable) {
        Page<Icon> result = iconLibraryComponent.search(keyword, category, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @GetMapping("/categories")
    @Operation(summary = "获取所有图标分类")
    public ResponseEntity<ApiResponse<List<IconCategory>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.success(List.of(IconCategory.values())));
    }
    
    @PostMapping
    @Operation(summary = "上传图标")
    public ResponseEntity<ApiResponse<Icon>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam String name,
            @RequestParam IconCategory category,
            @RequestParam(required = false) String tags) {
        Icon result = iconLibraryComponent.upload(file, name, category, tags);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "删除图标")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        iconLibraryComponent.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "获取图标详情")
    public ResponseEntity<ApiResponse<Icon>> getById(@PathVariable Long id) {
        Icon result = iconLibraryComponent.getById(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @GetMapping("/{id}/usage")
    @Operation(summary = "检查图标使用情况")
    public ResponseEntity<ApiResponse<Boolean>> checkUsage(@PathVariable Long id) {
        boolean inUse = iconLibraryComponent.isIconInUse(id);
        return ResponseEntity.ok(ApiResponse.success(inUse));
    }
}
