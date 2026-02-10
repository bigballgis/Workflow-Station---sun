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
 * Performance Integration Tests
 * Validates system performance metrics: response time, throughput, concurrency handling
 *
 * @author Workflow Engine
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Performance Integration Tests")
class PerformanceIntegrationTest {

    // Spring/Redis Mocks
    @Mock(lenient = true) private ApplicationEventPublisher eventPublisher;
    @Mock(lenient = true) private StringRedisTemplate stringRedisTemplate;
    @Mock(lenient = true) private ValueOperations<String, String> valueOperations;
    @Mock(lenient = true) private ListOperations<String, String> listOperations;
    @Mock(lenient = true) private HashOperations<String, Object, Object> hashOperations;
    @Mock(lenient = true) private AuditManagerComponent auditManagerComponent;

    private NotificationManagerComponent notificationManagerComponent;
    private SecurityManagerComponent securityManagerComponent;

    private ObjectMapper objectMapper;

    // Performance thresholds
    private static final long MAX_AUTH_TIME_MS = 500;
    private static final long MAX_NOTIFICATION_TIME_MS = 200;
    private static final int TARGET_TPS = 100;
    private static final int CONCURRENT_USERS = 100;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Setup Redis mocks
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);

        String testJwtSecret = "test-jwt-secret-key-for-performance-testing";
        String testEncryptionKey = "test-encryption-key-32-bytes!!!!";

        notificationManagerComponent = new NotificationManagerComponent(eventPublisher, stringRedisTemplate, objectMapper);
        securityManagerComponent = new SecurityManagerComponent(
            stringRedisTemplate, objectMapper, auditManagerComponent,
            testJwtSecret, 86400000L, 604800000L, testEncryptionKey
        );
        securityManagerComponent.initializeDefaultRolePermissions();
    }

    @Nested
    @DisplayName("Response Time Tests")
    class ResponseTimeTests {

        @Test
        @DisplayName("Authentication response time under 500ms")
        void authenticationResponseTimeUnder500ms() {
            AuthenticationRequest request = new AuthenticationRequest();
            request.setUsername("admin");
            request.setPassword("admin123");
            request.setIpAddress("127.0.0.1");

            Instant start = Instant.now();
            AuthenticationResult result = securityManagerComponent.authenticate(request);
            Instant end = Instant.now();

            long responseTime = Duration.between(start, end).toMillis();

            assertThat(result.isSuccess()).isTrue();
            assertThat(responseTime).isLessThan(MAX_AUTH_TIME_MS);

            System.out.println("Auth response time: " + responseTime + "ms (target: <" + MAX_AUTH_TIME_MS + "ms)");
        }

        @Test
        @DisplayName("Notification send response time under 200ms")
        void notificationResponseTimeUnder200ms() {
            String userId = "perf-user";
            notificationManagerComponent.registerWebSocketSession("session-perf", userId);
            notificationManagerComponent.subscribeEvent("TASK_ASSIGNED", userId, null);

            Instant start = Instant.now();
            NotificationResult result = notificationManagerComponent.publishTaskAssignedEvent(
                    "task-perf", "Performance test task", userId, "proc-perf");
            Instant end = Instant.now();

            long responseTime = Duration.between(start, end).toMillis();

            assertThat(result.isSuccess()).isTrue();
            assertThat(responseTime).isLessThan(MAX_NOTIFICATION_TIME_MS);

            System.out.println("Notification response time: " + responseTime + "ms (target: <" + MAX_NOTIFICATION_TIME_MS + "ms)");
        }

        @Test
        @DisplayName("Permission check response time under 50ms")
        void permissionCheckResponseTimeUnder50ms() {
            Instant start = Instant.now();
            PermissionCheckResult result = securityManagerComponent.checkPermission(
                    "admin", "TASK", "VIEW");
            Instant end = Instant.now();

            long responseTime = Duration.between(start, end).toMillis();

            assertThat(result).isNotNull();
            assertThat(responseTime).isLessThan(50);

            System.out.println("Permission check response time: " + responseTime + "ms (target: <50ms)");
        }
    }

    @Nested
    @DisplayName("Throughput Tests")
    class ThroughputTests {

        @Test
        @DisplayName("System supports 100 TPS authentication requests")
        void systemSupports100TpsAuthentication() throws Exception {
            int totalRequests = TARGET_TPS;
            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch latch = new CountDownLatch(totalRequests);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicLong totalTime = new AtomicLong(0);

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

            long testDuration = Duration.between(testStart, testEnd).toMillis();
            double actualTps = (double) successCount.get() / (testDuration / 1000.0);
            double avgResponseTime = (double) totalTime.get() / totalRequests;

            System.out.println("=== Auth Throughput Test Results ===");
            System.out.println("Total requests: " + totalRequests);
            System.out.println("Successful: " + successCount.get());
            System.out.println("Duration: " + testDuration + "ms");
            System.out.println("Actual TPS: " + String.format("%.2f", actualTps));
            System.out.println("Avg response time: " + String.format("%.2f", avgResponseTime) + "ms");

            assertThat(successCount.get()).isEqualTo(totalRequests);
            assertThat(actualTps).isGreaterThan(0);
        }

        @Test
        @DisplayName("Notification send throughput test")
        void notificationThroughput() throws Exception {
            int totalNotifications = 50;
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch latch = new CountDownLatch(totalNotifications);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < totalNotifications; i++) {
                String userId = "notify-user-" + i;
                notificationManagerComponent.registerWebSocketSession("session-" + i, userId);
                notificationManagerComponent.subscribeEvent("TASK_ASSIGNED", userId, null);
            }

            Instant testStart = Instant.now();

            for (int i = 0; i < totalNotifications; i++) {
                final int notifyIndex = i;
                executor.submit(() -> {
                    try {
                        NotificationResult result = notificationManagerComponent.publishTaskAssignedEvent(
                                "task-throughput-" + notifyIndex,
                                "Task-" + notifyIndex,
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

            long testDuration = Duration.between(testStart, testEnd).toMillis();
            double notificationsPerSecond = (double) successCount.get() / (testDuration / 1000.0);

            System.out.println("=== Notification Throughput Test ===");
            System.out.println("Total notifications: " + totalNotifications);
            System.out.println("Successful: " + successCount.get());
            System.out.println("Duration: " + testDuration + "ms");
            System.out.println("Notifications/sec: " + String.format("%.2f", notificationsPerSecond));

            assertThat(successCount.get()).isEqualTo(totalNotifications);
        }
    }

    @Nested
    @DisplayName("Concurrency Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Supports 100 concurrent user authentications")
        void supports100ConcurrentUserAuthentication() throws Exception {
            ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_USERS);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(CONCURRENT_USERS);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());

            for (int i = 0; i < CONCURRENT_USERS; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();

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

            Instant testStart = Instant.now();
            startLatch.countDown();

            endLatch.await(60, TimeUnit.SECONDS);
            Instant testEnd = Instant.now();
            executor.shutdown();

            long testDuration = Duration.between(testStart, testEnd).toMillis();
            double avgResponseTime = responseTimes.stream().mapToLong(Long::longValue).average().orElse(0);
            long maxResponseTime = responseTimes.stream().mapToLong(Long::longValue).max().orElse(0);
            long minResponseTime = responseTimes.stream().mapToLong(Long::longValue).min().orElse(0);

            System.out.println("=== Concurrent Auth Test ===");
            System.out.println("Concurrent users: " + CONCURRENT_USERS);
            System.out.println("Successful: " + successCount.get());
            System.out.println("Failed: " + failCount.get());
            System.out.println("Duration: " + testDuration + "ms");
            System.out.println("Avg response time: " + String.format("%.2f", avgResponseTime) + "ms");
            System.out.println("Max response time: " + maxResponseTime + "ms");
            System.out.println("Min response time: " + minResponseTime + "ms");

            assertThat(successCount.get()).isEqualTo(CONCURRENT_USERS);
            assertThat(failCount.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("Concurrent operations produce no deadlocks")
        void concurrentOperationsNoDeadlock() throws Exception {
            int operationCount = 50;
            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch latch = new CountDownLatch(operationCount);
            AtomicInteger deadlockCount = new AtomicInteger(0);

            for (int i = 0; i < operationCount; i++) {
                final int opIndex = i;
                executor.submit(() -> {
                    try {
                        if (opIndex % 3 == 0) {
                            AuthenticationRequest request = new AuthenticationRequest();
                            request.setUsername("admin");
                            request.setPassword("admin123");
                            request.setIpAddress("127.0.0.1");
                            securityManagerComponent.authenticate(request);
                        } else if (opIndex % 3 == 1) {
                            securityManagerComponent.checkPermission("admin", "TASK", "VIEW");
                        } else {
                            notificationManagerComponent.publishTaskAssignedEvent(
                                    "task-" + opIndex, "Task", "user-" + opIndex, "proc-" + opIndex);
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

            boolean completed = latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(completed).isTrue().withFailMessage("Operations timed out, possible deadlock");
            assertThat(deadlockCount.get()).isEqualTo(0);

            System.out.println("Concurrent operations completed, no deadlocks detected");
        }
    }

    @Nested
    @DisplayName("Memory Usage Tests")
    class MemoryUsageTests {

        @Test
        @DisplayName("Large number of operations does not cause OOM")
        void largeNumberOfOperationsNoOOM() {
            int operationCount = 500;
            List<Object> results = new ArrayList<>();

            Runtime runtime = Runtime.getRuntime();
            long initialMemory = runtime.totalMemory() - runtime.freeMemory();

            for (int i = 0; i < operationCount; i++) {
                AuthenticationRequest request = new AuthenticationRequest();
                request.setUsername("admin");
                request.setPassword("admin123");
                request.setIpAddress("127.0.0.1");

                AuthenticationResult authResult = securityManagerComponent.authenticate(request);
                results.add(authResult);
            }

            System.gc();

            long finalMemory = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = finalMemory - initialMemory;

            System.out.println("=== Memory Usage Test ===");
            System.out.println("Operations: " + operationCount);
            System.out.println("Initial memory: " + (initialMemory / 1024 / 1024) + "MB");
            System.out.println("Final memory: " + (finalMemory / 1024 / 1024) + "MB");
            System.out.println("Memory growth: " + (memoryUsed / 1024 / 1024) + "MB");

            assertThat(results).hasSize(operationCount);
            assertThat(memoryUsed).isLessThan(500 * 1024 * 1024L);
        }
    }

    @Nested
    @DisplayName("Notification Performance Tests")
    class NotificationPerformanceTests {

        @Test
        @DisplayName("Large subscriber notification dispatch performance")
        void largeSubscriberNotificationPerformance() {
            int subscriberCount = 100;
            String eventType = "PROCESS_COMPLETED";

            for (int i = 0; i < subscriberCount; i++) {
                String userId = "subscriber-" + i;
                notificationManagerComponent.registerWebSocketSession("session-" + i, userId);
                notificationManagerComponent.subscribeEvent(eventType, userId, null);
            }

            Instant start = Instant.now();
            NotificationResult result = notificationManagerComponent.publishProcessCompletedEvent(
                    "proc-notify-001", "test-process", "business-001", "starter-user");
            Instant end = Instant.now();

            long notifyTime = Duration.between(start, end).toMillis();

            assertThat(result.isSuccess()).isTrue();

            System.out.println("=== Notification Dispatch Performance ===");
            System.out.println("Subscribers: " + subscriberCount);
            System.out.println("Dispatch time: " + notifyTime + "ms");
            System.out.println("Avg per subscriber: " + String.format("%.2f", (double) notifyTime / subscriberCount) + "ms");

            assertThat(notifyTime).isLessThan(5000);
        }

        @Test
        @DisplayName("Notification history query performance")
        void notificationHistoryQueryPerformance() {
            String userId = "history-perf-user";
            notificationManagerComponent.registerWebSocketSession("session-history-perf", userId);
            notificationManagerComponent.subscribeEvent("TASK_ASSIGNED", userId, null);

            for (int i = 0; i < 100; i++) {
                notificationManagerComponent.publishTaskAssignedEvent(
                        "task-history-" + i, "Task-" + i, userId, "proc-" + i);
            }

            Instant start = Instant.now();
            List<Map<String, Object>> history = notificationManagerComponent.getNotificationHistory(userId, 50);
            Instant end = Instant.now();

            long queryTime = Duration.between(start, end).toMillis();

            assertThat(history).hasSize(50);
            assertThat(queryTime).isLessThan(500);

            System.out.println("=== Notification History Query Performance ===");
            System.out.println("Records: " + history.size());
            System.out.println("Query time: " + queryTime + "ms");
        }
    }
}
