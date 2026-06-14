package com.workflow.component;

import com.workflow.component.RetryAndCompensationComponent.CompensationTransaction;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 补偿事务执行器
 *
 * 从 {@link RetryAndCompensationComponent} 拆分而来，承载单个补偿事务的执行逻辑
 * （需求 9.8 的补偿事务机制）。负责按补偿类型分派到具体的补偿动作：
 * 变量回滚 / 取消任务 / 终止流程 / 自定义补偿。
 *
 * 设计说明：
 * - 纯结构重组，行为与原内联实现逐字一致（异常处理、日志、RuntimeService null 容错保持不变）。
 * - 不持有补偿注册表状态；编排（倒序、批量）仍由门面 {@link RetryAndCompensationComponent} 负责。
 */
@Component
public class CompensationExecutor {

    private static final Logger log = LoggerFactory.getLogger(CompensationExecutor.class);

    @Autowired(required = false)
    private RuntimeService runtimeService;

    /**
     * 执行单个补偿事务
     */
    public Map<String, Object> executeCompensationTransaction(CompensationTransaction transaction) {
        log.info("执行补偿事务: id={}, type={}", transaction.getId(), transaction.getCompensationType());

        Map<String, Object> result = new HashMap<>();
        result.put("transactionId", transaction.getId());
        result.put("activityId", transaction.getActivityId());
        result.put("compensationType", transaction.getCompensationType());
        result.put("executedTime", LocalDateTime.now());

        try {
            // 根据补偿类型执行不同的补偿逻辑
            switch (transaction.getCompensationType().toUpperCase()) {
                case "ROLLBACK_VARIABLES":
                    executeVariableRollback(transaction);
                    break;

                case "CANCEL_TASK":
                    executeCancelTask(transaction);
                    break;

                case "TERMINATE_PROCESS":
                    executeTerminateProcess(transaction);
                    break;

                case "CUSTOM":
                    executeCustomCompensation(transaction);
                    break;

                default:
                    log.warn("未知的补偿类型: {}", transaction.getCompensationType());
            }

            transaction.setExecuted(true);
            transaction.setExecutedTime(LocalDateTime.now());
            transaction.setSuccess(true);

            result.put("success", true);
            result.put("message", "补偿执行成功");

        } catch (Exception e) {
            log.error("执行补偿事务失败: {}", e.getMessage(), e);

            transaction.setExecuted(true);
            transaction.setExecutedTime(LocalDateTime.now());
            transaction.setSuccess(false);
            transaction.setErrorMessage(e.getMessage());

            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 执行变量回滚补偿
     */
    private void executeVariableRollback(CompensationTransaction transaction) {
        log.info("执行变量回滚: processInstanceId={}", transaction.getProcessInstanceId());

        if (runtimeService == null) {
            log.warn("RuntimeService不可用，跳过变量回滚");
            return;
        }

        Map<String, Object> originalValues = transaction.getCompensationData();
        if (originalValues != null && !originalValues.isEmpty()) {
            try {
                ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                        .processInstanceId(transaction.getProcessInstanceId())
                        .singleResult();

                if (instance != null) {
                    runtimeService.setVariables(transaction.getProcessInstanceId(), originalValues);
                    log.info("变量已回滚: {} 个变量", originalValues.size());
                }
            } catch (Exception e) {
                log.error("变量回滚失败: {}", e.getMessage());
                throw e;
            }
        }
    }

    /**
     * 执行取消任务补偿
     */
    private void executeCancelTask(CompensationTransaction transaction) {
        log.info("执行取消任务: activityId={}", transaction.getActivityId());

        // 任务取消逻辑（简化实现）
        log.info("任务取消补偿已执行");
    }

    /**
     * 执行终止流程补偿
     */
    private void executeTerminateProcess(CompensationTransaction transaction) {
        log.info("执行终止流程: processInstanceId={}", transaction.getProcessInstanceId());

        if (runtimeService == null) {
            log.warn("RuntimeService不可用，跳过流程终止");
            return;
        }

        try {
            ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(transaction.getProcessInstanceId())
                    .singleResult();

            if (instance != null) {
                String reason = (String) transaction.getCompensationData().getOrDefault("reason", "补偿终止");
                runtimeService.deleteProcessInstance(transaction.getProcessInstanceId(), reason);
                log.info("流程已终止: {}", transaction.getProcessInstanceId());
            }
        } catch (Exception e) {
            log.error("终止流程失败: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 执行自定义补偿
     */
    private void executeCustomCompensation(CompensationTransaction transaction) {
        log.info("执行自定义补偿: activityId={}", transaction.getActivityId());

        // 自定义补偿逻辑（可以通过回调或事件扩展）
        Map<String, Object> data = transaction.getCompensationData();
        if (data.containsKey("callback")) {
            log.info("执行自定义补偿回调: {}", data.get("callback"));
        }

        log.info("自定义补偿已执行");
    }
}
