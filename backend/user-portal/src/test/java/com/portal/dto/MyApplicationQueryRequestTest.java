package com.portal.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MyApplicationQueryRequestTest {

    @Test
    void blankKeywordBecomesNull() {
        MyApplicationQueryRequest request = new MyApplicationQueryRequest(
                0, 20, null, List.of(), null, null, "  ");
        assertNull(request.keyword());
    }

    @Test
    void keywordIsTrimmed() {
        MyApplicationQueryRequest request = new MyApplicationQueryRequest(
                0, 20, null, List.of(), null, null, "  请假  ");
        assertEquals("请假", request.keyword());
    }
}
