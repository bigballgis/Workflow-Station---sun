package com.workflow.component;

import com.workflow.dto.request.ProcessMonitorQueryRequest;
import com.workflow.exception.WorkflowValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Process Monitor Query Builder
 *
 * Validates monitoring query requests, builds runtime/history queries and converts
 * process instances into monitoring info maps. Extracted from {@link ProcessMonitorComponent}
 * as a pure structural refactor; behavior is preserved verbatim.
 *
 * @author Workflow Engine
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessMonitorQueryBuilder {

    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    /**
     * Validate query request parameters
     */
    public void validateQueryRequest(ProcessMonitorQueryRequest request) {
        List<WorkflowValidationException.ValidationError> errors = new ArrayList<>();

        if (request.getLimit() != null && request.getLimit() <= 0) {
            errors.add(new WorkflowValidationException.ValidationError("limit", "Page size must be greater than 0", request.getLimit()));
        }

        if (request.getOffset() != null && request.getOffset() < 0) {
            errors.add(new WorkflowValidationException.ValidationError("offset", "Offset must not be negative", request.getOffset()));
        }

        if (request.getStartTime() != null && request.getEndTime() != null &&
            request.getStartTime().after(request.getEndTime())) {
            errors.add(new WorkflowValidationException.ValidationError("timeRange", "Start time must not be after end time", null));
        }

        if (!errors.isEmpty()) {
            throw new WorkflowValidationException(errors);
        }
    }

    /**
     * Build runtime query
     */
    public ProcessInstanceQuery buildRuntimeQuery(ProcessMonitorQueryRequest request) {
        ProcessInstanceQuery query = runtimeService.createProcessInstanceQuery();

        if (StringUtils.hasText(request.getProcessDefinitionKey())) {
            query.processDefinitionKey(request.getProcessDefinitionKey());
        }

        if (StringUtils.hasText(request.getBusinessKey())) {
            query.processInstanceBusinessKey(request.getBusinessKey());
        }

        if (request.getStartTime() != null) {
            query.startedAfter(request.getStartTime());
        }

        if (request.getEndTime() != null) {
            query.startedBefore(request.getEndTime());
        }

        // Sorting - simplified to use ID ordering only
        query.orderByProcessInstanceId();

        if ("DESC".equalsIgnoreCase(request.getOrderDirection())) {
            query.desc();
        } else {
            query.asc();
        }

        return query;
    }

    /**
     * Build history query
     */
    public HistoricProcessInstanceQuery buildHistoryQuery(ProcessMonitorQueryRequest request) {
        HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery();

        if ("COMPLETED".equals(request.getStatus())) {
            query.finished();
        } else if ("TERMINATED".equals(request.getStatus())) {
            query.unfinished();
        }

        if (StringUtils.hasText(request.getProcessDefinitionKey())) {
            query.processDefinitionKey(request.getProcessDefinitionKey());
        }

        if (StringUtils.hasText(request.getBusinessKey())) {
            query.processInstanceBusinessKey(request.getBusinessKey());
        }

        if (request.getStartTime() != null) {
            query.startedAfter(request.getStartTime());
        }

        if (request.getEndTime() != null) {
            query.startedBefore(request.getEndTime());
        }

        // Sorting - simplified to use ID ordering only
        query.orderByProcessInstanceId();

        if ("DESC".equalsIgnoreCase(request.getOrderDirection())) {
            query.desc();
        } else {
            query.asc();
        }

        return query;
    }

    /**
     * Convert process instance info
     */
    public Map<String, Object> convertToProcessInstanceInfo(ProcessInstance instance) {
        Map<String, Object> info = new HashMap<>();
        info.put("id", instance.getId());
        info.put("processDefinitionId", instance.getProcessDefinitionId());
        info.put("processDefinitionKey", instance.getProcessDefinitionKey());
        info.put("processDefinitionName", instance.getProcessDefinitionName());
        info.put("businessKey", instance.getBusinessKey());
        info.put("startTime", instance.getStartTime());
        info.put("startUserId", instance.getStartUserId());
        info.put("status", "ACTIVE");
        info.put("suspended", instance.isSuspended());
        return info;
    }

    /**
     * Convert historic process instance info
     */
    public Map<String, Object> convertToProcessInstanceInfo(HistoricProcessInstance instance) {
        Map<String, Object> info = new HashMap<>();
        info.put("id", instance.getId());
        info.put("processDefinitionId", instance.getProcessDefinitionId());
        info.put("processDefinitionKey", instance.getProcessDefinitionKey());
        info.put("processDefinitionName", instance.getProcessDefinitionName());
        info.put("businessKey", instance.getBusinessKey());
        info.put("startTime", instance.getStartTime());
        info.put("endTime", instance.getEndTime());
        info.put("startUserId", instance.getStartUserId());
        info.put("status", instance.getEndTime() != null ? "COMPLETED" : "TERMINATED");
        info.put("durationInMillis", instance.getDurationInMillis());
        info.put("deleteReason", instance.getDeleteReason());
        return info;
    }
}
