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
        verify(repository).findLatestSubTableChanges(eq("synthetic-process"), eq("synthetic-lines"), argThat(ids -> ids.size() == 200), eq(false));
        verify(jdbc).batchUpdate(anyString(), argThat(records -> records.size() == 9600), eq(200),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.ParameterizedPreparedStatementSetter<ChangeHistory>>any());
        verifyNoMoreInteractions(repository);
    }

    @Test void exactLatestPairDeduplicatesButDifferentPairAndRowRemain() {
        when(repository.findLatestSubTableChanges(any(), any(), anyList(), eq(false))).thenReturn(List.of(
                ChangeHistory.builder().rowIdentifier("one").fieldName("caption").changeType(ChangeType.SUB_TABLE_ROW_UPDATE)
                        .oldValue("a").newValue("b").build()));
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
        verify(repository, times(3)).findLatestSubTableChanges(any(), any(), argThat(ids -> !ids.isEmpty() && ids.size() <= 200), eq(false));
        verify(jdbc).batchUpdate(anyString(), argThat(records -> records.size() == 401), eq(200),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.ParameterizedPreparedStatementSetter<ChangeHistory>>any());
    }

    @Test void auditInsertFailureDoesNotPrintBoundFieldValues() {
        when(jdbc.batchUpdate(anyString(), anyCollection(), eq(200),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.ParameterizedPreparedStatementSetter<ChangeHistory>>any()))
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
}
