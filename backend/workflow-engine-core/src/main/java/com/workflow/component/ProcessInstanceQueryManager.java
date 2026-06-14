package com.workflow.component;

import com.workflow.dto.request.ProcessInstanceQueryRequest;
import com.workflow.dto.response.ProcessInstanceQueryResult;
import com.workflow.exception.WorkflowBusinessException;

import lombok.extern.slf4j.Slf4j;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Process Instance Query Manager
 * Handles paged process-instance queries (runtime + historic) and runtime/historic purging.
 *
 * Extracted from {@link ProcessEngineComponent}; behavior is preserved verbatim.
 */
@Slf4j
@Component
@Transactional
public class ProcessInstanceQueryManager {

    private static final List<String> KEY_PROCESS_VARIABLES = List.of(
        "processTitle", "initiator", "initiatorName", "formDataId", "businessKey",
        "functionUnitId", "functionUnitKey"
    );

    @Autowired
    private ProcessEngine processEngine;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private MultiInstanceCanceller multiInstanceCanceller;

    /**
     * Query process instances
     */
    public ProcessInstanceQueryResult queryProcessInstances(ProcessInstanceQueryRequest request) {
        try {
            List<ProcessInstanceQueryResult.ProcessInstanceInfo> allInstances = new ArrayList<>();
            long totalCount = 0;

            // If no state specified or includes active/suspended, query runtime table
            if (request.getState() == null ||
                "active".equalsIgnoreCase(request.getState()) ||
                "suspended".equalsIgnoreCase(request.getState())) {

                ProcessInstanceQuery runtimeQuery = runtimeService.createProcessInstanceQuery();
                applyQueryConditions(runtimeQuery, request);

                long runtimeCount = runtimeQuery.count();
                List<ProcessInstance> runtimeInstances = runtimeQuery
                        .listPage(request.getPage() * request.getSize(), request.getSize());

                List<ProcessInstanceQueryResult.ProcessInstanceInfo> runtimeInfos =
                    runtimeInstances.stream()
                        .map(this::convertToProcessInstanceInfo)
                        .collect(Collectors.toList());

                allInstances.addAll(runtimeInfos);
                totalCount += runtimeCount;
            }

            // If no state specified or includes completed, query history table
            if (request.getState() == null || "completed".equalsIgnoreCase(request.getState())) {
                var historyQuery = processEngine.getHistoryService().createHistoricProcessInstanceQuery();
                applyHistoryQueryConditions(historyQuery, request);

                // If runtime query already has results, adjust history query pagination
                int historyOffset = Math.max(0, request.getPage() * request.getSize() - allInstances.size());
                int historyLimit = request.getSize() - allInstances.size();

                if (historyLimit > 0) {
                    long historyCount = historyQuery.count();
                    var historicInstances = historyQuery
                            .listPage(historyOffset, historyLimit);

                    List<ProcessInstanceQueryResult.ProcessInstanceInfo> historyInfos =
                        historicInstances.stream()
                            .map(this::convertToHistoricProcessInstanceInfo)
                            .collect(Collectors.toList());

                    allInstances.addAll(historyInfos);
                    totalCount += historyCount;
                }
            }

            int totalPages = (int) Math.ceil((double) totalCount / request.getSize());

            return ProcessInstanceQueryResult.builder()
                    .processInstances(allInstances)
                    .totalCount(totalCount)
                    .currentPage(request.getPage())
                    .pageSize(request.getSize())
                    .totalPages(totalPages)
                    .build();

        } catch (Exception e) {
            throw new WorkflowBusinessException("PROCESS_QUERY_ERROR", "Failed to query process instances: " + e.getMessage(), e);
        }
    }

