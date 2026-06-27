package com.workflow.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * 分布式调度锁：多副本部署下，带 {@code @SchedulerLock} 的 {@code @Scheduled} 任务
 * 只在抢到 Redis 锁的那个节点执行，避免重试执行器、死信清理等任务被多副本重复触发。
 *
 * <p>注意：{@code HorizontalScalingComponent.updateHeartbeat} 是“每节点各写各的心跳”，
 * 不加 {@code @SchedulerLock}（必须每个副本都执行）。
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, "workflow-engine");
    }
}
