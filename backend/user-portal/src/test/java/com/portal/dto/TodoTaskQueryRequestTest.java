package com.portal.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TodoTaskQueryRequestTest {

    @Test
    void blankKeywordBecomesNull() {
        TodoTaskQueryRequest request = new TodoTaskQueryRequest(
                0, 20, null, null, null, null, "  ", null, null);
        assertNull(request.keyword());
        assertTrue(request.assignmentTypes().isEmpty());
        assertTrue(request.priorities().isEmpty());
    }

    @Test
    void keywordIsTrimmed() {
        TodoTaskQueryRequest request = new TodoTaskQueryRequest(
                0, 20, null, null, null, null, "  请假  ", null, null);
        assertEquals("请假", request.keyword());
    }
}
