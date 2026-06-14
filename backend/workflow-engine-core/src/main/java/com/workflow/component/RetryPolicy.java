package com.workflow.component;

import com.workflow.entity.ExceptionRecord;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 重试策略
 *
 * 从 {@link RetryAndCompensationComponent} 拆分而来，承载重试机制（需求 9.6）中
 * 与具体执行/退避计算相关的逻辑：
 * - 指数退避下次重试时间计算
 * - 针对 Flowable 流程实例的重试尝试（实例存在性 / 挂起激活 / 活跃执行判定）
 *
 * 设计说明：
 * - 纯结构重组，行为与原内联实现逐字一致（退避公式、上限裁剪、RuntimeService null 容错、
 *   异常吞掉返回 false 的语义保持不变）。
 * - 重试次数/状态机/死信编排仍由门面 {@link RetryAndCompensationComponent} 负责。
 */
@Component
public class RetryPolicy {

    private static final Logger log = LoggerFactory.getLogger(RetryPolicy.class);

    @Autowired(required = false)
    private RuntimeService runtimeService;

    @Value("${workflow.retry.base-delay-seconds:30}")
    private int baseDelaySeconds = 30;

    @Value("${workflow.retry.max-delay-minutes:60}")
    private int maxDelayMinutes = 60;

    /**
     * 尝试执行重试逻辑
     */
    public boolean attemptRetryExecution(ExceptionRecord record) {
        log.info("尝试重试执行: processInstanceId={}, taskId={}",
                record.getProcessInstanceId(), record.getTaskId());

        try {
            // 检查流程实例是否仍然存在
            if (runtimeService != null && record.getProcessInstanceId() != null) {
                ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                        .processInstanceId(record.getProcessInstanceId())
                        .singleResult();

                if (instance == null) {
                    // 流程实例已不存在，视为成功（无需重试）
                    log.info("流程实例已不存在，标记为成功");
                    return true;
                }

                // 如果流程被挂起，尝试激活
                if (instance.isSuspended()) {
                    runtimeService.activateProcessInstanceById(record.getProcessInstanceId());
                    log.info("流程实例已激活");
                }

                // 尝试触发流程继续执行
                List<Execution> executions = runtimeService.createExecutionQuery()
                        .processInstanceId(record.getProcessInstanceId())
                        .list();

                if (!executions.isEmpty()) {
                    // 流程有活跃的执行，视为重试成功
                    log.info("流程有活跃执行，重试成功");
                    return true;
                }
            }

            // 默认返回false，表示需要继续重试
            return false;

        } catch (Exception e) {
            log.error("重试执行失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 计算下次重试时间（指数退避）
     */
    public LocalDateTime calculateNextRetryTime(int retryCount) {
        long delaySeconds = (long) (baseDelaySeconds * Math.pow(2, retryCount));
        long maxDelaySeconds = maxDelayMinutes * 60L;
        delaySeconds = Math.min(delaySeconds, maxDelaySeconds);

        return LocalDateTime.now().plusSeconds(delaySeconds);
    }
}
