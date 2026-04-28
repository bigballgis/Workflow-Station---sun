package com.workflow.controller;

import com.workflow.component.ProcessEngineComponent;
import com.workflow.dto.request.ProcessDefinitionRequest;
import com.workflow.dto.request.StartProcessRequest;
import com.workflow.dto.response.ApiResponse;
import com.workflow.dto.response.DeploymentResult;
import com.workflow.dto.response.ProcessDefinitionResult;
import com.workflow.dto.response.ProcessInstanceResult;
import com.workflow.exception.WorkflowBusinessException;
import com.workflow.exception.WorkflowValidationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程管理控制器
 * 
 * 提供流程定义部署、查询、删除和流程实例管理的RESTful API接口
 * 通过 ProcessEngineComponent 调用 Flowable 引擎
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/processes")
@RequiredArgsConstructor
@Tag(name = "Process Management", description = "Process definition and instance management API")
public class ProcessController {

    private final ProcessEngineComponent processEngineComponent;

    /**
     * 部署流程定义
     */
    @PostMapping("/definitions/deploy")
    @Operation(summary = "Deploy Process Definition", description = "Upload BPMN file and deploy process definition")
    public ResponseEntity<ApiResponse<DeploymentResult>> deployProcessDefinition(
            @RequestBody @Valid ProcessDefinitionRequest request) {
        
        log.info("Deploying process definition: key={}, name={}", request.getKey(), request.getName());
        DeploymentResult result = processEngineComponent.deployProcess(request);
        
        if (result.isSuccess()) {
            log.info("Process deployed successfully: deploymentId={}, processDefinitionId={}", 
                    result.getDeploymentId(), result.getProcessDefinitionId());
            return ResponseEntity.ok(ApiResponse.success(result));
        } else {
            log.warn("Process deployment failed: {}", result.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("DEPLOY_FAILED", result.getMessage()));
        }
    }

