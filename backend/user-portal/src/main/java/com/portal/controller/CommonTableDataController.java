package com.portal.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.dto.ApiResponse;
import com.portal.entity.CommonTableData;
import com.portal.entity.CommonTableDefinition;
import com.portal.repository.CommonTableDataRepository;
import com.portal.repository.CommonTableDefinitionRepository;
import com.portal.repository.CommonTableDeploymentRepository;
import com.portal.repository.UserRepository;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
    private final CommonTableDeploymentRepository deploymentRepository;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    private static final ZoneId ZONE_CST = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter CST_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Convert LocalDateTime (assumed UTC from DB) to UTC+8 string */
    private String toCST(LocalDateTime ldt) {
        if (ldt == null) return null;
        ZonedDateTime utc = ldt.atZone(ZoneId.of("UTC"));
        return utc.withZoneSameInstant(ZONE_CST).format(CST_FMT);
    }

    /** Resolve userId (UUID) to full_name; return as-is if not a UUID or not found */
    private String resolveFullName(String userId) {
        if (userId == null || userId.isBlank()) return null;
        if (!userId.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) return userId;
        return userRepository.findById(userId)
                .map(u -> {
                    String full = u.getFullName();
                    return (full != null && !full.isBlank()) ? full : u.getUsername();
                })
                .orElse(userId);
    }

    /** Convert a CommonTableData to response Map with formatted timestamps and resolved names */
    private Map<String, Object> toResponseMap(CommonTableData row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.getId());
        m.put("commonTableId", row.getCommonTableId());
        m.put("dataJson", row.getDataJson());
        m.put("createdBy", row.getCreatedBy());
        m.put("updatedBy", row.getUpdatedBy());
        m.put("createdAt", toCST(row.getCreatedAt()));
        m.put("updatedAt", toCST(row.getUpdatedAt()));
        return m;
    }

    @GetMapping("/tables")
    @Operation(summary = "获取所有公共表定义列表（字段来自最新部署快照）")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listTables() {
        List<CommonTableDefinition> tables = tableDefRepository.findAllWithFields();
        List<Map<String, Object>> result = tables.stream().map(t -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", t.getId());
            map.put("code", t.getCode());
            map.put("name", t.getName());
            map.put("description", t.getDescription());
            map.put("status", t.getStatus());
            map.put("enabled", t.getEnabled());
            map.put("fieldDefinitions", getSnapshotFields(t.getId()));
            return map;
        }).toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/tables/{code}")
    @Operation(summary = "获取指定公共表定义（字段来自最新部署快照）")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTable(@PathVariable String code) {
        CommonTableDefinition table = tableDefRepository.findByCodeWithFields(code)
                .orElseThrow(() -> new RuntimeException("公共表不存在: " + code));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", table.getId());
        result.put("code", table.getCode());
        result.put("name", table.getName());
        result.put("description", table.getDescription());
        result.put("status", table.getStatus());
        result.put("enabled", table.getEnabled());
        result.put("fieldDefinitions", getSnapshotFields(table.getId()));
        return ResponseEntity.ok(ApiResponse.success(result));
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

        List<Map<String, Object>> content = dataPage.getContent().stream()
                .map(this::toResponseMap)
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", content);
        result.put("totalElements", dataPage.getTotalElements());
        result.put("totalPages", dataPage.getTotalPages());
        result.put("page", page);
        result.put("size", size);
        result.put("fields", getSnapshotFields(table.getId()));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{tableCode}/search")
    @Operation(summary = "搜索公共表数据（供关联字段下拉框使用）")
    public ResponseEntity<ApiResponse<List<CommonTableData>>> search(
            @PathVariable String tableCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String displayField) {
        CommonTableDefinition table = getTableByCode(tableCode);
        List<CommonTableData> results;
        if (keyword == null || keyword.isBlank()) {
            Pageable top20 = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
            results = dataRepository.findByCommonTable_Id(table.getId(), top20).getContent();
        } else if (displayField != null && !displayField.isBlank()) {
            results = dataRepository.searchByKeywordInField(table.getId(), displayField, keyword);
        } else {
            results = dataRepository.searchByKeyword(table.getId(), keyword);
        }
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    @PostMapping("/{tableCode}")
    @Operation(summary = "新增公共表数据")
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @PathVariable String tableCode,
            @RequestBody Map<String, Object> dataJson,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        CommonTableDefinition table = getTableByCode(tableCode);
        CommonTableData data = CommonTableData.builder()
                .commonTable(table)
                .dataJson(dataJson)
                .createdBy(resolveFullName(userId))
                .build();
        CommonTableData saved = dataRepository.save(data);
        return ResponseEntity.ok(ApiResponse.success(toResponseMap(saved)));
    }

    @GetMapping("/{tableCode}/data/{rowId}")
    @Operation(summary = "按主键获取单条公共表数据")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRowById(
            @PathVariable String tableCode,
            @PathVariable Long rowId) {
        getTableByCode(tableCode);
        CommonTableData row = dataRepository.findById(rowId)
                .orElseThrow(() -> new RuntimeException("数据不存在: " + rowId));
        return ResponseEntity.ok(ApiResponse.success(toResponseMap(row)));
    }

    @PutMapping("/{tableCode}/{id}")
    @Operation(summary = "更新公共表数据")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(
            @PathVariable String tableCode,
            @PathVariable Long id,
            @RequestBody Map<String, Object> dataJson,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        CommonTableData existing = dataRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("数据不存在: " + id));
        existing.setDataJson(dataJson);
        existing.setUpdatedBy(resolveFullName(userId));
        CommonTableData saved = dataRepository.save(existing);
        return ResponseEntity.ok(ApiResponse.success(toResponseMap(saved)));
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
    @Operation(summary = "导出公共表数据为 CSV（字段来自最新部署快照）")
    public void export(@PathVariable String tableCode, HttpServletResponse response) throws IOException {
        CommonTableDefinition table = getTableByCode(tableCode);
        List<CommonTableData> allData = dataRepository.findByCommonTable_Id(table.getId());
        List<Map<String, Object>> fields = getSnapshotFields(table.getId());

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
            fields.forEach(f -> {
                String display = f.get("displayName") != null ? String.valueOf(f.get("displayName")) : String.valueOf(f.get("fieldName"));
                headers.add(display);
            });
            headers.add("Created At");
            headers.add("Updated At");
            headers.add("Created By");
            headers.add("Updated By");
            writer.println(String.join(",", headers.stream().map(this::escapeCsv).collect(Collectors.toList())));

            // Data rows
            for (CommonTableData row : allData) {
                List<String> values = new ArrayList<>();
                values.add(escapeCsv(String.valueOf(row.getId())));
                Map<String, Object> json = row.getDataJson() != null ? row.getDataJson() : Collections.emptyMap();
                for (Map<String, Object> field : fields) {
                    Object val = json.get(field.get("fieldName"));
                    values.add(escapeCsv(val != null ? String.valueOf(val) : ""));
                }
                values.add(escapeCsv(toCST(row.getCreatedAt())));
                values.add(escapeCsv(toCST(row.getUpdatedAt())));
                values.add(escapeCsv(row.getCreatedBy() != null ? row.getCreatedBy() : ""));
                values.add(escapeCsv(row.getUpdatedBy() != null ? row.getUpdatedBy() : ""));
                writer.println(String.join(",", values));
            }
        }
    }

    private CommonTableDefinition getTableByCode(String tableCode) {
        return tableDefRepository.findByCodeWithFields(tableCode)
                .orElseThrow(() -> new RuntimeException("公共表不存在: " + tableCode));
    }

    /**
     * 从最新 COMPLETED 部署记录的 field_snapshot 中读取字段定义。
     * 如果尚未部署，返回空列表（User Portal 中未部署的字段不可见）。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getSnapshotFields(Long tableId) {
        return deploymentRepository
                .findTopByCommonTableIdAndStatusOrderByDeployedAtDesc(tableId, "COMPLETED")
                .map(dep -> {
                    try {
                        String snapshot = dep.getFieldSnapshot();
                        if (snapshot == null || snapshot.isBlank()) return Collections.<Map<String, Object>>emptyList();
                        return objectMapper.readValue(snapshot, new TypeReference<List<Map<String, Object>>>() {});
                    } catch (Exception e) {
                        log.warn("Failed to parse field_snapshot for table {}: {}", tableId, e.getMessage());
                        return Collections.<Map<String, Object>>emptyList();
                    }
                })
                .orElse(Collections.emptyList());
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
