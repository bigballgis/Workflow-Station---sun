package com.admin.dto.request;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RelationTableDataListQueryRequestTest {

    @Test
    void pagingAndSearchAreAccepted() {
        RelationTableDataListQueryRequest request =
                new RelationTableDataListQueryRequest(0, 20, "q", List.of(), null, null);
        assertEquals(0, request.page());
        assertEquals(20, request.size());
        assertEquals("q", request.search());
    }
}
