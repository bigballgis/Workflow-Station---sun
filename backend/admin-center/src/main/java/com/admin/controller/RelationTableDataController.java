package com.admin.controller;

import com.admin.component.RelationTableDataListQueryComponent;
import com.admin.dto.list.AdminListPage;
import com.admin.dto.request.AllocatePrimaryKeyRequest;
import com.admin.dto.request.RelationTableDataListQueryRequest;
import com.admin.dto.response.AllocatePrimaryKeyResponse;
import com.admin.dto.response.RelationTableResponse;
import com.admin.entity.RelationTableAuditLog;
import com.admin.service.RelationTableAuditService;
import com.admin.service.RelationTableDataService;
import com.admin.service.RelationTablePrimaryKeyAllocationService;
import com.platform.common.dto.RelationTableDataRowDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Relation Table data management RESTful API
 */
@Slf4j
@RestController
@RequestMapping("/relation-tables/data")
@RequiredArgsConstructor
@Tag(name = "Relation Table Data Management", description = "Query, create, update, delete and change status of deployed table data")
public class RelationTableDataController {

    private final RelationTableDataService dataService;
    private final RelationTableDataListQueryComponent listQueryComponent;
    private final RelationTableAuditService auditService;
    private final RelationTablePrimaryKeyAllocationService primaryKeyAllocationService;

    // ==================== Deployed Table List ====================

    @GetMapping("/tables")
    @Operation(summary = "Get deployed table list", description = "Returns only DEPLOYED Relation Tables")
    public ResponseEntity<List<RelationTableResponse>> getDeployedTables() {
        log.info("Getting deployed relation tables");
        List<RelationTableResponse> tables = dataService.getDeployedTables();
        return ResponseEntity.ok(tables);
    }

    @GetMapping("/function-units")
    @Operation(summary = "Get Function Unit groups for deployed tables",
            description = "Distinct Function Units referenced by deployed Relation Tables, with table counts (for the nav sidebar)")
    public ResponseEntity<List<com.admin.dto.response.FunctionUnitTableGroupResponse>> getFunctionUnitGroups() {
        return ResponseEntity.ok(dataService.getDeployedTableFunctionUnitGroups());
    }

    // ==================== Table Data CRUD ====================

