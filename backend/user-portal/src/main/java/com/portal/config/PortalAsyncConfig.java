package com.portal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Portal 并行扇出专用线程池。
 *
 * <p>背景：{@code /tasks/query} 等查询用 {@code CompletableFuture.supplyAsync(...)} 做扇出
 * （引擎 future + 委托 future + 每个 delegator 一个 future）。若不指定 Executor，会全部落到
 * 进程共享的 {@code ForkJoinPool.commonPool()}：一是并行度只有 CPU-1，阻塞式 DB/HTTP 调用会把它占满；
 * 二是共享池被 Dashboard、权限富化、purge 等一起争抢。每个扇出任务运行时各借一条 Hikari 连接，
 * 请求内的连接占用被放大，是高并发下连接池(20)耗尽的第二主因（第一为 OSIV，已在 application.yml 关闭）。
 *
 * <p>这里用一个有界线程池替代 commonPool：
 * <ul>
 *   <li>核心/最大线程数有上限，避免瞬时并发把连接池打空；</li>
 *   <li>有界队列 + {@link ThreadPoolExecutor.CallerRunsPolicy} 提供背压——池满时任务回落到调用线程
 *       串行执行，而不是抛拒绝异常或无限堆积；</li>
 *   <li>线程命名 {@code portal-fanout-}，压测时便于在线程栈中定位。</li>
 * </ul>
 * 参数经 {@code portal.async.*} 可配置，默认值面向 pool-size=20 的连接池留足余量。
 */
@Configuration
public class PortalAsyncConfig {

    /** 叶子任务查询扇出池：{@code TaskQueryComponent} 的 engine/delegated future。 */
    public static final String TASK_QUERY_EXECUTOR = "portalTaskQueryExecutor";

    /**
     * 聚合类扇出池：Dashboard / ProcessApplication / Permission / Purge 等请求级扇出。
     *
     * <p>为什么要与 {@link #TASK_QUERY_EXECUTOR} 分开成两个池——避免有界池自等待死锁：
     * 这些聚合扇出（尤其 Dashboard 团队聚合）会在任务体内再调用 {@code queryTasks}，
     * 而 {@code queryTasks} 又向 {@code TASK_QUERY_EXECUTOR} 提交子任务并 join。
     * 若父子同池，父占满线程等子、子在队列无线程可跑即死锁；分池后父在 aggregation 池、
     * 子在 taskQuery 池，互不占用彼此线程，天然无环。
     * 这些扇出多为 workflow-engine / admin-center 的 HTTP 调用（不碰 DB），移出共享 commonPool
     * 也避免 commonPool(并行度≈CPU-1) 被阻塞式 HTTP 占满而拖慢全进程异步。
     */
    public static final String AGGREGATION_EXECUTOR = "portalAggregationExecutor";

    @Value("${portal.async.core-pool-size:8}")
    private int corePoolSize;

    @Value("${portal.async.max-pool-size:16}")
    private int maxPoolSize;

    @Value("${portal.async.queue-capacity:200}")
    private int queueCapacity;

    @Value("${portal.async.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    @Value("${portal.async.aggregation.core-pool-size:4}")
    private int aggregationCorePoolSize;

    @Value("${portal.async.aggregation.max-pool-size:8}")
    private int aggregationMaxPoolSize;

    @Value("${portal.async.aggregation.queue-capacity:200}")
    private int aggregationQueueCapacity;

    @Bean(name = TASK_QUERY_EXECUTOR)
    public ThreadPoolTaskExecutor portalTaskQueryExecutor() {
        return build("portal-fanout-", corePoolSize, maxPoolSize, queueCapacity, keepAliveSeconds);
    }

    @Bean(name = AGGREGATION_EXECUTOR)
    public ThreadPoolTaskExecutor portalAggregationExecutor() {
        return build("portal-agg-", aggregationCorePoolSize, aggregationMaxPoolSize,
                aggregationQueueCapacity, keepAliveSeconds);
    }

    private static ThreadPoolTaskExecutor build(
            String namePrefix, int core, int max, int queue, int keepAlive) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setKeepAliveSeconds(keepAlive);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix(namePrefix);
        // 池满时回落到调用线程串行执行：天然背压，避免连接池被瞬时扇出打空，也不丢任务。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅停机：等待在途扇出完成再关闭，避免请求半途丢连接/丢结果。
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
