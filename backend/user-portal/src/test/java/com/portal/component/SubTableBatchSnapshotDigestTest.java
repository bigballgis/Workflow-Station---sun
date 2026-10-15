package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SubTableBatchSnapshotDigestTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void canonicalizesNestedKeyOrderAndJsonbEquivalentNumbersWithoutReorderingLists() throws Exception {
        Map<String, Object> first = mapper.readValue("{\"b\":{\"z\":null,\"a\":1.00},\"a\":[true,1000,\"1\"]}", new TypeReference<>() { });
        Map<String, Object> second = mapper.readValue("{\"a\":[true,1e3,\"1\"],\"b\":{\"a\":1,\"z\":null}}", new TypeReference<>() { });
        assertThat(SubTableBatchSnapshotDigest.compute(first, mapper)).isEqualTo(SubTableBatchSnapshotDigest.compute(second, mapper));
        second.put("a", java.util.List.of(1000, true, "1"));
        assertThat(SubTableBatchSnapshotDigest.compute(first, mapper)).isNotEqualTo(SubTableBatchSnapshotDigest.compute(second, mapper));
        assertThat(first).doesNotContainKey("definitionDigest");
    }
}
