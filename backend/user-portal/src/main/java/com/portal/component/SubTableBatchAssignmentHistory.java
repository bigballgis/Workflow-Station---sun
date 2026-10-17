package com.portal.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.dto.BatchAssignmentVerification;
import com.portal.entity.ProcessHistory;
import com.portal.repository.ProcessHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Durable post-action observations, separate from business variables and BPMN history. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubTableBatchAssignmentHistory {
    static final String OPERATION = "BATCH_ASSIGNMENT_VERIFICATION";
    private final ProcessHistoryRepository repository;
    private final ObjectMapper mapper;
    private final PlatformTransactionManager transactionManager;

    public BatchAssignmentVerification record(SubTableBatchInitialMiOutcome.Plan plan, BatchAssignmentVerification result,
                                               String userId, String sourceTaskId) {
        if (plan == null || result == null) return result;
        var stored = new BatchAssignmentVerification(result.status(), result.issues(), true);
        try {
            String json = mapper.writeValueAsString(stored);
            var transaction = new TransactionTemplate(transactionManager);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transaction.executeWithoutResult(status -> repository.saveAndFlush(ProcessHistory.builder()
                    .processInstanceId(plan.instanceId()).taskId(sourceTaskId).operatorId(userId)
                    .operationType(OPERATION).activityType(OPERATION).comment(json).build()));
            return stored;
        } catch (RuntimeException | JsonProcessingException failure) {
            // The engine action is committed. Make this independent audit failure visible, never retry the action.
            log.error("Could not persist batch assignment observation for process {}", plan.instanceId());
            return new BatchAssignmentVerification(result.status(), result.issues(), false);
        }
    }

    public List<Map<String, Object>> merge(String processId, List<Map<String, Object>> engineHistory) {
        var stored = repository.findByProcessInstanceIdAndOperationTypeOrderByOperationTimeAsc(processId, OPERATION);
        if (stored.isEmpty()) return engineHistory;
        List<Map<String, Object>> merged = new ArrayList<>(engineHistory);
        for (var row : stored) merged.add(project(row));
        merged.sort(Comparator.comparing(row -> EngineTaskMapper.parseDateTime(row.get("operationTime")),
                Comparator.nullsLast(Comparator.naturalOrder())));
        return merged;
    }

    private Map<String, Object> project(ProcessHistory row) {
        try {
            var result = mapper.readValue(row.getComment(), BatchAssignmentVerification.class);
            if (!List.of("VERIFIED", "FAILED", "UNCONFIRMED").contains(result.status()) || result.issues() == null
                    || !Boolean.TRUE.equals(result.historyRecorded())) throw new IllegalStateException("Invalid stored batch observation");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", "batch-verification-" + row.getId());
            out.put("taskId", row.getTaskId());
            out.put("activityType", OPERATION);
            out.put("operationType", OPERATION);
            out.put("operationTime", row.getOperationTime());
            out.put("batchAssignmentVerification", result);
            return out;
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Stored batch observation cannot be read", failure);
        }
    }
}
