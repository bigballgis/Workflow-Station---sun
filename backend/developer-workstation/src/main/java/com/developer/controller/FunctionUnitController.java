package com.developer.controller;

import com.developer.component.FunctionUnitComponent;
import com.platform.common.dto.ApiResponse;
import com.developer.dto.DevGroupAssignmentRequest;
import com.developer.dto.FunctionUnitRequest;
import com.developer.dto.FunctionUnitResponse;
import com.developer.dto.ValidationResult;
import com.developer.dto.VersionResponse;
import com.developer.entity.FunctionUnit;
import com.developer.security.RequireDeveloperPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** REST controller for Function Unit operations. */
@RestController
@RequestMapping("/function-units")
@Slf4j
@Tag(name = "Function Unit Management", description = "Function Unit CRUD, publish, clone operations")
public class FunctionUnitController extends BaseController {

    private final FunctionUnitComponent functionUnitComponent;
    
    public FunctionUnitController(FunctionUnitComponent functionUnitComponent) {
        this.functionUnitComponent = functionUnitComponent;
    }
    
    @PostMapping
    @Operation(summary = "Create function unit")
    @RequireDeveloperPermission("FUNCTION_UNIT_CREATE")
    public ResponseEntity<ApiResponse<FunctionUnit>> create(@Valid @RequestBody FunctionUnitRequest request) {
        return handleRequest(() -> functionUnitComponent.create(request));
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update function unit")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<FunctionUnit>> update(
            @PathVariable Long id, 
            @Valid @RequestBody FunctionUnitRequest request) {
        return handleRequest(() -> functionUnitComponent.update(id, request));
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Archive or permanently delete function unit",
            description = "Non-archived units are marked ARCHIVED; archived units are permanently deleted")
    @RequireDeveloperPermission("FUNCTION_UNIT_DELETE")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        return handleRequest(() -> {
            functionUnitComponent.delete(id);
            return null;
        });
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get function unit details")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<FunctionUnitResponse>> getById(@PathVariable Long id) {
        return handleRequest(() -> functionUnitComponent.getByIdAsResponse(id));
    }
    
    @GetMapping
    @Operation(summary = "List function units (paginated) with optional tag filter")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<Page<FunctionUnitResponse>>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) List<String> tags,
            Pageable pageable) {
        return handleRequest(() -> functionUnitComponent.list(name, status, tags, pageable));
    }

    @GetMapping("/tags")
    @Operation(summary = "List all distinct tags across all enabled function units")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<String>>> getAllTags() {
        return handleRequest(() -> functionUnitComponent.getAllTags());
    }
    
    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish function unit")
    @RequireDeveloperPermission("FUNCTION_UNIT_PUBLISH")
    public ResponseEntity<ApiResponse<FunctionUnit>> publish(
            @PathVariable Long id,
            @RequestParam(required = false) String changeLog) {
        return handleRequest(() -> functionUnitComponent.publish(id, changeLog));
    }
    
    @PostMapping("/{id}/clone")
    @Operation(summary = "Clone function unit")
    @RequireDeveloperPermission("FUNCTION_UNIT_CREATE")
    public ResponseEntity<ApiResponse<FunctionUnit>> clone(
            @PathVariable Long id,
            @RequestParam String newName) {
        return handleRequest(() -> functionUnitComponent.clone(id, newName));
    }
    
    @GetMapping("/{id}/validate")
    @Operation(summary = "Validate function unit integrity")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<ValidationResult>> validate(@PathVariable Long id) {
        return handleRequest(() -> functionUnitComponent.validate(id));
    }
    
    @GetMapping("/{id}/versions")
    @Operation(summary = "Get version history")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<VersionResponse>>> getVersions(@PathVariable Long id) {
        return handleRequest(() -> functionUnitComponent.getVersionHistory(id));
    }

    @PostMapping("/{id}/versions/{versionId}/rollback")
    @Operation(summary = "Rollback to history version")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<FunctionUnit>> rollback(
            @PathVariable Long id,
            @PathVariable Long versionId) {
        return handleRequest(() -> functionUnitComponent.rollback(id, versionId));
    }

    @GetMapping("/{id}/versions/compare")
    @Operation(summary = "Compare two versions of a function unit")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<Map<String, Object>>> compareVersions(
            @PathVariable Long id,
            @RequestParam Long versionId1,
            @RequestParam Long versionId2) {
        return handleRequest(() -> functionUnitComponent.compareVersions(id, versionId1, versionId2));
    }

    @GetMapping("/{id}/versions/{versionId}/export")
    @Operation(summary = "Export the snapshot data of a specific version")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<byte[]> exportVersion(
            @PathVariable Long id,
            @PathVariable Long versionId) {
        byte[] payload = functionUnitComponent.exportVersion(id, versionId);
        String filename = "function-unit-" + id + "-version-" + versionId + ".json";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(payload);
    }

    @GetMapping("/{id}/dev-groups")
    @Operation(summary = "List virtual development group ids assigned to this function unit")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<String>>> getDevGroups(@PathVariable Long id) {
        return handleRequest(() -> functionUnitComponent.getDevGroupAssignments(id));
    }

    @PutMapping("/{id}/dev-groups")
    @Operation(summary = "Replace virtual development group assignments for this function unit")
    @RequireDeveloperPermission("FUNCTION_UNIT_ASSIGN_DEV_GROUP")
    public ResponseEntity<ApiResponse<Void>> replaceDevGroups(
            @PathVariable Long id,
            @Valid @RequestBody DevGroupAssignmentRequest request) {
        return handleRequest(() -> {
            functionUnitComponent.replaceDevGroupAssignments(id, request);
            return null;
        });
    }
}
