package com.workflow.controller;

import com.workflow.component.ProcessEngineComponent;
import com.workflow.dto.request.ProcessDefinitionRequest;
import com.workflow.dto.request.StartProcessRequest;
import com.workflow.dto.response.ApiResponse;
import com.workflow.dto.response.DeploymentResult;
import com.workflow.dto.response.ProcessDefinitionResult;
import com.workflow.dto.response.ProcessInstanceResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
@Tag(name = "流程管理", description = "流程定义和流程实例管理API")
public class ProcessController {

    private final ProcessEngineComponent processEngineComponent;

    /**
     * 部署流程定义
     */
    @PostMapping("/definitions/deploy")
    @Operation(summary = "部署流程定义", description = "上传BPMN文件并部署流程定义")
    public ResponseEntity<ApiResponse<DeploymentResult>> deployProcessDefinition(
            @RequestBody ProcessDefinitionRequest request) {
        
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
    @Operation(summary = "查询流程定义列表", description = "根据条件查询流程定义列表")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProcessDefinitions(
            @Parameter(description = "流程定义键")
            @RequestParam(value = "key", required = false) String key,
            @Parameter(description = "流程分类")
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
    @Operation(summary = "启动流程实例", description = "根据流程定义启动新的流程实例")
    public ResponseEntity<ApiResponse<ProcessInstanceResult>> startProcessInstance(
            @RequestBody StartProcessRequest request) {
        
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
    @Operation(summary = "获取流程实例详情", description = "根据ID获取流程实例详情")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProcessInstance(
            @Parameter(description = "流程实例ID", required = true)
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
     * 删除流程定义
     */
    @DeleteMapping("/definitions/deployments/{deploymentId}")
    @Operation(summary = "删除流程定义", description = "根据部署ID删除流程定义")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteProcessDefinition(
            @Parameter(description = "部署ID", required = true)
            @PathVariable String deploymentId,
            @Parameter(description = "是否级联删除")
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
    @Operation(summary = "暂停流程定义", description = "暂停指定的流程定义")
    public ResponseEntity<ApiResponse<Map<String, Object>>> suspendProcessDefinition(
            @Parameter(description = "流程定义ID", required = true)
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
    @Operation(summary = "激活流程定义", description = "激活指定的流程定义")
    public ResponseEntity<ApiResponse<Map<String, Object>>> activateProcessDefinition(
            @Parameter(description = "流程定义ID", required = true)
            @PathVariable String processDefinitionId) {
        
        log.info("Activating process definition: {}", processDefinitionId);
        processEngineComponent.activateProcessDefinition(processDefinitionId);
        
        Map<String, Object> result = Map.of("processDefinitionId", processDefinitionId, "activated", true);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}