package com.platform.common.list;

import com.platform.common.list.ListColumnFilter;
import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListFileNameSqlTest {

    private final List<Object> params = new ArrayList<>();

    private ListFilterSql sql() {
        Map<String, ListColumnMeta> byField = new LinkedHashMap<>();
        byField.put("file", ListColumnMeta.of("file", "File", Kind.FILE));
        byField.put("name", ListColumnMeta.of("name", "Name", Kind.TEXT));
        return ListFilterSql.orderedById(byField, ListFilterSql.JSON_ROW);
    }

    private String where(String operator, String value) {
        return sql().whereClause(List.of(new ListColumnFilter("file", operator, value, null)), params);
    }

    @Test
    void jsonbMemberRefKeepsTheObjectNotTheTextDump() {
        assertEquals("data->'file'", ListFileNameSql.jsonbOf("data->>'file'"));
        assertEquals("pi.variables->'scan'", ListFileNameSql.jsonbOf("pi.variables->>'scan'"));
        assertEquals("to_jsonb(scan)", ListFileNameSql.jsonbOf("scan"));
    }

    @Test
    void containsComparesExtractedNamesNotTheRawUrl() {
        String clause = where("contains", "report");
        assertFalse(clause.contains("data->>'file' ILIKE"), clause);
        assertTrue(clause.contains("data->'file'"), clause);
        assertTrue(clause.contains("originalName"), clause);
        assertTrue(clause.contains("upload/files"), clause);
        assertTrue(clause.contains("ESCAPE"), clause);
        assertEquals(List.of("%report%"), params);
    }

    @Test
    void containsEscapesATypedPercentSoItIsLiteral() {
        where("contains", "50%");
        assertEquals(List.of("%50\\%%"), params);
    }

    @Test
    void containsDoesNotIlikeTheUuidPathSegmentAsIfItWereTheFilename() {
        String clause = where("contains", "abc123");
        assertFalse(clause.contains("data->>'file' ILIKE"), clause);
        assertTrue(clause.contains("originalName"), clause);
        assertEquals(List.of("%abc123%"), params);
    }

    @Test
    void objectNameBranchIsInTheCompiledSql() {
        String clause = where("contains", "合同");
        assertTrue(clause.contains("elem->>'name'"), clause);
        assertTrue(clause.contains("elem->>'url'"), clause);
        assertEquals(List.of("%合同%"), params);
    }

    @Test
    void emptyFileCellIsNoExtractedName() {
        String clause = where("isNull", null);
        assertTrue(clause.startsWith(" AND (NOT EXISTS"), clause);
        assertTrue(params.isEmpty());
    }

    @Test
    void hasDataRequiresAnExtractedName() {
        String clause = where("isNotNull", null);
        assertTrue(clause.startsWith(" AND EXISTS"), clause);
        assertFalse(clause.contains("data->>'file' IS NOT NULL"), clause);
    }

    @Test
    void notContainsAndNeTreatNoNamesAsAMatch() {
        String notContains = where("notContains", "a");
        assertTrue(notContains.contains("NOT EXISTS"), notContains);
        params.clear();
        String ne = where("ne", "a.pdf");
        assertTrue(ne.contains("NOT EXISTS"), ne);
        assertTrue(ne.contains("file_names.n = ?"), ne);
    }

    @Test
    void sortOrdersByTheExtractedFilename() {
        String order = sql().orderBy("file", "ASC");
        assertTrue(order.contains("min(file_names.n)"), order);
        assertFalse(order.contains("ORDER BY data->>'file'"), order);
    }

    @Test
    void keywordSearchOnAFileColumnUsesFilenamesNotTheJsonText() {
        String clause = sql().searchClause("report", List.of("file", "name"), params);
        assertTrue(clause.contains("originalName"), clause);
        assertTrue(clause.contains("data->>'name'" + ListFilterSql.ILIKE), clause);
        assertFalse(clause.contains("data->>'file' ILIKE"), clause);
        assertEquals(List.of("%report%", "%report%"), params);
    }
}
