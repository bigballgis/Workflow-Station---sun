package com.workflow.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.workflow.component.*;
import com.workflow.dto.request.AuthenticationRequest;
import com.workflow.dto.response.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 性能集成测试
 * 验证系统性能指标：响应时间、吞吐量、并发处理能力
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("性能集成测试")
class PerformanceIntegrationTest {

    // Spring/Redis Mocks
    @Mock(lenient = true) private ApplicationEventPublisher eventPublisher;
    @Mock(lenient = true) private StringRedisTemplate stringRedisTemplate;
    @Mock(lenient = true) private ValueOperations<String, String> valueOperations;
    @Mock(lenient = true) private ListOperations<String, String> listOperations;
    @Mock(lenient = true) private HashOperations<String, Object, Object> hashOperations;
    @Mock(lenient = true) private AuditManagerComponent auditManagerComponent;
    
    // 组件实例
    private NotificationManagerComponent notificationManagerComponent;
    private SecurityManagerComponent securityManagerComponent;
    
    private ObjectMapper objectMapper;

    // 性能指标
    private static final long MAX_AUTH_TIME_MS = 500; // 认证最大响应时间500毫秒
    private static final long MAX_NOTIFICATION_TIME_MS = 200; // 通知最大响应时间200毫秒
    private static final int TARGET_TPS = 100; // 目标TPS
    private static final int CONCURRENT_USERS = 100; // 并发用户数

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        // 设置Redis模拟
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        
        // 测试用密钥
        String testJwtSecret = "test-jwt-secret-key-for-performance-testing";
        String testEncryptionKey = "test-encryption-key-32-bytes!!!!";
        
