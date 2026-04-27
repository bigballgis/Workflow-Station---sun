package com.workflow.controller;

import com.workflow.component.N8nTaskExecutor;
import com.workflow.dto.request.N8nActionRequest;
import com.workflow.dto.response.ApiResponse;
import com.workflow.dto.response.N8nExecutionResult;
import com.workflow.entity.N8nExecutionRecord;
import com.workflow.repository.N8nExecutionRecordRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * N8N 执行记录查询与 Action 同步执行控制器
 *
 * 提供执行记录的查询（支持筛选和分页）以及 N8N Action 同步执行内部 API。
 *
 * Validates: Requirements 7.3, 7.4, 10.20, 10.21, 10.22, 10.23
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "N8N Execution Records", description = "N8N execution record query and Action sync execution API")
public class N8nExecutionController {

    private final N8nExecutionRecordRepository executionRecordRepository;
    private final N8nTaskExecutor n8nTaskExecutor;

    // ==================== 执行记录查询 ====================

    /**
     * 查询 N8N 执行记录列表，支持按 processInstanceId、status、时间范围筛选和分页。
     *
     * Validates: Requirements 7.3
     */
    @GetMapping("/api/workflow/n8n/executions")
    @Operation(summary = "Query execution record list", description = "Support filtering by process instance ID, status, time range and pagination")
    public ResponseEntity<ApiResponse<Page<N8nExecutionRecord>>> listExecutions(
            @Parameter(description = "Process instance ID")
            @RequestParam(value = "processInstanceId", required = false) String processInstanceId,
            @Parameter(description = "Execution status: PENDING, RUNNING, SUCCESS, FAILED, TIMEOUT")
            @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "Start time (ISO-8601)")
            @RequestParam(value = "startTime", required = false) String startTime,
            @Parameter(description = "End time (ISO-8601)")
            @RequestParam(value = "endTime", required = false) String endTime,
            @Parameter(description = "Page number, starting from 0")
            @RequestParam(value = "page", defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(value = "size", defaultValue = "20") int size) {

        log.info("Querying N8N executions: processInstanceId={}, status={}, startTime={}, endTime={}, page={}, size={}",
                processInstanceId, status, startTime, endTime, page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<N8nExecutionRecord> spec = buildFilterSpecification(processInstanceId, status, startTime, endTime);
        Page<N8nExecutionRecord> result = executionRecordRepository.findAll(spec, pageable);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 查询单条 N8N 执行记录详情。
     *
     * Validates: Requirements 7.4
     */
    @GetMapping("/api/workflow/n8n/executions/{id}")
    @Operation(summary = "Query execution record detail", description = "Returns complete execution record detail by ID")
    public ResponseEntity<ApiResponse<N8nExecutionRecord>> getExecution(
            @Parameter(description = "Execution record ID", required = true)
            @PathVariable("id") Long id) {

        log.info("Querying N8N execution detail: id={}", id);

        return executionRecordRepository.findById(id)
                .map(record -> ResponseEntity.ok(ApiResponse.success(record)))
                .orElseGet(() -> ResponseEntity.ok(
                        ApiResponse.error("NOT_FOUND", "Execution record not found: " + id)));
    }

    // ==================== N8N Action 同步执行 ====================

    /**
     * N8N Action 同步执行内部 API。
     * 接收 N8N Action 执行请求，调用 N8nTaskExecutor.executeSynchronous() 同步等待结果。
     *
     * Validates: Requirements 10.20, 10.21, 10.22, 10.23
     */
    @PostMapping("/api/v1/n8n/execute")
    @Operation(summary = "N8N Action sync execution", description = "Internal API: Synchronously execute N8N workflow and return result")
    public ResponseEntity<ApiResponse<N8nExecutionResult>> executeSynchronous(
            @RequestBody @Valid N8nActionRequest request) {

        log.info("N8N Action sync execution request: webhookUrl={}, processInstanceId={}, timeoutSeconds={}",
                request.getWebhookUrl(), request.getProcessInstanceId(), request.getTimeoutSeconds());

        if (request.getWebhookUrl() == null || request.getWebhookUrl().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("BAD_REQUEST", "webhookUrl is required"));
        }
        if (request.getN8nConfigId() == null || request.getN8nConfigId().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("BAD_REQUEST", "n8nConfigId is required"));
        }

        try {
            N8nExecutionResult result = n8nTaskExecutor.executeSynchronous(request);

            if (result.isSuccess()) {
                return ResponseEntity.ok(ApiResponse.success(result));
            } else {
                return ResponseEntity.ok(ApiResponse.<N8nExecutionResult>builder()
                        .success(false)
                        .code(result.getStatus())
                        .message(result.getErrorMessage())
                        .data(result)
                        .timestamp(System.currentTimeMillis())
                        .build());
            }
        } catch (Exception e) {
            log.error("N8N Action sync execution failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("INTERNAL_ERROR", "N8N execution failed: " + e.getMessage()));
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 构建过滤条件 Specification，支持 processInstanceId、status、时间范围组合过滤。
     */
    Specification<N8nExecutionRecord> buildFilterSpecification(
            String processInstanceId, String status, String startTime, String endTime) {

        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (processInstanceId != null && !processInstanceId.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("processInstanceId"), processInstanceId));
            }

            if (status != null && !status.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            if (startTime != null && !startTime.isBlank()) {
                try {
                    Instant start = Instant.parse(startTime);
                    predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), start));
                } catch (Exception e) {
                    log.warn("Invalid startTime format: {}", startTime);
                }
            }

            if (endTime != null && !endTime.isBlank()) {
                try {
                    Instant end = Instant.parse(endTime);
                    predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), end));
                } catch (Exception e) {
                    log.warn("Invalid endTime format: {}", endTime);
                }
            }

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
