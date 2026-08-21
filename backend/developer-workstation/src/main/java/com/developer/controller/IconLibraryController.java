package com.developer.controller;

import com.developer.component.IconLibraryComponent;
import com.developer.security.RequireDeveloperPermission;
import com.platform.common.dto.ApiResponse;
import com.developer.dto.IconDTO;
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
@RequestMapping("/icons")
@RequiredArgsConstructor
@Tag(name = "Icon Library", description = "Icon management operations")
public class IconLibraryController {
    
    private final IconLibraryComponent iconLibraryComponent;
    
    @GetMapping
    @Operation(summary = "List icons (paginated)")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<Page<IconDTO>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) IconCategory category,
            @RequestParam(required = false) String tag,
            Pageable pageable) {
        Page<Icon> result = iconLibraryComponent.search(keyword, category, tag, pageable);
        Page<IconDTO> dtoPage = result.map(IconDTO::fromEntity);
        return ResponseEntity.ok(ApiResponse.success(dtoPage));
    }
    
    @GetMapping("/tags")
    @Operation(summary = "Get all tags")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<String>>> getTags() {
        return ResponseEntity.ok(ApiResponse.success(iconLibraryComponent.getAllTags()));
    }
    
    @GetMapping("/categories")
    @Operation(summary = "Get all icon categories")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<IconCategory>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.success(List.of(IconCategory.values())));
    }
    
    @PostMapping
    @Operation(summary = "Upload icon")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<IconDTO>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam String name,
            @RequestParam IconCategory category,
            @RequestParam(required = false) String description) {
        Icon result = iconLibraryComponent.upload(file, name, category, description);
        return ResponseEntity.ok(ApiResponse.success(IconDTO.fromEntity(result)));
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete icon")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        iconLibraryComponent.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get icon details")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<IconDTO>> getById(@PathVariable Long id) {
        Icon result = iconLibraryComponent.getById(id);
        return ResponseEntity.ok(ApiResponse.success(IconDTO.fromEntity(result)));
    }
    
    @GetMapping("/{id}/usage")
    @Operation(summary = "Check icon usage")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<Boolean>> checkUsage(@PathVariable Long id) {
        boolean inUse = iconLibraryComponent.isIconInUse(id);
        return ResponseEntity.ok(ApiResponse.success(inUse));
    }
}
