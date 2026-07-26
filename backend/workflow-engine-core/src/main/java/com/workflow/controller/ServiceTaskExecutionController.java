package com.workflow.controller;

import com.workflow.component.ServiceTaskExecutor;
import com.workflow.dto.request.ServiceTaskActionRequest;
import com.workflow.dto.response.ApiResponse;
import com.workflow.dto.response.ServiceTaskExecutionResult;
import com.workflow.entity.ServiceTaskExecutionRecord;
import com.workflow.repository.ServiceTaskExecutionRecordRepository;
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
 * Activepieces execution record query and Action sync execution controller.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "AP Execution Records", description = "Activepieces execution record query and Action sync execution API")
public class ServiceTaskExecutionController {

    private final ServiceTaskExecutionRecordRepository executionRecordRepository;
    private final ServiceTaskExecutor apTaskExecutor;

    // ==================== Execution Record Queries ====================

    @GetMapping("/api/workflow/ap/executions")
    @Operation(summary = "Query execution record list", description = "Support filtering by process instance ID, status, time range and pagination")
    public ResponseEntity<ApiResponse<Page<ServiceTaskExecutionRecord>>> listExecutions(
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

        log.info("Querying AP executions: processInstanceId={}, status={}, page={}, size={}",
                processInstanceId, status, page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<ServiceTaskExecutionRecord> spec = buildFilterSpecification(processInstanceId, status, startTime, endTime);
        Page<ServiceTaskExecutionRecord> result = executionRecordRepository.findAll(spec, pageable);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/api/workflow/ap/executions/{id}")
    @Operation(summary = "Get single execution record")
    public ResponseEntity<ApiResponse<ServiceTaskExecutionRecord>> getExecution(@PathVariable("id") Long id) {
        return executionRecordRepository.findById(id)
                .map(record -> ResponseEntity.ok(ApiResponse.success(record)))
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(ApiResponse.error("NOT_FOUND", "AP execution record not found: " + id)));
    }

    // ==================== AP Action Sync Execution ====================

    @PostMapping("/api/v1/ap/execute")
    @Operation(summary = "AP Action sync execution", description = "Internal API: Synchronously execute AP flow and return result")
    public ResponseEntity<ApiResponse<ServiceTaskExecutionResult>> executeSynchronous(
            @RequestBody @Valid ServiceTaskActionRequest request) {

        log.info("AP Action sync execution request: apFlowId={}, processInstanceId={}, timeoutSeconds={}",
                request.getApFlowId(), request.getProcessInstanceId(), request.getTimeoutSeconds());

        if ((request.getApFlowId() == null || request.getApFlowId().isBlank())
                && (request.getWebhookUrl() == null || request.getWebhookUrl().isBlank())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("BAD_REQUEST", "apFlowId or webhookUrl is required"));
        }

        try {
            ServiceTaskExecutionResult result = apTaskExecutor.executeSynchronous(request);

            if (result.isSuccess()) {
                return ResponseEntity.ok(ApiResponse.success(result));
            } else {
                return ResponseEntity.ok(ApiResponse.<ServiceTaskExecutionResult>builder()
                        .success(false)
                        .code(result.getStatus())
                        .message(result.getErrorMessage())
                        .data(result)
                        .timestamp(System.currentTimeMillis())
                        .build());
            }
        } catch (Exception e) {
            log.error("AP Action sync execution failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("INTERNAL_ERROR", "AP execution failed: " + e.getMessage()));
        }
    }

    // ==================== Internal Methods ====================

    Specification<ServiceTaskExecutionRecord> buildFilterSpecification(
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
