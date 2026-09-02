package com.portal.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MainTableViewFkDisplaySupportTest {

    @Test
    void resolveAttribute_matchesSinglePkField() {
        Map<String, Object> mainVars = Map.of(
                "case_number", "CASE-100",
                "legal_hold", "Yes",
                "status", "Open"
        );
        assertThat(MainTableViewFkDisplaySupport.resolveAttribute(
                mainVars, "CASE-100", List.of("case_number"), "legal_hold"))
                .isEqualTo("Yes");
    }

    @Test
    void resolveAttribute_unmatchedReturnsNull() {
        Map<String, Object> mainVars = Map.of("case_number", "CASE-100", "legal_hold", "Yes");
        assertThat(MainTableViewFkDisplaySupport.resolveAttribute(
                mainVars, "CASE-999", List.of("case_number"), "legal_hold"))
                .isNull();
    }

    /**
     * No PK metadata means no match — the caller shows the raw FK value.
     *
     * <p>This previously fell back to the literals {@code "id"} / {@code "id_idw"}. That looked
     * harmless for tables whose PK happens to use those names, but a table with a differently
     * named PK that also carries an {@code id} column matched the WRONG row and displayed a wrong
     * related attribute. Guessing a column name is worse than not resolving.
     */
    @Test
    void resolveAttribute_returnsNullWhenPkMetaEmpty() {
        Map<String, Object> mainVars = Map.of("id", "row-1", "title", "Doc");
        assertThat(MainTableViewFkDisplaySupport.resolveAttribute(
                mainVars, "row-1", List.of(), "title"))
                .isNull();
    }

    /** A configured PK still resolves, even when an unrelated {@code id} column is present. */
    @Test
    void resolveAttribute_matchesConfiguredPkNotTheIdColumn() {
        Map<String, Object> mainVars = Map.of("id", "row-1", "row_id", "R-7", "title", "Doc");
        assertThat(MainTableViewFkDisplaySupport.resolveAttribute(
                mainVars, "R-7", List.of("row_id"), "title"))
                .isEqualTo("Doc");
        // "row-1" is the id column's value, not the configured PK's — must NOT match.
        assertThat(MainTableViewFkDisplaySupport.resolveAttribute(
                mainVars, "row-1", List.of("row_id"), "title"))
                .isNull();
    }
}
