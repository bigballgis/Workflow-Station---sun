package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.dto.BatchAssignmentVerification;
import com.portal.entity.ProcessHistory;
import com.portal.repository.ProcessHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SubTableBatchAssignmentHistoryTest {
    private final ProcessHistoryRepository repository = mock(ProcessHistoryRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final SubTableBatchInitialMiOutcome.Plan plan = new SubTableBatchInitialMiOutcome.Plan("synthetic-instance", "definition", List.of());
    private final BatchAssignmentVerification result = new BatchAssignmentVerification("FAILED",
            List.of(new BatchAssignmentVerification.Issue("synthetic-child", "ROLE_TASK_CANDIDATES_MISMATCH")));

    @Test void commitsObservationInIndependentTransactionAndProjectsOnlySafeResults() {
        var database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try {
            var history = new SubTableBatchAssignmentHistory(repository, mapper, new DataSourceTransactionManager(database));
            when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                ProcessHistory row = invocation.getArgument(0);
                row.setId(7L); row.setOperationTime(LocalDateTime.of(2026, 10, 17, 12, 0));
                assertThat(row.getProcessInstanceId()).isEqualTo(plan.instanceId());
                assertThat(row.getTaskId()).isEqualTo("synthetic-source");
                assertThat(row.getOperatorId()).isEqualTo("synthetic-user");
                when(repository.findByProcessInstanceIdAndOperationTypeOrderByOperationTimeAsc(plan.instanceId(), SubTableBatchAssignmentHistory.OPERATION))
                        .thenReturn(List.of(row));
                return row;
            });
            var saved = history.record(plan, result, "synthetic-user", "synthetic-source");
            assertThat(saved.historyRecorded()).isTrue();
            assertThat(saved.status()).isEqualTo("FAILED");
            var entries = history.merge(plan.instanceId(), List.of());
            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).get("batchAssignmentVerification")).isEqualTo(saved);
            assertThat(entries.get(0)).doesNotContainKeys("operatorId", "operatorName", "comment", "variables", "rowDesign");
        } finally { database.shutdown(); }
    }

    @Test void failureToPersistDoesNotTurnCommittedActionIntoRetryableFailure() {
        var database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try {
            var history = new SubTableBatchAssignmentHistory(repository, mapper, new DataSourceTransactionManager(database));
            when(repository.saveAndFlush(any())).thenThrow(new IllegalStateException("synthetic-private-db-error"));
            var observed = history.record(plan, result, "synthetic-user", null);
            assertThat(observed.historyRecorded()).isFalse();
            assertThat(observed.status()).isEqualTo(result.status());
            assertThat(observed.issues()).isEqualTo(result.issues());
            assertThat(observed.toString()).doesNotContain("private-db-error");
        } finally { database.shutdown(); }
    }

    @Test void legacyActionsHaveNoWritesAndEmptyHistoryDoesNotChangeEngineRows() {
        var history = new SubTableBatchAssignmentHistory(repository, mapper, null);
        assertThat(history.record(null, null, "synthetic-user", null)).isNull();
        verifyNoInteractions(repository);
        when(repository.findByProcessInstanceIdAndOperationTypeOrderByOperationTimeAsc("legacy", SubTableBatchAssignmentHistory.OPERATION)).thenReturn(List.of());
        List<Map<String, Object>> old = List.of(Map.of("id", "engine-history"));
        assertThat(history.merge("legacy", old)).isSameAs(old);
    }

    @Test void corruptStoredObservationIsAnExplicitReadFailure() {
        var history = new SubTableBatchAssignmentHistory(repository, mapper, null);
        when(repository.findByProcessInstanceIdAndOperationTypeOrderByOperationTimeAsc("synthetic-instance", SubTableBatchAssignmentHistory.OPERATION))
                .thenReturn(List.of(ProcessHistory.builder().comment("invalid-json").build()));
        assertThatThrownBy(() -> history.merge("synthetic-instance", List.of())).isInstanceOf(IllegalStateException.class);
    }
}
