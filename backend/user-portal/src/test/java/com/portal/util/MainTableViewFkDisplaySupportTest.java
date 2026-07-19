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

    @Test
    void resolveAttribute_fallsBackToIdWhenPkMetaEmpty() {
        Map<String, Object> mainVars = Map.of("id", "row-1", "title", "Doc");
        assertThat(MainTableViewFkDisplaySupport.resolveAttribute(
                mainVars, "row-1", List.of(), "title"))
                .isEqualTo("Doc");
    }
}