    /**
     * 查询流程定义列表
     */
    @GetMapping("/definitions")
    @Operation(summary = "List Process Definitions", description = "Query process definitions by criteria")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProcessDefinitions(
            @Parameter(description = "Process definition key")
            @RequestParam(value = "key", required = false) String key,
            @Parameter(description = "Process category")
            @RequestParam(value = "category", required = false) String category) {
        
        log.info("Querying process definitions: key={}, category={}", key, category);
        List<ProcessDefinitionResult> definitions = processEngineComponent.getProcessDefinitions(category, key);
        
        Map<String, Object> result = new HashMap<>();
        result.put("processDefinitions", definitions);
        result.put("total", definitions.size());
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 启动流程实例
     */
    @PostMapping("/instances")
    @Operation(summary = "Start Process Instance", description = "Start a new process instance by process definition")
    public ResponseEntity<ApiResponse<ProcessInstanceResult>> startProcessInstance(
            @RequestBody @Valid StartProcessRequest request) {
        
        log.info("Starting process instance: processDefinitionKey={}, businessKey={}, startUserId={}", 
                request.getProcessDefinitionKey(), request.getBusinessKey(), request.getStartUserId());
        
        ProcessInstanceResult result = processEngineComponent.startProcess(request);
        
        if (result.isSuccess()) {
            log.info("Process instance started: processInstanceId={}", result.getProcessInstanceId());
            return ResponseEntity.ok(ApiResponse.success(result));
        } else {
            log.warn("Failed to start process instance: {}", result.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("START_FAILED", result.getMessage()));
        }
    }

    /**
     * 获取流程实例详情
     */
    @GetMapping("/instances/{processInstanceId}")
    @Operation(summary = "Get Process Instance Details", description = "Get process instance details by ID")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProcessInstance(
            @Parameter(description = "Process instance ID", required = true)
            @PathVariable String processInstanceId) {
        
        log.info("Getting process instance: {}", processInstanceId);
        // 使用查询方法获取流程实例详情
        var queryRequest = new com.workflow.dto.request.ProcessInstanceQueryRequest();
        queryRequest.setProcessInstanceId(processInstanceId);
        queryRequest.setPage(0);
        queryRequest.setSize(1);
        
        var queryResult = processEngineComponent.queryProcessInstances(queryRequest);
        
        if (queryResult.getProcessInstances() != null && !queryResult.getProcessInstances().isEmpty()) {
            var instance = queryResult.getProcessInstances().get(0);
            Map<String, Object> result = new HashMap<>();
            result.put("processInstanceId", instance.getProcessInstanceId());
            result.put("processDefinitionId", instance.getProcessDefinitionId());
            result.put("processDefinitionKey", instance.getProcessDefinitionKey());
            result.put("businessKey", instance.getBusinessKey());
            result.put("status", instance.getState());
            result.put("startTime", instance.getStartTime());
            result.put("startUserId", instance.getStartUserId());
            return ResponseEntity.ok(ApiResponse.success(result));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 取消（终止）流程实例
     */
    @DeleteMapping("/instances/{processInstanceId}")
    @Operation(summary = "Cancel Process Instance", description = "Terminate a running process instance")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelProcessInstance(
            @Parameter(description = "Process instance ID", required = true)
            @PathVariable String processInstanceId,
            @RequestBody(required = false) Map<String, Object> body) {
        
        String reason = body != null && body.get("reason") != null 
                ? (String) body.get("reason") : "User cancelled";
        log.info("Cancelling process instance: {}, reason: {}", processInstanceId, reason);
        
        var request = new com.workflow.dto.request.ProcessInstanceControlRequest();
        request.setProcessInstanceId(processInstanceId);
        request.setAction("terminate");
        request.setReason(reason);
        
        var result = processEngineComponent.controlProcessInstance(request);
        
        if (result.isSuccess()) {
            Map<String, Object> data = Map.of(
                    "processInstanceId", processInstanceId, 
                    "cancelled", true);
            return ResponseEntity.ok(ApiResponse.success(data));
        } else {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("CANCEL_FAILED", result.getMessage()));
        }
    }

    /**
     * purge 运行中与历史流程实例（功能单元版本回滚等场景，由 user-portal / 管理端编排调用）
     */
    @PostMapping("/instances/{processInstanceId}/purge")
    @Operation(summary = "Purge Process Instance", description = "Delete running instance (if exists) and remove history records")
    public ResponseEntity<ApiResponse<Map<String, Object>>> purgeProcessInstance(
            @Parameter(description = "Process instance ID", required = true)
            @PathVariable String processInstanceId) {
        log.info("Purging process instance (runtime+history): {}", processInstanceId);
        processEngineComponent.purgeProcessInstanceAndHistory(processInstanceId);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "processInstanceId", processInstanceId,
                "purged", true)));
    }

    /**
     * 删除流程定义
     */
    @DeleteMapping("/definitions/deployments/{deploymentId}")
    @Operation(summary = "Delete Process Definition", description = "Delete process definition by deployment ID")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteProcessDefinition(
            @Parameter(description = "Deployment ID", required = true)
            @PathVariable String deploymentId,
            @Parameter(description = "Cascade delete")
            @RequestParam(value = "cascade", defaultValue = "false") boolean cascade) {
        
        log.info("Deleting process definition: deploymentId={}, cascade={}", deploymentId, cascade);
        processEngineComponent.deleteProcessDefinition(deploymentId, cascade);
        
        Map<String, Object> result = Map.of("deploymentId", deploymentId, "deleted", true);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    /**
     * 暂停流程定义
     */
    @PostMapping("/definitions/{processDefinitionId}/suspend")
    @Operation(summary = "Suspend Process Definition", description = "Suspend the specified process definition")
    public ResponseEntity<ApiResponse<Map<String, Object>>> suspendProcessDefinition(
            @Parameter(description = "Process definition ID", required = true)
            @PathVariable String processDefinitionId) {
        
        log.info("Suspending process definition: {}", processDefinitionId);
        processEngineComponent.suspendProcessDefinition(processDefinitionId);
        
        Map<String, Object> result = Map.of("processDefinitionId", processDefinitionId, "suspended", true);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    /**
     * 激活流程定义
     */
    @PostMapping("/definitions/{processDefinitionId}/activate")
    @Operation(summary = "Activate Process Definition", description = "Activate the specified process definition")
    public ResponseEntity<ApiResponse<Map<String, Object>>> activateProcessDefinition(
            @Parameter(description = "Process definition ID", required = true)
            @PathVariable String processDefinitionId) {
        
        log.info("Activating process definition: {}", processDefinitionId);
        processEngineComponent.activateProcessDefinition(processDefinitionId);
        
        Map<String, Object> result = Map.of("processDefinitionId", processDefinitionId, "activated", true);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    /**
     * 根据流程定义 key 获取 BPMN XML
     */
    @GetMapping("/definitions/{processDefinitionKey}/bpmn")
    @Operation(summary = "Get BPMN XML", description = "Get BPMN XML content by process definition key")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBpmnXml(
            @Parameter(description = "Process definition key")
            @PathVariable String processDefinitionKey) {
        String bpmnXml = processEngineComponent.getBpmnXml(processDefinitionKey);
        if (bpmnXml == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", "Process definition not found: " + processDefinitionKey));
        }
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "processDefinitionKey", processDefinitionKey,
                "bpmnXml", bpmnXml)));
    }

    /**
     * 获取流程实例状态
     * 用于检查流程是否已完成以及获取最后一个活动节点
     */
    @GetMapping("/{processInstanceId}/status")
    @Operation(summary = "Get Process Instance Status", description = "Get current status of a process instance, including completion status and last active node")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProcessInstanceStatus(
            @Parameter(description = "Process instance ID", required = true)
            @PathVariable String processInstanceId) {
        
        log.info("Getting process instance status: {}", processInstanceId);
        Map<String, Object> status = processEngineComponent.getProcessInstanceStatus(processInstanceId);
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    // ==================== 异常处理 ====================

    @ExceptionHandler(WorkflowValidationException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationException(
            WorkflowValidationException ex, WebRequest request) {
        log.warn("Workflow validation error: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(
                ApiResponse.error("VALIDATION_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(WorkflowBusinessException.class)
    public ResponseEntity<ApiResponse<?>> handleBusinessException(
            WorkflowBusinessException ex, WebRequest request) {
        log.warn("Workflow business error [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.badRequest().body(
                ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }
}