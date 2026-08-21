package com.platform.common.jdbc;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SubTableRowIdentityTest {

    private Map<String, Object> row(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return row;
    }

    @Test
    void highestPriorityKeyWins() {
        assertThat(SubTableRowIdentity.identityFieldOf(row("id", 9, "row_id", "abc"))).isEqualTo("row_id");
        assertThat(SubTableRowIdentity.identityOf(row("id", 9, "row_id", "abc"))).isEqualTo("row_id=abc");
    }

    @Test
    void identityCarriesTheFieldNameSoDifferentKeysWithEqualValuesDiffer() {
        assertThat(SubTableRowIdentity.identityOf(row("row_id", 7)))
                .isNotEqualTo(SubTableRowIdentity.identityOf(row("id", 7)));
    }

    @Test
    void keyLookupIgnoresCaseButRowIdAndRowIdUnderscoreStayDistinct() {
        assertThat(SubTableRowIdentity.identityOf(row("ROW_ID", "x"))).isEqualTo("row_id=x");
        // rowId differs from row_id by an underscore, so it cannot be reached case-insensitively
        // and must be listed on its own.
        assertThat(SubTableRowIdentity.identityFieldOf(row("rowId", "x"))).isEqualTo("rowId");
    }

    @Test
    void blankAndNullValuesDoNotIdentifyAnything() {
        assertThat(SubTableRowIdentity.hasIdentity(row("row_id", "   "))).isFalse();
        assertThat(SubTableRowIdentity.hasIdentity(row("row_id", null))).isFalse();
        assertThat(SubTableRowIdentity.hasIdentity(row("name", "no key here"))).isFalse();
        assertThat(SubTableRowIdentity.identityOf(row("name", "no key here"))).isNull();
        assertThat(SubTableRowIdentity.hasIdentity(null)).isFalse();
    }

    @Test
    void identityValuesCollectEveryKeySoPartialRecordsOfTheSameRowStillMatch() {
        assertThat(SubTableRowIdentity.identityValuesOf(row("row_id", "a", "id", 9, "name", "x")))
                .containsExactly("a", "9");
        assertThat(SubTableRowIdentity.identityValuesOf(row("name", "x"))).isEmpty();
        assertThat(SubTableRowIdentity.identityValuesOf(null)).isEmpty();
    }

    @Test
    void ensureIdentityAssignsRowIdOnlyWhenTheRowHasNone() {
        Map<String, Object> anonymous = row("name", "x");
        assertThat(SubTableRowIdentity.ensureIdentity(anonymous)).isTrue();
        assertThat(String.valueOf(anonymous.get("row_id"))).isNotBlank();

        // A designer-allocated key must survive untouched.
        Map<String, Object> identified = row("id_idw", 42);
        assertThat(SubTableRowIdentity.ensureIdentity(identified)).isFalse();
        assertThat(identified).doesNotContainKey("row_id");
    }

    @Test
    void generatedIdentitiesAreUniquePerRow() {
        Map<String, Object> first = row("name", "same");
        Map<String, Object> second = row("name", "same");
        SubTableRowIdentity.ensureIdentity(first);
        SubTableRowIdentity.ensureIdentity(second);
        // Two rows with identical content are two rows — this is exactly what content
        // hashing would have merged.
        assertThat(first.get("row_id")).isNotEqualTo(second.get("row_id"));
    }

    @Test
    void theSqlExpressionKeepsTheSamePriorityAsTheJavaLookup() {
        String sql = SubTableRowIdentity.sqlIdentityExpression("elem");

        // Derived from the one list, in the one order — SQL that de-duplicates rows and Java that
        // compares them must not be able to disagree about which key wins.
        assertThat(sql).isEqualTo("COALESCE(elem->>'row_id', elem->>'rowId', elem->>'rowID',"
                + " elem->>'id_idw', elem->>'_rowKey', elem->>'rowKey', elem->>'id')");
        for (String field : SubTableRowIdentity.IDENTITY_FIELDS) {
            assertThat(sql).contains("elem->>'" + field + "'");
        }
    }
}
