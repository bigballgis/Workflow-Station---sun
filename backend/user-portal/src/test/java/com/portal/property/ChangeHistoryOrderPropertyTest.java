package com.portal.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.client.WorkflowEngineClient;
import com.portal.component.ChangeHistoryComponent;
import com.portal.dto.ChangeHistoryRecord;
import com.portal.entity.ChangeHistory;
import com.portal.enums.ChangeType;
import com.portal.repository.ChangeHistoryRepository;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.testsupport.PortalTransactionTestSupport;
import com.platform.security.repository.UserRepository;
import net.jqwik.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property 10: Change_History records returned in chronological order
 *
 * For any set of Change_History records for a given processInstanceId,
 * the API should return them sorted by timestamp in ascending order.
 * For any two consecutive records in the result, record[i].timestamp <= record[i+1].timestamp.
 *
 * Validates: Requirements 6.4
 */
public class ChangeHistoryOrderPropertyTest {

    @Example
    @Label("Legacy internal fields remain hidden when form metadata is unavailable")
    void legacyInternalFieldsRemainHiddenWithoutMetadata() {
    ChangeHistoryRepository repository = mock(ChangeHistoryRepository.class);
    UserRepository userRepository = mock(UserRepository.class);
    WorkflowEngineClient workflowEngineClient = mock(WorkflowEngineClient.class);
    when(userRepository.findAllById(any())).thenReturn(Collections.emptyList());
    when(workflowEngineClient.getTaskHistory(anyString())).thenReturn(Optional.empty());
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    when(repository.findByProcessInstanceIdOrderByTimestampAsc("process-1")).thenReturn(List.of(
        ChangeHistory.builder().id(1L).processInstanceId("process-1").userId("user-1")
            .timestamp(now).fieldName("currentUserId").newValue("system-user")
            .changeType(ChangeType.FIELD_UPDATE).build(),
        ChangeHistory.builder().id(2L).processInstanceId("process-1").userId("user-1")
            .timestamp(now.plusSeconds(1)).fieldName("description").newValue("visible")
            .changeType(ChangeType.FIELD_UPDATE).build()));
    ChangeHistoryComponent component = new ChangeHistoryComponent(
        repository, mock(ProcessInstanceRepository.class), userRepository, workflowEngineClient,
        mock(JdbcTemplate.class), new ObjectMapper(),
        PortalTransactionTestSupport.noopPlatformTransactionManager());
    assertThat(component.getChangeHistory("process-1"))
        .extracting(ChangeHistoryRecord::getFieldName)
        .containsExactly("description");
    }

    /**
     * Property 10: Records are returned in chronological (ascending timestamp) order.
     *
     * Validates: Requirements 6.4
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 10: Change_History records returned in chronological order")
    void changeHistoryReturnedInChronologicalOrder(
            @ForAll("changeHistoryLists") TimestampedHistoryList historyList) {

        ChangeHistoryRepository repository = mock(ChangeHistoryRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        WorkflowEngineClient workflowEngineClient = mock(WorkflowEngineClient.class);
        when(userRepository.findAllById(any())).thenReturn(Collections.emptyList());
        when(workflowEngineClient.getTaskHistory(anyString())).thenReturn(Optional.empty());

        // Simulate the repository returning records sorted by timestamp (as the query does)
        List<ChangeHistory> sorted = new ArrayList<>(historyList.records);
        sorted.sort(Comparator.comparing(ChangeHistory::getTimestamp));

        when(repository.findByProcessInstanceIdOrderByTimestampAsc(historyList.processInstanceId))
                .thenReturn(sorted);

        ChangeHistoryComponent component = new ChangeHistoryComponent(
                repository,
                mock(ProcessInstanceRepository.class),
                userRepository,
                workflowEngineClient,
                mock(JdbcTemplate.class),
                new ObjectMapper(),
                PortalTransactionTestSupport.noopPlatformTransactionManager());
        List<ChangeHistoryRecord> result = component.getChangeHistory(historyList.processInstanceId);

        // Verify chronological order
        assertThat(result).hasSameSizeAs(historyList.records);

        for (int i = 0; i < result.size() - 1; i++) {
            Instant current = result.get(i).getTimestamp();
            Instant next = result.get(i + 1).getTimestamp();
            assertThat(current).isBeforeOrEqualTo(next);
        }
    }

    // ========== Data class ==========

    static class TimestampedHistoryList {
        String processInstanceId;
        List<ChangeHistory> records;
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<TimestampedHistoryList> changeHistoryLists() {
        Arbitrary<String> processIds = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15)
                .map(s -> "proc_" + s);
        Arbitrary<Integer> sizes = Arbitraries.integers().between(1, 20);

        return Combinators.combine(processIds, sizes).as((procId, size) -> {
            TimestampedHistoryList list = new TimestampedHistoryList();
            list.processInstanceId = procId;
            list.records = new ArrayList<>();

            Instant base = Instant.parse("2026-01-01T00:00:00Z");
            for (int i = 0; i < size; i++) {
                // Random offset in seconds (not necessarily ordered)
                long offsetSeconds = (long) (Math.random() * 86400 * 30); // up to 30 days
                ChangeHistory record = ChangeHistory.builder()
                        .id((long) (i + 1))
                        .processInstanceId(procId)
                        .userId("user_" + i)
                        .timestamp(base.plusSeconds(offsetSeconds))
                        .fieldName("field_" + i)
                        .oldValue("old_" + i)
                        .newValue("new_" + i)
                        .changeType(ChangeType.FIELD_UPDATE)
                        .build();
                list.records.add(record);
            }
            return list;
        });
    }
}