    /**
     * Delete runtime instance (if any) and historic records after function-unit version rollback cleanup.
     */
    public void purgeProcessInstanceAndHistory(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            return;
        }
        try {
            ProcessInstance runtimePi = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            if (runtimePi != null) {
                try {
                    multiInstanceCanceller.cancelMultiInstanceTasks(processInstanceId);
                } catch (Exception e) {
                    log.warn("cancelMultiInstanceTasks before purge: {}", e.getMessage());
                }
                runtimeService.deleteProcessInstance(processInstanceId, "PURGE_FUNCTION_UNIT_VERSION");
            }
        } catch (Exception e) {
            log.warn("Runtime purge failed for {}: {}", processInstanceId, e.getMessage());
        }
        try {
            if (historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .count() > 0) {
                historyService.deleteHistoricProcessInstance(processInstanceId);
            }
        } catch (Exception e) {
            log.warn("Historic purge failed for {}: {}", processInstanceId, e.getMessage());
        }
    }

    // ==================== Query helpers ====================

    private void applyQueryConditions(ProcessInstanceQuery query, ProcessInstanceQueryRequest request) {
        if (StringUtils.hasText(request.getProcessInstanceId())) {
            query.processInstanceId(request.getProcessInstanceId());
        }

        if (StringUtils.hasText(request.getProcessDefinitionKey())) {
            query.processDefinitionKey(request.getProcessDefinitionKey());
        }

        if (StringUtils.hasText(request.getBusinessKey())) {
            query.processInstanceBusinessKey(request.getBusinessKey());
        }

        if (StringUtils.hasText(request.getStartUserId())) {
            query.startedBy(request.getStartUserId());
        }

        if (request.getStartTimeFrom() != null) {
            query.startedAfter(java.util.Date.from(request.getStartTimeFrom().atZone(ZoneId.systemDefault()).toInstant()));
        }

        if (request.getStartTimeTo() != null) {
            query.startedBefore(java.util.Date.from(request.getStartTimeTo().atZone(ZoneId.systemDefault()).toInstant()));
        }

        // Handle state filtering
        if (StringUtils.hasText(request.getState())) {
            switch (request.getState().toLowerCase()) {
                case "active" -> query.active();
                case "suspended" -> query.suspended();
            }
        }

        // Handle variable filtering
        if (request.getVariables() != null && !request.getVariables().isEmpty()) {
            for (Map.Entry<String, Object> entry : request.getVariables().entrySet()) {
                query.variableValueEquals(entry.getKey(), entry.getValue());
            }
        }

        // Sorting
        if ("startTime".equals(request.getSortBy())) {
            if ("asc".equals(request.getSortDirection())) {
                query.orderByStartTime().asc();
            } else {
                query.orderByStartTime().desc();
            }
        } else {
            query.orderByProcessInstanceId().desc();
        }
    }

    private void applyHistoryQueryConditions(org.flowable.engine.history.HistoricProcessInstanceQuery query, ProcessInstanceQueryRequest request) {
        if (StringUtils.hasText(request.getProcessInstanceId())) {
            query.processInstanceId(request.getProcessInstanceId());
        }

        if (StringUtils.hasText(request.getProcessDefinitionKey())) {
            query.processDefinitionKey(request.getProcessDefinitionKey());
        }

        if (StringUtils.hasText(request.getBusinessKey())) {
            query.processInstanceBusinessKey(request.getBusinessKey());
        }

        if (StringUtils.hasText(request.getStartUserId())) {
            query.startedBy(request.getStartUserId());
        }

        if (request.getStartTimeFrom() != null) {
            query.startedAfter(java.util.Date.from(request.getStartTimeFrom().atZone(ZoneId.systemDefault()).toInstant()));
        }

        if (request.getStartTimeTo() != null) {
            query.startedBefore(java.util.Date.from(request.getStartTimeTo().atZone(ZoneId.systemDefault()).toInstant()));
        }

        // History query only queries completed instances
        if (request.getState() == null || "completed".equalsIgnoreCase(request.getState())) {
            query.finished();
        }

        // Handle variable filtering
        if (request.getVariables() != null && !request.getVariables().isEmpty()) {
            for (Map.Entry<String, Object> entry : request.getVariables().entrySet()) {
                query.variableValueEquals(entry.getKey(), entry.getValue());
            }
        }

        // Sorting
        if ("startTime".equals(request.getSortBy())) {
            if ("asc".equals(request.getSortDirection())) {
                query.orderByProcessInstanceStartTime().asc();
            } else {
                query.orderByProcessInstanceStartTime().desc();
            }
        } else {
            query.orderByProcessInstanceId().desc();
        }
    }

    private ProcessInstanceQueryResult.ProcessInstanceInfo convertToHistoricProcessInstanceInfo(org.flowable.engine.history.HistoricProcessInstance historicProcessInstance) {
        // Get process definition info
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(historicProcessInstance.getProcessDefinitionId())
                .singleResult();

        // Get history variables
        Map<String, Object> variables = processEngine.getHistoryService()
                .createHistoricVariableInstanceQuery()
                .processInstanceId(historicProcessInstance.getId())
                .list()
                .stream()
                .collect(Collectors.toMap(
                    org.flowable.variable.api.history.HistoricVariableInstance::getVariableName,
                    org.flowable.variable.api.history.HistoricVariableInstance::getValue
                ));

        return ProcessInstanceQueryResult.ProcessInstanceInfo.builder()
                .processInstanceId(historicProcessInstance.getId())
                .processDefinitionId(historicProcessInstance.getProcessDefinitionId())
                .processDefinitionKey(historicProcessInstance.getProcessDefinitionKey())
                .processDefinitionName(processDefinition != null ? processDefinition.getName() : null)
                .businessKey(historicProcessInstance.getBusinessKey())
                .name(historicProcessInstance.getName())
                .startTime(historicProcessInstance.getStartTime() != null ?
                    LocalDateTime.ofInstant(historicProcessInstance.getStartTime().toInstant(), ZoneId.systemDefault()) : null)
                .endTime(historicProcessInstance.getEndTime() != null ?
                    LocalDateTime.ofInstant(historicProcessInstance.getEndTime().toInstant(), ZoneId.systemDefault()) : null)
                .startUserId(historicProcessInstance.getStartUserId())
                .state("completed")
                .suspended(false)
                .ended(true)
                .variables(variables)
                .activeTaskCount(0)
                .build();
    }

    private ProcessInstanceQueryResult.ProcessInstanceInfo convertToProcessInstanceInfo(ProcessInstance processInstance) {
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processInstance.getProcessDefinitionId())
                .singleResult();

        long activeTaskCount = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .count();

        Map<String, Object> variables = runtimeService.getVariables(
                processInstance.getId(), KEY_PROCESS_VARIABLES);

        return ProcessInstanceQueryResult.ProcessInstanceInfo.builder()
                .processInstanceId(processInstance.getId())
                .processDefinitionId(processInstance.getProcessDefinitionId())
                .processDefinitionKey(processInstance.getProcessDefinitionKey())
                .processDefinitionName(processDefinition != null ? processDefinition.getName() : null)
                .businessKey(processInstance.getBusinessKey())
                .name(processInstance.getName())
                .startTime(processInstance.getStartTime() != null ?
                    LocalDateTime.ofInstant(processInstance.getStartTime().toInstant(), ZoneId.systemDefault()) : null)
                .endTime(null)
                .startUserId(processInstance.getStartUserId())
                .state(processInstance.isSuspended() ? "suspended" : "active")
                .suspended(processInstance.isSuspended())
                .ended(processInstance.isEnded())
                .variables(variables)
                .activeTaskCount(activeTaskCount)
                .build();
    }
}
