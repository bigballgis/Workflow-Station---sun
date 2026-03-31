package com.platform.common.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseBodyUnwrapTest {

    @Test
    void unwrapDataMap_extractsNestedDataObject() {
        Map<String, Object> body = Map.of(
                "success", true,
                "data", Map.of("id", "1", "name", "X")
        );
        assertThat(ApiResponseBodyUnwrap.unwrapDataMap(body))
                .containsEntry("id", "1")
                .containsEntry("name", "X");
    }

    @Test
    void unwrapDataMap_passesThroughRawEntity() {
        Map<String, Object> flat = Map.of("fullName", "Alice");
        assertThat(ApiResponseBodyUnwrap.unwrapDataMap(flat)).isSameAs(flat);
    }

    @Test
    void normalizeToListOfMaps_supportsApiResponseListAndPageContent() {
        assertThat(ApiResponseBodyUnwrap.normalizeToListOfMaps(Map.of(
                "success", true,
                "data", List.of(Map.of("a", 1))
        ))).hasSize(1);

        assertThat(ApiResponseBodyUnwrap.normalizeToListOfMaps(Map.of(
                "content", List.of(Map.of("b", 2))
        ))).hasSize(1);
    }
}
