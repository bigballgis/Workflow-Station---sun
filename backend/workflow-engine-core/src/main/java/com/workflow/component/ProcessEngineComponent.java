package com.workflow.component;

import com.workflow.aspect.WorkflowAuditAspect.Auditable;
import com.workflow.dto.request.ProcessDefinitionRequest;
import com.workflow.dto.request.StartProcessRequest;
import com.workflow.dto.request.ProcessInstanceQueryRequest;
import com.workflow.dto.request.ProcessInstanceControlRequest;
import com.workflow.dto.response.DeploymentResult;
import com.workflow.dto.response.ProcessInstanceResult;
import com.workflow.dto.response.ProcessDefinitionResult;
import com.workflow.dto.response.ProcessInstanceQueryResult;
import com.workflow.dto.response.ProcessInstanceControlResult;
import com.workflow.dto.response.ActivityInfo;
import com.workflow.dto.response.GatewayEvaluationResult;
import com.workflow.dto.response.ParallelGatewayResult;
import com.workflow.dto.response.EventTriggerResult;
import com.workflow.dto.response.SubProcessInfo;
import com.workflow.enums.AuditOperationType;
import com.workflow.enums.AuditResourceType;
import com.workflow.exception.WorkflowBusinessException;
import com.workflow.exception.WorkflowValidationException;

import com.platform.common.i18n.I18nService;
import com.platform.messaging.support.NotificationDispatchHelper;

import lombok.extern.slf4j.Slf4j;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.task.api.Task;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.Gateway;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.ParallelGateway;
import org.flowable.bpmn.model.InclusiveGateway;
import org.flowable.bpmn.model.EventGateway;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.Event;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.IntermediateCatchEvent;
import org.flowable.bpmn.model.BoundaryEvent;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.bpmn.model.CallActivity;
import org.flowable.common.engine.api.FlowableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Process Engine Component
 * Handles process definition management, process instance execution, BPMN parsing
 */
@Slf4j
@Component
@Transactional
public class ProcessEngineComponent {
    
    @Autowired
    private ProcessEngine processEngine;
    
    @Autowired
    private RepositoryService repositoryService;
    
    @Autowired
    private RuntimeService runtimeService;
    
    @Autowired
    private TaskService taskService;
    
    @Autowired
    private ManagementService managementService;
    
    @Autowired
    private HistoryService historyService;
    
    @Autowired
    private MultiInstanceCanceller multiInstanceCanceller;

    @Autowired
    private NotificationDispatchHelper notificationDispatchHelper;

    @Autowired
    private I18nService i18nService;
    
    /**
     * Deploy process definition
     * Supports BPMN 2.0 file validation and version management
     */
    @Auditable(
        operationType = AuditOperationType.DEPLOY_PROCESS,
        resourceType = AuditResourceType.PROCESS_DEFINITION,
        description = "Deploy process definition",
        captureArgs = true,
        captureResult = true
    )
    public DeploymentResult deployProcess(ProcessDefinitionRequest request) {
        try {
            // Validate request parameters
            validateDeploymentRequest(request);
            
            // Normalize known legacy BPMN serialization issues before validation/deploy
            String normalizedBpmnXml = normalizeBpmnXml(request.getBpmnXml());

            // Validate BPMN file format
            validateBpmnFile(normalizedBpmnXml);
            
            // Create deployment
            Deployment deployment = repositoryService.createDeployment()
                .name(request.getName())
                .category(request.getCategory())
                .key(request.getKey())
                .addString(request.getKey() + ".bpmn", normalizedBpmnXml)
                .deploy();
            
            // Get deployed process definitions
            List<ProcessDefinition> processDefinitions = repositoryService
                .createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .list();
            
            if (!processDefinitions.isEmpty()) {
                ProcessDefinition processDefinition = processDefinitions.get(0);
                // Ensure the process definition ID is always in key:version:uuid format.
                // In some Flowable versions getId() may return only the raw UUID part;
                // explicitly construct the composite format when that happens.
                String rawId = processDefinition.getId();
                String compositeId = rawId.contains(":")
                        ? rawId
                        : String.format("%s:%d:%s",
                                processDefinition.getKey(),
                                processDefinition.getVersion(),
                                rawId);
                return DeploymentResult.success(
                    deployment.getId(),
                    compositeId,
                    processDefinition.getKey(),
                    processDefinition.getName(),
                    processDefinition.getVersion()
                );
            } else {
                return DeploymentResult.failure("Deployment succeeded but no process definition found");
            }
                
        } catch (WorkflowValidationException e) {
            // Re-throw validation exceptions so they can be caught by tests
            throw e;
        } catch (Exception e) {
            return DeploymentResult.failure("Failed to deploy process definition: " + e.getMessage());
        }
    }
    
    /**
     * Start process instance
     */
    @Auditable(
        operationType = AuditOperationType.START_PROCESS,
        resourceType = AuditResourceType.PROCESS_INSTANCE,
        description = "Start process instance",
        captureArgs = true,
        captureResult = true
    )
    public ProcessInstanceResult startProcess(StartProcessRequest request) {
        try {
            // Validate request parameters
            validateStartProcessRequest(request);
            
            // Verify process definition exists
            ProcessDefinition processDefinition = getProcessDefinition(request.getProcessDefinitionKey());
            
            // Start process instance with start user
            ProcessInstance processInstance;
            if (StringUtils.hasText(request.getStartUserId())) {
                // Set start user ID
                org.flowable.common.engine.impl.identity.Authentication.setAuthenticatedUserId(request.getStartUserId());
                try {
                    processInstance = runtimeService.startProcessInstanceByKey(
                        request.getProcessDefinitionKey(), 
                        request.getBusinessKey(), 
                        request.getVariables());
                } finally {
                    // Clear authenticated user ID
                    org.flowable.common.engine.impl.identity.Authentication.setAuthenticatedUserId(null);
                }
            } else {
                processInstance = runtimeService.startProcessInstanceByKey(
                    request.getProcessDefinitionKey(), 
                    request.getBusinessKey(), 
                    request.getVariables());
            }
            
            ProcessInstanceResult result = ProcessInstanceResult.builder()
                .processInstanceId(processInstance.getId())
                .processDefinitionId(processInstance.getProcessDefinitionId())
                .processDefinitionKey(processInstance.getProcessDefinitionKey())
                .businessKey(processInstance.getBusinessKey())
                .name(processInstance.getName())
                .startTime(processInstance.getStartTime() != null ? 
                    LocalDateTime.ofInstant(processInstance.getStartTime().toInstant(), ZoneId.systemDefault()) : null)
                .startUserId(request.getStartUserId()) // Use the request startUserId
                .variables(request.getVariables())
                .success(true)
                .message("Process instance started successfully")
                .build();

            if (StringUtils.hasText(request.getStartUserId())) {
                String key = request.getProcessDefinitionKey();
                notificationDispatchHelper.publishToUserAfterCommit(
                        request.getStartUserId(),
                        "PROCESS",
                        i18nService.getMessage("workflow.notification.process_started_title"),
                        i18nService.getMessage(
                                "workflow.notification.process_started_body",
                                key,
                                processInstance.getId()),
                        "/tasks",
                        "workflow-engine");
            }

            return result;
                
        } catch (Exception e) {
            throw new WorkflowBusinessException("PROCESS_START_ERROR", "Failed to start process instance: " + e.getMessage(), e);
        }
    }
    
