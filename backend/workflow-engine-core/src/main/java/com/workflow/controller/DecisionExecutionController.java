package com.workflow.controller;

import com.platform.common.exception.ResourceNotFoundException;
import com.workflow.component.DecisionExecutionComponent;
import com.workflow.dto.response.ApiResponse;
import com.workflow.exception.WorkflowBusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 决策表运行时执行控制器
 * 提供决策表评估的 RESTful API 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/processes/decisions")
@RequiredArgsConstructor
@Tag(name = "Decision Execution", description = "Decision table runtime evaluation")
public class DecisionExecutionController {

    private final DecisionExecutionComponent decisionExecutionComponent;

    /**
     * 评估决策表
     */
    @PostMapping("/{decisionKey}/evaluate")
    @Operation(summary = "评估决策表", description = "根据输入变量评估指定的决策表并返回匹配规则的输出条目")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> evaluateDecision(
            @Parameter(description = "决策键", required = true)
            @PathVariable String decisionKey,
            @RequestBody(required = false) Map<String, Object> variables) {

        log.info("Evaluating decision: decisionKey={}, variableKeys={}",
                decisionKey, variables != null ? variables.keySet() : "none");

        try {
            List<Map<String, Object>> results = decisionExecutionComponent.evaluate(
                    decisionKey, variables != null ? variables : Map.of());
            return ResponseEntity.ok(ApiResponse.success(results));

        } catch (ResourceNotFoundException e) {
            log.warn("Decision not found: decisionKey={}", decisionKey);
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("DECISION_NOT_FOUND", e.getMessage()));

        } catch (WorkflowBusinessException e) {
            return handleWorkflowBusinessException(e, decisionKey);

        } catch (Exception e) {
            log.error("Unexpected error evaluating decision: decisionKey={}", decisionKey, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred"));
        }
    }

    private ResponseEntity<ApiResponse<List<Map<String, Object>>>> handleWorkflowBusinessException(
            WorkflowBusinessException e, String decisionKey) {

        String errorCode = e.getErrorCode();
        log.warn("Decision evaluation error: decisionKey={}, errorCode={}", decisionKey, errorCode);

        return switch (errorCode) {
            case "DECISION_HIT_POLICY_VIOLATION" -> ResponseEntity.status(409)
                    .body(ApiResponse.error(errorCode, e.getMessage()));
            case "DECISION_EVALUATION_TIMEOUT" -> ResponseEntity.status(408)
                    .body(ApiResponse.error(errorCode, e.getMessage()));
            default -> ResponseEntity.badRequest()
                    .body(ApiResponse.error(errorCode, e.getMessage()));
        };
    }
}
