package com.workflow.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.request.N8nCallbackRequest;
import com.workflow.dto.response.ApiResponse;
import com.workflow.entity.N8nExecutionRecord;
import com.workflow.repository.N8nExecutionRecordRepository;
import com.workflow.util.N8nVariableMappingUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Map;

/**
 * N8N 回调处理控制器
 * 接收 N8N 工作流执行完成后的回调通知，验证 callbackToken，
 * 根据执行结果写入 Flowable 流程变量并触发流程继续执行。
 *
 * Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7
 */
@Slf4j
@RestController
@RequestMapping("/api/workflow/n8n")
@RequiredArgsConstructor
@Tag(name = "N8N 回调处理", description = "接收 N8N 工作流执行结果回调")
public class N8nCallbackHandler {

    private static final String REDIS_KEY_PREFIX = "n8n:callback:";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_TIMEOUT = "TIMEOUT";
    private static final String CALLBACK_STATUS_SUCCESS = "success";

    private final N8nExecutionRecordRepository executionRecordRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final RuntimeService runtimeService;
    private final ObjectMapper objectMapper;

    /**
     * 接收 N8N 工作流执行结果回调
     *
     * 验证流程：
     * 1. 检查 callbackToken 是否存在于 Redis（不存在 → 401）
     * 2. 查询对应的 ExecutionRecord
     * 3. 如果执行记录状态为 TIMEOUT → 410
     * 4. 成功回调：根据 outputMapping 写入流程变量，触发流程继续
     * 5. 失败回调：标记 FAILED，记录错误信息
     * 6. 处理完成后删除 Redis 中的 callbackToken
     */
    @PostMapping("/callback")
    @Operation(summary = "N8N 回调", description = "接收 N8N 工作流执行完成后的回调通知")
    public ResponseEntity<ApiResponse<Void>> handleCallback(@RequestBody @Valid N8nCallbackRequest request) {
        String callbackToken = request.getCallbackToken();
        log.info("Received N8N callback: callbackToken={}, status={}", callbackToken, request.getStatus());

        // 1. Validate callbackToken in Redis
        if (callbackToken == null || callbackToken.isBlank()) {
            log.warn("N8N callback received with empty callbackToken");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", "Invalid or missing callbackToken"));
        }

        String redisKey = REDIS_KEY_PREFIX + callbackToken;
        String executionRecordIdStr = stringRedisTemplate.opsForValue().get(redisKey);

        if (executionRecordIdStr == null) {
            log.warn("N8N callback token not found in Redis: {}", callbackToken);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", "Invalid or expired callbackToken"));
        }

        // 2. Look up the execution record
        Long executionRecordId;
        try {
            executionRecordId = Long.parseLong(executionRecordIdStr);
        } catch (NumberFormatException e) {
            log.error("Invalid executionRecordId in Redis for token {}: {}", callbackToken, executionRecordIdStr);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", "Invalid callbackToken data"));
        }

        N8nExecutionRecord record = executionRecordRepository.findById(executionRecordId).orElse(null);
        if (record == null) {
            log.error("ExecutionRecord not found for id: {}", executionRecordId);
            deleteRedisKey(redisKey);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", "Execution record not found"));
        }

        // 3. Check if the task has already timed out
        if (STATUS_TIMEOUT.equals(record.getStatus())) {
            log.warn("N8N callback for timed-out task: callbackToken={}, recordId={}", callbackToken, executionRecordId);
            deleteRedisKey(redisKey);
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(ApiResponse.error("GONE", "Task has already timed out"));
        }

        // 4. Process the callback based on status
        try {
            if (CALLBACK_STATUS_SUCCESS.equalsIgnoreCase(request.getStatus())) {
                handleSuccessCallback(record, request);
            } else {
                handleFailureCallback(record, request);
            }
        } catch (Exception e) {
            log.error("Error processing N8N callback: callbackToken={}, error={}", callbackToken, e.getMessage(), e);
            // Mark as FAILED if processing itself fails
            record.setStatus(STATUS_FAILED);
            record.setErrorMessage("Callback processing error: " + e.getMessage());
            record.setCompletedAt(Instant.now());
            executionRecordRepository.save(record);
        }

        // 5. Delete the callbackToken from Redis
        deleteRedisKey(redisKey);

        log.info("N8N callback processed successfully: callbackToken={}, status={}", callbackToken, record.getStatus());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 处理成功回调：
     * - 根据 outputMapping 写入 Flowable 流程变量
     * - 触发流程继续执行
     * - 更新 ExecutionRecord（status=SUCCESS, outputData, completedAt）
     */
    private void handleSuccessCallback(N8nExecutionRecord record, N8nCallbackRequest request) {
        String processInstanceId = record.getProcessInstanceId();
        String taskId = record.getTaskId();

        log.info("Processing success callback: recordId={}, processInstanceId={}, taskId={}",
                record.getId(), processInstanceId, taskId);

        // Serialize outputData
        String outputDataJson = toJson(request.getOutputData());

        // Apply output mapping to write Flowable process variables
        if (processInstanceId != null && request.getOutputData() != null) {
            applyOutputMappingToProcess(processInstanceId, taskId, request.getOutputData());
        }

        // Trigger Flowable process to continue
        if (taskId != null) {
            triggerProcessContinuation(taskId, request.getOutputData());
        }

        // Update execution record
        record.setStatus(STATUS_SUCCESS);
        record.setOutputData(outputDataJson);
        record.setCompletedAt(Instant.now());
        executionRecordRepository.save(record);
    }

    /**
     * 处理失败回调：
     * - 标记 FAILED，记录错误信息
     * - 触发异常处理
     */
    private void handleFailureCallback(N8nExecutionRecord record, N8nCallbackRequest request) {
        String errorMessage = request.getErrorMessage();
        if (errorMessage == null || errorMessage.isBlank()) {
            errorMessage = "N8N workflow execution failed (no error message provided)";
        }

        log.warn("Processing failure callback: recordId={}, errorMessage={}", record.getId(), errorMessage);

        record.setStatus(STATUS_FAILED);
        record.setErrorMessage(errorMessage);
        record.setCompletedAt(Instant.now());
        executionRecordRepository.save(record);

        // Trigger Flowable error handling
        if (record.getTaskId() != null) {
            triggerErrorHandling(record.getTaskId(), errorMessage);
        }
    }

    /**
     * 根据 outputMapping 将 N8N 输出数据写入 Flowable 流程变量。
     * outputMapping JSON 存储在流程变量 n8n_outputMapping 中（由 N8nTaskExecutor 设置）。
     */
    private void applyOutputMappingToProcess(String processInstanceId, String executionId,
                                              Map<String, Object> outputData) {
        try {
            // Retrieve the outputMapping from process variables
            Object outputMappingObj = runtimeService.getVariable(executionId, "n8n_outputMapping");
            if (outputMappingObj == null) {
                log.debug("No outputMapping found in process variables for execution: {}", executionId);
                return;
            }

            String outputMappingJson = outputMappingObj.toString();
            Map<String, Object> mappedVariables = N8nVariableMappingUtil.applyOutputMapping(outputData, outputMappingJson);

            if (!mappedVariables.isEmpty()) {
                runtimeService.setVariables(executionId, mappedVariables);
                log.info("Applied output mapping: {} variables written to process {}", mappedVariables.size(), processInstanceId);
            }
        } catch (Exception e) {
            log.error("Failed to apply output mapping for process {}: {}", processInstanceId, e.getMessage(), e);
            throw new RuntimeException("Failed to apply output mapping: " + e.getMessage(), e);
        }
    }

    /**
     * 触发 Flowable 流程继续执行。
     * 使用 RuntimeService.trigger() 通知等待中的执行继续。
     */
    private void triggerProcessContinuation(String executionId, Map<String, Object> outputData) {
        try {
            runtimeService.trigger(executionId);
            log.info("Triggered process continuation for execution: {}", executionId);
        } catch (Exception e) {
            log.error("Failed to trigger process continuation for execution {}: {}", executionId, e.getMessage(), e);
            throw new RuntimeException("Failed to trigger process continuation: " + e.getMessage(), e);
        }
    }

    /**
     * 触发 Flowable 异常处理流程。
     */
    private void triggerErrorHandling(String executionId, String errorMessage) {
        try {
            runtimeService.trigger(executionId);
            log.info("Triggered error handling for execution: {}", executionId);
        } catch (Exception e) {
            log.warn("Failed to trigger error handling for execution {}: {}", executionId, e.getMessage());
            // Don't rethrow - the record is already marked as FAILED
        }
    }

    /**
     * 删除 Redis 中的 callbackToken
     */
    private void deleteRedisKey(String redisKey) {
        try {
            stringRedisTemplate.delete(redisKey);
        } catch (Exception e) {
            log.warn("Failed to delete Redis key {}: {}", redisKey, e.getMessage());
        }
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize object to JSON: {}", e.getMessage());
            return null;
        }
    }
}