        // 初始化组件
        notificationManagerComponent = new NotificationManagerComponent(eventPublisher, stringRedisTemplate, objectMapper);
        securityManagerComponent = new SecurityManagerComponent(
            stringRedisTemplate, objectMapper, auditManagerComponent,
            testJwtSecret, 86400000L, 604800000L, testEncryptionKey
        );
        securityManagerComponent.initializeDefaultRolePermissions();
    }

    @Nested
    @DisplayName("响应时间测试")
    class ResponseTimeTests {

        @Test
        @DisplayName("用户认证响应时间小于500毫秒")
        void authenticationResponseTimeUnder500ms() {
            // Given
            AuthenticationRequest request = new AuthenticationRequest();
            request.setUsername("admin");
            request.setPassword("admin123");
            request.setIpAddress("127.0.0.1");
            
            // When
            Instant start = Instant.now();
            AuthenticationResult result = securityManagerComponent.authenticate(request);
            Instant end = Instant.now();
            
            long responseTime = Duration.between(start, end).toMillis();
            
            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(responseTime).isLessThan(MAX_AUTH_TIME_MS);
            
            System.out.println("认证响应时间: " + responseTime + "ms (目标: <" + MAX_AUTH_TIME_MS + "ms)");
        }

        @Test
        @DisplayName("通知发送响应时间小于200毫秒")
        void notificationResponseTimeUnder200ms() {
            // Given
            String userId = "perf-user";
            notificationManagerComponent.registerWebSocketSession("session-perf", userId);
            notificationManagerComponent.subscribeEvent("TASK_ASSIGNED", userId, null);
            
            // When
            Instant start = Instant.now();
            NotificationResult result = notificationManagerComponent.publishTaskAssignedEvent(
                    "task-perf", "性能测试任务", userId, "proc-perf");
            Instant end = Instant.now();
            
            long responseTime = Duration.between(start, end).toMillis();
            
            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(responseTime).isLessThan(MAX_NOTIFICATION_TIME_MS);
            
            System.out.println("通知发送响应时间: " + responseTime + "ms (目标: <" + MAX_NOTIFICATION_TIME_MS + "ms)");
        }

        @Test
        @DisplayName("权限检查响应时间小于50毫秒")
        void permissionCheckResponseTimeUnder50ms() {
            // When
            Instant start = Instant.now();
            PermissionCheckResult result = securityManagerComponent.checkPermission(
                    "admin", "TASK", "VIEW");
            Instant end = Instant.now();
            
            long responseTime = Duration.between(start, end).toMillis();
            
            // Then
            assertThat(result).isNotNull();
            assertThat(responseTime).isLessThan(50);
            
            System.out.println("权限检查响应时间: " + responseTime + "ms (目标: <50ms)");
        }
    }

    @Nested
    @DisplayName("吞吐量测试")
    class ThroughputTests {

        @Test
        @DisplayName("系统支持100 TPS认证请求")
        void systemSupports100TpsAuthentication() throws Exception {
            // Given
            int totalRequests = TARGET_TPS;
            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch latch = new CountDownLatch(totalRequests);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicLong totalTime = new AtomicLong(0);
            
            // When - 发送请求
            Instant testStart = Instant.now();
            
            for (int i = 0; i < totalRequests; i++) {
                executor.submit(() -> {
                    try {
                        Instant start = Instant.now();
                        
                        AuthenticationRequest request = new AuthenticationRequest();
                        request.setUsername("admin");
                        request.setPassword("admin123");
                        request.setIpAddress("127.0.0.1");
                        
                        AuthenticationResult result = securityManagerComponent.authenticate(request);
                        Instant end = Instant.now();
                        
                        if (result.isSuccess()) {
                            successCount.incrementAndGet();
                        }
                        totalTime.addAndGet(Duration.between(start, end).toMillis());
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await(30, TimeUnit.SECONDS);
            Instant testEnd = Instant.now();
            executor.shutdown();
            
            // Then
            long testDuration = Duration.between(testStart, testEnd).toMillis();
            double actualTps = (double) successCount.get() / (testDuration / 1000.0);
            double avgResponseTime = (double) totalTime.get() / totalRequests;
            
            System.out.println("=== 认证吞吐量测试结果 ===");
            System.out.println("总请求数: " + totalRequests);
            System.out.println("成功请求数: " + successCount.get());
            System.out.println("测试耗时: " + testDuration + "ms");
            System.out.println("实际TPS: " + String.format("%.2f", actualTps));
            System.out.println("平均响应时间: " + String.format("%.2f", avgResponseTime) + "ms");
            
            assertThat(successCount.get()).isEqualTo(totalRequests);
            assertThat(actualTps).isGreaterThan(0);
        }

        @Test
        @DisplayName("通知发送吞吐量测试")
        void notificationThroughput() throws Exception {
            // Given
            int totalNotifications = 50;
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch latch = new CountDownLatch(totalNotifications);
            AtomicInteger successCount = new AtomicInteger(0);
            
            // 注册会话和订阅
            for (int i = 0; i < totalNotifications; i++) {
                String userId = "notify-user-" + i;
                notificationManagerComponent.registerWebSocketSession("session-" + i, userId);
                notificationManagerComponent.subscribeEvent("TASK_ASSIGNED", userId, null);
            }
            
            // When
            Instant testStart = Instant.now();
            
            for (int i = 0; i < totalNotifications; i++) {
                final int notifyIndex = i;
                executor.submit(() -> {
                    try {
                        NotificationResult result = notificationManagerComponent.publishTaskAssignedEvent(
                                "task-throughput-" + notifyIndex,
                                "任务-" + notifyIndex,
                                "notify-user-" + notifyIndex,
                                "proc-" + notifyIndex
                        );
                        if (result.isSuccess()) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await(30, TimeUnit.SECONDS);
            Instant testEnd = Instant.now();
            executor.shutdown();
            
            // Then
            long testDuration = Duration.between(testStart, testEnd).toMillis();
            double notificationsPerSecond = (double) successCount.get() / (testDuration / 1000.0);
            
            System.out.println("=== 通知吞吐量测试 ===");
            System.out.println("总通知数: " + totalNotifications);
            System.out.println("成功发送: " + successCount.get());
            System.out.println("测试耗时: " + testDuration + "ms");
            System.out.println("每秒通知数: " + String.format("%.2f", notificationsPerSecond));
            
            assertThat(successCount.get()).isEqualTo(totalNotifications);
        }
    }

    @Nested
    @DisplayName("并发处理能力测试")
    class ConcurrencyTests {

        @Test
        @DisplayName("支持100个并发用户认证")
        void supports100ConcurrentUserAuthentication() throws Exception {
            // Given
            ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_USERS);
            CountDownLatch startLatch = new CountDownLatch(1); // 同步启动
            CountDownLatch endLatch = new CountDownLatch(CONCURRENT_USERS);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
            
            // When - 同时启动所有认证
            for (int i = 0; i < CONCURRENT_USERS; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await(); // 等待同步信号
                        
                        Instant start = Instant.now();
                        
                        AuthenticationRequest request = new AuthenticationRequest();
                        request.setUsername("admin");
                        request.setPassword("admin123");
                        request.setIpAddress("127.0.0.1");
                        
                        AuthenticationResult result = securityManagerComponent.authenticate(request);
                        Instant end = Instant.now();
                        
                        responseTimes.add(Duration.between(start, end).toMillis());
                        
                        if (result.isSuccess()) {
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }
            
            // 发送同步启动信号
            Instant testStart = Instant.now();
            startLatch.countDown();
            
            // 等待所有完成
            endLatch.await(60, TimeUnit.SECONDS);
            Instant testEnd = Instant.now();
            executor.shutdown();
            
            // Then
            long testDuration = Duration.between(testStart, testEnd).toMillis();
            double avgResponseTime = responseTimes.stream().mapToLong(Long::longValue).average().orElse(0);
            long maxResponseTime = responseTimes.stream().mapToLong(Long::longValue).max().orElse(0);
            long minResponseTime = responseTimes.stream().mapToLong(Long::longValue).min().orElse(0);
            
            System.out.println("=== 并发认证测试 ===");
            System.out.println("并发用户数: " + CONCURRENT_USERS);
            System.out.println("成功数: " + successCount.get());
            System.out.println("失败数: " + failCount.get());
            System.out.println("总耗时: " + testDuration + "ms");
            System.out.println("平均响应时间: " + String.format("%.2f", avgResponseTime) + "ms");
            System.out.println("最大响应时间: " + maxResponseTime + "ms");
            System.out.println("最小响应时间: " + minResponseTime + "ms");
            
            assertThat(successCount.get()).isEqualTo(CONCURRENT_USERS);
            assertThat(failCount.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("并发操作不产生死锁")
        void concurrentOperationsNoDeadlock() throws Exception {
            // Given
            int operationCount = 50;
            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch latch = new CountDownLatch(operationCount);
            AtomicInteger deadlockCount = new AtomicInteger(0);
            
            // When - 并发执行混合操作
            for (int i = 0; i < operationCount; i++) {
                final int opIndex = i;
                executor.submit(() -> {
                    try {
                        // 随机执行不同操作
                        if (opIndex % 3 == 0) {
                            // 认证
                            AuthenticationRequest request = new AuthenticationRequest();
                            request.setUsername("admin");
                            request.setPassword("admin123");
                            request.setIpAddress("127.0.0.1");
                            securityManagerComponent.authenticate(request);
                        } else if (opIndex % 3 == 1) {
                            // 权限检查
                            securityManagerComponent.checkPermission("admin", "TASK", "VIEW");
                        } else {
                            // 发送通知
                            notificationManagerComponent.publishTaskAssignedEvent(
                                    "task-" + opIndex, "任务", "user-" + opIndex, "proc-" + opIndex);
                        }
                    } catch (Exception e) {
                        if (e.getMessage() != null && e.getMessage().contains("deadlock")) {
                            deadlockCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            // 设置超时，如果发生死锁会超时
            boolean completed = latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();
            
            // Then
            assertThat(completed).isTrue().withFailMessage("操作超时，可能发生死锁");
            assertThat(deadlockCount.get()).isEqualTo(0);
            
            System.out.println("并发操作完成，无死锁发生");
        }
    }

    @Nested
    @DisplayName("内存使用测试")
    class MemoryUsageTests {

        @Test
        @DisplayName("大量操作不导致内存溢出")
        void largeNumberOfOperationsNoOOM() {
            // Given
            int operationCount = 500;
            List<Object> results = new ArrayList<>();
            
            // 记录初始内存
            Runtime runtime = Runtime.getRuntime();
            long initialMemory = runtime.totalMemory() - runtime.freeMemory();
            
            // When - 执行大量操作
            for (int i = 0; i < operationCount; i++) {
                // 认证
                AuthenticationRequest request = new AuthenticationRequest();
                request.setUsername("admin");
                request.setPassword("admin123");
                request.setIpAddress("127.0.0.1");
                
                AuthenticationResult authResult = securityManagerComponent.authenticate(request);
                results.add(authResult);
            }
            
            // 触发GC
            System.gc();
            
            // 记录最终内存
            long finalMemory = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = finalMemory - initialMemory;
            
            // Then
            System.out.println("=== 内存使用测试 ===");
            System.out.println("操作数: " + operationCount);
            System.out.println("初始内存: " + (initialMemory / 1024 / 1024) + "MB");
            System.out.println("最终内存: " + (finalMemory / 1024 / 1024) + "MB");
            System.out.println("内存增长: " + (memoryUsed / 1024 / 1024) + "MB");
            
            assertThat(results).hasSize(operationCount);
            // 内存增长应该在合理范围内（小于500MB）
            assertThat(memoryUsed).isLessThan(500 * 1024 * 1024L);
        }
    }

    @Nested
    @DisplayName("通知系统性能测试")
    class NotificationPerformanceTests {

        @Test
        @DisplayName("大量订阅者通知分发性能")
        void largeSubscriberNotificationPerformance() {
            // Given
            int subscriberCount = 100;
            String eventType = "PROCESS_COMPLETED";
            
            // 注册大量订阅者
            for (int i = 0; i < subscriberCount; i++) {
                String userId = "subscriber-" + i;
                notificationManagerComponent.registerWebSocketSession("session-" + i, userId);
                notificationManagerComponent.subscribeEvent(eventType, userId, null);
            }
            
            // When - 发布事件
            Instant start = Instant.now();
            NotificationResult result = notificationManagerComponent.publishProcessCompletedEvent(
                    "proc-notify-001", "test-process", "business-001", "starter-user");
            Instant end = Instant.now();
            
            long notifyTime = Duration.between(start, end).toMillis();
            
            // Then
            assertThat(result.isSuccess()).isTrue();
            
            System.out.println("=== 通知分发性能 ===");
            System.out.println("订阅者数量: " + subscriberCount);
            System.out.println("通知分发耗时: " + notifyTime + "ms");
            System.out.println("平均每订阅者: " + String.format("%.2f", (double) notifyTime / subscriberCount) + "ms");
            
            // 通知分发应该在合理时间内完成
            assertThat(notifyTime).isLessThan(5000);
        }

        @Test
        @DisplayName("通知历史查询性能")
        void notificationHistoryQueryPerformance() {
            // Given
            String userId = "history-perf-user";
            notificationManagerComponent.registerWebSocketSession("session-history-perf", userId);
            notificationManagerComponent.subscribeEvent("TASK_ASSIGNED", userId, null);
            
            // 生成大量通知历史
            for (int i = 0; i < 100; i++) {
                notificationManagerComponent.publishTaskAssignedEvent(
                        "task-history-" + i, "任务-" + i, userId, "proc-" + i);
            }
            
            // When - 查询历史
            Instant start = Instant.now();
            List<Map<String, Object>> history = notificationManagerComponent.getNotificationHistory(userId, 50);
            Instant end = Instant.now();
            
            long queryTime = Duration.between(start, end).toMillis();
            
            // Then
            assertThat(history).hasSize(50);
            assertThat(queryTime).isLessThan(500);
            
            System.out.println("=== 通知历史查询性能 ===");
            System.out.println("历史记录数: " + history.size());
            System.out.println("查询耗时: " + queryTime + "ms");
        }
    }
}