    /**
     * Query process definition list
     */
    public List<ProcessDefinitionResult> getProcessDefinitions(String category, String key) {
        try {
            var query = repositoryService.createProcessDefinitionQuery()
                .latestVersion()
                .active();
            
            if (StringUtils.hasText(key)) {
                query.processDefinitionKey(key);
            }
            
            List<ProcessDefinition> processDefinitions = query.list();
            
            List<String> deploymentIds = processDefinitions.stream()
                .map(ProcessDefinition::getDeploymentId)
                .distinct()
                .toList();
            
            Map<String, Deployment> deploymentMap = new HashMap<>();
            if (!deploymentIds.isEmpty()) {
                List<Deployment> deployments = repositoryService.createDeploymentQuery()
                    .list();
                for (Deployment d : deployments) {
                    deploymentMap.put(d.getId(), d);
                }
            }
            
            if (StringUtils.hasText(category)) {
                processDefinitions = processDefinitions.stream()
                    .filter(pd -> {
                        Deployment deployment = deploymentMap.get(pd.getDeploymentId());
                        return deployment != null && category.equals(deployment.getCategory());
                    })
                    .collect(Collectors.toList());
            }
            
            return processDefinitions.stream()
                .map(pd -> convertToProcessDefinitionResult(pd, deploymentMap.get(pd.getDeploymentId())))
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            throw new WorkflowBusinessException("PROCESS_QUERY_ERROR", "Failed to query process definitions: " + e.getMessage(), e);
        }
    }
    
    /**
     * Delete process definition
     */
    public void deleteProcessDefinition(String deploymentId, boolean cascade) {
        try {
            // Check for running process instances
            if (!cascade) {
                long runningInstances = runtimeService.createProcessInstanceQuery()
                    .deploymentId(deploymentId)
                    .count();
                
                if (runningInstances > 0) {
                    throw new WorkflowValidationException(Collections.singletonList(
                        new WorkflowValidationException.ValidationError(
                            "deploymentId", 
                            "Cannot delete process definition, there are " + runningInstances + " running process instances", 
                            deploymentId)));
                }
            }
            
            repositoryService.deleteDeployment(deploymentId, cascade);
            
        } catch (WorkflowValidationException e) {
            // Re-throw validation exceptions as-is
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("PROCESS_DELETE_ERROR", "Failed to delete process definition: " + e.getMessage(), e);
        }
    }
    
    /**
     * Suspend process definition
     */
    public void suspendProcessDefinition(String processDefinitionId) {
        try {
            repositoryService.suspendProcessDefinitionById(processDefinitionId);
        } catch (Exception e) {
            throw new WorkflowBusinessException("PROCESS_SUSPEND_ERROR", "Failed to suspend process definition: " + e.getMessage(), e);
        }
    }
    
    /**
     * Activate process definition
     */
    public void activateProcessDefinition(String processDefinitionId) {
        try {
            repositoryService.activateProcessDefinitionById(processDefinitionId);
        } catch (Exception e) {
            throw new WorkflowBusinessException("PROCESS_ACTIVATE_ERROR", "Failed to activate process definition: " + e.getMessage(), e);
        }
    }
    
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
     * Get process instance status
     * Used to check if process is completed and get last activity node
     */
    public Map<String, Object> getProcessInstanceStatus(String processInstanceId) {
        Map<String, Object> status = new HashMap<>();
        
        try {
            // First check runtime process instance
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            
            if (processInstance != null) {
                // Process is still running
                status.put("completed", false);
                status.put("processInstanceId", processInstanceId);
                status.put("state", processInstance.isSuspended() ? "SUSPENDED" : "RUNNING");
                
                // Get current active tasks
                List<Task> tasks = taskService.createTaskQuery()
                        .processInstanceId(processInstanceId)
                        .list();
                
                if (!tasks.isEmpty()) {
                    Task currentTask = tasks.get(0);
                    status.put("nextTaskName", currentTask.getName());
                    status.put("nextAssignee", currentTask.getAssignee());
                    status.put("nextTaskId", currentTask.getId());

                    List<String> candidateUserIds = new ArrayList<>();
                    for (org.flowable.identitylink.api.IdentityLink link
                            : taskService.getIdentityLinksForTask(currentTask.getId())) {
                        if ("candidate".equals(link.getType())
                                && link.getUserId() != null
                                && !link.getUserId().isBlank()) {
                            candidateUserIds.add(link.getUserId().trim());
                        }
                    }
                    if (!candidateUserIds.isEmpty()) {
                        status.put("nextCandidateUsers", String.join(",", candidateUserIds));
                    }
                }
                
                return status;
            }
            
            // Process not in runtime table, check history
            HistoricProcessInstance historicProcessInstance = historyService
                    .createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            
            if (historicProcessInstance != null) {
                status.put("completed", true);
                status.put("processInstanceId", processInstanceId);
                status.put("state", "COMPLETED");
                status.put("endTime", historicProcessInstance.getEndTime());
                
                // Get last activity node (prioritize end events)
                List<HistoricActivityInstance> endEvents = historyService
                        .createHistoricActivityInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .activityType("endEvent")
                        .orderByHistoricActivityInstanceStartTime()
                        .desc()
                        .list();
                
                if (!endEvents.isEmpty()) {
                    HistoricActivityInstance endEvent = endEvents.get(0);
                    String activityName = endEvent.getActivityName();
                    if (activityName != null && !activityName.isEmpty() && 
                        !activityName.equalsIgnoreCase("End")) {
                        status.put("lastActivityName", activityName);
                        return status;
                    }
                }
                
                // If end event has no meaningful name, get the last user task
                List<HistoricActivityInstance> userTasks = historyService
                        .createHistoricActivityInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .activityType("userTask")
                        .finished()
                        .orderByHistoricActivityInstanceEndTime()
                        .desc()
                        .list();
                
                if (!userTasks.isEmpty()) {
                    status.put("lastActivityName", userTasks.get(0).getActivityName());
                } else {
                    status.put("lastActivityName", "Completed");
                }
                
                return status;
            }
            
            // Process instance does not exist
            status.put("completed", false);
            status.put("error", "Process instance does not exist");
            return status;
            
        } catch (Exception e) {
            log.error("Failed to get process instance status: {}", e.getMessage(), e);
            status.put("completed", false);
            status.put("error", e.getMessage());
            return status;
        }
    }
    
