package com.developer.util;

import com.developer.entity.FieldDefinition;
import com.developer.enums.DataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Generates mock multi-instance collection rows from sub-table field metadata (design-time debug).
 */
public final class DebugMockCollectionGenerator {

    private static final int DEFAULT_INSTANCE_COUNT = 3;

    private static final Pattern ASSIGNEE_FIELD = Pattern.compile(
            "^(assignee|assignee_id|assignee_user_id|user_id|handler|owner_user_id|approver)$",
            Pattern.CASE_INSENSITIVE);

    private DebugMockCollectionGenerator() {
    }

    public static int defaultInstanceCount() {
        return DEFAULT_INSTANCE_COUNT;
    }

    public static List<Map<String, Object>> generate(List<FieldDefinition> fields, int instanceCount) {
        int count = instanceCount > 0 ? instanceCount : DEFAULT_INSTANCE_COUNT;
        List<FieldDefinition> safeFields = fields != null ? fields : List.of();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            rows.add(buildRow(safeFields, i));
        }
        return rows;
    }

    private static Map<String, Object> buildRow(List<FieldDefinition> fields, int index) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("rowId", "debug-row-" + index);

        for (FieldDefinition field : fields) {
            if (field == null || field.getFieldName() == null || field.getFieldName().isBlank()) {
                continue;
            }
            String name = field.getFieldName().trim();
            if ("rowId".equalsIgnoreCase(name)) {
                continue;
            }
            if (ASSIGNEE_FIELD.matcher(name).matches()) {
                row.put(name, "debug-user-" + index);
                continue;
            }
            if (!row.containsKey(name)) {
                row.put(name, mockValue(field, index));
            }
        }

        row.putIfAbsent("assignee_id", "debug-user-" + index);
        return row;
    }

    private static Object mockValue(FieldDefinition field, int index) {
        DataType type = field.getDataType();
        if (type == null) {
            return "sample-" + index;
        }
        return switch (type) {
            case INTEGER, BIGINT -> index;
            case DECIMAL -> 100.0 + index;
            case BOOLEAN -> index % 2 == 1;
            case DATE -> "2026-01-0" + Math.min(index, 9);
            case TIME -> "09:00:00";
            case TIMESTAMP -> "2026-01-0" + Math.min(index, 9) + " 10:00:00";
            case JSON -> Map.of("debug", true, "index", index);
            case FILE -> "sample-document-" + index + ".pdf";
            case BYTEA -> "";
            case TEXT -> "Sample text " + index;
            default -> "sample-" + index;
        };
    }
}
