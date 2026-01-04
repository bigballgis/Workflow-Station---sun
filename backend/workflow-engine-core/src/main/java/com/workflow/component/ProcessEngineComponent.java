package com.workflow.component;

import com.workflow.aspect.AuditAspect.Auditable;
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

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ActivityInstance;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流程引擎组件
 * 负责流程定义管理、流程实例执行、BPMN解析
 */
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
    
    /**
     * 部署流程定义
     * 支持BPMN 2.0文件验证和版本管理
     */
    @Auditable(
        operationType = AuditOperationType.DEPLOY_PROCESS,
        resourceType = AuditResourceType.PROCESS_DEFINITION,
        description = "部署流程定义",
        captureArgs = true,
        captureResult = true
    )
    public DeploymentResult deployProcess(ProcessDefinitionRequest request) {
        try {
            // 验证请求参数
            validateDeploymentRequest(request);
            
            // 验证BPMN文件格式
            validateBpmnFile(request.getBpmnXml());
            
            // 创建部署
            Deployment deployment = repositoryService.createDeployment()
                .name(request.getName())
                .category(request.getCategory())
                .key(request.getKey())
                .addString(request.getKey() + ".bpmn", request.getBpmnXml())
                .deploy();
            
            // 获取部署的流程定义
            List<ProcessDefinition> processDefinitions = repositoryService
                .createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .list();
            
            if (!processDefinitions.isEmpty()) {
                ProcessDefinition processDefinition = processDefinitions.get(0);
                return DeploymentResult.success(
                    deployment.getId(),
                    processDefinition.getId(),
                    processDefinition.getKey(),
                    processDefinition.getName(),
                    processDefinition.getVersion()
                );
            } else {
                return DeploymentResult.failure("部署成功但未找到流程定义");
            }
                
        } catch (WorkflowValidationException e) {
            // Re-throw validation exceptions so they can be caught by tests
            throw e;
        } catch (Exception e) {
            return DeploymentResult.failure("流程定义部署失败: " + e.getMessage());
        }
    }
    
    /**
     * 启动流程实例
     */
    @Auditable(
        operationType = AuditOperationType.START_PROCESS,
        resourceType = AuditResourceType.PROCESS_INSTANCE,
        description = "启动流程实例",
        captureArgs = true,
        captureResult = true
    )
    public ProcessInstanceResult startProcess(StartProcessRequest request) {
        try {
            // 验证请求参数
            validateStartProcessRequest(request);
            
            // 验证流程定义是否存在
            ProcessDefinition processDefinition = getProcessDefinition(request.getProcessDefinitionKey());
            
            // 启动流程实例，设置启动用户
            ProcessInstance processInstance;
            if (StringUtils.hasText(request.getStartUserId())) {
                // 设置启动用户ID
                org.flowable.common.engine.impl.identity.Authentication.setAuthenticatedUserId(request.getStartUserId());
                try {
                    processInstance = runtimeService.startProcessInstanceByKey(
                        request.getProcessDefinitionKey(), 
                        request.getBusinessKey(), 
                        request.getVariables());
                } finally {
                    // 清除认证用户ID
                    org.flowable.common.engine.impl.identity.Authentication.setAuthenticatedUserId(null);
                }
            } else {
                processInstance = runtimeService.startProcessInstanceByKey(
                    request.getProcessDefinitionKey(), 
                    request.getBusinessKey(), 
                    request.getVariables());
            }
            
            return ProcessInstanceResult.builder()
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
                .message("流程实例启动成功")
                .build();
                
        } catch (Exception e) {
            throw new WorkflowBusinessException("PROCESS_START_ERROR", "流程实例启动失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 查询流程定义列表
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
            
            // 如果指定了类别，需要手动过滤，因为Flowable没有直接的部署类别查询方法
            if (StringUtils.hasText(category)) {
                processDefinitions = processDefinitions.stream()
                    .filter(pd -> {
                        try {
                            var deployment = repositoryService.createDeploymentQuery()
                                .deploymentId(pd.getDeploymentId())
                                .singleResult();
                            return deployment != null && category.equals(deployment.getCategory());
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());
            }
            
            return processDefinitions.stream()
                .map(this::convertToProcessDefinitionResult)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            throw new WorkflowBusinessException("PROCESS_QUERY_ERROR", "查询流程定义失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 删除流程定义
     */
    public void deleteProcessDefinition(String deploymentId, boolean cascade) {
        try {
            // 检查是否有运行中的流程实例
            if (!cascade) {
                long runningInstances = runtimeService.createProcessInstanceQuery()
                    .deploymentId(deploymentId)
                    .count();
                
                if (runningInstances > 0) {
                    throw new WorkflowValidationException(Collections.singletonList(
                        new WorkflowValidationException.ValidationError(
                            "deploymentId", 
                            "无法删除流程定义，存在 " + runningInstances + " 个运行中的流程实例", 
                            deploymentId)));
                }
            }
            
            repositoryService.deleteDeployment(deploymentId, cascade);
            
        } catch (WorkflowValidationException e) {
            // Re-throw validation exceptions as-is
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("PROCESS_DELETE_ERROR", "删除流程定义失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 暂停流程定义
     */
    public void suspendProcessDefinition(String processDefinitionId) {
        try {
            repositoryService.suspendProcessDefinitionById(processDefinitionId);
        } catch (Exception e) {
            throw new WorkflowBusinessException("PROCESS_SUSPEND_ERROR", "暂停流程定义失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 激活流程定义
     */
    public void activateProcessDefinition(String processDefinitionId) {
        try {
            repositoryService.activateProcessDefinitionById(processDefinitionId);
        } catch (Exception e) {
            throw new WorkflowBusinessException("PROCESS_ACTIVATE_ERROR", "激活流程定义失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 查询流程实例
     */
    public ProcessInstanceQueryResult queryProcessInstances(ProcessInstanceQueryRequest request) {
        try {
            List<ProcessInstanceQueryResult.ProcessInstanceInfo> allInstances = new ArrayList<>();
            long totalCount = 0;
            
            // 如果没有指定状态或者包含active/suspended状态，查询运行时表
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
            
            // 如果没有指定状态或者包含completed状态，查询历史表
            if (request.getState() == null || "completed".equalsIgnoreCase(request.getState())) {
                var historyQuery = processEngine.getHistoryService().createHistoricProcessInstanceQuery();
                applyHistoryQueryConditions(historyQuery, request);
                
                // 如果查询运行时表时已经有结果，需要调整历史查询的分页参数
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
            throw new WorkflowBusinessException("PROCESS_QUERY_ERROR", "查询流程实例失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 控制流程实例（暂停、恢复、终止）
     */
    public ProcessInstanceControlResult controlProcessInstance(ProcessInstanceControlRequest request) {
        try {
            // 验证请求参数
            validateProcessInstanceControlRequest(request);
            
            // 验证流程实例是否存在
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(request.getProcessInstanceId())
                    .singleResult();
            
            if (processInstance == null) {
                return ProcessInstanceControlResult.failure(
                    request.getProcessInstanceId(), 
                    request.getAction(), 
                    request.getUserId(),
                    "流程实例不存在");
            }
            
            // 执行相应操作
            switch (request.getAction().toLowerCase()) {
                case "suspend" -> {
                    if (processInstance.isSuspended()) {
                        return ProcessInstanceControlResult.failure(
                            request.getProcessInstanceId(), 
                            request.getAction(), 
                            request.getUserId(),
                            "流程实例已经处于暂停状态");
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
                            "流程实例已经处于活动状态");
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
                            "流程实例已经结束");
                    }
                    runtimeService.deleteProcessInstance(
                        request.getProcessInstanceId(), 
                        request.getReason() != null ? request.getReason() : "手动终止");
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
                        "不支持的操作类型: " + request.getAction());
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
    
    // ==================== BPMN网关和事件处理功能 ====================
    
    /**
     * 获取流程实例的当前活动节点信息
     * 用于网关和事件处理的状态查询
     */
    public List<ActivityInfo> getCurrentActivities(String processInstanceId) {
        try {
            // 验证流程实例是否存在
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            
            if (processInstance == null) {
                throw new WorkflowBusinessException("PROCESS_NOT_FOUND", "流程实例不存在: " + processInstanceId);
            }
            
            // 获取当前活动的执行实例
            List<Execution> executions = runtimeService.createExecutionQuery()
                    .processInstanceId(processInstanceId)
                    .list();
            
            List<ActivityInfo> activities = new ArrayList<>();
            
            for (Execution execution : executions) {
                if (execution.getActivityId() != null) {
                    // 获取BPMN模型以获取活动详细信息
                    BpmnModel bpmnModel = repositoryService.getBpmnModel(processInstance.getProcessDefinitionId());
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
            throw new WorkflowBusinessException("ACTIVITY_QUERY_ERROR", "查询当前活动节点失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 评估排他网关的条件表达式
     * 根据流程变量和条件表达式选择执行路径
     */
    public GatewayEvaluationResult evaluateExclusiveGateway(String processInstanceId, String gatewayId) {
        try {
            // 获取流程实例和BPMN模型
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            
            if (processInstance == null) {
                throw new WorkflowBusinessException("PROCESS_NOT_FOUND", "流程实例不存在: " + processInstanceId);
            }
            
            BpmnModel bpmnModel = repositoryService.getBpmnModel(processInstance.getProcessDefinitionId());
            FlowElement flowElement = bpmnModel.getFlowElement(gatewayId);
            
            if (!(flowElement instanceof ExclusiveGateway)) {
                throw new WorkflowBusinessException("INVALID_GATEWAY", "指定的元素不是排他网关: " + gatewayId);
            }
            
            ExclusiveGateway exclusiveGateway = (ExclusiveGateway) flowElement;
            
            // 获取流程变量
            Map<String, Object> variables = runtimeService.getVariables(processInstanceId);
            
            // 评估所有出口条件
            List<SequenceFlow> outgoingFlows = exclusiveGateway.getOutgoingFlows();
            List<GatewayEvaluationResult.FlowEvaluation> flowEvaluations = new ArrayList<>();
            
            String selectedFlowId = null;
            String selectedFlowName = null;
            
            for (SequenceFlow sequenceFlow : outgoingFlows) {
                boolean conditionResult = false;
                String conditionExpression = sequenceFlow.getConditionExpression();
                String evaluationMessage = "无条件表达式";
                
                if (StringUtils.hasText(conditionExpression)) {
                    try {
                        // 简化的条件评估 - 在实际项目中应该使用Flowable的表达式引擎
                        // 这里提供基本的条件评估逻辑
                        conditionResult = evaluateSimpleCondition(conditionExpression, variables);
                        evaluationMessage = "条件表达式: " + conditionExpression + ", 结果: " + conditionResult;
                        
                    } catch (Exception e) {
                        evaluationMessage = "条件表达式评估失败: " + e.getMessage();
                    }
                } else {
                    // 默认流（没有条件表达式的流）
                    conditionResult = (selectedFlowId == null); // 如果没有其他流被选中，则选择默认流
                    evaluationMessage = "默认流";
                }
                
                flowEvaluations.add(GatewayEvaluationResult.FlowEvaluation.builder()
                        .flowId(sequenceFlow.getId())
                        .flowName(sequenceFlow.getName())
                        .conditionExpression(conditionExpression)
                        .conditionResult(conditionResult)
                        .evaluationMessage(evaluationMessage)
                        .build());
                
                // 排他网关只选择第一个条件为真的流
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
            throw new WorkflowBusinessException("GATEWAY_EVALUATION_ERROR", "排他网关条件评估失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 处理并行网关的分支创建和合并
     * 支持并行执行路径的管理
     */
    public ParallelGatewayResult handleParallelGateway(String processInstanceId, String gatewayId) {
        try {
            // 获取流程实例和BPMN模型
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            
            if (processInstance == null) {
                throw new WorkflowBusinessException("PROCESS_NOT_FOUND", "流程实例不存在: " + processInstanceId);
            }
            
            BpmnModel bpmnModel = repositoryService.getBpmnModel(processInstance.getProcessDefinitionId());
            FlowElement flowElement = bpmnModel.getFlowElement(gatewayId);
            
            if (!(flowElement instanceof ParallelGateway)) {
                throw new WorkflowBusinessException("INVALID_GATEWAY", "指定的元素不是并行网关: " + gatewayId);
            }
            
            ParallelGateway parallelGateway = (ParallelGateway) flowElement;
            
            // 获取当前在该网关的执行实例
            List<Execution> gatewayExecutions = runtimeService.createExecutionQuery()
                    .processInstanceId(processInstanceId)
                    .activityId(gatewayId)
                    .list();
            
            List<ParallelGatewayResult.BranchInfo> branches = new ArrayList<>();
            
            // 检查是否为分支网关（有多个出口）或合并网关（有多个入口）
            boolean isForkGateway = parallelGateway.getOutgoingFlows().size() > 1;
            boolean isJoinGateway = parallelGateway.getIncomingFlows().size() > 1;
            
            if (isForkGateway) {
                // 分支网关：创建多个并行分支
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
                // 合并网关：等待所有分支完成
                for (SequenceFlow incomingFlow : parallelGateway.getIncomingFlows()) {
                    // 检查该分支是否已到达合并点
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
            
            // 计算网关状态
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
            throw new WorkflowBusinessException("PARALLEL_GATEWAY_ERROR", "并行网关处理失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 触发流程事件
     * 支持消息事件、信号事件等的触发和传播
     */
    public EventTriggerResult triggerEvent(String processInstanceId, String eventId, String eventType, Map<String, Object> eventData) {
        try {
            // 验证流程实例是否存在
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            
            if (processInstance == null) {
                throw new WorkflowBusinessException("PROCESS_NOT_FOUND", "流程实例不存在: " + processInstanceId);
            }
            
            List<String> triggeredExecutions = new ArrayList<>();
            String resultMessage = "";
            
            switch (eventType.toLowerCase()) {
                case "message" -> {
                    // 触发消息事件
                    if (eventData != null && eventData.containsKey("messageName")) {
                        String messageName = (String) eventData.get("messageName");
                        
                        // 查找等待该消息的执行实例
                        List<Execution> messageExecutions = runtimeService.createExecutionQuery()
                                .processInstanceId(processInstanceId)
                                .messageEventSubscriptionName(messageName)
                                .list();
                        
                        for (Execution execution : messageExecutions) {
                            runtimeService.messageEventReceived(messageName, execution.getId(), eventData);
                            triggeredExecutions.add(execution.getId());
                        }
                        
                        resultMessage = "触发消息事件: " + messageName + ", 影响执行实例: " + triggeredExecutions.size();
                    } else {
                        throw new WorkflowBusinessException("INVALID_EVENT_DATA", "消息事件需要提供messageName参数");
                    }
                }
                case "signal" -> {
                    // 触发信号事件
                    if (eventData != null && eventData.containsKey("signalName")) {
                        String signalName = (String) eventData.get("signalName");
                        
                        // 查找等待该信号的执行实例
                        List<Execution> signalExecutions = runtimeService.createExecutionQuery()
                                .processInstanceId(processInstanceId)
                                .signalEventSubscriptionName(signalName)
                                .list();
                        
                        for (Execution execution : signalExecutions) {
                            runtimeService.signalEventReceived(signalName, execution.getId(), eventData);
                            triggeredExecutions.add(execution.getId());
                        }
                        
                        resultMessage = "触发信号事件: " + signalName + ", 影响执行实例: " + triggeredExecutions.size();
                    } else {
                        throw new WorkflowBusinessException("INVALID_EVENT_DATA", "信号事件需要提供signalName参数");
                    }
                }
                case "timer" -> {
                    // 触发定时器事件（通常由系统自动触发，这里提供手动触发能力）
                    List<Execution> timerExecutions = runtimeService.createExecutionQuery()
                            .processInstanceId(processInstanceId)
                            .activityId(eventId)
                            .list();
                    
                    for (Execution execution : timerExecutions) {
                        // 手动推进定时器事件
                        runtimeService.trigger(execution.getId(), eventData);
                        triggeredExecutions.add(execution.getId());
                    }
                    
                    resultMessage = "触发定时器事件: " + eventId + ", 影响执行实例: " + triggeredExecutions.size();
                }
                default -> {
                    throw new WorkflowBusinessException("UNSUPPORTED_EVENT_TYPE", "不支持的事件类型: " + eventType);
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
                    .message("事件触发失败: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * 获取子流程信息
     * 支持子流程和调用活动的嵌套执行查询
     */
    public List<SubProcessInfo> getSubProcesses(String processInstanceId) {
        try {
            // 验证流程实例是否存在
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            
            if (processInstance == null) {
                throw new WorkflowBusinessException("PROCESS_NOT_FOUND", "流程实例不存在: " + processInstanceId);
            }
            
            List<SubProcessInfo> subProcesses = new ArrayList<>();
            
            // 查找子流程实例
            List<ProcessInstance> childProcessInstances = runtimeService.createProcessInstanceQuery()
                    .superProcessInstanceId(processInstanceId)
                    .list();
            
            for (ProcessInstance childProcess : childProcessInstances) {
                // 获取调用活动信息
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
            
            // 查找嵌入式子流程（在同一流程实例内的子流程）
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
                                .callActivityId(null) // 嵌入式子流程没有调用活动
                                .businessKey(null)
                                .startTime(LocalDateTime.now()) // 嵌入式子流程的开始时间难以精确获取
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
            throw new WorkflowBusinessException("SUBPROCESS_QUERY_ERROR", "查询子流程失败: " + e.getMessage(), e);
        }
    }
    
    // 私有辅助方法
    
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
        // 用户任务、接收任务、消息事件等是等待状态
        return flowElement instanceof org.flowable.bpmn.model.UserTask ||
               flowElement instanceof org.flowable.bpmn.model.ReceiveTask ||
               flowElement instanceof IntermediateCatchEvent ||
               flowElement instanceof BoundaryEvent;
    }
    
    private boolean evaluateSimpleCondition(String conditionExpression, Map<String, Object> variables) {
        // 简化的条件评估逻辑
        // 在实际项目中应该使用Flowable的表达式引擎
        
        // 移除可能的表达式语法标记
        String expression = conditionExpression.trim();
        if (expression.startsWith("${") && expression.endsWith("}")) {
            expression = expression.substring(2, expression.length() - 1).trim();
        }
        
        // 处理简单的比较表达式
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
        
        // 处理布尔变量
        Object varValue = variables.get(expression);
        if (varValue instanceof Boolean) {
            return (Boolean) varValue;
        }
        
        // 默认返回true（作为默认流）
        return true;
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
        
        // 处理状态过滤
        if (StringUtils.hasText(request.getState())) {
            switch (request.getState().toLowerCase()) {
                case "active" -> query.active();
                case "suspended" -> query.suspended();
            }
        }
        
        // 处理变量过滤
        if (request.getVariables() != null && !request.getVariables().isEmpty()) {
            for (Map.Entry<String, Object> entry : request.getVariables().entrySet()) {
                query.variableValueEquals(entry.getKey(), entry.getValue());
            }
        }
        
        // 排序
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
        
        // 历史查询只查询已完成的实例
        if (request.getState() == null || "completed".equalsIgnoreCase(request.getState())) {
            query.finished();
        }
        
        // 处理变量过滤
        if (request.getVariables() != null && !request.getVariables().isEmpty()) {
            for (Map.Entry<String, Object> entry : request.getVariables().entrySet()) {
                query.variableValueEquals(entry.getKey(), entry.getValue());
            }
        }
        
        // 排序
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
        // 获取流程定义信息
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(historicProcessInstance.getProcessDefinitionId())
                .singleResult();
        
        // 获取历史变量
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
                new WorkflowValidationException.ValidationError("name", "部署名称不能为空", request.getName())));
        }
        
        if (!StringUtils.hasText(request.getKey())) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("key", "流程定义键不能为空", request.getKey())));
        }
        
        if (!StringUtils.hasText(request.getBpmnXml())) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("bpmnXml", "BPMN内容不能为空", request.getBpmnXml())));
        }
    }
    
    private void validateBpmnFile(String bpmnContent) {
        // 首先进行基本的内容检查
        if (bpmnContent == null || bpmnContent.trim().isEmpty()) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("bpmnXml", "BPMN内容不能为空", bpmnContent)));
        }
        
        // 检查是否为纯空白字符
        if (bpmnContent.trim().matches("^\\s*$")) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("bpmnXml", "BPMN文件格式验证失败: 内容只包含空白字符", bpmnContent)));
        }
        
        // 检查是否为有效的XML格式
        try {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            
            // 设置错误处理器来捕获XML解析错误
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
            
            // 检查根元素是否为BPMN definitions
            org.w3c.dom.Element rootElement = document.getDocumentElement();
            if (rootElement == null || !"definitions".equals(rootElement.getLocalName())) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError("bpmnXml", "BPMN文件格式验证失败: 根元素必须是definitions", bpmnContent)));
            }
            
            // 检查是否包含BPMN命名空间
            String namespaceURI = rootElement.getNamespaceURI();
            if (namespaceURI == null || (!namespaceURI.contains("BPMN") && !namespaceURI.contains("bpmn"))) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError("bpmnXml", "BPMN文件格式验证失败: 缺少BPMN命名空间", bpmnContent)));
            }
            
            // 检查是否包含至少一个process元素
            org.w3c.dom.NodeList processNodes = document.getElementsByTagNameNS("*", "process");
            if (processNodes.getLength() == 0) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError("bpmnXml", "BPMN文件格式验证失败: 必须包含至少一个process元素", bpmnContent)));
            }
            
            // 检查process元素是否有id属性
            for (int i = 0; i < processNodes.getLength(); i++) {
                org.w3c.dom.Element processElement = (org.w3c.dom.Element) processNodes.item(i);
                if (!processElement.hasAttribute("id") || processElement.getAttribute("id").trim().isEmpty()) {
                    throw new WorkflowValidationException(Collections.singletonList(
                        new WorkflowValidationException.ValidationError("bpmnXml", "BPMN文件格式验证失败: process元素必须有id属性", bpmnContent)));
                }
            }
            
        } catch (WorkflowValidationException e) {
            // Re-throw validation exceptions as-is
            throw e;
        } catch (org.xml.sax.SAXParseException e) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("bpmnXml", "BPMN文件格式验证失败: XML解析错误 - " + e.getMessage(), bpmnContent)));
        } catch (Exception e) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("bpmnXml", "BPMN文件格式验证失败: " + e.getMessage(), bpmnContent)));
        }
        
        // 最后使用Flowable进行更深层的验证（但只在基本验证通过后）
        try {
            Deployment tempDeployment = repositoryService.createDeployment()
                .name("temp-validation")
                .addInputStream("temp.bpmn", new ByteArrayInputStream(bpmnContent.getBytes()))
                .deploy();
                
            // 验证成功，删除临时部署
            repositoryService.deleteDeployment(tempDeployment.getId(), true);
            
        } catch (Exception e) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("bpmnXml", "BPMN文件格式验证失败: " + e.getMessage(), bpmnContent)));
        }
    }
    
    private void validateStartProcessRequest(StartProcessRequest request) {
        if (!StringUtils.hasText(request.getProcessDefinitionKey())) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("processDefinitionKey", "流程定义Key不能为空", request.getProcessDefinitionKey())));
        }
    }
    
    private ProcessDefinition getProcessDefinition(String processDefinitionKey) {
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey(processDefinitionKey)
            .latestVersion()
            .singleResult();
        
        if (processDefinition == null) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("processDefinitionKey", "流程定义不存在", processDefinitionKey)));
        }
        
        if (processDefinition.isSuspended()) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("processDefinitionKey", "流程定义已暂停，无法启动新实例", processDefinitionKey)));
        }
        
        return processDefinition;
    }
    
    private ProcessDefinitionResult convertToProcessDefinitionResult(ProcessDefinition processDefinition) {
        // 获取部署信息以获取部署时指定的类别和名称
        String deploymentCategory = null;
        String deploymentName = null;
        try {
            var deployment = repositoryService.createDeploymentQuery()
                .deploymentId(processDefinition.getDeploymentId())
                .singleResult();
            if (deployment != null) {
                deploymentCategory = deployment.getCategory();
                deploymentName = deployment.getName();
            }
        } catch (Exception e) {
            // 如果获取部署信息失败，使用流程定义的信息
            deploymentCategory = processDefinition.getCategory();
        }
        
        // 优先使用部署名称，如果没有则使用流程定义名称
        String finalName = (deploymentName != null && !deploymentName.equals("temp-validation")) 
            ? deploymentName 
            : processDefinition.getName();
        
        return ProcessDefinitionResult.builder()
            .id(processDefinition.getId())
            .key(processDefinition.getKey())
            .name(finalName) // 使用部署名称或流程定义名称
            .version(processDefinition.getVersion())
            .category(deploymentCategory) // 使用部署类别而不是BPMN命名空间
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
    
    private ProcessInstanceQueryResult.ProcessInstanceInfo convertToProcessInstanceInfo(ProcessInstance processInstance) {
        // 获取流程定义信息
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processInstance.getProcessDefinitionId())
                .singleResult();
        
        // 获取活动任务数
        long activeTaskCount = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .count();
        
        // 获取流程变量
        Map<String, Object> variables = runtimeService.getVariables(processInstance.getId());
        
        return ProcessInstanceQueryResult.ProcessInstanceInfo.builder()
                .processInstanceId(processInstance.getId())
                .processDefinitionId(processInstance.getProcessDefinitionId())
                .processDefinitionKey(processInstance.getProcessDefinitionKey())
                .processDefinitionName(processDefinition != null ? processDefinition.getName() : null)
                .businessKey(processInstance.getBusinessKey())
                .name(processInstance.getName())
                .startTime(processInstance.getStartTime() != null ? 
                    LocalDateTime.ofInstant(processInstance.getStartTime().toInstant(), ZoneId.systemDefault()) : null)
                .endTime(null) // 运行中的流程实例没有结束时间
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
                new WorkflowValidationException.ValidationError("processInstanceId", "流程实例ID不能为空", request.getProcessInstanceId())));
        }
        
        if (!StringUtils.hasText(request.getAction())) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("action", "操作类型不能为空", request.getAction())));
        }
        
        String action = request.getAction().toLowerCase();
        if (!List.of("suspend", "activate", "terminate").contains(action)) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("action", "不支持的操作类型", request.getAction())));
        }
    }
}