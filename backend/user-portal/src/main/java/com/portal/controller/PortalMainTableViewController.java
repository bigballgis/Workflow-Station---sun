package com.portal.controller;

import com.portal.exception.PortalException;
import com.platform.common.dto.ApiResponse;
import com.portal.dto.MainTableViewImportResult;
import com.portal.dto.MainTableViewPortalDtos.FunctionUnitViewMenuItem;
import com.portal.dto.MainTableViewPortalDtos.MainTableViewDataPage;
import com.portal.dto.MainTableViewPortalDtos.MainTableViewSummary;
import com.portal.dto.MainTableViewQueryRequest;
import com.portal.security.CurrentUserId;
import com.portal.service.PortalMainTableViewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/main-table-views")
@RequiredArgsConstructor
@Tag(name = "Main Table Views", description = "Portal runtime for FU Main Table views")
public class PortalMainTableViewController {

    private final PortalMainTableViewService portalMainTableViewService;

    @GetMapping("/function-units")
    @Operation(summary = "List function units with published Main Table views")
    public ResponseEntity<ApiResponse<List<FunctionUnitViewMenuItem>>> listFunctionUnits(
            @CurrentUserId String userId) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }
        return ResponseEntity.ok(ApiResponse.success(portalMainTableViewService.listAccessibleFunctionUnits(userId)));
    }

    @GetMapping("/function-units/{functionUnitCode}/views")
    @Operation(summary = "List published views for a function unit")
    public ResponseEntity<ApiResponse<List<MainTableViewSummary>>> listViews(
            @CurrentUserId String userId,
            @PathVariable String functionUnitCode) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }
        return ResponseEntity.ok(ApiResponse.success(
                portalMainTableViewService.listPublishedViews(userId, functionUnitCode)));
    }

    @PostMapping("/{viewId}/data")
    @Operation(summary = "Query view data (true paging; column filters and sort are pushed into SQL)")
    public ResponseEntity<ApiResponse<MainTableViewDataPage>> queryData(
            @CurrentUserId String userId,
            @PathVariable Long viewId,
            @RequestBody MainTableViewQueryRequest request) {
        if (userId == null || userId.isBlank()) {
            throw new PortalException("403", "User context required");
        }
        return ResponseEntity.ok(ApiResponse.success(
                portalMainTableViewService.queryViewData(userId, viewId, request)));
    }

    @PostMapping("/{viewId}/export")
    @Operation(summary = "Export the rows the caller is currently looking at as CSV")
    public ResponseEntity<byte[]> exportCsv(
            @CurrentUserId String userId,
            @PathVariable Long viewId,
            @RequestParam(defaultValue = "10000") int maxRows,
            @RequestBody MainTableViewQueryRequest request) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(403).build();
        }
        // The export carries the caller's filters, search and sort: paging is what the list and the
        // export differ on, so exporting anything else would hand back a different set of rows.
        byte[] csv = portalMainTableViewService.exportViewCsv(userId, viewId, maxRows, request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"main-table-view.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @PostMapping(value = "/{viewId}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import view data from CSV (updates by processInstanceId, creates new rows when blank)")
    public ResponseEntity<ApiResponse<MainTableViewImportResult>> importCsv(
            @CurrentUserId String userId,
            @PathVariable Long viewId,
            @RequestParam("file") MultipartFile file) {
        if (userId == null || userId.isBlank()) {
            throw new PortalException("403", "User context required");
        }
        try {
            MainTableViewImportResult result = portalMainTableViewService.importViewCsv(
                    userId, viewId, file.getBytes());
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (IllegalArgumentException ex) {
            throw new PortalException("400", ex.getMessage());
        } catch (java.io.IOException ex) {
            throw new PortalException("400", "Failed to read uploaded file", ex);
        }
    }
}
