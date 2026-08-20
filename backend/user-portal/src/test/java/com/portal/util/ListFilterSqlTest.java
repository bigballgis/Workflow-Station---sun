package com.portal.util;

import com.portal.dto.ListColumnFilter;
import com.portal.dto.PortalListColumnMeta;
import com.portal.dto.PortalListColumnMeta.Kind;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListFilterSqlTest {

    private final List<Object> params = new ArrayList<>();

    private Map<String, PortalListColumnMeta> columns() {
        Map<String, PortalListColumnMeta> byField = new LinkedHashMap<>();
        byField.put("name", filterable("name", Kind.TEXT));
        byField.put("amount", filterable("amount", Kind.NUMBER));
        byField.put("created_at", filterable("created_at", Kind.DATETIME));
        byField.put("created_by", filterable("created_by", Kind.USER));
        byField.put("active", filterable("active", Kind.BOOLEAN));
        byField.put("payload", PortalListColumnMeta.displayOnly("payload", "Payload", Kind.TEXT));
        return byField;
    }

    private PortalListColumnMeta filterable(String field, Kind kind) {
        if (kind == Kind.BOOLEAN) {
            return PortalListColumnMeta.of(field, field, kind);
        }
        return new PortalListColumnMeta(field, field, kind, true, true, false,
                PortalListColumnMeta.operatorsFor(kind), List.of());
    }

    private ListFilterSql jsonRow() {
        return ListFilterSql.orderedById(columns(), ListFilterSql.JSON_ROW);
    }

    /** Compile one filter over the JSON row store, collecting bind values in {@link #params}. */
    private String where(String field, String operator, String value, String value2) {
        return jsonRow().whereClause(List.of(new ListColumnFilter(field, operator, value, value2)), params);
    }

    private String sortBy(String field, String direction) {
        return jsonRow().orderBy(field, direction);
    }

    // ---- where clause ----

    @Test
    void textContainsCompilesToIlikeWithEscapedWildcards() {
        assertEquals(" AND data->>'name' ILIKE ?", where("name", "contains", "50%_a", null));
        assertEquals(List.of("%50\\%\\_a%"), params);
    }

    @Test
    void textNeAlsoMatchesNullRows() {
        assertEquals(" AND (data->>'name' IS NULL OR data->>'name' <> ?)", where("name", "ne", "x", null));
    }

    @Test
    void isNullTakesNoParams() {
        assertEquals(" AND (data->>'name' IS NULL OR data->>'name' = '')",
                where("name", "isNull", null, null));
        assertTrue(params.isEmpty());
        params.clear();
        assertEquals(" AND (data->>'active' IS NULL OR data->>'active' = '')",
                where("active", "isNull", null, null));
        assertTrue(params.isEmpty());
    }

    @Test
    void userEqMatchesAnyStoredIdentityOfTheSelectedSysUser() {
        String sql = where("created_by", "eq", "user-dev", null);
        assertTrue(sql.contains("EXISTS (SELECT 1 FROM sys_users u WHERE u.id = ?"));
        assertTrue(sql.contains("data->>'created_by' = u.display_name"));
        assertTrue(sql.contains("data->>'created_by' = u.employee_id"));
        assertEquals(List.of("user-dev"), params);
    }

    @Test
    void userNeNegatesTheIdentityMatch() {
        String sql = where("created_by", "ne", "user-dev", null);
        assertTrue(sql.startsWith(" AND (NOT EXISTS"));
        assertEquals(List.of("user-dev"), params);
    }

    @Test
    void numberGtGuardsNonNumericStoredValues() {
        String sql = where("amount", "gt", "500", null);
        assertTrue(sql.contains("data->>'amount' ~ '^-?[0-9]+(\\.[0-9]+)?$'"));
        assertTrue(sql.contains("(data->>'amount')::numeric > ?"));
        assertEquals(List.of(new BigDecimal("500")), params);
    }

    @Test
    void numberBetweenBindsBothBounds() {
        where("amount", "between", "10", "20");
        assertEquals(List.of(new BigDecimal("10"), new BigDecimal("20")), params);
    }

    @Test
    void numberRejectsNonNumericInput() {
        assertThrows(IllegalArgumentException.class, () -> where("amount", "gt", "abc", null));
    }

    @Test
    void dateOnComparesFirstTenChars() {
        assertEquals(" AND left(data->>'created_at', 10) = ?",
                where("created_at", "on", "2026-08-17", null));
        assertEquals(List.of("2026-08-17"), params);
    }

    @Test
    void dateBetweenIsInclusiveOnBothDays() {
        assertEquals(" AND (left(data->>'created_at', 10) >= ? AND left(data->>'created_at', 10) <= ?)",
                where("created_at", "between", "2026-08-01", "2026-08-17"));
        assertEquals(List.of("2026-08-01", "2026-08-17"), params);
    }

    @Test
    void dateRejectsNonIsoValue() {
        assertThrows(IllegalArgumentException.class, () -> where("created_at", "on", "17/08/2026", null));
    }

    @Test
    void relativeTodayExpandsAgainstTheClockCalendarDayWithoutAValue() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-19T08:00:00Z"), ListRelativeDates.ZONE);
        ListFilterSql sql = new ListFilterSql(columns(), ListFilterSql.JSON_ROW, "id", null, clock);
        String clause = sql.whereClause(
                List.of(new ListColumnFilter("created_at", "today", "", null)), params);
        assertEquals(" AND (left(data->>'created_at', 10) >= ? AND left(data->>'created_at', 10) <= ?)",
                clause);
        assertEquals(List.of("2026-08-19", "2026-08-19"), params);
    }

    @Test
    void relativeThisWeekIsMondayThroughSundayInShanghai() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-19T08:00:00Z"), ListRelativeDates.ZONE);
        ListFilterSql sql = new ListFilterSql(columns(), ListFilterSql.JSON_ROW, "id", null, clock);
        sql.whereClause(List.of(new ListColumnFilter("created_at", "thisWeek", null, null)), params);
        assertEquals(List.of("2026-08-17", "2026-08-23"), params);
    }

    @Test
    void relativeLast7DaysIncludesToday() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-19T08:00:00Z"), ListRelativeDates.ZONE);
        ListFilterSql sql = new ListFilterSql(columns(), ListFilterSql.JSON_ROW, "id", null, clock);
        sql.whereClause(List.of(new ListColumnFilter("created_at", "last7days", null, null)), params);
        assertEquals(List.of("2026-08-13", "2026-08-19"), params);
    }

    @Test
    void booleanEqNormalizesCase() {
        assertEquals(" AND lower(data->>'active') = ?", where("active", "eq", "TRUE", null));
        assertEquals(List.of("true"), params);
    }

    @Test
    void booleanNeIncludesEmptyCells() {
        assertEquals(" AND (data->>'active' IS NULL OR lower(data->>'active') <> ?)",
                where("active", "ne", "true", null));
        assertEquals(List.of("true"), params);
    }

    @Test
    void booleanRejectsNonBooleanValue() {
        assertThrows(IllegalArgumentException.class, () -> where("active", "eq", "yes", null));
    }

    @Test
    void multipleFiltersAreAnded() {
        String sql = jsonRow().whereClause(List.of(
                new ListColumnFilter("name", "contains", "a", null),
                new ListColumnFilter("amount", "lte", "9", null)), params);
        assertTrue(sql.startsWith(" AND data->>'name' ILIKE ?"));
        assertTrue(sql.contains(" AND (data->>'amount'"));
        assertEquals(2, params.size());
    }

    @Test
    void rejectsUnknownColumnNonFilterableColumnAndForeignOperator() {
        assertThrows(IllegalArgumentException.class, () -> where("ghost", "eq", "x", null));
        assertThrows(IllegalArgumentException.class, () -> where("payload", "eq", "x", null));
        assertThrows(IllegalArgumentException.class, () -> where("name", "gt", "x", null));
    }

    @Test
    void missingRequiredValueIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> where("name", "contains", " ", null));
        assertThrows(IllegalArgumentException.class, () -> where("amount", "between", "1", null));
    }

    // ---- physical columns (built-in System User view) ----

    @Test
    void physicalColumnRefTargetsTheColumnItselfNotTheJsonDocument() {
        ListFilterSql physical = ListFilterSql.orderedById(columns(), ListFilterSql.PHYSICAL_COLUMN);
        assertEquals(" AND name ILIKE ?",
                physical.whereClause(List.of(new ListColumnFilter("name", "contains", "ann", null)), params));
        assertEquals(" ORDER BY name ASC NULLS LAST, id", physical.orderBy("name", "ASC"));
    }

    @Test
    void columnRefRejectsIdentifiersThatCouldCarrySql() {
        assertThrows(IllegalArgumentException.class,
                () -> ListFilterSql.JSON_ROW.sqlFor("name'; DROP TABLE x --"));
        assertThrows(IllegalArgumentException.class,
                () -> ListFilterSql.PHYSICAL_COLUMN.sqlFor("1name"));
    }

    // ---- order by ----

    @Test
    void orderByDefaultsToInsertionOrder() {
        assertEquals(" ORDER BY id", sortBy(null, null));
    }

    @Test
    void orderByTextColumn() {
        assertEquals(" ORDER BY data->>'name' DESC NULLS LAST, id", sortBy("name", "DESC"));
    }

    @Test
    void orderByNumberColumnCastsNumerically() {
        String sql = sortBy("amount", "ASC");
        assertTrue(sql.contains("(data->>'amount')::numeric"));
        assertTrue(sql.endsWith("ASC NULLS LAST, id"));
    }

    @Test
    void orderByRejectsUnknownOrNonSortableColumn() {
        assertThrows(IllegalArgumentException.class, () -> sortBy("ghost", "ASC"));
        assertThrows(IllegalArgumentException.class, () -> sortBy("payload", "ASC"));
    }

    // ---- grouping ----

    @Test
    void groupingLeadsTheOrderSoAGroupsRowsCannotStraddleAPage() {
        Map<String, PortalListColumnMeta> byField = columns();
        byField.put("status", PortalListColumnMeta.withOptions("status", "status", Kind.ENUM,
                List.of(new PortalListColumnMeta.Option("OPEN", "Open"))));
        ListFilterSql sql = ListFilterSql.orderedById(byField, ListFilterSql.JSON_ROW);

        String groupExpression = sql.groupByExpression("status");
        assertEquals("data->>'status'", groupExpression);
        assertEquals(" ORDER BY data->>'status' ASC NULLS LAST, data->>'name' ASC NULLS LAST, id",
                sql.orderByGrouped(groupExpression, "name", "ASC"));
    }

    @Test
    void groupByRejectsUnknownOrNonGroupableColumn() {
        ListFilterSql sql = jsonRow();
        assertThrows(IllegalArgumentException.class, () -> sql.groupByExpression("ghost"));
        // Declared groupable=false: offering it would produce counts the query cannot stand behind.
        assertThrows(IllegalArgumentException.class, () -> sql.groupByExpression("name"));
    }

    @Test
    void aListWithItsOwnDefaultOrderStillEndsOnTheTiebreak() {
        ListFilterSql byStartTime = new ListFilterSql(columns(), ListFilterSql.JSON_ROW,
                "id", "start_time DESC");
        assertEquals(" ORDER BY start_time DESC, id", byStartTime.orderBy(null, null));
        assertEquals(" ORDER BY data->>'name' ASC NULLS LAST, id", byStartTime.orderBy("name", "ASC"));
    }

    // ---- keyword search ----

    @Test
    void searchOrsOneIlikePerSearchableField() {
        String sql = jsonRow().searchClause("ann", List.of("name", "created_at"), params);
        assertEquals(" AND (data->>'name' ILIKE ? OR data->>'created_at' ILIKE ?)", sql);
        assertEquals(List.of("%ann%", "%ann%"), params);
    }

    @Test
    void searchEscapesWildcardsSoTypedPercentMatchesLiterally() {
        jsonRow().searchClause("50%", List.of("name"), params);
        assertEquals(List.of("%50\\%%"), params);
    }

    @Test
    void blankKeywordOrNoSearchableFieldProducesNoClause() {
        assertEquals("", jsonRow().searchClause("  ", List.of("name"), params));
        assertEquals("", jsonRow().searchClause("ann", List.of(), params));
        assertTrue(params.isEmpty());
    }
}