    @GetMapping("/{tableId}/export")
    @Operation(summary = "Export table data as CSV", description = "Export table data as CSV file")
    public ResponseEntity<byte[]> exportCsv(
            @Parameter(description = "Table definition ID") @PathVariable Long tableId,
            @Parameter(description = "Max rows") @RequestParam(defaultValue = "10000") int maxRows) {
        log.info("Exporting CSV for table: tableId={}, maxRows={}", tableId, maxRows);
        String csv = dataService.exportCsv(tableId, maxRows);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=export.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.getBytes());
    }

    @GetMapping("/{tableId}/template")
    @Operation(summary = "Download import template", description = "CSV/XLSX template with field-name headers")
    public ResponseEntity<byte[]> downloadTemplate(
            @Parameter(description = "Table definition ID") @PathVariable Long tableId,
            @Parameter(description = "Format csv|xlsx") @RequestParam(defaultValue = "csv") String format) {
        byte[] bytes = dataService.generateTemplate(tableId, format);
        boolean xlsx = "xlsx".equalsIgnoreCase(format);
        String filename = "template." + (xlsx ? "xlsx" : "csv");
        MediaType ct = xlsx
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.parseMediaType("text/csv");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(ct)
                .body(bytes);
    }

    @PostMapping("/{tableId}/import")
    @Operation(summary = "Import table data", description = "Validate a CSV/XLSX upload against the table structure and insert valid rows")
    public ResponseEntity<Map<String, Object>> importData(
            @Parameter(description = "Table definition ID") @PathVariable Long tableId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(required = false) String format,
            @Parameter(description = "When true, validate only and do not insert (preview step)")
            @RequestParam(required = false, defaultValue = "false") boolean dryRun) throws java.io.IOException {
        String fmt = (format != null && !format.isBlank()) ? format
                : (file.getOriginalFilename() != null && file.getOriginalFilename().toLowerCase().endsWith(".xlsx") ? "xlsx" : "csv");
        log.info("Importing data into table: tableId={}, format={}, dryRun={}", tableId, fmt, dryRun);
        Map<String, Object> result = dataService.importData(tableId, file.getBytes(), fmt, dryRun);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{tableId}")
    @Operation(summary = "Query table data paginated", description = "Dynamically query physical table data using the latest deployed table structure, with search filter support")
    public ResponseEntity<Page<RelationTableDataRowDTO>> queryData(
            @Parameter(description = "Table definition ID") @PathVariable Long tableId,
            @Parameter(description = "Search keyword") @RequestParam(required = false) String search,
            Pageable pageable) {
        log.info("Querying data for table: tableId={}, search={}, page={}", tableId, search, pageable);
        Page<RelationTableDataRowDTO> page = dataService.queryData(tableId, search, pageable);
        return ResponseEntity.ok(page);
    }

    @PostMapping("/{tableId}/query")
    @Operation(summary = "Page table data", description = "Shared list: COUNT(*) and the page share one predicate including toolbar keyword and column filters")
    public ResponseEntity<AdminListPage<RelationTableDataRowDTO>> queryDataPage(
            @Parameter(description = "Table definition ID") @PathVariable Long tableId,
            @RequestBody @Valid RelationTableDataListQueryRequest request) {
        return ResponseEntity.ok(listQueryComponent.query(tableId, request));
    }

    @GetMapping("/{tableId}/search")
    @Operation(summary = "Lookup search", description = "Search rows of a table for a LOOKUP field dropdown / derived auto-fill")
    public ResponseEntity<List<Map<String, Object>>> searchForLookup(
            @Parameter(description = "Table definition ID") @PathVariable Long tableId,
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false) List<String> searchFields,
            @RequestParam(required = false, defaultValue = "") String displayField,
            @RequestParam(required = false) String filterConditions,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false, defaultValue = "0") int offset) {
        List<Map<String, Object>> rows = dataService.searchForLookup(
                tableId, keyword, searchFields, displayField, filterConditions, limit, offset);
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/{tableId}/view-fields")
    @Operation(summary = "Get view fields", description = "Backfill panel columns for a LOOKUP field")
    public ResponseEntity<List<Map<String, Object>>> getViewFields(
            @Parameter(description = "Table definition ID") @PathVariable Long tableId) {
        return ResponseEntity.ok(dataService.getViewFields(tableId));
    }

    @PostMapping("/{tableId}/primary-keys/allocate")
    @Operation(summary = "Allocate primary key value(s)", description = "Backend PK allocation for Relation Table data add-row (PRD S5)")
    public ResponseEntity<AllocatePrimaryKeyResponse> allocatePrimaryKeys(
            @Parameter(description = "Table definition ID") @PathVariable Long tableId,
            @Valid @RequestBody AllocatePrimaryKeyRequest request) {
        log.info("Allocating PK for relation table: tableId={}, field={}", tableId, request.getFieldName());
        AllocatePrimaryKeyResponse response = primaryKeyAllocationService.allocate(
                tableId, request.getFieldName(), request.getCount(), request.getScopeKey());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{tableId}")
    @Operation(summary = "Add data", description = "Add a new row to the table")
    public ResponseEntity<RelationTableDataRowDTO> addData(
            @Parameter(description = "Table definition ID") @PathVariable Long tableId,
            @RequestBody Map<String, Object> data) {
        log.info("Adding data to table: tableId={}", tableId);
        RelationTableDataRowDTO row = dataService.addData(tableId, data);
        return ResponseEntity.status(HttpStatus.CREATED).body(row);
    }

    @PutMapping("/{tableId}/{rowId}")
    @Operation(summary = "Update data", description = "Update a row in the table")
    public ResponseEntity<RelationTableDataRowDTO> updateData(
            @Parameter(description = "Table definition ID") @PathVariable Long tableId,
            @Parameter(description = "Row ID") @PathVariable String rowId,
            @RequestBody Map<String, Object> data) {
        log.info("Updating data in table: tableId={}, rowId={}", tableId, rowId);
        RelationTableDataRowDTO row = dataService.updateData(tableId, rowId, data);
        return ResponseEntity.ok(row);
    }

    @DeleteMapping("/{tableId}/{rowId}")
    @Operation(summary = "Delete data", description = "Delete a row from the table")
    public ResponseEntity<Void> deleteData(
            @Parameter(description = "Table definition ID") @PathVariable Long tableId,
            @Parameter(description = "Row ID") @PathVariable String rowId) {
        log.info("Deleting data from table: tableId={}, rowId={}", tableId, rowId);
        dataService.deleteData(tableId, rowId);
        return ResponseEntity.noContent().build();
    }

    // ==================== Status Change ====================

    private static final Set<String> ALLOWED_STATUSES = Set.of("ACTIVE", "INACTIVE");

    @PutMapping("/{tableId}/{rowId}/status")
    @Operation(summary = "Change data status", description = "Change the Active/Inactive status of data")
    public ResponseEntity<RelationTableDataRowDTO> changeStatus(
            @Parameter(description = "Table definition ID") @PathVariable Long tableId,
            @Parameter(description = "Row ID") @PathVariable String rowId,
            @RequestBody Map<String, String> request) {
        String status = request.get("status");
        if (status == null) {
            return ResponseEntity.badRequest().build();
        }
        if (!ALLOWED_STATUSES.contains(status.toUpperCase())) {
            log.warn("Invalid status change attempt: tableId={}, rowId={}, status={}", tableId, rowId, status);
            return ResponseEntity.badRequest().build();
        }
        log.info("Changing status for table: tableId={}, rowId={}, status={}", tableId, rowId, status);
        RelationTableDataRowDTO row = dataService.changeStatus(tableId, rowId, status);
        return ResponseEntity.ok(row);
    }

    // ==================== Audit Log ====================

    @GetMapping("/{tableId}/audit-logs")
    @Operation(summary = "Query audit logs", description = "Query audit logs for the table, supporting filter by operation time, operator, and action type")
    public ResponseEntity<Page<RelationTableAuditLog>> queryAuditLogs(
            @Parameter(description = "Table definition ID") @PathVariable Long tableId,
            @Parameter(description = "Action type") @RequestParam(required = false) String action,
            @Parameter(description = "Operator ID") @RequestParam(required = false) String operatorId,
            @Parameter(description = "Start time") @RequestParam(required = false) Instant startTime,
            @Parameter(description = "End time") @RequestParam(required = false) Instant endTime,
            Pageable pageable) {
        log.info("Querying audit logs for table: tableId={}, action={}, operatorId={}", tableId, action, operatorId);
        Page<RelationTableAuditLog> logs = auditService.queryAuditLogs(tableId, action, operatorId, startTime, endTime, pageable);
        return ResponseEntity.ok(logs);
    }
}
