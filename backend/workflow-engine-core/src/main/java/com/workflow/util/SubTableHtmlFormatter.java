package com.workflow.util;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders {@code __subTables__} row slices as inline-styled HTML tables for workflow email bodies.
 */
public final class SubTableHtmlFormatter {

    private static final Set<String> EXCLUDED_COLUMNS = Set.of(
            "__subTables__",
            "sub_task_id",
            "id_idw",
            "task_status",
            "participant_id",
            "assignee"
    );

    private static final String TABLE_STYLE =
            "border-collapse:collapse;width:100%;font-size:14px;";
    private static final String CELL_STYLE =
            "border:1px solid #dcdfe6;padding:6px 10px;text-align:left;";
    private static final String HEADER_STYLE =
            "border:1px solid #dcdfe6;padding:6px 10px;text-align:left;background:#f5f7fa;font-weight:600;";

    private SubTableHtmlFormatter() {
    }

    /** A column to render: {@code field} reads the row value, {@code header} is the table heading. */
    public record ColumnSpec(String field, String header) {
    }

    /** Renders all business columns (header = field name). */
    public static String format(Map<String, Object> variables, String bindingId) {
        return format(variables, bindingId, null);
    }

    /**
     * Renders the sub-table slice as an HTML table.
     *
     * @param columnSpecs explicit columns (in order) with custom headers; when {@code null}/empty
     *                    all business columns are auto-detected with the field name as the header.
     */
    public static String format(Map<String, Object> variables, String bindingId, List<ColumnSpec> columnSpecs) {
        if (!StringUtils.hasText(bindingId) || variables == null) {
            return emptyTableHtml();
        }
        Object subTablesObj = variables.get("__subTables__");
        if (!(subTablesObj instanceof Map<?, ?> subTables)) {
            return emptyTableHtml();
        }

        Object rowsObj = subTables.get(bindingId.trim());
        if (!(rowsObj instanceof List<?> rows) || rows.isEmpty()) {
            return emptyTableHtml();
        }

        List<Map<String, Object>> rowMaps = new ArrayList<>();
        for (Object row : rows) {
            if (row instanceof Map<?, ?> rowMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) rowMap;
                rowMaps.add(typed);
            }
        }
        if (rowMaps.isEmpty()) {
            return emptyTableHtml();
        }

        List<ColumnSpec> columns = (columnSpecs != null && !columnSpecs.isEmpty())
                ? columnSpecs
                : collectColumns(rowMaps).stream().map(c -> new ColumnSpec(c, c)).toList();
        if (columns.isEmpty()) {
            return emptyTableHtml();
        }

        StringBuilder html = new StringBuilder();
        html.append("<table style=\"").append(TABLE_STYLE).append("\"><thead><tr>");
        for (ColumnSpec column : columns) {
            html.append("<th style=\"").append(HEADER_STYLE).append("\">")
                    .append(escapeHtml(column.header())).append("</th>");
        }
        html.append("</tr></thead><tbody>");

        for (Map<String, Object> row : rowMaps) {
            html.append("<tr>");
            for (ColumnSpec column : columns) {
                html.append("<td style=\"").append(CELL_STYLE).append("\">")
                        .append(escapeHtml(formatCell(row.get(column.field())))).append("</td>");
            }
            html.append("</tr>");
        }

        html.append("</tbody></table>");
        return html.toString();
    }

    private static List<String> collectColumns(List<Map<String, Object>> rows) {
        LinkedHashSet<String> columns = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            for (String key : row.keySet()) {
                if (!EXCLUDED_COLUMNS.contains(key) && !key.startsWith("_")) {
                    columns.add(key);
                }
            }
        }
        return new ArrayList<>(columns);
    }

    private static String formatCell(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString();
    }

    private static String emptyTableHtml() {
        return "<table style=\"" + TABLE_STYLE + "\"><tbody><tr><td style=\"" + CELL_STYLE
                + "\">No data</td></tr></tbody></table>";
    }

    static String escapeHtml(String raw) {
        if (raw == null) {
            return "";
        }
        return raw
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
