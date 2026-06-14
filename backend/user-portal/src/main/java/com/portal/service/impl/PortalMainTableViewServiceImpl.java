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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortalMainTableViewServiceImpl implements PortalMainTableViewService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
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
                            SELECT v.id, v.view_name, v.is_default, v.filter_config::text AS filter_config
                            FROM dw_main_table_view_configs v
                            INNER JOIN dw_function_units fu ON fu.id = v.function_unit_id
                            WHERE fu.code = ? AND v.status = 'PUBLISHED'
                            ORDER BY v.is_default DESC, v.view_name
                            """,
                    (rs, rowNum) -> {
                        Map<String, Object> toolbar = parseToolbarConfig(stringVal(rs.getString("filter_config")));
                        return MainTableViewSummary.builder()
                                .id(rs.getLong("id"))
                                .viewName(rs.getString("view_name"))
                                .isDefault(rs.getBoolean("is_default"))
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
        applyViewSort(allRows, view.sortConfig());

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
        applyViewSort(allRows, view.sortConfig());
        int limit = Math.min(Math.max(maxRows, 1), 10000);
        List<Map<String, Object>> slice = allRows.size() <= limit ? allRows : allRows.subList(0, limit);

        StringBuilder sb = new StringBuilder();
        sb.append(csvEscape(PROCESS_INSTANCE_ID_FIELD));
        if (!columns.isEmpty()) {
            sb.append(',');
        }
        sb.append(columns.stream().map(c -> csvEscape(c.displayLabel())).collect(Collectors.joining(",")));
        sb.append('\n');
        for (Map<String, Object> row : slice) {
            Map<String, Object> values = stripInternalKeys(row);
            sb.append(csvEscape(String.valueOf(row.get("_processInstanceId"))));
            if (!columns.isEmpty()) {
                sb.append(',');
            }
            sb.append(columns.stream()
                    .map(c -> csvEscape(formatCell(values.get(c.fieldName()))))
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

        List<String[]> parsedRows = parseCsvRows(csvBytes);
        if (parsedRows.isEmpty()) {
            throw new IllegalArgumentException("CSV has no data rows");
        }

        String[] headers = parsedRows.get(0);
        int processIdCol = findProcessInstanceIdColumn(headers);

        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (int rowIdx = 1; rowIdx < parsedRows.size(); rowIdx++) {
            String[] cells = parsedRows.get(rowIdx);
            if (cells.length == 0 || isBlankRow(cells)) {
                skipped++;
                continue;
            }
            String processInstanceId = processIdCol >= 0 ? cellValue(cells, processIdCol) : "";
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
                String raw = cellValue(cells, col);
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
            String raw = cellValue(cells, col);
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
        Pageable pageable = PageRequest.of(0, 5000, Sort.by(Sort.Direction.DESC, "startTime"));
        Page<ProcessInstance> instances = processInstanceRepository
                .findByFunctionUnitCodeAndStartUserIdOrderByStartTimeDesc(view.functionUnitCode(), userId, pageable);

        List<Map<String, Object>> rows = new ArrayList<>();
        String needle = search != null ? search.trim().toLowerCase(Locale.ROOT) : null;

        for (ProcessInstance pi : instances.getContent()) {
            Map<String, Object> row = projectInstanceRow(pi, view);
            if (!matchesFilter(row, view.filterConfig())) {
                continue;
            }
            if (needle != null && !needle.isEmpty() && !matchesSearch(row, needle)) {
                continue;
            }
            rows.add(row);
        }
        return rows;
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

    @SuppressWarnings("unchecked")
    private boolean matchesFilter(Map<String, Object> row, Map<String, Object> filterConfig) {
        if (filterConfig == null || filterConfig.isEmpty()) {
            return true;
        }
        return matchesFilterNode(row, filterConfig);
    }

    @SuppressWarnings("unchecked")
    private boolean matchesFilterNode(Map<String, Object> row, Map<String, Object> node) {
        if (node == null || node.isEmpty()) {
            return true;
        }
        String logic = stringVal(node.get("logic"));
        boolean useOr = "or".equalsIgnoreCase(logic);

        Object conditionsObj = node.get("conditions");
        List<Map<String, Object>> conditions = new ArrayList<>();
        if (conditionsObj instanceof List<?> rawConditions) {
            for (Object condObj : rawConditions) {
                if (condObj instanceof Map<?, ?> cond) {
                    conditions.add((Map<String, Object>) cond);
                }
            }
        }

        Object groupsObj = node.get("groups");
        List<Map<String, Object>> groups = new ArrayList<>();
        if (groupsObj instanceof List<?> rawGroups) {
            for (Object groupObj : rawGroups) {
                if (groupObj instanceof Map<?, ?> group) {
                    groups.add((Map<String, Object>) group);
                }
            }
        }

        if (conditions.isEmpty() && groups.isEmpty()) {
            return true;
        }

        if (useOr) {
            for (Map<String, Object> cond : conditions) {
                if (evaluateFilterCondition(row, cond)) {
                    return true;
                }
            }
            for (Map<String, Object> group : groups) {
                if (matchesFilterNode(row, group)) {
                    return true;
                }
            }
            return false;
        }

        for (Map<String, Object> cond : conditions) {
            if (!evaluateFilterCondition(row, cond)) {
                return false;
            }
        }
        for (Map<String, Object> group : groups) {
            if (!matchesFilterNode(row, group)) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluateFilterCondition(Map<String, Object> row, Map<String, Object> cond) {
        String fieldName = stringVal(cond.get("fieldName"));
        String operator = stringVal(cond.get("operator"));
        Object expected = cond.get("value");
        Object actual = row.get(fieldName);
        return evaluateCondition(actual, operator, expected);
    }

    private boolean evaluateCondition(Object actual, String operator, Object expected) {
        if (operator == null || operator.isBlank()) {
            return true;
        }
        String op = operator.trim();
        if ("isNull".equals(op)) {
            return actual == null || String.valueOf(actual).isBlank();
        }
        if ("isNotNull".equals(op)) {
            return actual != null && !String.valueOf(actual).isBlank();
        }
        if (actual == null) {
            return false;
        }
        String actualStr = String.valueOf(actual);
        String expectedStr = expected != null ? String.valueOf(expected) : "";
        return switch (op) {
            case "eq" -> actualStr.equalsIgnoreCase(expectedStr);
            case "ne" -> !actualStr.equalsIgnoreCase(expectedStr);
            case "contains" -> actualStr.toLowerCase(Locale.ROOT).contains(expectedStr.toLowerCase(Locale.ROOT));
            case "notContains" -> !actualStr.toLowerCase(Locale.ROOT).contains(expectedStr.toLowerCase(Locale.ROOT));
            case "startsWith" -> actualStr.toLowerCase(Locale.ROOT).startsWith(expectedStr.toLowerCase(Locale.ROOT));
            case "notStartsWith" -> !actualStr.toLowerCase(Locale.ROOT).startsWith(expectedStr.toLowerCase(Locale.ROOT));
            case "endsWith" -> actualStr.toLowerCase(Locale.ROOT).endsWith(expectedStr.toLowerCase(Locale.ROOT));
            case "notEndsWith" -> !actualStr.toLowerCase(Locale.ROOT).endsWith(expectedStr.toLowerCase(Locale.ROOT));
            case "gt" -> compareAsDouble(actualStr, expectedStr) > 0;
            case "lt" -> compareAsDouble(actualStr, expectedStr) < 0;
            case "in" -> {
                if (expected instanceof Collection<?> col) {
                    yield col.stream().anyMatch(v -> actualStr.equalsIgnoreCase(String.valueOf(v)));
                }
                yield Arrays.stream(expectedStr.split(","))
                        .map(String::trim)
                        .anyMatch(v -> actualStr.equalsIgnoreCase(v));
            }
            default -> true;
        };
    }

    private int compareAsDouble(String a, String b) {
        try {
            return Double.compare(Double.parseDouble(a), Double.parseDouble(b));
        } catch (NumberFormatException e) {
            return a.compareToIgnoreCase(b);
        }
    }

    private boolean matchesSearch(Map<String, Object> row, String needle) {
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey().startsWith("_")) {
                continue;
            }
            if (e.getValue() != null && String.valueOf(e.getValue()).toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void applyViewSort(List<Map<String, Object>> rows, List<Map<String, Object>> sortConfig) {
        if (sortConfig == null || sortConfig.isEmpty()) {
            return;
        }
        rows.sort((a, b) -> {
            for (Map<String, Object> spec : sortConfig) {
                String field = stringVal(spec.get("fieldName"));
                if (field == null) {
                    continue;
                }
                Object va = a.get(field);
                Object vb = b.get(field);
                int cmp = compareSortValues(va, vb);
                if (cmp != 0) {
                    String dir = stringVal(spec.get("direction"));
                    return "DESC".equalsIgnoreCase(dir) ? -cmp : cmp;
                }
            }
            return 0;
        });
    }

    private int compareSortValues(Object a, Object b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        if (a instanceof LocalDateTime ta && b instanceof LocalDateTime tb) {
            return ta.compareTo(tb);
        }
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue());
        }
        return String.valueOf(a).compareToIgnoreCase(String.valueOf(b));
    }

    private List<MainTableViewFieldColumn> visibleColumns(ViewDefinition view) {
        return view.fields().stream()
                .filter(f -> Boolean.TRUE.equals(f.visible()))
                .sorted(Comparator.comparingInt(f -> f.sortOrder() != null ? f.sortOrder() : 0))
                .map(f -> MainTableViewFieldColumn.builder()
                        .fieldName(f.fieldName())
                        .displayLabel(f.displayLabel() != null ? f.displayLabel() : f.fieldName())
                        .columnWidth(f.columnWidth())
                        .systemField(f.systemField())
                        .build())
                .toList();
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
                       v.filter_config::text AS filter_config, fu.code AS fu_code
                FROM dw_main_table_view_configs v
                INNER JOIN dw_function_units fu ON fu.id = v.function_unit_id
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

        return new ViewDefinition(
                viewId,
                stringVal(row.get("view_name")),
                stringVal(row.get("fu_code")),
                parseJsonList(stringVal(row.get("sort_config"))),
                parseJsonMap(stringVal(row.get("filter_config"))),
                fields);
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

    private String formatCell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime dt) {
            return DT_FMT.format(dt);
        }
        return String.valueOf(value);
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
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

    private int findProcessInstanceIdColumn(String[] headers) {
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i] != null ? headers[i].trim() : "";
            if (PROCESS_INSTANCE_ID_HEADERS.contains(h)) {
                return i;
            }
        }
        return -1;
    }

    private List<String[]> parseCsvRows(byte[] csvBytes) {
        String text = new String(csvBytes, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        List<String[]> rows = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else if (c == '\r') {
                // skip
            } else if (c == '\n') {
                fields.add(current.toString());
                current.setLength(0);
                if (!fields.isEmpty()) {
                    rows.add(fields.toArray(new String[0]));
                }
                fields = new ArrayList<>();
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0 || !fields.isEmpty()) {
            fields.add(current.toString());
            if (!fields.isEmpty()) {
                rows.add(fields.toArray(new String[0]));
            }
        }
        return rows;
    }

    private boolean isBlankRow(String[] cells) {
        for (String cell : cells) {
            if (cell != null && !cell.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String cellValue(String[] cells, int index) {
        if (index < 0 || index >= cells.length || cells[index] == null) {
            return "";
        }
        return cells[index].trim();
    }

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
            List<ViewFieldDef> fields) {}
}