    /**
     * Get current activity node of process instance
     */
    public Map<String, Object> getCurrentActivity(String processInstanceId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // First check runtime process instance
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            
            if (processInstance != null) {
                // Process still running, get current activity nodes
                List<org.flowable.engine.runtime.Execution> executions = runtimeService
                        .createExecutionQuery()
                        .processInstanceId(processInstanceId)
                        .list();
                
                org.flowable.bpmn.model.BpmnModel bpmnModel = repositoryService.getBpmnModel(processInstance.getProcessDefinitionId());
                
                // Prioritize non-SequenceFlow activity nodes (userTask, endEvent, gateway, etc.)
                // SequenceFlow name is typically a condition label (e.g. "Yes"/"No"), not suitable as currentNode
                Map<String, Object> fallback = null;
                for (org.flowable.engine.runtime.Execution execution : executions) {
                    String activityId = execution.getActivityId();
                    if (activityId != null) {
                        org.flowable.bpmn.model.FlowElement flowElement = bpmnModel.getFlowElement(activityId);
                        
                        if (flowElement != null) {
                            // Skip SequenceFlow - its name is a condition label (e.g. "Yes"/"No"), not a node name
                            if (flowElement instanceof org.flowable.bpmn.model.SequenceFlow) {
                                log.debug("Skipping SequenceFlow {} (name: {}) for process {}", 
                                        activityId, flowElement.getName(), processInstanceId);
                                if (fallback == null) {
                                    fallback = new HashMap<>();
                                    fallback.put("activityId", activityId);
                                    fallback.put("activityName", flowElement.getName());
                                    fallback.put("activityType", "SequenceFlow");
                                    fallback.put("processInstanceId", processInstanceId);
                                    fallback.put("state", "RUNNING");
                                }
                                continue;
                            }
                            
                            result.put("activityId", activityId);
                            result.put("activityName", flowElement.getName());
                            result.put("activityType", flowElement.getClass().getSimpleName().replace("Impl", ""));
                            result.put("processInstanceId", processInstanceId);
                            result.put("state", "RUNNING");
                            
                            log.info("Current activity for process {}: {} ({})", 
                                    processInstanceId, flowElement.getName(), flowElement.getClass().getSimpleName());
                            
                            return result;
                        }
                    }
                }
                
                // If only SequenceFlow found, try to resolve its target node as current activity
                if (fallback != null) {
                    String seqFlowId = (String) fallback.get("activityId");
                    org.flowable.bpmn.model.FlowElement seqFlow = bpmnModel.getFlowElement(seqFlowId);
                    if (seqFlow instanceof org.flowable.bpmn.model.SequenceFlow) {
                        String targetRef = ((org.flowable.bpmn.model.SequenceFlow) seqFlow).getTargetRef();
                        org.flowable.bpmn.model.FlowElement targetElement = bpmnModel.getFlowElement(targetRef);
                        if (targetElement != null) {
                            result.put("activityId", targetRef);
                            result.put("activityName", targetElement.getName());
                            result.put("activityType", targetElement.getClass().getSimpleName().replace("Impl", ""));
                            result.put("processInstanceId", processInstanceId);
                            result.put("state", "RUNNING");
                            
                            log.info("Current activity (resolved from SequenceFlow target) for process {}: {} ({})", 
                                    processInstanceId, targetElement.getName(), targetElement.getClass().getSimpleName());
                            return result;
                        }
                    }
                    // Cannot resolve target node, return SequenceFlow as last resort
                    log.warn("Could not resolve SequenceFlow target for process {}, returning SequenceFlow as fallback", processInstanceId);
                    return fallback;
                }
                
                result.put("error", "Current activity node not found");
                return result;
            }
            
            // Process not in runtime table, check history
            HistoricProcessInstance historicProcessInstance = historyService
                    .createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            
            if (historicProcessInstance != null) {
                // Process completed, get last activity node
                List<HistoricActivityInstance> activities = historyService
                        .createHistoricActivityInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .orderByHistoricActivityInstanceStartTime()
                        .desc()
                        .list();
                
                if (!activities.isEmpty()) {
                    HistoricActivityInstance lastActivity = activities.get(0);
                    result.put("activityId", lastActivity.getActivityId());
                    result.put("activityName", lastActivity.getActivityName());
                    result.put("activityType", lastActivity.getActivityType());
                    result.put("processInstanceId", processInstanceId);
                    result.put("state", "COMPLETED");
                    return result;
                }
            }
            
            result.put("error", "Process instance does not exist");
            return result;
            
        } catch (Exception e) {
            log.error("Failed to get current activity: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            return result;
        }
    }
    
