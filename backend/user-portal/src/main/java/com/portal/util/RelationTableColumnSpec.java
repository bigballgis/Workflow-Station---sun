package com.portal.util;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import com.platform.common.audit.SystemAuditFields;
import com.platform.common.dto.RelationFieldDTO;
import com.platform.common.enums.RelationDataType;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the shared-list column declaration for a relation table from its field
 * definitions ({@code rt_field_definitions} is the authority for both column set and
 * display labels — see {@code getViewFieldsByTableId}). The row-level {@code status}
 * column is a toggle, not a data column, and is excluded here like in the grid.
 */
public final class RelationTableColumnSpec {

    private RelationTableColumnSpec() {
    }

    public static List<ListColumnMeta> columnsFor(List<RelationFieldDTO> fields) {
        List<ListColumnMeta> columns = new ArrayList<>();
        for (RelationFieldDTO field : fields) {
            if (field.getFieldName() == null || "status".equals(field.getFieldName())) {
                continue;
            }
            columns.add(columnFor(field));
        }
        if (columns.isEmpty()) {
            throw new IllegalStateException("relation table declares no displayable fields");
        }
        return columns;
    }

    private static ListColumnMeta columnFor(RelationFieldDTO field) {
        String label = field.getDisplayName() != null && !field.getDisplayName().isBlank()
                ? field.getDisplayName()
                : field.getFieldName();
        RelationDataType dataType = field.getDataType();
        Kind kind = SystemAuditFields.isTimestamp(field.getFieldName())
                ? Kind.DATETIME
                : SystemAuditFields.isUser(field.getFieldName())
                        ? Kind.USER
                        : kindFor(dataType);
        if (kind == null) {
            // FILE / BYTEA are blob references, not a value a user would filter on.
            return ListColumnMeta.displayOnly(field.getFieldName(), label, Kind.TEXT);
        }
        List<ListColumnMeta.Option> options = kind == Kind.BOOLEAN
                ? ListColumnMeta.booleanOptions()
                : List.of();
        return new ListColumnMeta(
                field.getFieldName(), label, kind,
                true, true,
                ListColumnMeta.operatorsFor(kind), options);
    }

    /** @return the filterable kind for a data type, or null when the type is display-only */
    private static Kind kindFor(RelationDataType dataType) {
        if (dataType == null) {
            return Kind.TEXT;
        }
        return switch (dataType) {
            case VARCHAR, TEXT, JSON -> Kind.TEXT;
            // A LOOKUP stores the referenced row's PK as text (or a JSON array when multiple),
            // so text operators over the stored value are the honest capability.
            case LOOKUP -> Kind.TEXT;
            case INTEGER, BIGINT, DECIMAL -> Kind.NUMBER;
            case BOOLEAN -> Kind.BOOLEAN;
            case DATE, TIMESTAMP, TIME -> Kind.DATETIME;
            case BYTEA, FILE -> null;
        };
    }
}
