package com.portal.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RelationTableQueryRequestTest {

    @Test
    void ofDelegatesToCanonicalConstructor() {
        RelationTableQueryRequest request = RelationTableQueryRequest.of(0, 20, null, List.of(), null, null);
        assertEquals(0, request.page());
        assertEquals(20, request.size());
        assertDoesNotThrow(() -> new RelationTableQueryRequest(1, 10, "q", List.of(), "name", "ASC"));
    }
}
