package com.admin.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * 分布式调度锁：多副本部署下，带 {@code @SchedulerLock} 的 {@code @Scheduled} 任务
 * 只在抢到 Redis 锁的那个节点执行，避免 LDAP/BI 同步等任务被多副本重复触发。
 *
 * <p>defaultLockAtMostFor 是兜底持锁上限（节点崩溃后锁最长保留时间）；具体任务可在
 * {@code @SchedulerLock(lockAtMostFor=...)} 覆盖。
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        // 环境隔离前缀，避免与其它服务/环境的锁键冲突
        return new RedisLockProvider(connectionFactory, "admin-center");
    }
}
