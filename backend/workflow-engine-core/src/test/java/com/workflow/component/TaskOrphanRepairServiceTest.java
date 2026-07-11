package com.workflow.component;

import com.workflow.client.AdminCenterClient;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * initiator 孤儿修复的限频行为。
 *
 * <p>该扫描在 {@code act_ru_variable} 上无索引、只能顺序扫，因此必须限频；
 * 但它是 per-user 的，所以门必须也是 per-user——否则 A 用户会把 B 用户挡在门外。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TaskOrphanRepairService — initiator repair throttling")
class TaskOrphanRepairServiceTest {

    @Mock
    private TaskService taskService;
    @Mock
    private RuntimeService runtimeService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private AdminCenterClient adminCenterClient;
    @Mock
    private BpmnActionParser bpmnActionParser;

    private TaskOrphanRepairService service;

    /** 统计底层扫描被真正触发了几次（每次扫描都会 createTaskQuery）。 */
    private AtomicInteger scanCount;

    @BeforeEach
    void setUp() {
        service = new TaskOrphanRepairService();
        ReflectionTestUtils.setField(service, "taskService", taskService);
        ReflectionTestUtils.setField(service, "runtimeService", runtimeService);
        ReflectionTestUtils.setField(service, "repositoryService", repositoryService);
        ReflectionTestUtils.setField(service, "adminCenterClient", adminCenterClient);
        ReflectionTestUtils.setField(service, "bpmnActionParser", bpmnActionParser);

        scanCount = new AtomicInteger();
        lenient().when(taskService.createTaskQuery()).thenAnswer(inv -> {
            scanCount.incrementAndGet();
            return stubQueryReturningNoTasks();
        });
    }

    /**
     * 返回一个链式 TaskQuery：所有返回 TaskQuery 的方法都回自己，listPage 给空列表。
     *
     * <p>注意 {@code listPage(int,int)} 是原始类型参数——必须在默认 Answer 里显式处理，
     * 否则会返回 null，被调用方的 for-each 触发 NPE 并被外层 catch 静默吞掉，
     * 让测试"通过"却什么都没验证。
     */
    private TaskQuery stubQueryReturningNoTasks() {
        return mock(TaskQuery.class, inv -> {
            Class<?> ret = inv.getMethod().getReturnType();
            if (java.util.List.class.isAssignableFrom(ret)) {
                return Collections.<Task>emptyList();
            }
            // Flowable 的链式方法声明在 Query<T,U> 上、返回泛型 T，擦除后是 Object——
            // 所以不能只判 TaskQuery.isAssignableFrom(ret)，否则 taskUnassigned() 返回 null，
            // 调用方 NPE 被外层 catch 吞掉，测试会"通过"但什么都没测到。
            if (ret.isAssignableFrom(TaskQuery.class)) {
                return inv.getMock();
            }
            return null;
        });
    }

    @Test
    @DisplayName("同一用户 30 秒内连续请求：只扫一次")
    void shouldThrottleRepeatedCallsForSameUser() {
        for (int i = 0; i < 5; i++) {
            service.mergeOrphanInitiatorTasksRepair("alice", 20, new LinkedHashMap<>());
        }
        assertThat(scanCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("不同用户互不阻塞：各自都能扫（关键——门必须是 per-user）")
    void shouldNotThrottleAcrossDifferentUsers() {
        service.mergeOrphanInitiatorTasksRepair("alice", 20, new LinkedHashMap<>());
        int afterAlice = scanCount.get();

        service.mergeOrphanInitiatorTasksRepair("bob", 20, new LinkedHashMap<>());
        service.mergeOrphanInitiatorTasksRepair("carol", 20, new LinkedHashMap<>());

        assertThat(afterAlice).isEqualTo(1);
        assertThat(scanCount.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("数字 userId 会跑 String + Long 两种变量查询（首次放行时）")
    void shouldRunBothVariantsForNumericUserId() {
        service.mergeOrphanInitiatorTasksRepair("12345", 20, new LinkedHashMap<>());
        assertThat(scanCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("空 userId 不触发任何扫描")
    void shouldSkipBlankUserId() {
        service.mergeOrphanInitiatorTasksRepair("  ", 20, new LinkedHashMap<>());
        service.mergeOrphanInitiatorTasksRepair(null, 20, new LinkedHashMap<>());
        assertThat(scanCount.get()).isZero();
    }

    @Test
    @DisplayName("同一用户并发 32 个请求：只有一个线程拿到修复名额")
    void shouldAllowOnlyOneConcurrentRepairPerUser() throws Exception {
        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.mergeOrphanInitiatorTasksRepair("dave", 20, new LinkedHashMap<>());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(scanCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("限频窗口过期后，同一用户可再次扫描")
    void shouldAllowRepairAgainAfterWindowElapses() {
        service.mergeOrphanInitiatorTasksRepair("erin", 20, new LinkedHashMap<>());
        assertThat(scanCount.get()).isEqualTo(1);

        // 把该用户的上次修复时间往前拨到窗口之外
        @SuppressWarnings("unchecked")
        java.util.Map<String, Long> gate = (java.util.Map<String, Long>)
                ReflectionTestUtils.getField(service, "lastInitiatorRepairAtMsByUser");
        gate.put("erin", System.currentTimeMillis() - 31_000L);

        service.mergeOrphanInitiatorTasksRepair("erin", 20, new LinkedHashMap<>());
        assertThat(scanCount.get()).isEqualTo(2);
    }
}
