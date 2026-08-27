package com.admin.list;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import com.platform.common.dto.RelationFieldDTO;
import com.platform.common.enums.RelationDataType;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelationTableColumnSpecTest {

    private RelationFieldDTO field(String name, RelationDataType type, String displayName) {
        return RelationFieldDTO.builder()
                .fieldName(name)
                .dataType(type)
                .displayName(displayName)
                .build();
    }

    @Test
    void mapsDataTypesToKinds() {
        List<ListColumnMeta> columns = RelationTableColumnSpec.columnsFor(List.of(
                field("name", RelationDataType.VARCHAR, "Name"),
                field("notes", RelationDataType.TEXT, "Notes"),
                field("ref_user", RelationDataType.LOOKUP, "User"),
                field("qty", RelationDataType.INTEGER, "Qty"),
                field("amount", RelationDataType.DECIMAL, "Amount"),
                field("active", RelationDataType.BOOLEAN, "Active"),
                field("due_date", RelationDataType.DATE, "Due"),
                field("created_at", RelationDataType.TIMESTAMP, "Created")));
        assertEquals(Kind.TEXT, byField(columns, "name").kind());
        assertEquals(Kind.TEXT, byField(columns, "notes").kind());
        assertEquals(Kind.TEXT, byField(columns, "ref_user").kind());
        assertEquals(Kind.NUMBER, byField(columns, "qty").kind());
        assertEquals(Kind.NUMBER, byField(columns, "amount").kind());
        assertEquals(Kind.BOOLEAN, byField(columns, "active").kind());
        assertEquals(Kind.DATETIME, byField(columns, "due_date").kind());
        assertEquals(Kind.DATETIME, byField(columns, "created_at").kind());
    }

    @Test
    void blobTypesAreDisplayOnly() {
        List<ListColumnMeta> columns = RelationTableColumnSpec.columnsFor(List.of(
                field("blob", RelationDataType.BYTEA, "Blob"),
                field("doc", RelationDataType.FILE, "Doc"),
                field("name", RelationDataType.VARCHAR, "Name")));
        for (String f : List.of("blob", "doc")) {
            ListColumnMeta col = byField(columns, f);
            assertFalse(col.filterable(), f);
            assertFalse(col.sortable(), f);
            assertTrue(col.operators().isEmpty(), f);
        }
        assertTrue(byField(columns, "name").filterable());
    }

    @Test
    void timeJsonAndUntypedFieldsAreQueryable() {
        List<ListColumnMeta> columns = RelationTableColumnSpec.columnsFor(List.of(
                field("cfg", RelationDataType.JSON, "Config"),
                field("at", RelationDataType.TIME, "At"),
                field("legacy", null, "Legacy")));
        assertEquals(Kind.TEXT, byField(columns, "cfg").kind());
        assertTrue(byField(columns, "cfg").filterable());
        assertEquals(Kind.DATETIME, byField(columns, "at").kind());
        assertTrue(byField(columns, "at").filterable());
        assertEquals(Kind.TEXT, byField(columns, "legacy").kind());
        assertTrue(byField(columns, "legacy").filterable());
    }

    @Test
    void statusIsExcludedFromTheDataGrid() {
        List<ListColumnMeta> columns = RelationTableColumnSpec.columnsFor(List.of(
                field("status", RelationDataType.VARCHAR, "Status"),
                field("active", RelationDataType.BOOLEAN, "Active"),
                field("name", RelationDataType.VARCHAR, "Name")));
        assertEquals(List.of("active", "name"), columns.stream().map(ListColumnMeta::field).toList());
    }

    @Test
    void auditTimestampDeclaredAsVarcharIsStillADateFilter() {
        ListColumnMeta created = byField(RelationTableColumnSpec.columnsFor(List.of(
                field("created_at", RelationDataType.VARCHAR, "Created At"),
                field("name", RelationDataType.VARCHAR, "Name"))), "created_at");
        assertEquals(Kind.DATETIME, created.kind());
        assertEquals("today", created.operators().get(0));
    }

    @Test
    void auditUserDeclaredAsVarcharIsStillAPeopleFilter() {
        ListColumnMeta createdBy = byField(RelationTableColumnSpec.columnsFor(List.of(
                field("created_by", RelationDataType.VARCHAR, "Created By"),
                field("name", RelationDataType.VARCHAR, "Name"))), "created_by");
        assertEquals(Kind.USER, createdBy.kind());
        assertEquals(List.of("eq", "ne", "contains", "notContains", "isNotNull", "isNull"),
                createdBy.operators());
    }

    @Test
    void booleanColumnCarriesTrueFalseOptions() {
        ListColumnMeta active = byField(RelationTableColumnSpec.columnsFor(List.of(
                field("active", RelationDataType.BOOLEAN, "Active"))), "active");
        assertEquals(List.of("true", "false"),
                active.options().stream().map(ListColumnMeta.Option::value).toList());
    }

    @Test
    void labelFallsBackToFieldName() {
        List<ListColumnMeta> columns = RelationTableColumnSpec.columnsFor(List.of(
                field("raw_code", RelationDataType.VARCHAR, "  ")));
        assertEquals("raw_code", byField(columns, "raw_code").label());
        assertTrue(byField(columns, "raw_code").filterable());
    }

    @Test
    void rejectsTablesWithoutDisplayableFields() {
        assertThrows(IllegalStateException.class, () -> RelationTableColumnSpec.columnsFor(List.of(
                field("status", RelationDataType.VARCHAR, "Status"))));
    }

    private ListColumnMeta byField(List<ListColumnMeta> columns, String field) {
        return columns.stream().filter(c -> c.field().equals(field)).findFirst().orElseThrow();
    }
}
