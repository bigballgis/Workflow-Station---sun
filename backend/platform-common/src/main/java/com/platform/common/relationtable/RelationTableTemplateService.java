package com.platform.common.relationtable;

import com.platform.common.dto.RelationFieldDTO;
import com.platform.common.enums.RelationDataType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates import templates and parses uploaded import files (CSV / XLSX) for Relation Tables.
 *
 * <p>Templates and import files share the same column layout: one header row whose cells are the
 * importable <em>field names</em> (system-managed fields excluded). Parsing yields a list of
 * field-name → cell-string maps, which {@link RelationRowValidator} then validates/coerces.
 *
 * <p>Stateless and dependency-free beyond Apache POI; instantiate or use as a Spring bean in any app.
 */
public class RelationTableTemplateService {

    public static final String FORMAT_CSV = "csv";
    public static final String FORMAT_XLSX = "xlsx";

    /** Maximum number of data rows accepted in a single import (files with more are rejected). */
    public static final int MAX_IMPORT_ROWS = 1000;

    /** Build an import template containing only a header row of importable field names. */
    public byte[] generateTemplate(List<RelationFieldDTO> fields, String format) {
        List<RelationFieldDTO> cols = RelationRowValidator.importableFields(fields);
        if (FORMAT_XLSX.equalsIgnoreCase(format)) {
            return generateXlsxTemplate(cols);
        }
        return generateCsvTemplate(cols);
    }

    private byte[] generateCsvTemplate(List<RelationFieldDTO> cols) {
        StringBuilder header = new StringBuilder();
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) header.append(',');
            header.append(escapeCsv(headerLabel(cols.get(i))));
        }
        // Single header row only; the type/length hint is embedded in each header label.
        return (header + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private byte[] generateXlsxTemplate(List<RelationFieldDTO> cols) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Import");
            Row header = sheet.createRow(0);
            for (int i = 0; i < cols.size(); i++) {
                header.createCell(i).setCellValue(headerLabel(cols.get(i)));
                sheet.autoSizeColumn(i);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate XLSX template: " + e.getMessage(), e);
        }
    }

    /**
     * Header label shown to the user: {@code fieldName (TYPE(len) *required)} — the type/length hint is
     * embedded directly in the header so there is no separate hint row. {@link #fieldNameFromHeader}
     * strips this annotation back to the bare field name when parsing an uploaded file.
     */
    private String headerLabel(RelationFieldDTO f) {
        return f.getFieldName() + " (" + typeHint(f) + ")";
    }

    private String typeHint(RelationFieldDTO f) {
        RelationDataType t = f.getDataType() != null ? f.getDataType() : RelationDataType.VARCHAR;
        StringBuilder sb = new StringBuilder(t.getCode());
        if (f.getLength() != null && f.getLength() > 0) sb.append('(').append(f.getLength()).append(')');
        if (Boolean.FALSE.equals(f.getNullable()) || Boolean.TRUE.equals(f.getIsPrimaryKey())) sb.append(" *required");
        return sb.toString();
    }

    /**
     * Recover the bare field name from a (possibly annotated) header cell. Field names are slug-like
     * ({@code ^[a-z][a-z0-9_]*$}) so they never contain a space; everything from the first space — i.e.
     * the {@code (TYPE …)} annotation our own template adds — is dropped. Plain headers pass through.
     */
    static String fieldNameFromHeader(String header) {
        if (header == null) return null;
        String trimmed = header.trim();
        int sp = trimmed.indexOf(' ');
        return sp >= 0 ? trimmed.substring(0, sp) : trimmed;
    }

    /**
     * Parse an uploaded file into rows of field-name → cell-string maps. The first row is treated
     * as headers; rows whose first cell starts with '#' (hint rows) and fully blank rows are skipped.
     */
    public List<Map<String, Object>> parseImport(byte[] bytes, String format) {
        if (FORMAT_XLSX.equalsIgnoreCase(format)) {
            return parseXlsx(bytes);
        }
        return parseCsv(bytes);
    }

    private List<Map<String, Object>> parseCsv(byte[] bytes) {
        String content = new String(bytes, StandardCharsets.UTF_8);
        // Normalize line endings; strip a leading UTF-8 BOM if present.
        if (content.startsWith("﻿")) content = content.substring(1);
        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> headers = null;
        for (String line : lines) {
            if (line.isEmpty()) continue;
            List<String> cells = parseCsvLine(line);
            if (headers == null) {
                headers = cells;
                continue;
            }
            if (!cells.isEmpty() && cells.get(0).startsWith("#")) continue; // hint row
            if (cells.stream().allMatch(c -> c == null || c.isBlank())) continue; // blank row
            rows.add(toRowMap(headers, cells));
        }
        return rows;
    }

    private List<Map<String, Object>> parseXlsx(byte[] bytes) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (InputStream in = new ByteArrayInputStream(bytes); Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) return rows;
            List<String> headers = null;
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                int last = row.getLastCellNum();
                for (int c = 0; c < last; c++) {
                    Cell cell = row.getCell(c);
                    cells.add(cell == null ? "" : cellToString(cell));
                }
                if (headers == null) {
                    headers = cells;
                    continue;
                }
                if (!cells.isEmpty() && cells.get(0).startsWith("#")) continue;
                if (cells.stream().allMatch(c -> c == null || c.isBlank())) continue;
                rows.add(toRowMap(headers, cells));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse XLSX: " + e.getMessage(), e);
        }
        return rows;
    }

    private Map<String, Object> toRowMap(List<String> headers, List<String> cells) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            // Headers may carry a "(TYPE …)" annotation from our template; map back to the bare field name.
            String key = fieldNameFromHeader(headers.get(i));
            if (key == null || key.isEmpty()) continue;
            String value = i < cells.size() ? cells.get(i) : null;
            map.put(key, value);
        }
        return map;
    }

    private String cellToString(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case NUMERIC -> {
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                }
                double d = cell.getNumericCellValue();
                yield (d == Math.floor(d) && !Double.isInfinite(d))
                        ? String.valueOf((long) d) : String.valueOf(d);
            }
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    // --- minimal RFC-4180-ish CSV parsing (quotes, escaped quotes, embedded commas) ---
    private List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
