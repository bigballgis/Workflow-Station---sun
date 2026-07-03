package com.portal.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.portal.entity.ProcessInstance;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MainTableViewInvolvementCheckerTest {

    private JdbcTemplate jdbcTemplate;
    private MainTableViewInvolvementChecker checker;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        checker = new MainTableViewInvolvementChecker(jdbcTemplate);
    }

    @Test
    void initiatorIsInvolved() {
        ProcessInstance pi = ProcessInstance.builder()
                .id("pi-1")
                .startUserId("user-a")
                .build();
        assertThat(checker.isUserInvolved("user-a", pi)).isTrue();
        assertThat(checker.isUserInvolved("user-b", pi)).isFalse();
    }

    @Test
    void historicAssigneeIsInvolved() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("pi-2"), eq("user-b")))
                .thenReturn(1);
        ProcessInstance pi = ProcessInstance.builder()
                .id("pi-2")
                .startUserId("user-a")
                .build();
        assertThat(checker.isUserInvolved("user-b", pi)).isTrue();
    }

    @Test
    void miParticipantFieldInSubTablesIsInvolved() {
        Map<String, Object> subRow = Map.of("assignee", "user-c", "name", "Test-000001");
        Map<String, Object> subTables = Map.of("30", List.of(subRow));
        Map<String, Object> vars = Map.of("__subTables__", subTables);
        ProcessInstance pi = ProcessInstance.builder()
                .id("pi-3")
                .startUserId("user-a")
                .variables(vars)
                .build();
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("pi-3"), eq("user-c")))
                .thenReturn(0);
        assertThat(checker.isUserInvolved("user-c", pi)).isTrue();
    }
}