    /**
     * Control process instance (suspend, resume, terminate)
     */
    public ProcessInstanceControlResult controlProcessInstance(ProcessInstanceControlRequest request) {
        try {
            // Validate request parameters
            validateProcessInstanceControlRequest(request);
            
            // Verify process instance exists
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(request.getProcessInstanceId())
                    .singleResult();
            
            if (processInstance == null) {
                return ProcessInstanceControlResult.failure(
                    request.getProcessInstanceId(), 
                    request.getAction(), 
                    request.getUserId(),
                    "Process instance does not exist");
            }
            
            // Execute the corresponding operation
            switch (request.getAction().toLowerCase()) {
                case "suspend" -> {
                    if (processInstance.isSuspended()) {
                        return ProcessInstanceControlResult.failure(
                            request.getProcessInstanceId(), 
                            request.getAction(), 
                            request.getUserId(),
                            "Process instance is already suspended");
                    }
                    runtimeService.suspendProcessInstanceById(request.getProcessInstanceId());
                    return ProcessInstanceControlResult.success(
                        request.getProcessInstanceId(), 
                        request.getAction(), 
                        request.getUserId(),
                        "suspended");
                }
                case "activate" -> {
                    if (!processInstance.isSuspended()) {
                        return ProcessInstanceControlResult.failure(
                            request.getProcessInstanceId(), 
                            request.getAction(), 
                            request.getUserId(),
                            "Process instance is already active");
                    }
                    runtimeService.activateProcessInstanceById(request.getProcessInstanceId());
                    return ProcessInstanceControlResult.success(
                        request.getProcessInstanceId(), 
                        request.getAction(), 
                        request.getUserId(),
                        "active");
                }
                case "terminate" -> {
                    if (processInstance.isEnded()) {
                        return ProcessInstanceControlResult.failure(
                            request.getProcessInstanceId(), 
                            request.getAction(), 
                            request.getUserId(),
                            "Process instance has already ended");
                    }
                    
                    // Cancel multi-instance sub-tasks before terminating the process
                    try {
                        multiInstanceCanceller.cancelMultiInstanceTasks(request.getProcessInstanceId());
                    } catch (Exception e) {
                        log.error("Failed to cancel multi-instance tasks for process instance {}: {}", 
                            request.getProcessInstanceId(), e.getMessage(), e);
                        // Continue with termination even if cancellation fails
                    }
                    
                    runtimeService.deleteProcessInstance(
                        request.getProcessInstanceId(), 
                        request.getReason() != null ? request.getReason() : "Manually terminated");
                    return ProcessInstanceControlResult.success(
                        request.getProcessInstanceId(), 
                        request.getAction(), 
                        request.getUserId(),
                        "terminated");
                }
                default -> {
                    return ProcessInstanceControlResult.failure(
                        request.getProcessInstanceId(), 
                        request.getAction(), 
                        request.getUserId(),
                        "Unsupported operation type: " + request.getAction());
                }
            }
            
        } catch (WorkflowValidationException e) {
            return ProcessInstanceControlResult.failure(
                request.getProcessInstanceId(), 
                request.getAction(), 
                request.getUserId(),
                e.getMessage());
        } catch (Exception e) {
            return ProcessInstanceControlResult.failure(
                request.getProcessInstanceId(), 
                request.getAction(), 
                request.getUserId(),
                e.getMessage());
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
    
    // ==================== BPMN gateway and event handling ====================
    
    /**
     * Get current activity node information for process instance
     * For gateway and event handling status queries
     */
    public List<ActivityInfo> getCurrentActivities(String processInstanceId) {
        try {
            // Verify process instance exists
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            
            if (processInstance == null) {
                throw new WorkflowBusinessException("PROCESS_NOT_FOUND", "Process instance does not exist: " + processInstanceId);
            }
            
            List<Execution> executions = runtimeService.createExecutionQuery()
                    .processInstanceId(processInstanceId)
                    .list();
            
            BpmnModel bpmnModel = repositoryService.getBpmnModel(processInstance.getProcessDefinitionId());
            List<ActivityInfo> activities = new ArrayList<>();
            
            for (Execution execution : executions) {
                if (execution.getActivityId() != null) {
                    FlowElement flowElement = bpmnModel.getFlowElement(execution.getActivityId());
                    
                    if (flowElement != null) {
                        ActivityInfo activityInfo = ActivityInfo.builder()
                                .executionId(execution.getId())
                                .activityId(execution.getActivityId())
                                .activityName(flowElement.getName())
                                .activityType(getActivityType(flowElement))
                                .isActive(!execution.isEnded())
                                .isWaitState(isWaitState(flowElement))
                                .build();
                        
                        activities.add(activityInfo);
                    }
                }
            }
            
            return activities;
            
        } catch (Exception e) {
            throw new WorkflowBusinessException("ACTIVITY_QUERY_ERROR", "Failed to query current activity nodes: " + e.getMessage(), e);
        }
    }
    
    /**
     * Evaluate exclusive gateway condition expressions
     * Select execution path based on process variables and condition expressions
     */
    public GatewayEvaluationResult evaluateExclusiveGateway(String processInstanceId, String gatewayId) {
        try {
            // Get process instance and BPMN model
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            
            if (processInstance == null) {
                throw new WorkflowBusinessException("PROCESS_NOT_FOUND", "Process instance does not exist: " + processInstanceId);
            }
            
            BpmnModel bpmnModel = repositoryService.getBpmnModel(processInstance.getProcessDefinitionId());
            FlowElement flowElement = bpmnModel.getFlowElement(gatewayId);
            
            if (!(flowElement instanceof ExclusiveGateway)) {
                throw new WorkflowBusinessException("INVALID_GATEWAY", "Specified element is not an exclusive gateway: " + gatewayId);
            }
            
            ExclusiveGateway exclusiveGateway = (ExclusiveGateway) flowElement;
            
            // Get process variables
            Map<String, Object> variables = runtimeService.getVariables(processInstanceId);
            
            // Evaluate all outgoing conditions
            List<SequenceFlow> outgoingFlows = exclusiveGateway.getOutgoingFlows();
            List<GatewayEvaluationResult.FlowEvaluation> flowEvaluations = new ArrayList<>();
            
            String selectedFlowId = null;
            String selectedFlowName = null;
            
            for (SequenceFlow sequenceFlow : outgoingFlows) {
                boolean conditionResult = false;
                String conditionExpression = sequenceFlow.getConditionExpression();
                String evaluationMessage = "No condition expression";
                
                if (StringUtils.hasText(conditionExpression)) {
                    try {
                        // Simplified condition evaluation - should use Flowable's expression engine in production
                        // Basic condition evaluation logic provided here
                        conditionResult = evaluateSimpleCondition(conditionExpression, variables);
                        evaluationMessage = "Condition expression: " + conditionExpression + ", result: " + conditionResult;
                        
                    } catch (Exception e) {
                        evaluationMessage = "Condition expression evaluation failed: " + e.getMessage();
                    }
                } else {
                    // Default flow (flow without condition expression)
                    conditionResult = (selectedFlowId == null); // Select default flow if no other flow selected
                    evaluationMessage = "Default flow";
                }
                
                flowEvaluations.add(GatewayEvaluationResult.FlowEvaluation.builder()
                        .flowId(sequenceFlow.getId())
                        .flowName(sequenceFlow.getName())
                        .conditionExpression(conditionExpression)
                        .conditionResult(conditionResult)
                        .evaluationMessage(evaluationMessage)
                        .build());
                
                // Exclusive gateway selects only the first flow with true condition
                if (conditionResult && selectedFlowId == null) {
                    selectedFlowId = sequenceFlow.getId();
                    selectedFlowName = sequenceFlow.getName();
                }
            }
            
            return GatewayEvaluationResult.builder()
                    .gatewayId(gatewayId)
                    .gatewayName(exclusiveGateway.getName())
                    .gatewayType("ExclusiveGateway")
                    .selectedFlowId(selectedFlowId)
                    .selectedFlowName(selectedFlowName)
                    .flowEvaluations(flowEvaluations)
                    .variables(variables)
                    .evaluationTime(LocalDateTime.now())
                    .build();
            
        } catch (Exception e) {
            throw new WorkflowBusinessException("GATEWAY_EVALUATION_ERROR", "Failed to evaluate exclusive gateway conditions: " + e.getMessage(), e);
        }
    }
    
    /**
     * Handle parallel gateway branch creation and merging
     * Supports parallel execution path management
     */
    public ParallelGatewayResult handleParallelGateway(String processInstanceId, String gatewayId) {
        try {
            // Get process instance and BPMN model
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            
            if (processInstance == null) {
                throw new WorkflowBusinessException("PROCESS_NOT_FOUND", "Process instance does not exist: " + processInstanceId);
            }
            
            BpmnModel bpmnModel = repositoryService.getBpmnModel(processInstance.getProcessDefinitionId());
            FlowElement flowElement = bpmnModel.getFlowElement(gatewayId);
            
            if (!(flowElement instanceof ParallelGateway)) {
                throw new WorkflowBusinessException("INVALID_GATEWAY", "Specified element is not a parallel gateway: " + gatewayId);
            }
            
            ParallelGateway parallelGateway = (ParallelGateway) flowElement;
            
            // Get current execution instances at this gateway
            List<Execution> gatewayExecutions = runtimeService.createExecutionQuery()
                    .processInstanceId(processInstanceId)
                    .activityId(gatewayId)
                    .list();
            
            List<ParallelGatewayResult.BranchInfo> branches = new ArrayList<>();
            
            // Check if fork gateway (multiple outgoing) or join gateway (multiple incoming)
            boolean isForkGateway = parallelGateway.getOutgoingFlows().size() > 1;
            boolean isJoinGateway = parallelGateway.getIncomingFlows().size() > 1;
            
            if (isForkGateway) {
                // Fork gateway: create multiple parallel branches
                for (SequenceFlow outgoingFlow : parallelGateway.getOutgoingFlows()) {
                    ParallelGatewayResult.BranchInfo branchInfo = ParallelGatewayResult.BranchInfo.builder()
                            .branchId(outgoingFlow.getId())
                            .branchName(outgoingFlow.getName())
                            .targetActivityId(outgoingFlow.getTargetRef())
                            .status("created")
                            .createdTime(LocalDateTime.now())
                            .build();
                    
                    branches.add(branchInfo);
                }
            }
            
            if (isJoinGateway) {
                // Join gateway: wait for all branches to complete
                for (SequenceFlow incomingFlow : parallelGateway.getIncomingFlows()) {
                    // Check if branch has reached join point
                    List<Execution> branchExecutions = runtimeService.createExecutionQuery()
                            .processInstanceId(processInstanceId)
                            .activityId(incomingFlow.getSourceRef())
                            .list();
                    
                    String status = branchExecutions.isEmpty() ? "completed" : "active";
                    
                    ParallelGatewayResult.BranchInfo branchInfo = ParallelGatewayResult.BranchInfo.builder()
                            .branchId(incomingFlow.getId())
                            .branchName(incomingFlow.getName())
                            .sourceActivityId(incomingFlow.getSourceRef())
                            .status(status)
                            .completedTime(status.equals("completed") ? LocalDateTime.now() : null)
                            .build();
                    
                    branches.add(branchInfo);
                }
            }
            
            // Calculate gateway status
            String gatewayStatus = "waiting";
            if (isForkGateway && !isJoinGateway) {
                gatewayStatus = "forked";
            } else if (isJoinGateway && !isForkGateway) {
                long completedBranches = branches.stream()
                        .filter(b -> "completed".equals(b.getStatus()))
                        .count();
                gatewayStatus = (completedBranches == branches.size()) ? "joined" : "joining";
            }
            
            return ParallelGatewayResult.builder()
                    .gatewayId(gatewayId)
                    .gatewayName(parallelGateway.getName())
                    .gatewayType("ParallelGateway")
                    .isForkGateway(isForkGateway)
                    .isJoinGateway(isJoinGateway)
                    .status(gatewayStatus)
                    .branches(branches)
                    .totalBranches(branches.size())
                    .activeBranches((int) branches.stream().filter(b -> "active".equals(b.getStatus())).count())
                    .completedBranches((int) branches.stream().filter(b -> "completed".equals(b.getStatus())).count())
                    .evaluationTime(LocalDateTime.now())
                    .build();
            
        } catch (Exception e) {
            throw new WorkflowBusinessException("PARALLEL_GATEWAY_ERROR", "Failed to handle parallel gateway: " + e.getMessage(), e);
        }
    }
    
    /**
     * Trigger process event
     * Supports triggering and propagation of message events, signal events, etc.
     */
    public EventTriggerResult triggerEvent(String processInstanceId, String eventId, String eventType, Map<String, Object> eventData) {
        try {
            // Verify process instance exists
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            
            if (processInstance == null) {
                throw new WorkflowBusinessException("PROCESS_NOT_FOUND", "Process instance does not exist: " + processInstanceId);
            }
            
            List<String> triggeredExecutions = new ArrayList<>();
            String resultMessage = "";
            
            switch (eventType.toLowerCase()) {
                case "message" -> {
                    // Trigger message event
                    if (eventData != null && eventData.containsKey("messageName")) {
                        String messageName = (String) eventData.get("messageName");
                        
                        // Find execution instances waiting for this message
                        List<Execution> messageExecutions = runtimeService.createExecutionQuery()
                                .processInstanceId(processInstanceId)
                                .messageEventSubscriptionName(messageName)
                                .list();
                        
                        for (Execution execution : messageExecutions) {
                            runtimeService.messageEventReceived(messageName, execution.getId(), eventData);
                            triggeredExecutions.add(execution.getId());
                        }
                        
                        resultMessage = "Triggered message event: " + messageName + ", affected executions: " + triggeredExecutions.size();
                    } else {
                        throw new WorkflowBusinessException("INVALID_EVENT_DATA", "Message event requires messageName parameter");
                    }
                }
                case "signal" -> {
                    // Trigger signal event
                    if (eventData != null && eventData.containsKey("signalName")) {
                        String signalName = (String) eventData.get("signalName");
                        
                        // Find execution instances waiting for this signal
                        List<Execution> signalExecutions = runtimeService.createExecutionQuery()
                                .processInstanceId(processInstanceId)
                                .signalEventSubscriptionName(signalName)
                                .list();
                        
                        for (Execution execution : signalExecutions) {
                            runtimeService.signalEventReceived(signalName, execution.getId(), eventData);
                            triggeredExecutions.add(execution.getId());
                        }
                        
                        resultMessage = "Triggered signal event: " + signalName + ", affected executions: " + triggeredExecutions.size();
                    } else {
                        throw new WorkflowBusinessException("INVALID_EVENT_DATA", "Signal event requires signalName parameter");
                    }
                }
                case "timer" -> {
                    // Trigger timer event (usually auto-triggered by system, manual trigger provided here)
                    List<Execution> timerExecutions = runtimeService.createExecutionQuery()
                            .processInstanceId(processInstanceId)
                            .activityId(eventId)
                            .list();
                    
                    for (Execution execution : timerExecutions) {
                        // Manually advance timer event
                        runtimeService.trigger(execution.getId(), eventData);
                        triggeredExecutions.add(execution.getId());
                    }
                    
                    resultMessage = "Triggered timer event: " + eventId + ", affected executions: " + triggeredExecutions.size();
                }
                default -> {
                    throw new WorkflowBusinessException("UNSUPPORTED_EVENT_TYPE", "Unsupported event type: " + eventType);
                }
            }
            
            return EventTriggerResult.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .processInstanceId(processInstanceId)
                    .triggeredExecutions(triggeredExecutions)
                    .eventData(eventData)
                    .triggerTime(LocalDateTime.now())
                    .success(true)
                    .message(resultMessage)
                    .build();
            
        } catch (Exception e) {
            return EventTriggerResult.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .processInstanceId(processInstanceId)
                    .triggeredExecutions(new ArrayList<>())
                    .eventData(eventData)
                    .triggerTime(LocalDateTime.now())
                    .success(false)
                    .message("Failed to trigger event: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * Get sub-process information
     * Supports nested execution queries for sub-processes and call activities
     */
    public List<SubProcessInfo> getSubProcesses(String processInstanceId) {
        try {
            // Verify process instance exists
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            
            if (processInstance == null) {
                throw new WorkflowBusinessException("PROCESS_NOT_FOUND", "Process instance does not exist: " + processInstanceId);
            }
            
            List<SubProcessInfo> subProcesses = new ArrayList<>();
            
            // Find sub-process instances
            List<ProcessInstance> childProcessInstances = runtimeService.createProcessInstanceQuery()
                    .superProcessInstanceId(processInstanceId)
                    .list();
            
            for (ProcessInstance childProcess : childProcessInstances) {
                // Get call activity info
                Execution superExecution = runtimeService.createExecutionQuery()
                        .executionId(childProcess.getSuperExecutionId())
                        .singleResult();
                
                SubProcessInfo subProcessInfo = SubProcessInfo.builder()
                        .subProcessInstanceId(childProcess.getId())
                        .subProcessDefinitionKey(childProcess.getProcessDefinitionKey())
                        .subProcessDefinitionName(childProcess.getName())
                        .callActivityId(superExecution != null ? superExecution.getActivityId() : null)
                        .businessKey(childProcess.getBusinessKey())
                        .startTime(childProcess.getStartTime() != null ? 
                            LocalDateTime.ofInstant(childProcess.getStartTime().toInstant(), ZoneId.systemDefault()) : null)
                        .startUserId(childProcess.getStartUserId())
                        .isActive(!childProcess.isEnded())
                        .isSuspended(childProcess.isSuspended())
                        .build();
                
                subProcesses.add(subProcessInfo);
            }
            
            // Find embedded sub-processes (within same process instance)
            BpmnModel bpmnModel = repositoryService.getBpmnModel(processInstance.getProcessDefinitionId());
            List<Execution> executions = runtimeService.createExecutionQuery()
                    .processInstanceId(processInstanceId)
                    .list();
            
            for (Execution execution : executions) {
                if (execution.getActivityId() != null) {
                    FlowElement flowElement = bpmnModel.getFlowElement(execution.getActivityId());
                    
                    if (flowElement instanceof SubProcess) {
                        SubProcess subProcess = (SubProcess) flowElement;
                        
                        SubProcessInfo subProcessInfo = SubProcessInfo.builder()
                                .subProcessInstanceId(execution.getId())
                                .subProcessDefinitionKey(subProcess.getId())
                                .subProcessDefinitionName(subProcess.getName())
                                .callActivityId(null) // Embedded sub-process has no call activity
                                .businessKey(null)
                                .startTime(LocalDateTime.now()) // Embedded sub-process start time is hard to determine precisely
                                .startUserId(processInstance.getStartUserId())
                                .isActive(!execution.isEnded())
                                .isSuspended(false)
                                .isEmbedded(true)
                                .build();
                        
                        subProcesses.add(subProcessInfo);
                    }
                }
            }
            
            return subProcesses;
            
        } catch (Exception e) {
            throw new WorkflowBusinessException("SUBPROCESS_QUERY_ERROR", "Failed to query sub-processes: " + e.getMessage(), e);
        }
    }
    
    // Private helper methods
    
    private String getActivityType(FlowElement flowElement) {
        if (flowElement instanceof org.flowable.bpmn.model.UserTask) {
            return "UserTask";
        } else if (flowElement instanceof org.flowable.bpmn.model.ServiceTask) {
            return "ServiceTask";
        } else if (flowElement instanceof ExclusiveGateway) {
            return "ExclusiveGateway";
        } else if (flowElement instanceof ParallelGateway) {
            return "ParallelGateway";
        } else if (flowElement instanceof InclusiveGateway) {
            return "InclusiveGateway";
        } else if (flowElement instanceof EventGateway) {
            return "EventGateway";
        } else if (flowElement instanceof StartEvent) {
            return "StartEvent";
        } else if (flowElement instanceof EndEvent) {
            return "EndEvent";
        } else if (flowElement instanceof IntermediateCatchEvent) {
            return "IntermediateCatchEvent";
        } else if (flowElement instanceof BoundaryEvent) {
            return "BoundaryEvent";
        } else if (flowElement instanceof SubProcess) {
            return "SubProcess";
        } else if (flowElement instanceof CallActivity) {
            return "CallActivity";
        } else {
            return flowElement.getClass().getSimpleName();
        }
    }
    
    private boolean isWaitState(FlowElement flowElement) {
        // User tasks, receive tasks, message events, etc. are wait states
        return flowElement instanceof org.flowable.bpmn.model.UserTask ||
               flowElement instanceof org.flowable.bpmn.model.ReceiveTask ||
               flowElement instanceof IntermediateCatchEvent ||
               flowElement instanceof BoundaryEvent;
    }
    
    private boolean evaluateSimpleCondition(String conditionExpression, Map<String, Object> variables) {
        // Simplified condition evaluation logic
        // Should use Flowable's expression engine in production
        
        // Remove potential expression syntax markers
        String expression = conditionExpression.trim();
        if (expression.startsWith("${") && expression.endsWith("}")) {
            expression = expression.substring(2, expression.length() - 1).trim();
        }
        
        // Handle simple comparison expressions
        if (expression.contains("==")) {
            String[] parts = expression.split("==");
            if (parts.length == 2) {
                String leftVar = parts[0].trim();
                String rightValue = parts[1].trim().replace("'", "").replace("\"", "");
                
                Object varValue = variables.get(leftVar);
                return rightValue.equals(String.valueOf(varValue));
            }
        }
        
        if (expression.contains("!=")) {
            String[] parts = expression.split("!=");
            if (parts.length == 2) {
                String leftVar = parts[0].trim();
                String rightValue = parts[1].trim().replace("'", "").replace("\"", "");
                
                Object varValue = variables.get(leftVar);
                return !rightValue.equals(String.valueOf(varValue));
            }
        }
        
        // Handle numeric comparison expressions: <=, >=, <, >
        // Note: must check <= and >= before < and >, to avoid false matches
        String[] numericOperators = {"<=", ">=", "<", ">"};
        for (String op : numericOperators) {
            if (expression.contains(op)) {
                String[] parts = expression.split(java.util.regex.Pattern.quote(op), 2);
                if (parts.length == 2) {
                    String leftVar = parts[0].trim();
                    String rightLiteral = parts[1].trim();
                    
                    Object varValue = variables.get(leftVar);
                    if (varValue == null) {
                        log.warn("Variable '{}' is null during numeric comparison, returning false", leftVar);
                        return false;
                    }
                    
                    try {
                        double leftNum = toDouble(varValue);
                        double rightNum = Double.parseDouble(rightLiteral);
                        
                        if ("<=".equals(op)) return leftNum <= rightNum;
                        if (">=".equals(op)) return leftNum >= rightNum;
                        if ("<".equals(op)) return leftNum < rightNum;
                        if (">".equals(op)) return leftNum > rightNum;
                    } catch (NumberFormatException e) {
                        log.warn("Failed to parse numeric comparison: {} {} {}, varValue={}", leftVar, op, rightLiteral, varValue);
                        return false;
                    }
                }
            }
        }
        
        // Handle boolean variables
        Object varValue = variables.get(expression);
        if (varValue instanceof Boolean) {
            return (Boolean) varValue;
        }
        
        // Default return true (as default flow)
        return true;
    }

    /**
     * Safely convert variable value to double, supporting common numeric types and strings
     */
    private double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }
    
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
    
    private void validateDeploymentRequest(ProcessDefinitionRequest request) {
        if (!StringUtils.hasText(request.getName())) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("name", "Deployment name must not be empty", request.getName())));
        }
        
        if (!StringUtils.hasText(request.getKey())) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("key", "Process definition key must not be empty", request.getKey())));
        }
        
        if (!StringUtils.hasText(request.getBpmnXml())) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("bpmnXml", "BPMN content must not be empty", request.getBpmnXml())));
        }
    }
    
    private void validateBpmnFile(String bpmnContent) {
        // First perform basic content checks
        if (bpmnContent == null || bpmnContent.trim().isEmpty()) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("bpmnXml", "BPMN content must not be empty", bpmnContent)));
        }
        
        // Check for whitespace-only content
        if (bpmnContent.trim().matches("^\\s*$")) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("bpmnXml", "BPMN file format validation failed: content contains only whitespace", bpmnContent)));
        }
        
        // Check for valid XML format
        try {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setNamespaceAware(true);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            
            // Set error handler to capture XML parsing errors
            builder.setErrorHandler(new org.xml.sax.ErrorHandler() {
                @Override
                public void warning(org.xml.sax.SAXParseException exception) throws org.xml.sax.SAXException {
                    throw exception;
                }
                
                @Override
                public void error(org.xml.sax.SAXParseException exception) throws org.xml.sax.SAXException {
                    throw exception;
                }
                
                @Override
                public void fatalError(org.xml.sax.SAXParseException exception) throws org.xml.sax.SAXException {
                    throw exception;
                }
            });
            
            org.w3c.dom.Document document = builder.parse(new ByteArrayInputStream(bpmnContent.getBytes()));
            
            // Check if root element is BPMN definitions
            org.w3c.dom.Element rootElement = document.getDocumentElement();
            if (rootElement == null || !"definitions".equals(rootElement.getLocalName())) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError("bpmnXml", "BPMN file format validation failed: root element must be definitions", bpmnContent)));
            }
            
            // Check for BPMN namespace
            String namespaceURI = rootElement.getNamespaceURI();
            if (namespaceURI == null || (!namespaceURI.contains("BPMN") && !namespaceURI.contains("bpmn"))) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError("bpmnXml", "BPMN file format validation failed: missing BPMN namespace", bpmnContent)));
            }
            
            // Check for at least one process element
            org.w3c.dom.NodeList processNodes = document.getElementsByTagNameNS("*", "process");
            if (processNodes.getLength() == 0) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError("bpmnXml", "BPMN file format validation failed: must contain at least one process element", bpmnContent)));
            }
            
            // Check if process elements have id attribute
            for (int i = 0; i < processNodes.getLength(); i++) {
                org.w3c.dom.Element processElement = (org.w3c.dom.Element) processNodes.item(i);
                if (!processElement.hasAttribute("id") || processElement.getAttribute("id").trim().isEmpty()) {
                    throw new WorkflowValidationException(Collections.singletonList(
                        new WorkflowValidationException.ValidationError("bpmnXml", "BPMN file format validation failed: process element must have id attribute", bpmnContent)));
                }
            }
            
        } catch (WorkflowValidationException e) {
            // Re-throw validation exceptions as-is
            throw e;
        } catch (org.xml.sax.SAXParseException e) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("bpmnXml", "BPMN file format validation failed: XML parsing error - " + e.getMessage(), bpmnContent)));
        } catch (Exception e) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("bpmnXml", "BPMN file format validation failed: " + e.getMessage(), bpmnContent)));
        }
        
        // Finally use Flowable for deeper validation (only after basic validation passes)
        try {
            String normalizedBpmnContent = normalizeBpmnXml(bpmnContent);
            Deployment tempDeployment = repositoryService.createDeployment()
                .name("temp-validation")
                .addInputStream("temp.bpmn", new ByteArrayInputStream(normalizedBpmnContent.getBytes()))
                .deploy();
                
            // Validation succeeded, delete temp deployment
            repositoryService.deleteDeployment(tempDeployment.getId(), true);
            
        } catch (Exception e) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("bpmnXml", "BPMN file format validation failed: " + e.getMessage(), bpmnContent)));
        }
    }

    /**
     * Normalize BPMN XML to tolerate known legacy serialization mistakes.
     *
     * IMPORTANT: Keep this minimal and targeted. We only normalize casing of BPMN element names
     * that must match the BPMN 2.0 XSD exactly, otherwise Flowable deployment validation fails.
     */
    private String normalizeBpmnXml(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return bpmnXml;
        }

        // Legacy bug: Some exports used <*:MultiInstanceLoopCharacteristics> (capital M),
        // but BPMN 2.0 requires <*:multiInstanceLoopCharacteristics>.
        // Accept any prefix (bpmn:, bpmn2:, etc.) and also the no-prefix variant.
        String normalized = bpmnXml
                .replaceAll("(<\\s*[^\\s:>]+:)MultiInstanceLoopCharacteristics(\\b)", "$1multiInstanceLoopCharacteristics$2")
                .replaceAll("(<\\s*)MultiInstanceLoopCharacteristics(\\b)", "$1multiInstanceLoopCharacteristics$2")
                .replaceAll("(</\\s*[^\\s:>]+:)MultiInstanceLoopCharacteristics(\\s*>)", "$1multiInstanceLoopCharacteristics$2")
                .replaceAll("(</\\s*)MultiInstanceLoopCharacteristics(\\s*>)", "$1multiInstanceLoopCharacteristics$2");

        return normalized;
    }
    
    private void validateStartProcessRequest(StartProcessRequest request) {
        if (!StringUtils.hasText(request.getProcessDefinitionKey())) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("processDefinitionKey", "Process definition key must not be empty", request.getProcessDefinitionKey())));
        }
    }
    
    private ProcessDefinition getProcessDefinition(String processDefinitionKey) {
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey(processDefinitionKey)
            .latestVersion()
            .singleResult();
        
        if (processDefinition == null) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("processDefinitionKey", "Process definition does not exist", processDefinitionKey)));
        }
        
        if (processDefinition.isSuspended()) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("processDefinitionKey", "Process definition is suspended, cannot start new instance", processDefinitionKey)));
        }
        
        return processDefinition;
    }
    
    private ProcessDefinitionResult convertToProcessDefinitionResult(ProcessDefinition processDefinition, Deployment deployment) {
        String deploymentCategory = processDefinition.getCategory();
        String deploymentName = null;
        if (deployment != null) {
            deploymentCategory = deployment.getCategory();
            deploymentName = deployment.getName();
        }
        
        String finalName = (deploymentName != null && !deploymentName.equals("temp-validation")) 
            ? deploymentName 
            : processDefinition.getName();
        
        return ProcessDefinitionResult.builder()
            .id(processDefinition.getId())
            .key(processDefinition.getKey())
            .name(finalName)
            .version(processDefinition.getVersion())
            .category(deploymentCategory)
            .deploymentId(processDefinition.getDeploymentId())
            .resourceName(processDefinition.getResourceName())
            .diagramResourceName(processDefinition.getDiagramResourceName())
            .description(processDefinition.getDescription())
            .hasStartFormKey(processDefinition.hasStartFormKey())
            .hasGraphicalNotation(processDefinition.hasGraphicalNotation())
            .suspended(processDefinition.isSuspended())
            .tenantId(processDefinition.getTenantId())
            .build();
    }
    
    private static final List<String> KEY_PROCESS_VARIABLES = List.of(
        "processTitle", "initiator", "initiatorName", "formDataId", "businessKey",
        "functionUnitId", "functionUnitKey"
    );

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
    
    private void validateProcessInstanceControlRequest(ProcessInstanceControlRequest request) {
        if (!StringUtils.hasText(request.getProcessInstanceId())) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("processInstanceId", "Process instance ID must not be empty", request.getProcessInstanceId())));
        }

        if (!StringUtils.hasText(request.getAction())) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("action", "Operation type must not be empty", request.getAction())));
        }

        String action = request.getAction().toLowerCase();
        if (!List.of("suspend", "activate", "terminate").contains(action)) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("action", "Unsupported operation type", request.getAction())));
        }
    }

    /**
     * Load latest BPMN XML for a process definition key, or null if missing.
     * @param processDefinitionKey process definition key
     * @return BPMN XML string
     */
    public String getBpmnXml(String processDefinitionKey) {
        try {
            ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionKey(processDefinitionKey)
                    .latestVersion()
                    .singleResult();
            if (pd == null) {
                log.warn("Process definition not found: {}", processDefinitionKey);
                return null;
            }
            try (var in = repositoryService.getResourceAsStream(pd.getDeploymentId(), pd.getResourceName())) {
                return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("Failed to get BPMN XML for processDefinitionKey={}: {}", processDefinitionKey, e.getMessage());
            return null;
        }
    }
}