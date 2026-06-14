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

import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.Execution;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.ParallelGateway;
import org.flowable.bpmn.model.InclusiveGateway;
import org.flowable.bpmn.model.EventGateway;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.IntermediateCatchEvent;
import org.flowable.bpmn.model.BoundaryEvent;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.bpmn.model.CallActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Process Engine Component
 * Handles process definition management, process instance execution, BPMN parsing
 */
@Slf4j
@Component
@Transactional
public class ProcessEngineComponent {

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private MultiInstanceCanceller multiInstanceCanceller;

    @Autowired
    private NotificationDispatchHelper notificationDispatchHelper;

    @Autowired
    private I18nService i18nService;

    @Autowired
    private ProcessDeploymentManager processDeploymentManager;

    @Autowired
    private ProcessInstanceQueryManager processInstanceQueryManager;

    @Autowired
    private ProcessEventManager processEventManager;

    @Autowired
    private ProcessInstanceStatusReader processInstanceStatusReader;

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
        return processDeploymentManager.deployProcess(request);
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
        return processDeploymentManager.getProcessDefinitions(category, key);
    }

    /**
     * Delete process definition
     */
    public void deleteProcessDefinition(String deploymentId, boolean cascade) {
        processDeploymentManager.deleteProcessDefinition(deploymentId, cascade);
    }

    /**
     * Suspend process definition
     */
    public void suspendProcessDefinition(String processDefinitionId) {
        processDeploymentManager.suspendProcessDefinition(processDefinitionId);
    }

    /**
     * Activate process definition
     */
    public void activateProcessDefinition(String processDefinitionId) {
        processDeploymentManager.activateProcessDefinition(processDefinitionId);
    }

    /**
     * Query process instances
     */
    public ProcessInstanceQueryResult queryProcessInstances(ProcessInstanceQueryRequest request) {
        return processInstanceQueryManager.queryProcessInstances(request);
    }

    /**
     * Get process instance status
     * Used to check if process is completed and get last activity node
     */
    public Map<String, Object> getProcessInstanceStatus(String processInstanceId) {
        return processInstanceStatusReader.getProcessInstanceStatus(processInstanceId);
    }

    /**
     * Get current activity node of process instance
     */
    public Map<String, Object> getCurrentActivity(String processInstanceId) {
        return processInstanceStatusReader.getCurrentActivity(processInstanceId);
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
        processInstanceQueryManager.purgeProcessInstanceAndHistory(processInstanceId);
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
        return processEventManager.triggerEvent(processInstanceId, eventId, eventType, eventData);
    }

    /**
     * Get sub-process information
     * Supports nested execution queries for sub-processes and call activities
     */
    public List<SubProcessInfo> getSubProcesses(String processInstanceId) {
        return processEventManager.getSubProcesses(processInstanceId);
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
        return processDeploymentManager.getBpmnXml(processDefinitionKey);
    }
}
