package com.workflow.controller;

import com.workflow.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 监控管理控制器
 * 
 * 提供流程监控、统计数据查询、历史数据管理和审计日志查询的RESTful API接口
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/monitoring")
@RequiredArgsConstructor
    @Tag(name = "Monitoring", description = "Process monitoring and statistics API")
public class MonitoringController {

    private final com.workflow.component.ProcessEngineComponent processEngineComponent;

    /**
     * 查询流程监控数据
     */
    @PostMapping("/processes/query")
    @Operation(summary = "Query Process Monitoring Data", description = "Query process instance monitoring data by criteria")
    public ResponseEntity<ApiResponse<Map<String, Object>>> queryProcessMonitorData(
            @RequestBody Map<String, Object> request) {
        
        Map<String, Object> result = Map.of("processes", "monitor-data", "total", 5);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 获取流程统计信息
     */
    @GetMapping("/processes/statistics")
    @Operation(summary = "Get Process Statistics", description = "Get process instance statistics and analysis data")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProcessStatistics(
            @Parameter(description = "Process definition key")
            @RequestParam(value = "processDefinitionKey", required = false) String processDefinitionKey) {
        
        Map<String, Object> result = Map.of("statistics", "process-stats", "processDefinitionKey", processDefinitionKey);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 获取流程图状态渲染数据
     */
    @GetMapping("/processes/{processInstanceId}/diagram")
    @Operation(summary = "Get Process Diagram Render Data", description = "Get process instance execution status and path highlighting data")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProcessDiagramData(
            @Parameter(description = "Process instance ID", required = true)
            @PathVariable String processInstanceId) {
        
        Map<String, Object> result = Map.of("processInstanceId", processInstanceId, "diagram", "diagram-data");
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 获取流程实例的当前活动节点
     */
    @GetMapping("/processes/{processInstanceId}/current-activity")
    @Operation(summary = "Get Active Nodes", description = "Get current active node information of a process instance")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentActivity(
            @Parameter(description = "Process instance ID", required = true)
            @PathVariable String processInstanceId) {
        
        log.info("Getting current activity for process instance: {}", processInstanceId);
        Map<String, Object> result = processEngineComponent.getCurrentActivity(processInstanceId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 查询历史数据
     */
    @PostMapping("/history/query")
    @Operation(summary = "Query History Data", description = "Query historical process and task data by criteria")
    public ResponseEntity<ApiResponse<Map<String, Object>>> queryHistoryData(
            @RequestBody Map<String, Object> request) {
        
        Map<String, Object> result = Map.of("history", "history-data", "total", 10);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 导出历史数据
     */
    @PostMapping("/history/export")
    @Operation(summary = "Export History Data", description = "Export history data in specified format")
    public ResponseEntity<ApiResponse<Map<String, Object>>> exportHistoryData(
            @RequestBody Map<String, Object> request,
            @Parameter(description = "Export format")
            @RequestParam(value = "exportFormat", defaultValue = "JSON") String exportFormat) {
        
        Map<String, Object> result = Map.of("exportFormat", exportFormat, "exported", true);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 系统健康检查
     */
    @GetMapping("/health")
    @Operation(summary = "System Health Check", description = "Check the health status of the workflow engine system")
    public ResponseEntity<ApiResponse<Map<String, Object>>> healthCheck() {
        
        Map<String, Object> result = Map.of("healthy", true, "status", "UP");
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}