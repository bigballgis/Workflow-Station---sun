package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.dto.TaskActionInfo;
import com.portal.dto.TaskInfo;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.service.TaskActionService;
import com.portal.util.WorkflowEnginePayloadHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Task detail payload: engine row plus portal process variables, actions, and request id.
 */
@Slf4j
@Component
public class TaskDetailQueryComponent {

    private final ProcessInstanceRepository processInstanceRepository;
    private final WorkflowEngineClient workflowEngineClient;
    private final EngineSubTableHydrator engineSubTableHydrator;
    private final TaskActionService taskActionService;
    private final MiParticipantEnrichmentComponent miParticipantEnricher;
    private final RequestIdEnricher requestIdEnricher;

    @Lazy
    @Autowired
    private ProcessComponent processComponent;

    public TaskDetailQueryComponent(
            ProcessInstanceRepository processInstanceRepository,
            WorkflowEngineClient workflowEngineClient,
            EngineSubTableHydrator engineSubTableHydrator,
            TaskActionService taskActionService,
            MiParticipantEnrichmentComponent miParticipantEnricher,
            RequestIdEnricher requestIdEnricher) {
        this.processInstanceRepository = processInstanceRepository;
        this.workflowEngineClient = workflowEngineClient;
        this.engineSubTableHydrator = engineSubTableHydrator;
        this.taskActionService = taskActionService;
        this.miParticipantEnricher = miParticipantEnricher;
        this.requestIdEnricher = requestIdEnricher;
    }

    public Optional<TaskInfo> getTaskById(String taskId) {
        log.debug("getTaskById called with taskId: {}", taskId);
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException(
                    "Flowable engine unavailable, please check if workflow-engine-core service is running");
        }
        try {
            long tEngine = System.nanoTime();
            Optional<Map<String, Object>> result = workflowEngineClient.getTaskById(taskId);
            log.info("[PERF] detail.getTaskById(engine) took {} ms", (System.nanoTime() - tEngine) / 1_000_000L);
            if (result.isEmpty()) {
                return Optional.empty();
            }
            Map<String, Object> data = WorkflowEnginePayloadHelper.singleTaskFromPayload(result.get());
            if (data == null) {
                return Optional.empty();
            }
            TaskInfo taskInfo = EngineTaskMapper.convertMapToTaskInfo(data);
            mergePortalProcessVariables(taskInfo);
            attachActions(taskId, data, taskInfo);
            requestIdEnricher.enrichTaskRequestIds(List.of(taskInfo));
            return Optional.of(taskInfo);
        } catch (Exception e) {
            log.warn("Failed to get task by id {} from Flowable: {}", taskId, e.getMessage());
            return Optional.empty();
        }
    }

    private void mergePortalProcessVariables(TaskInfo taskInfo) {
        String processInstanceId = taskInfo.getProcessInstanceId();
        if (processInstanceId == null) {
            return;
        }
        processInstanceRepository.findById(processInstanceId).ifPresent(pi -> {
            fillInitiatorFromProcess(taskInfo, pi);
            if (pi.getVariables() == null) {
                return;
            }
            Map<String, Object> merged = new HashMap<>();
            EngineTaskMapper.mergePortalProcessVariablesPreferringFlowableMiElementItem(
                    merged, taskInfo.getVariables(), pi.getVariables());
            miParticipantEnricher.enrichMissingParticipantRowIdsInSubTables(merged);
            long tEnrich = System.nanoTime();
            processComponent.enrichSubTablesVariablesFromPhysicalTables(processInstanceId, merged);
            log.info("[PERF] detail.enrichSubTables took {} ms", (System.nanoTime() - tEnrich) / 1_000_000L);
            long tPart = System.nanoTime();
            miParticipantEnricher.enrichParticipantAssignmentData(merged);
            log.info("[PERF] detail.enrichParticipantAssignmentData took {} ms",
                    (System.nanoTime() - tPart) / 1_000_000L);
            hydrateEngineSubTablesIntoMerged(processInstanceId, pi, merged);
            taskInfo.setVariables(merged);
        });
    }

    private static void fillInitiatorFromProcess(TaskInfo taskInfo, ProcessInstance pi) {
        if (taskInfo.getInitiatorId() == null || taskInfo.getInitiatorId().isBlank()) {
            if (pi.getInitiatorId() != null && !pi.getInitiatorId().isBlank()) {
                taskInfo.setInitiatorId(pi.getInitiatorId().trim());
            } else if (pi.getStartUserId() != null && !pi.getStartUserId().isBlank()) {
                taskInfo.setInitiatorId(pi.getStartUserId().trim());
            }
        }
        if ((taskInfo.getInitiatorName() == null || taskInfo.getInitiatorName().isBlank())
                && pi.getStartUserName() != null && !pi.getStartUserName().isBlank()) {
            taskInfo.setInitiatorName(pi.getStartUserName().trim());
        }
    }

    @SuppressWarnings("unchecked")
    private void hydrateEngineSubTablesIntoMerged(
            String processInstanceId, ProcessInstance pi, Map<String, Object> merged) {
        if (workflowEngineClient == null || !workflowEngineClient.isAvailable()) {
            return;
        }
        Map<String, Object> current = merged.get("__subTables__") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : null;
        engineSubTableHydrator.mergeFromEngine(processInstanceId, current).ifPresent(result -> {
            Map<String, Object> cur = result.mergedSubTables();
            merged.put("__subTables__", cur);
            if (result.rowCount() != null && merged.get("rowCount") == null) {
                merged.put("rowCount", result.rowCount());
            }
            Map<String, Object> storeVars = pi.getVariables() != null
                    ? new HashMap<>(pi.getVariables()) : new HashMap<>();
            storeVars.put("__subTables__", cur);
            pi.setVariables(storeVars);
            processInstanceRepository.save(pi);
        });
    }

    private void attachActions(String taskId, Map<String, Object> data, TaskInfo taskInfo) {
        Object rawActionIds = data.get("actionIds");
        if (rawActionIds == null) {
            return;
        }
        try {
            long tActions = System.nanoTime();
            List<TaskActionInfo> actions = taskActionService.getTaskActions(taskId);
            log.info("[PERF] detail.getTaskActions took {} ms", (System.nanoTime() - tActions) / 1_000_000L);
            taskInfo.setActions(actions != null ? actions : Collections.emptyList());
        } catch (Exception e) {
            log.warn("Failed to get actions for task {}: {}", taskId, e.getMessage(), e);
            taskInfo.setActions(Collections.emptyList());
        }
    }
}
