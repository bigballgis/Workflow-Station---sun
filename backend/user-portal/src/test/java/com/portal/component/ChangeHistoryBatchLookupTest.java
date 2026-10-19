package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.security.repository.UserRepository;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.ChangeHistoryContext;
import com.portal.dto.SubTableChange;
import com.portal.entity.ChangeHistory;
import com.portal.enums.ChangeType;
import com.portal.repository.ChangeHistoryRepository;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.testsupport.PortalTransactionTestSupport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChangeHistoryBatchLookupTest {
    private final ChangeHistoryRepository repository = mock(ChangeHistoryRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ChangeHistoryComponent component = new ChangeHistoryComponent(repository,
            mock(ProcessInstanceRepository.class), mock(UserRepository.class), mock(WorkflowEngineClient.class),
            jdbc, new ObjectMapper(), mock(UserPortalAuditEnricher.class),
            mock(UserPortalAuditProcessInstanceMatcher.class), PortalTransactionTestSupport.noopPlatformTransactionManager());
    private final ChangeHistoryContext context = ChangeHistoryContext.builder()
            .processInstanceId("synthetic-process").taskInstanceId("synthetic-task").userId("synthetic-user").build();

    @Test void twoHundredWideRowsUseOneLookupAndKeepEveryCell() {
        List<SubTableChange> changes = new ArrayList<>();
        for (int row = 0; row < 200; row++) {
            Map<String, Object> before = new LinkedHashMap<>(), after = new LinkedHashMap<>();
            for (int column = 0; column < 48; column++) {
                before.put("column_" + column, "before"); after.put("column_" + column, "after");
            }
            changes.add(change("row-" + row, before, after));
        }
        component.recordSubTableChangesWithName(context, "synthetic-lines", changes);
        verify(repository).findLatestAuditCells(eq("synthetic-process"), eq("synthetic-lines"), argThat(cells ->
                cells.size() == 9600 && cells.stream().map(ChangeHistoryRepository.AuditCell::rowIdentifier).distinct().count() == 200
                && cells.stream().allMatch(cell -> cell.changeType() == ChangeType.SUB_TABLE_ROW_UPDATE)));
        assertBoundedMultiRowInserts(9600);
        verifyNoMoreInteractions(repository);
    }

    @Test void exactLatestPairDeduplicatesButDifferentPairAndRowRemain() {
        when(repository.findLatestAuditCells(any(), any(), anyList())).thenReturn(List.of(
                new ChangeHistoryRepository.AuditCellSnapshot(
                        new ChangeHistoryRepository.AuditCell("one", "caption", ChangeType.SUB_TABLE_ROW_UPDATE), "a", "b")));
        component.recordSubTableChangesWithName(context, "synthetic-lines", List.of(
                change("one", Map.of("caption", "a"), Map.of("caption", "b")),
                change("two", Map.of("caption", "a"), Map.of("caption", "b"))));
        verify(repository).saveAll(argThat(records -> {
            List<ChangeHistory> saved = new ArrayList<>(); records.forEach(saved::add);
            assertThat(saved).singleElement().satisfies(record -> assertThat(record.getRowIdentifier()).isEqualTo("two"));
            return true;
        }));
    }

    @Test void emptyInputDoesNotQueryOrWrite() {
        component.recordSubTableChangesWithName(context, "synthetic-lines", List.of());
        verifyNoInteractions(repository);
    }

    @Test void ordinaryLargeImportsUseBoundedRowChunksInsteadOfAnUnboundedInList() {
        List<SubTableChange> changes = java.util.stream.IntStream.range(0, 401)
                .mapToObj(index -> change("row-" + index, Map.of("caption", "before"), Map.of("caption", "after"))).toList();
        component.recordSubTableChangesWithName(context, "synthetic-lines", changes);
        verify(repository, times(3)).findLatestAuditCells(any(), any(), argThat(cells -> !cells.isEmpty() && cells.size() <= 200));
        assertBoundedMultiRowInserts(401);
    }

    @Test void auditInsertFailureDoesNotPrintBoundFieldValues() {
        when(jdbc.update(anyString(), any(org.springframework.jdbc.core.PreparedStatementSetter.class)))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("SYNTHETIC-PRIVATE-AUDIT-VALUE"));
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ChangeHistoryComponent.class);
        var events = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        events.start(); logger.addAppender(events);
        try {
            var changes = java.util.stream.IntStream.range(0, 201)
                    .mapToObj(index -> change("row-" + index, Map.of("caption", "before"), Map.of("caption", "after"))).toList();
            component.recordSubTableChangesWithName(context, "synthetic-lines", changes);
            assertThat(events.list).isNotEmpty().allSatisfy(event ->
                    assertThat(event.getFormattedMessage()).doesNotContain("SYNTHETIC-PRIVATE-AUDIT-VALUE"));
        } finally { logger.detachAppender(events); events.stop(); }
    }

    private static SubTableChange change(String row, Map<String, Object> before, Map<String, Object> after) {
        return SubTableChange.builder().rowIdentifier(row).changeType("ROW_UPDATE").oldValues(before).newValues(after).build();
    }

    private void assertBoundedMultiRowInserts(int rows) {
        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        var setters = org.mockito.ArgumentCaptor.forClass(org.springframework.jdbc.core.PreparedStatementSetter.class);
        verify(jdbc, times((rows + 199) / 200)).update(sql.capture(), setters.capture());
        assertThat(sql.getAllValues().stream().mapToLong(value -> value.chars().filter(c -> c == '?').count()).sum())
                .isEqualTo(rows * 12L);
        assertThat(sql.getAllValues()).allSatisfy(value ->
                assertThat(value.chars().filter(c -> c == '?').count()).isBetween(12L, 2400L));
        var statement = mock(java.sql.PreparedStatement.class);
        try {
            for (var setter : setters.getAllValues()) setter.setValues(statement);
            verify(statement, times(rows)).setBoolean(anyInt(), eq(false));
            verify(statement, times(rows)).setString(anyInt(), eq("synthetic-process"));
        } catch (java.sql.SQLException error) { throw new AssertionError(error); }
    }
}
