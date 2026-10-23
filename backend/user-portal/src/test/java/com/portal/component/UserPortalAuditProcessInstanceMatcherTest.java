package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserPortalAuditProcessInstanceMatcher")
class UserPortalAuditProcessInstanceMatcherTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ProcessInstanceRepository processInstanceRepository;

    private UserPortalAuditProcessInstanceMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new UserPortalAuditProcessInstanceMatcher(
                jdbcTemplate, processInstanceRepository, new ObjectMapper());
    }

    @Test
    void escapeLike_escapesPercentUnderscoreAndBackslash() {
        assertThat(UserPortalAuditProcessInstanceMatcher.escapeLike("a%b_c\\d"))
                .isEqualTo("a\\%b\\_c\\\\d");
    }

    @Test
    void resolveMatching_blankKeyword_returnsEmptyWithoutDb() {
        assertThat(matcher.resolveMatchingProcessInstanceIds("  ", null, null)).isEmpty();
        verify(jdbcTemplate, never()).query(
                any(String.class), ArgumentMatchers.<RowMapper<String>>any(), any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolveMatching_matchesColumnHits() {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    if (sql.contains("LOWER(id) LIKE")) {
                        return List.of("pi-column-1");
                    }
                    return List.of();
                });

        List<String> ids = matcher.resolveMatchingProcessInstanceIds("column-1", null, null);

        assertThat(ids).containsExactly("pi-column-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolveMatching_matchesStoredRequestId_notVariablesJsonSubstring() {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    if (sql.contains("LOWER(id) LIKE")) {
                        return List.of();
                    }
                    if (sql.contains("up_change_history")) {
                        return List.of("pi-req-1");
                    }
                    return List.of();
                });

        Instant start = Instant.parse("2026-08-01T00:00:00Z");
        Instant end = Instant.parse("2026-08-04T23:59:59Z");

        ProcessInstance pi = ProcessInstance.builder()
                .id("pi-req-1")
                .functionUnitCode("ATM")
                .variables(Map.of("dept", "HR", "year", "2026", "seq", "001",
                        RequestIdEnricher.REQUEST_ID_FIELD, "HR-2026-001"))
                .build();
        when(processInstanceRepository.findAllById(List.of("pi-req-1"))).thenReturn(List.of(pi));

        List<String> ids = matcher.resolveMatchingProcessInstanceIds("HR-2026-001", start, end);

        assertThat(ids).containsExactly("pi-req-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolveMatching_requestIdMiss_returnsEmpty() {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    if (sql.contains("LOWER(id) LIKE")) {
                        return List.of();
                    }
                    if (sql.contains("up_change_history")) {
                        return List.of("pi-other");
                    }
                    return List.of();
                });

        ProcessInstance pi = ProcessInstance.builder()
                .id("pi-other")
                .functionUnitCode("ATM")
                .variables(Map.of("dept", "IT", "year", "2025", "seq", "9",
                        RequestIdEnricher.REQUEST_ID_FIELD, "IT-2025-9"))
                .build();
        when(processInstanceRepository.findAllById(List.of("pi-other"))).thenReturn(List.of(pi));

        Instant start = Instant.parse("2026-08-01T00:00:00Z");
        Instant end = Instant.parse("2026-08-04T23:59:59Z");
        assertThat(matcher.resolveMatchingProcessInstanceIds("HR-2026-001", start, end)).isEmpty();
    }
}
