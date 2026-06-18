package com.developer.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FunctionUnitTagUtilsTest {

    @Test
    void normalizeTags_trimsDedupesAndCapsLength() {
        // Trims whitespace, removes empty/blank entries, deduplicates (case-sensitive), respects order
        assertThat(FunctionUnitTagUtils.normalizeTags(List.of("  UAT  ", "UAT", "", "  ")))
                .containsExactly("UAT");
    }

    @Test
    void normalizeTags_nullOrEmpty_returnsEmptyList() {
        assertThat(FunctionUnitTagUtils.normalizeTags(null)).isEmpty();
        assertThat(FunctionUnitTagUtils.normalizeTags(List.of())).isEmpty();
    }
}
