package com.workflow.component.impl;

import com.platform.common.exception.ResourceNotFoundException;
import com.workflow.component.DecisionExecutionComponent;
import com.workflow.exception.WorkflowBusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.dmn.api.DmnDecisionService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 决策表执行组件实现
 * 使用 Flowable DmnRuleService 评估决策表
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DecisionExecutionComponentImpl implements DecisionExecutionComponent {

    private static final long EVALUATION_TIMEOUT_SECONDS = 30;

    private final DmnDecisionService dmnDecisionService;

    @Override
    public List<Map<String, Object>> evaluate(String decisionKey, Map<String, Object> variables) {
        log.info("Evaluating decision table: decisionKey={}, variableKeys={}", decisionKey,
                variables != null ? variables.keySet() : "null");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<List<Map<String, Object>>> future = executor.submit(() -> executeDecision(decisionKey, variables));

        try {
            return future.get(EVALUATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.error("Decision evaluation timed out: decisionKey={}", decisionKey);
            throw new WorkflowBusinessException("DECISION_EVALUATION_TIMEOUT",
                    "Decision evaluation timed out after " + EVALUATION_TIMEOUT_SECONDS + " seconds for key: " + decisionKey);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ResourceNotFoundException) {
                throw (ResourceNotFoundException) cause;
            }
            if (cause instanceof WorkflowBusinessException) {
                throw (WorkflowBusinessException) cause;
            }
            log.error("Decision evaluation failed: decisionKey={}, errorType={}", decisionKey,
                    cause != null ? cause.getClass().getSimpleName() : "unknown");
            throw new WorkflowBusinessException("DECISION_EVALUATION_ERROR",
                    "Failed to evaluate decision: " + (cause != null ? cause.getMessage() : e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Decision evaluation interrupted: decisionKey={}", decisionKey);
            throw new WorkflowBusinessException("DECISION_EVALUATION_ERROR",
                    "Decision evaluation was interrupted for key: " + decisionKey);
        } finally {
            executor.shutdownNow();
        }
    }

    private List<Map<String, Object>> executeDecision(String decisionKey, Map<String, Object> variables) {
        try {
            List<Map<String, Object>> results = dmnDecisionService.createExecuteDecisionBuilder()
                    .decisionKey(decisionKey)
                    .variables(variables)
                    .execute();

            log.info("Decision evaluation completed: decisionKey={}, resultCount={}", decisionKey,
                    results != null ? results.size() : 0);
            return results;

        } catch (FlowableObjectNotFoundException e) {
            log.warn("Decision table not found: decisionKey={}", decisionKey);
            throw new ResourceNotFoundException("DecisionTable", decisionKey);
        } catch (Exception e) {
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error";

            if (isHitPolicyViolation(e)) {
                log.error("Hit policy violation: decisionKey={}, errorType={}", decisionKey,
                        e.getClass().getSimpleName());
                throw new WorkflowBusinessException("DECISION_HIT_POLICY_VIOLATION",
                        "Hit policy violation for decision: " + decisionKey + " - " + errorMessage);
            }

            if (isExpressionError(e)) {
                log.error("JUEL expression error: decisionKey={}, errorType={}", decisionKey,
                        e.getClass().getSimpleName());
                throw new WorkflowBusinessException("DECISION_EVALUATION_ERROR",
                        "Expression evaluation error for decision: " + decisionKey + " - " + errorMessage);
            }

            log.error("Unexpected error evaluating decision: decisionKey={}, errorType={}", decisionKey,
                    e.getClass().getSimpleName());
            throw new WorkflowBusinessException("DECISION_EVALUATION_ERROR",
                    "Failed to evaluate decision: " + decisionKey + " - " + errorMessage);
        }
    }

    private boolean isHitPolicyViolation(Exception e) {
        String message = e.getMessage();
        if (message == null) return false;
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("hit policy") || lowerMessage.contains("hitpolicy");
    }

    private boolean isExpressionError(Exception e) {
        String message = e.getMessage();
        if (message == null) return false;
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("expression") || lowerMessage.contains("juel")
                || lowerMessage.contains("el1") || lowerMessage.contains("evaluation");
    }
}
