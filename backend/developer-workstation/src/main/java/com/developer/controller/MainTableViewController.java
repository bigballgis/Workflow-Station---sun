package com.developer.controller;

import com.platform.common.dto.ApiResponse;
import com.developer.dto.MainTableViewDtos.CreateMainTableViewRequest;
import com.developer.dto.MainTableViewDtos.MainTableViewDTO;
import com.developer.dto.MainTableViewDtos.UpdateMainTableViewRequest;
import com.developer.security.RequireDeveloperPermission;
import com.developer.service.MainTableViewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/function-units/{functionUnitId}/main-table-views")
@RequiredArgsConstructor
@Tag(name = "Main Table View", description = "Function Unit Main Table list view design")
public class MainTableViewController {

    private final MainTableViewService mainTableViewService;

    @GetMapping
    @Operation(summary = "List Main Table views for a function unit")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<MainTableViewDTO>>> list(@PathVariable Long functionUnitId) {
        return ResponseEntity.ok(ApiResponse.success(mainTableViewService.listViews(functionUnitId)));
    }

    @GetMapping("/{viewId}")
    @Operation(summary = "Get Main Table view detail")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<MainTableViewDTO>> get(
            @PathVariable Long functionUnitId,
            @PathVariable Long viewId) {
        return ResponseEntity.ok(ApiResponse.success(mainTableViewService.getView(functionUnitId, viewId)));
    }

    @PostMapping
    @Operation(summary = "Create a new Main Table view")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<MainTableViewDTO>> create(
            @PathVariable Long functionUnitId,
            @Valid @RequestBody CreateMainTableViewRequest request) {
        return ResponseEntity.ok(ApiResponse.success(mainTableViewService.createView(functionUnitId, request)));
    }

    @PutMapping("/{viewId}")
    @Operation(summary = "Update Main Table view")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<MainTableViewDTO>> update(
            @PathVariable Long functionUnitId,
            @PathVariable Long viewId,
            @Valid @RequestBody UpdateMainTableViewRequest request) {
        return ResponseEntity.ok(ApiResponse.success(mainTableViewService.updateView(functionUnitId, viewId, request)));
    }

    @DeleteMapping("/{viewId}")
    @Operation(summary = "Delete Main Table view")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long functionUnitId,
            @PathVariable Long viewId) {
        mainTableViewService.deleteView(functionUnitId, viewId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
