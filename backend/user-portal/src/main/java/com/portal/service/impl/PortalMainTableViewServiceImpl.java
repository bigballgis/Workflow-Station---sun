package com.portal.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.component.FunctionUnitAccessComponent;
import com.portal.component.ProcessComponent;
import com.portal.dto.MainTableViewImportResult;
import com.portal.dto.ProcessInstanceInfo;
import com.portal.dto.ProcessStartRequest;
import com.portal.dto.MainTableViewPortalDtos.FunctionUnitViewMenuItem;
import com.portal.dto.MainTableViewPortalDtos.MainTableViewDataPage;
import com.portal.dto.MainTableViewPortalDtos.MainTableViewDataRow;
import com.portal.dto.MainTableViewPortalDtos.MainTableViewFieldColumn;
import com.portal.dto.MainTableViewPortalDtos.MainTableViewSummary;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.service.PortalMainTableViewService;
import com.portal.util.PortalMainTableViewCsvUtils;
import com.portal.util.PortalMainTableViewFilterUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortalMainTableViewServiceImpl implements PortalMainTableViewService {

    private static final String PROCESS_INSTANCE_ID_FIELD = "processInstanceId";
    private static final Set<String> PROCESS_INSTANCE_ID_HEADERS = Set.of(
            PROCESS_INSTANCE_ID_FIELD,
            "Process Instance ID",
            "process_instance_id"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final FunctionUnitAccessComponent functionUnitAccessComponent;
    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessComponent processComponent;

    @Override
    @Transactional(readOnly = true)
    public List<FunctionUnitViewMenuItem> listAccessibleFunctionUnits(String userId) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList("""
                    SELECT fu.id AS fu_id, fu.code AS fu_code, fu.name AS fu_name, COUNT(v.id) AS view_count
                    FROM dw_main_table_view_configs v
                    INNER JOIN dw_function_units fu ON fu.id = v.function_unit_id
                    WHERE v.status = 'PUBLISHED'
                    GROUP BY fu.id, fu.code, fu.name
                    ORDER BY fu.name
                    """);
        } catch (Exception e) {
            log.warn("Main table view menu query failed: {}", e.getMessage());
            return List.of();
        }

        List<FunctionUnitViewMenuItem> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String code = stringVal(row.get("fu_code"));
            if (code == null || !functionUnitAccessComponent.canAccessFunctionUnit(userId, code)) {
                continue;
            }
            if (!functionUnitAccessComponent.isFunctionUnitEnabled(code)) {
                continue;
            }
            result.add(FunctionUnitViewMenuItem.builder()
                    .functionUnitId(String.valueOf(row.get("fu_id")))
                    .functionUnitCode(code)
                    .functionUnitName(stringVal(row.get("fu_name")))
                    .viewCount(((Number) row.get("view_count")).intValue())
                    .build());
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MainTableViewSummary> listPublishedViews(String userId, String functionUnitCode) {
        assertFuAccess(userId, functionUnitCode);
        try {
            return jdbcTemplate.query("""
                            SELECT v.id, v.view_name, v.is_default, v.filter_config::text AS filter_config,
                                   v.main_table_id,
                                   COALESCE(td.table_display_name, td.table_name) AS table_label
                            FROM dw_main_table_view_configs v
                            INNER JOIN dw_function_units fu ON fu.id = v.function_unit_id
                            LEFT JOIN dw_table_definitions td ON td.id = v.main_table_id
                            WHERE fu.code = ? AND v.status = 'PUBLISHED'
                            ORDER BY COALESCE(td.table_display_name, td.table_name),
                                     v.is_default DESC, v.view_name
                            """,
                    (rs, rowNum) -> {
                        Map<String, Object> toolbar = parseToolbarConfig(stringVal(rs.getString("filter_config")));
                        return MainTableViewSummary.builder()
                                .id(rs.getLong("id"))
                                .viewName(rs.getString("view_name"))
                                .isDefault(rs.getBoolean("is_default"))
                                .tableId(rs.getObject("main_table_id") != null ? rs.getLong("main_table_id") : null)
                                .tableLabel(rs.getString("table_label"))
                                .enableExport(toolbarEnable(toolbar, "enableExport", true))
                                .enableImport(toolbarEnable(toolbar, "enableImport", true))
                                .build();
                    },
                    functionUnitCode.trim());
        } catch (Exception e) {
            log.warn("List published views failed for {}: {}", functionUnitCode, e.getMessage());
            return List.of();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public MainTableViewDataPage queryViewData(String userId, Long viewId, int page, int size, String search) {
        ViewDefinition view = loadPublishedView(viewId);
        assertFuAccess(userId, view.functionUnitCode());

        List<MainTableViewFieldColumn> columns = visibleColumns(view);
        List<Map<String, Object>> allRows = loadAndProjectRows(userId, view, search);
        PortalMainTableViewFilterUtils.applyViewSort(allRows, view.sortConfig());

        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 0);
        int from = safePage * safeSize;
        int to = Math.min(from + safeSize, allRows.size());
        List<Map<String, Object>> pageSlice = from >= allRows.size()
                ? List.of()
                : allRows.subList(from, to);

        List<MainTableViewDataRow> rows = pageSlice.stream()
                .map(r -> MainTableViewDataRow.builder()
                        .processInstanceId(String.valueOf(r.get("_processInstanceId")))
                        .values(stripInternalKeys(r))
                        .build())
                .toList();

        return MainTableViewDataPage.builder()
                .columns(columns)
                .rows(rows)
                .total(allRows.size())
                .page(safePage)
                .size(safeSize)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportViewCsv(String userId, Long viewId, int maxRows) {
        ViewDefinition view = loadPublishedView(viewId);
        assertFuAccess(userId, view.functionUnitCode());

        List<MainTableViewFieldColumn> columns = visibleColumns(view);
        List<Map<String, Object>> allRows = loadAndProjectRows(userId, view, null);
        PortalMainTableViewFilterUtils.applyViewSort(allRows, view.sortConfig());
        int limit = Math.min(Math.max(maxRows, 1), 10000);
        List<Map<String, Object>> slice = allRows.size() <= limit ? allRows : allRows.subList(0, limit);

        StringBuilder sb = new StringBuilder();
        sb.append(PortalMainTableViewCsvUtils.csvEscape(PROCESS_INSTANCE_ID_FIELD));
        if (!columns.isEmpty()) {
            sb.append(',');
        }
        sb.append(columns.stream().map(c -> PortalMainTableViewCsvUtils.csvEscape(c.displayLabel())).collect(Collectors.joining(",")));
        sb.append('\n');
        for (Map<String, Object> row : slice) {
            Map<String, Object> values = stripInternalKeys(row);
            sb.append(PortalMainTableViewCsvUtils.csvEscape(String.valueOf(row.get("_processInstanceId"))));
            if (!columns.isEmpty()) {
                sb.append(',');
            }
            sb.append(columns.stream()
                    .map(c -> PortalMainTableViewCsvUtils.csvEscape(PortalMainTableViewCsvUtils.formatCell(values.get(c.fieldName()))))
                    .collect(Collectors.joining(",")));
            sb.append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    @Transactional
    public MainTableViewImportResult importViewCsv(String userId, Long viewId, byte[] csvBytes) {
        ViewDefinition view = loadPublishedView(viewId);
        assertFuAccess(userId, view.functionUnitCode());

        if (csvBytes == null || csvBytes.length == 0) {
            throw new IllegalArgumentException("CSV file is empty");
        }

        List<MainTableViewFieldColumn> columns = visibleColumns(view);
        Map<String, String> headerToField = new LinkedHashMap<>();
        for (MainTableViewFieldColumn col : columns) {
            if (Boolean.TRUE.equals(col.systemField())) {
                continue;
            }
            headerToField.put(col.displayLabel(), col.fieldName());
            headerToField.put(col.fieldName(), col.fieldName());
        }

        List<String[]> parsedRows = PortalMainTableViewCsvUtils.parseCsvRows(csvBytes);
        if (parsedRows.isEmpty()) {
            throw new IllegalArgumentException("CSV has no data rows");
        }

        String[] headers = parsedRows.get(0);
        int processIdCol = PortalMainTableViewCsvUtils.findProcessInstanceIdColumn(headers, PROCESS_INSTANCE_ID_HEADERS);

        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (int rowIdx = 1; rowIdx < parsedRows.size(); rowIdx++) {
            String[] cells = parsedRows.get(rowIdx);
            if (cells.length == 0 || PortalMainTableViewCsvUtils.isBlankRow(cells)) {
                skipped++;
                continue;
            }
            String processInstanceId = processIdCol >= 0 ? PortalMainTableViewCsvUtils.cellValue(cells, processIdCol) : "";
            if (processInstanceId.isBlank()) {
                if (createProcessFromCsvRow(userId, view, headers, cells, processIdCol, headerToField, rowIdx + 1, errors)) {
                    created++;
                }
                continue;
            }

            Optional<ProcessInstance> optPi = processInstanceRepository.findById(processInstanceId);
            if (optPi.isEmpty()) {
                errors.add("Row " + (rowIdx + 1) + ": process not found " + processInstanceId);
                continue;
            }
            ProcessInstance pi = optPi.get();
            if (!userId.equals(pi.getStartUserId())) {
                errors.add("Row " + (rowIdx + 1) + ": access denied for " + processInstanceId);
                continue;
            }
            if (view.functionUnitCode() != null
                    && pi.getFunctionUnitCode() != null
                    && !view.functionUnitCode().equalsIgnoreCase(pi.getFunctionUnitCode())) {
                errors.add("Row " + (rowIdx + 1) + ": function unit mismatch for " + processInstanceId);
                continue;
            }

            Map<String, Object> vars = pi.getVariables() != null
                    ? new HashMap<>(pi.getVariables())
                    : new HashMap<>();
            boolean changed = false;
            for (int col = 0; col < headers.length; col++) {
                if (col == processIdCol) {
                    continue;
                }
                String header = headers[col] != null ? headers[col].trim() : "";
                String fieldName = headerToField.get(header);
                if (fieldName == null) {
                    continue;
                }
                String raw = PortalMainTableViewCsvUtils.cellValue(cells, col);
                Object parsed = raw.isBlank() ? null : raw;
                Object existing = vars.get(fieldName);
                if (!Objects.equals(existing, parsed)) {
                    if (parsed == null) {
                        vars.remove(fieldName);
                    } else {
                        vars.put(fieldName, parsed);
                    }
                    changed = true;
                }
            }
            if (changed) {
                pi.setVariables(vars);
                processInstanceRepository.save(pi);
                updated++;
            } else {
                skipped++;
            }
        }

        return MainTableViewImportResult.builder()
                .createdCount(created)
                .updatedCount(updated)
                .skippedCount(skipped)
                .errorCount(errors.size())
                .errors(errors.size() > 20 ? errors.subList(0, 20) : errors)
                .build();
    }

    /**
     * Starts a new process for a CSV row with blank {@code processInstanceId}.
     *
     * @return {@code true} if a process was created
     */
    private boolean createProcessFromCsvRow(
            String userId,
            ViewDefinition view,
            String[] headers,
            String[] cells,
            int processIdCol,
            Map<String, String> headerToField,
            int rowNumber,
            List<String> errors) {
        Map<String, Object> formData = new LinkedHashMap<>();
        String businessKey = null;
        for (int col = 0; col < headers.length; col++) {
            if (col == processIdCol) {
                continue;
            }
            String header = headers[col] != null ? headers[col].trim() : "";
            String fieldName = headerToField.get(header);
            if (fieldName == null) {
                continue;
            }
            String raw = PortalMainTableViewCsvUtils.cellValue(cells, col);
            if (raw.isBlank()) {
                continue;
            }
            formData.put(fieldName, parseImportCellValue(raw));
            if (businessKey == null && ("case_number".equals(fieldName) || "business_key".equals(fieldName))) {
                businessKey = raw;
            }
        }
        if (formData.isEmpty()) {
            errors.add("Row " + rowNumber + ": no field values for new record");
            return false;
        }
        if (view.functionUnitCode() == null || view.functionUnitCode().isBlank()) {
            errors.add("Row " + rowNumber + ": view has no function unit for process start");
            return false;
        }
        try {
            ProcessStartRequest request = ProcessStartRequest.builder()
                    .formData(formData)
                    .businessKey(businessKey)
                    .build();
            ProcessInstanceInfo created = processComponent.startProcess(userId, view.functionUnitCode(), request);
            log.info("CSV import created process {} for user {} at row {}", created.getId(), userId, rowNumber);
            return true;
        } catch (Exception ex) {
            String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            errors.add("Row " + rowNumber + ": create failed – " + message);
            log.warn("CSV import create failed at row {}: {}", rowNumber, message);
            return false;
        }
    }

    private Object parseImportCellValue(String raw) {
        if ("true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw)) {
            return Boolean.parseBoolean(raw);
        }
        return raw;
    }

    private List<Map<String, Object>> loadAndProjectRows(String userId, ViewDefinition view, String search) {
        // View Design shows ALL data of the function unit (every initiator), not just the current user.
        Pageable pageable = PageRequest.of(0, 5000, Sort.by(Sort.Direction.DESC, "startTime"));
        Page<ProcessInstance> instances = processInstanceRepository
                .findByFunctionUnitCodeOrderByStartTimeDesc(view.functionUnitCode(), pageable);

        boolean isSub = "SUB".equalsIgnoreCase(view.tableType());
        List<Map<String, Object>> rows = new ArrayList<>();
        String needle = search != null ? search.trim().toLowerCase(Locale.ROOT) : null;
        // SUB rows can appear under several form bindings + repeat across process snapshots — dedup by PK.
        Set<String> seenSubKeys = new LinkedHashSet<>();

        for (ProcessInstance pi : instances.getContent()) {
            List<Map<String, Object>> piRows = isSub
                    ? projectSubTableRows(pi, view, seenSubKeys)
                    : List.of(projectInstanceRow(pi, view));
            for (Map<String, Object> row : piRows) {
                if (!PortalMainTableViewFilterUtils.matchesFilter(row, view.filterConfig())) {
                    continue;
                }
                if (needle != null && !needle.isEmpty()
                        && !PortalMainTableViewFilterUtils.matchesSearch(row, needle)) {
                    continue;
                }
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * Flatten a SUB-table view's rows from a process instance's {@code variables.__subTables__}.
     * Each of the view-table's form bindings contributes a list keyed by its binding id; rows are
     * deduplicated by primary-key signature so the same child row bound into multiple forms appears once.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> projectSubTableRows(ProcessInstance pi, ViewDefinition view,
                                                          Set<String> seenSubKeys) {
        Map<String, Object> vars = pi.getVariables();
        if (vars == null) {
            return List.of();
        }
        Object subTablesObj = vars.get("__subTables__");
        if (!(subTablesObj instanceof Map<?, ?> subTables)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (String bindingKey : view.subBindingKeys()) {
            Object listObj = subTables.get(bindingKey);
            if (!(listObj instanceof List<?> list)) {
                continue;
            }
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> rowMap)) {
                    continue;
                }
                Map<String, Object> source = (Map<String, Object>) rowMap;
                // Dedup signature: process instance + the row's own id/keys.
                String sig = pi.getId() + "|" + bindingKey + "|"
                        + String.valueOf(source.getOrDefault("id",
                            source.getOrDefault("id_idw", source.hashCode())));
                if (!seenSubKeys.add(sig)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("_processInstanceId", pi.getId());
                for (ViewFieldDef field : view.fields()) {
                    if (!Boolean.TRUE.equals(field.visible())) {
                        continue;
                    }
                    row.put(field.fieldName(), source.get(field.fieldName()));
                }
                out.add(row);
            }
        }
        return out;
    }

    private Map<String, Object> projectInstanceRow(ProcessInstance pi, ViewDefinition view) {
        Map<String, Object> vars = pi.getVariables() != null ? pi.getVariables() : Map.of();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("_processInstanceId", pi.getId());

        for (ViewFieldDef field : view.fields()) {
            if (!Boolean.TRUE.equals(field.visible())) {
                continue;
            }
            if (Boolean.TRUE.equals(field.systemField())) {
                row.put(field.fieldName(), systemFieldValue(pi, field.fieldName()));
            } else {
                row.put(field.fieldName(), vars.get(field.fieldName()));
            }
        }
        return row;
    }

    private Object systemFieldValue(ProcessInstance pi, String fieldName) {
        return switch (fieldName) {
            case "process_status" -> pi.getStatus();
            case "start_time" -> pi.getStartTime();
            case "initiator" -> pi.getStartUserName() != null ? pi.getStartUserName() : pi.getStartUserId();
            case "current_step" -> pi.getCurrentNode();
            default -> null;
        };
    }

    private List<MainTableViewFieldColumn> visibleColumns(ViewDefinition view) {
        Map<String, FkColumnMeta> fkMeta = loadFkColumnMeta(view.id());
        return view.fields().stream()
                .filter(f -> Boolean.TRUE.equals(f.visible()))
                .sorted(Comparator.comparingInt(f -> f.sortOrder() != null ? f.sortOrder() : 0))
                .map(f -> {
                    FkColumnMeta fk = fkMeta.get(f.fieldName());
                    return MainTableViewFieldColumn.builder()
                            .fieldName(f.fieldName())
                            .displayLabel(f.displayLabel() != null ? f.displayLabel() : f.fieldName())
                            .columnWidth(f.columnWidth())
                            .systemField(f.systemField())
                            .isForeignKey(fk != null)
                            .refViewId(fk != null ? fk.refViewId() : null)
                            .refFunctionUnitCode(fk != null ? fk.refFunctionUnitCode() : null)
                            .refPrimaryKeyFields(fk != null ? fk.refPrimaryKeyFields() : null)
                            .build();
                })
                .toList();
    }

    /**
     * For each FK field of the view's owning table, resolve the referenced table's PUBLISHED default
     * view (id + FU code) so the portal can render a drill-down link. FK columns whose referenced table
     * has no published default view are omitted from the map (rendered as plain text — graceful degrade).
     */
    private Map<String, FkColumnMeta> loadFkColumnMeta(Long viewId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT fd.field_name,
                           fd.ref_primary_key_fields::text AS ref_pk_fields,
                           rv.id AS ref_view_id,
                           rfu.code AS ref_fu_code
                    FROM dw_main_table_view_configs v
                    INNER JOIN dw_field_definitions fd ON fd.table_id = v.main_table_id
                    INNER JOIN dw_table_definitions rt ON rt.id = fd.ref_table_id
                    INNER JOIN dw_function_units rfu ON rfu.id = rt.function_unit_id
                    INNER JOIN dw_main_table_view_configs rv
                            ON rv.main_table_id = fd.ref_table_id
                           AND rv.is_default = TRUE
                           AND rv.status = 'PUBLISHED'
                    WHERE v.id = ? AND fd.is_foreign_key = TRUE AND fd.ref_table_id IS NOT NULL
                    """, viewId);
            Map<String, FkColumnMeta> meta = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                String fieldName = stringVal(row.get("field_name"));
                if (fieldName == null) {
                    continue;
                }
                meta.put(fieldName, new FkColumnMeta(
                        ((Number) row.get("ref_view_id")).longValue(),
                        stringVal(row.get("ref_fu_code")),
                        parseStringList(stringVal(row.get("ref_pk_fields")))));
            }
            return meta;
        } catch (Exception e) {
            log.warn("Failed to load FK column metadata for view {}: {}", viewId, e.getMessage());
            return Map.of();
        }
    }

    private List<String> parseStringList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> stripInternalKeys(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        row.forEach((k, v) -> {
            if (!k.startsWith("_")) {
                out.put(k, v);
            }
        });
        return out;
    }

    private ViewDefinition loadPublishedView(Long viewId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT v.id, v.view_name, v.sort_config::text AS sort_config,
                       v.filter_config::text AS filter_config, fu.code AS fu_code,
                       v.main_table_id, td.table_type
                FROM dw_main_table_view_configs v
                INNER JOIN dw_function_units fu ON fu.id = v.function_unit_id
                LEFT JOIN dw_table_definitions td ON td.id = v.main_table_id
                WHERE v.id = ? AND v.status = 'PUBLISHED'
                """, viewId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Published view not found: " + viewId);
        }
        Map<String, Object> row = rows.get(0);
        List<ViewFieldDef> fields = jdbcTemplate.query("""
                        SELECT field_name, display_label, column_width, sort_order, visible, is_system_field
                        FROM dw_main_table_view_fields
                        WHERE view_config_id = ?
                        ORDER BY sort_order
                        """,
                (rs, rowNum) -> new ViewFieldDef(
                        rs.getString("field_name"),
                        rs.getString("display_label"),
                        rs.getObject("column_width") != null ? rs.getInt("column_width") : null,
                        rs.getInt("sort_order"),
                        rs.getBoolean("visible"),
                        rs.getBoolean("is_system_field")),
                viewId);

        Long mainTableId = row.get("main_table_id") != null
                ? ((Number) row.get("main_table_id")).longValue() : null;
        String tableType = stringVal(row.get("table_type"));
        // SUB-table data is nested under variables.__subTables__, keyed by each binding id that maps
        // this table into a form. Collect those binding keys so we can flatten the rows below.
        List<String> subBindingKeys = new ArrayList<>();
        if ("SUB".equalsIgnoreCase(tableType) && mainTableId != null) {
            subBindingKeys = jdbcTemplate.queryForList(
                    "SELECT id FROM dw_form_table_bindings WHERE table_id = ?",
                    Long.class, mainTableId).stream().map(String::valueOf).toList();
        }

        return new ViewDefinition(
                viewId,
                stringVal(row.get("view_name")),
                stringVal(row.get("fu_code")),
                parseJsonList(stringVal(row.get("sort_config"))),
                parseJsonMap(stringVal(row.get("filter_config"))),
                fields,
                mainTableId,
                tableType,
                subBindingKeys);
    }

    private void assertFuAccess(String userId, String functionUnitCode) {
        if (!functionUnitAccessComponent.canAccessFunctionUnit(userId, functionUnitCode)) {
            throw new FunctionUnitAccessComponent.FunctionUnitAccessDeniedException(
                    "Access denied for function unit: " + functionUnitCode);
        }
        if (!functionUnitAccessComponent.isFunctionUnitEnabled(functionUnitCode)) {
            throw new FunctionUnitAccessComponent.FunctionUnitDisabledException(
                    "Function unit is disabled: " + functionUnitCode);
        }
    }

    private List<Map<String, Object>> parseJsonList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> parseJsonMap(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String stringVal(Object o) {
        return o != null ? String.valueOf(o) : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseToolbarConfig(String filterConfigJson) {
        Map<String, Object> filter = parseJsonMap(filterConfigJson);
        Object toolbar = filter.get("toolbar");
        if (toolbar instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private boolean toolbarEnable(Map<String, Object> toolbar, String key, boolean defaultValue) {
        Object val = toolbar.get(key);
        if (val == null) {
            return defaultValue;
        }
        if (val instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(val));
    }

    private record FkColumnMeta(
            Long refViewId,
            String refFunctionUnitCode,
            List<String> refPrimaryKeyFields) {}

    private record ViewFieldDef(
            String fieldName,
            String displayLabel,
            Integer columnWidth,
            Integer sortOrder,
            Boolean visible,
            Boolean systemField) {}

    private record ViewDefinition(
            Long id,
            String viewName,
            String functionUnitCode,
            List<Map<String, Object>> sortConfig,
            Map<String, Object> filterConfig,
            List<ViewFieldDef> fields,
            Long mainTableId,
            String tableType,
            List<String> subBindingKeys) {}
}
