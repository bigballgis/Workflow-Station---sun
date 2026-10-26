package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.security.repository.UserRepository;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.ChangeHistoryContext;
import com.portal.dto.SubTableChange;
import com.portal.entity.ChangeHistory;
import com.portal.repository.ChangeHistoryRepository;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.testsupport.PortalTransactionTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ChangeHistoryBatchTest {
    @Test
    void mainAndMultipleSubTablesShareContextButAnotherSaveGetsAnotherBatch() {
        var repository = mock(ChangeHistoryRepository.class);
        List<ChangeHistory> persisted = new ArrayList<>();
        when(repository.saveAll(anyList())).thenAnswer(call -> {
            List<ChangeHistory> rows = call.getArgument(0);
            persisted.addAll(rows);
            return rows;
        });
        var component = component(repository);
        var first = context();
        component.recordFieldChanges(first, Map.of("caption", "one"), Map.of("caption", "two"));
        var changes = List.of(SubTableChange.builder().changeType("ROW_UPDATE").rowIdentifier("row")
                .oldValues(Map.of("amount", 1)).newValues(Map.of("amount", 2)).build());
        component.recordSubTableChangesWithName(first, "first-table", changes);
        component.recordSubTableChangesWithName(first, "second-table", changes);
        assertEquals(3, persisted.size());
        assertTrue(persisted.stream().allMatch(row -> first.getBatchId().equals(row.getBatchId())
                && first.getBatchTime().equals(row.getBatchTime())));
        component.recordFieldChanges(context(), Map.of("caption", "two"), Map.of("caption", "two"));
        assertEquals(3, persisted.size(), "No-op Save must not append history");
        var second = context();
        component.recordFieldChanges(second, Map.of("caption", "two"), Map.of("caption", "three"));
        assertNotEquals(first.getBatchId(), persisted.get(3).getBatchId());
        assertEquals(second.getBatchId(), persisted.get(3).getBatchId());
    }

    @Test
    void notesAndAssignmentEventsGetIndependentBatchesAndPreserveTheirActor() {
        var repository = mock(ChangeHistoryRepository.class);
        List<ChangeHistory> rows = new ArrayList<>();
        when(repository.save(org.mockito.ArgumentMatchers.any(ChangeHistory.class))).thenAnswer(call -> {
            ChangeHistory row = call.getArgument(0);
            rows.add(row);
            return row;
        });
        var component = component(repository);
        component.recordNoteChange("process", "author", com.portal.enums.ChangeType.RECORD_NOTE_ADD, null, null, "note");
        component.recordNoteChange("process", "author", com.portal.enums.ChangeType.RECORD_NOTE_UPDATE, null, "note", "edit");
        var recorder = new TaskAssignmentHistoryRecorder(repository, PortalTransactionTestSupport.noopPlatformTransactionManager());
        var task = com.portal.dto.TaskInfo.builder().taskId("task").processInstanceId("process")
                .taskDefinitionKey("stage").assignee("old-holder").build();
        recorder.record(task, "leader", com.portal.enums.ChangeType.REASSIGN, "new-holder");
        assertEquals(3, rows.size());
        assertEquals(3, rows.stream().map(ChangeHistory::getBatchId).distinct().count());
        assertTrue(rows.stream().allMatch(row -> row.getBatchId() != null && row.getBatchTime() != null));
        assertEquals("leader", rows.get(2).getUserId());
        assertEquals("old-holder", rows.get(2).getOldValue());
        assertEquals("new-holder", rows.get(2).getNewValue());
        assertNull(rows.get(0).getStageId());
    }

    private ChangeHistoryComponent component(ChangeHistoryRepository repository) {
        return new ChangeHistoryComponent(repository, mock(ProcessInstanceRepository.class),
                mock(UserRepository.class), mock(WorkflowEngineClient.class), mock(JdbcTemplate.class),
                new ObjectMapper(), mock(UserPortalAuditEnricher.class), mock(UserPortalAuditProcessInstanceMatcher.class),
                PortalTransactionTestSupport.noopPlatformTransactionManager());
    }

    private ChangeHistoryContext context() {
        return ChangeHistoryContext.builder().processInstanceId("process").taskInstanceId("task")
                .stageId("stage").userId("actor").build();
    }
}
