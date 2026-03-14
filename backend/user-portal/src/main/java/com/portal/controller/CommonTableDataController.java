package com.portal.controller;

import com.portal.dto.ApiResponse;
import com.portal.entity.CommonFieldDefinition;
import com.portal.entity.CommonTableData;
import com.portal.entity.CommonTableDefinition;
import com.portal.repository.CommonTableDataRepository;
import com.portal.repository.CommonTableDefinitionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 公共表数据控制器（User Portal）
 * 支持公共表数据的增删改查、搜索和 CSV 导出
 */
@Slf4j
@RestController
@RequestMapping("/common-table-data")
@RequiredArgsConstructor
@Tag(name = "公共表数据", description = "公共表数据的增删改查和导出操作")
public class CommonTableDataController {

    private final CommonTableDefinitionRepository tableDefRepository;
    private final CommonTableDataRepository dataRepository;

    @GetMapping("/tables")
    @Operation(summary = "获取所有公共表定义列表")
    public ResponseEntity<ApiResponse<List<CommonTableDefinition>>> listTables() {
        List<CommonTableDefinition> tables = tableDefRepository.findAllWithFields();
        return ResponseEntity.ok(ApiResponse.success(tables));
    }

    @GetMapping("/tables/{code}")
    @Operation(summary = "获取指定公共表定义（含字段）")
    public ResponseEntity<ApiResponse<CommonTableDefinition>> getTable(@PathVariable String code) {
        CommonTableDefinition table = tableDefRepository.findByCodeWithFields(code)
                .orElseThrow(() -> new RuntimeException("公共表不存在: " + code));
        return ResponseEntity.ok(ApiResponse.success(table));
    }

    @GetMapping("/{tableCode}")
    @Operation(summary = "分页查询公共表数据")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listData(
            @PathVariable String tableCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CommonTableDefinition table = getTableByCode(tableCode);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CommonTableData> dataPage = dataRepository.findByCommonTable_Id(table.getId(), pageable);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", dataPage.getContent());
        result.put("totalElements", dataPage.getTotalElements());
        result.put("totalPages", dataPage.getTotalPages());
        result.put("page", page);
        result.put("size", size);
        result.put("fields", table.getFieldDefinitions());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{tableCode}/search")
    @Operation(summary = "搜索公共表数据（供关联字段下拉框使用）")
    public ResponseEntity<ApiResponse<List<CommonTableData>>> search(
            @PathVariable String tableCode,
            @RequestParam(required = false) String keyword) {
        CommonTableDefinition table = getTableByCode(tableCode);
        List<CommonTableData> results;
        if (keyword == null || keyword.isBlank()) {
            Pageable top20 = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
            results = dataRepository.findByCommonTable_Id(table.getId(), top20).getContent();
        } else {
            results = dataRepository.searchByKeyword(table.getId(), keyword);
        }
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    @PostMapping("/{tableCode}")
    @Operation(summary = "新增公共表数据")
    public ResponseEntity<ApiResponse<CommonTableData>> create(
            @PathVariable String tableCode,
            @RequestBody Map<String, Object> dataJson,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        CommonTableDefinition table = getTableByCode(tableCode);
        CommonTableData data = CommonTableData.builder()
                .commonTable(table)
                .dataJson(dataJson)
                .createdBy(userId)
                .build();
        CommonTableData saved = dataRepository.save(data);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    @PutMapping("/{tableCode}/{id}")
    @Operation(summary = "更新公共表数据")
    public ResponseEntity<ApiResponse<CommonTableData>> update(
            @PathVariable String tableCode,
            @PathVariable Long id,
            @RequestBody Map<String, Object> dataJson) {
        CommonTableData existing = dataRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("数据不存在: " + id));
        existing.setDataJson(dataJson);
        CommonTableData saved = dataRepository.save(existing);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    @DeleteMapping("/{tableCode}/{id}")
    @Operation(summary = "删除公共表数据")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable String tableCode,
            @PathVariable Long id) {
        dataRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{tableCode}/export")
    @Operation(summary = "导出公共表数据为 CSV")
    public void export(@PathVariable String tableCode, HttpServletResponse response) throws IOException {
        CommonTableDefinition table = getTableByCode(tableCode);
        List<CommonTableData> allData = dataRepository.findByCommonTable_Id(table.getId());
        List<CommonFieldDefinition> fields = table.getFieldDefinitions();

        String filename = URLEncoder.encode(table.getName(), StandardCharsets.UTF_8) + "_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".csv";
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);

        try (PrintWriter writer = response.getWriter()) {
            // BOM for Excel UTF-8 compatibility
            writer.write('\uFEFF');

            // Header row
            List<String> headers = new ArrayList<>();
            headers.add("ID");
            headers.add("创建时间");
            fields.forEach(f -> headers.add(f.getDisplayName() != null ? f.getDisplayName() : f.getFieldName()));
            writer.println(String.join(",", headers.stream().map(this::escapeCsv).collect(Collectors.toList())));

            // Data rows
            for (CommonTableData row : allData) {
                List<String> values = new ArrayList<>();
                values.add(escapeCsv(String.valueOf(row.getId())));
                values.add(escapeCsv(row.getCreatedAt() != null ? row.getCreatedAt().toString() : ""));
                Map<String, Object> json = row.getDataJson() != null ? row.getDataJson() : Collections.emptyMap();
                for (CommonFieldDefinition field : fields) {
                    Object val = json.get(field.getFieldName());
                    values.add(escapeCsv(val != null ? String.valueOf(val) : ""));
                }
                writer.println(String.join(",", values));
            }
        }
    }

    private CommonTableDefinition getTableByCode(String tableCode) {
        return tableDefRepository.findByCodeWithFields(tableCode)
                .orElseThrow(() -> new RuntimeException("公共表不存在: " + tableCode));
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
