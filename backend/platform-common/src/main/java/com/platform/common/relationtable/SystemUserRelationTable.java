package com.platform.common.relationtable;

import com.platform.common.dto.RelationFieldDTO;
import com.platform.common.enums.RelationDataType;
import com.platform.common.functionunit.FunctionUnitVersionException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stable platform relation identity; packages carry schema only, never user rows. */
public final class SystemUserRelationTable {
    public static final long ID = -1_000_000_001L;
    public static final String NAME = "sys_users";

    private SystemUserRelationTable() { }

    public static List<RelationFieldDTO> fields() {
        return List.of(field(1, "id", "User ID"), field(2, "username", "Username"),
                field(3, "display_name", "Display Name"), field(4, "full_name", "Full Name"),
                field(5, "email", "Email"), field(6, "employee_id", "Employee ID"),
                field(7, "status", "Status"), field(8, "language", "Language"));
    }

    private static RelationFieldDTO field(int order, String name, String label) {
        return RelationFieldDTO.builder().id((long) -order).fieldName(name)
                .dataType(RelationDataType.VARCHAR).length(255).nullable(order != 1)
                .isPrimaryKey(order == 1).displayName(label).sortOrder(order).build();
    }

    public static Map<String, Object> snapshot() {
        List<Map<String, Object>> schema = fields().stream().map(field -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fieldName", field.getFieldName());
            result.put("dataType", field.getDataType().name());
            result.put("length", field.getLength());
            result.put("nullable", field.getNullable());
            result.put("isPrimaryKey", field.getIsPrimaryKey());
            result.put("isForeignKey", false);
            result.put("isComputed", false);
            result.put("displayName", field.getDisplayName());
            result.put("sortOrder", field.getSortOrder());
            return result;
        }).toList();
        return Map.of("relationTableId", ID, "tableName", NAME, "displayName", "User", "fields", schema);
    }

    /** Recognize a platform identity, rejecting conflicting reserved names or ids. */
    public static boolean matches(Map<String, Object> table) {
        boolean id = table.get("relationTableId") instanceof Number number && number.longValue() == ID;
        boolean name = NAME.equals(table.get("tableName"));
        if (id != name) throw new FunctionUnitVersionException("VERSION_SNAPSHOT_INCOMPLETE", null);
        if (id && !snapshot().get("fields").equals(table.get("fields"))) {
            throw new FunctionUnitVersionException("VERSION_SNAPSHOT_INCOMPLETE", null);
        }
        return id;
    }
}
