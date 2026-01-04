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

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 端到端集成测试
 * 测试完整的业务流程从开始到结束
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("端到端集成测试")
class EndToEndIntegrationTest {

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

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        // 设置Redis模拟
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        
        // 初始化组件
        notificationManagerComponent = new NotificationManagerComponent(eventPublisher, stringRedisTemplate, objectMapper);
        securityManagerComponent = new SecurityManagerComponent(stringRedisTemplate, objectMapper, auditManagerComponent);
        securityManagerComponent.initializeDefaultRolePermissions();
    }

    @Nested
    @DisplayName("完整请假流程测试")
    class LeaveRequestFlowTests {

        @Test
        @DisplayName("请假申请从提交到审批完成")
        void leaveRequestFromSubmitToApproval() {
            // ========== 阶段1: 用户认证 ==========
            String applicant = "user";
            String manager = "admin";
            
            AuthenticationRequest authRequest = new AuthenticationRequest();
            authRequest.setUsername(applicant);
            authRequest.setPassword("user123");
            authRequest.setIpAddress("127.0.0.1");
            
            AuthenticationResult authResult = securityManagerComponent.authenticate(authRequest);
            assertThat(authResult.isSuccess()).isTrue();
            String token = authResult.getAccessToken();
            assertThat(token).isNotNull();
            
            // ========== 阶段2: 发送通知给审批人 ==========
            String processInstanceId = "proc-inst-001";
            String processDefinitionKey = "leave-request";
            String businessKey = "LEAVE-2024-001";
            
            notificationManagerComponent.registerWebSocketSession("session-mgr", manager);
            notificationManagerComponent.subscribeEvent("TASK_ASSIGNED", manager, null);
            
            NotificationResult taskNotify = notificationManagerComponent.publishTaskAssignedEvent(
                    "task-001", "请假审批", manager, processInstanceId);
            assertThat(taskNotify.isSuccess()).isTrue();
            
            // ========== 阶段3: 经理审批任务 ==========
            AuthenticationRequest mgrRequest = new AuthenticationRequest();
            mgrRequest.setUsername(manager);
            mgrRequest.setPassword("admin123");
            mgrRequest.setIpAddress("127.0.0.1");
            
            AuthenticationResult mgrAuth = securityManagerComponent.authenticate(mgrRequest);
            assertThat(mgrAuth.isSuccess()).isTrue();
            
            // ========== 阶段4: 发送审批结果通知 ==========
            notificationManagerComponent.registerWebSocketSession("session-emp", applicant);
            notificationManagerComponent.subscribeEvent("PROCESS_COMPLETED", applicant, null);
            
            NotificationResult completeNotify = notificationManagerComponent.publishProcessCompletedEvent(
                    processInstanceId, processDefinitionKey, businessKey, applicant);
            assertThat(completeNotify.isSuccess()).isTrue();
            
            // 验证通知历史
            List<Map<String, Object>> history = notificationManagerComponent.getNotificationHistory(applicant, 10);
            assertThat(history).isNotEmpty();
        }

        @Test
        @DisplayName("请假申请被拒绝流程")
        void leaveRequestRejected() {
            // Given
            String applicant = "user";
            
            // 发送拒绝通知
            notificationManagerComponent.registerWebSocketSession("session-emp2", applicant);
            NotificationResult rejectNotify = notificationManagerComponent.sendInAppNotification(
                    applicant, "请假申请被拒绝", "您的请假申请已被拒绝，原因：请假天数过长，请重新申请", "LEAVE_REJECTED");
            assertThat(rejectNotify.isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("多用户并发操作测试")
    class ConcurrentOperationTests {

        @Test
        @DisplayName("多用户同时登录")
        void multipleUsersLoginConcurrently() throws Exception {
            // Given
            int userCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(userCount);
            CountDownLatch latch = new CountDownLatch(userCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            
            // When - 并发登录（使用默认用户）
            List<Future<AuthenticationResult>> futures = new ArrayList<>();
            for (int i = 0; i < userCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        AuthenticationRequest request = new AuthenticationRequest();
                        request.setUsername("user");
                        request.setPassword("user123");
                        request.setIpAddress("127.0.0.1");
                        
                        AuthenticationResult result = securityManagerComponent.authenticate(request);
                        if (result.isSuccess()) {
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                        }
                        return result;
                    } finally {
                        latch.countDown();
                    }
                }));
            }
            
            // 等待所有任务完成
            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();
            
            // Then
            assertThat(successCount.get()).isEqualTo(userCount);
            assertThat(failCount.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("多用户同时发送通知")
        void multipleUsersSendNotificationsConcurrently() throws Exception {
            // Given
            int notificationCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch latch = new CountDownLatch(notificationCount);
            AtomicInteger successCount = new AtomicInteger(0);
            
            // 注册会话
            for (int i = 0; i < notificationCount; i++) {
                notificationManagerComponent.registerWebSocketSession("session-" + i, "user-" + i);
                notificationManagerComponent.subscribeEvent("TASK_ASSIGNED", "user-" + i, null);
            }
            
            // When - 并发发送通知
            for (int i = 0; i < notificationCount; i++) {
                final int notifyIndex = i;
                executor.submit(() -> {
                    try {
                        NotificationResult result = notificationManagerComponent.publishTaskAssignedEvent(
                                "task-" + notifyIndex,
                                "任务-" + notifyIndex,
                                "user-" + notifyIndex,
                                "proc-inst-" + notifyIndex
                        );
                        if (result.isSuccess()) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();
            
            // Then
            assertThat(successCount.get()).isEqualTo(notificationCount);
        }
    }

    @Nested
    @DisplayName("异常场景处理测试")
    class ExceptionScenarioTests {

        @Test
        @DisplayName("认证失败后的安全事件记录")
        void authenticationFailureSecurityEvent() {
            // Given
            String username = "hacker";
            String wrongPassword = "wrong-password";
            
            // When - 多次认证失败
            int failCount = 0;
            for (int i = 0; i < 3; i++) {
                AuthenticationRequest request = new AuthenticationRequest();
                request.setUsername(username);
                request.setPassword(wrongPassword);
                request.setIpAddress("127.0.0.1");
                
                AuthenticationResult result = securityManagerComponent.authenticate(request);
                if (!result.isSuccess()) {
                    failCount++;
                }
            }
            
            // Then - 验证认证失败
            assertThat(failCount).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("数据完整性测试")
    class DataIntegrityTests {

        @Test
        @DisplayName("通知历史记录完整性")
        void notificationHistoryIntegrity() {
            // Given
            String userId = "user-history-001";
            notificationManagerComponent.registerWebSocketSession("session-history", userId);
            notificationManagerComponent.subscribeEvent("PROCESS_STARTED", userId, null);
            notificationManagerComponent.subscribeEvent("TASK_ASSIGNED", userId, null);
            notificationManagerComponent.subscribeEvent("PROCESS_COMPLETED", userId, null);
            
            // When - 发送多个通知
            notificationManagerComponent.publishProcessStartedEvent(
                    "proc-001", "test-process", "business-001", userId);
            notificationManagerComponent.publishTaskAssignedEvent(
                    "task-001", "测试任务", userId, "proc-001");
            notificationManagerComponent.publishProcessCompletedEvent(
                    "proc-001", "test-process", "business-001", userId);
            
            // Then - 验证历史记录
            List<Map<String, Object>> history = notificationManagerComponent.getNotificationHistory(userId, 10);
            assertThat(history).hasSize(3);
            
            // 验证按时间倒序
            for (Map<String, Object> record : history) {
                assertThat(record.get("userId")).isEqualTo(userId);
                assertThat(record.get("sentTime")).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("权限控制测试")
    class PermissionControlTests {

        @Test
        @DisplayName("角色权限正确检查")
        void rolePermissionCheck() {
            // Given - 使用默认用户
            String adminUser = "admin";
            String normalUser = "user";
            
            // When - 检查权限
            PermissionCheckResult adminCheck = securityManagerComponent.checkPermission(
                    adminUser, "SYSTEM", "MANAGE");
            PermissionCheckResult userCheck = securityManagerComponent.checkPermission(
                    normalUser, "SYSTEM", "MANAGE");
            PermissionCheckResult userTaskCheck = securityManagerComponent.checkPermission(
                    normalUser, "TASK", "VIEW");
            
            // Then
            assertThat(adminCheck.isAllowed()).isTrue(); // 管理员有所有权限
            assertThat(userCheck.isAllowed()).isFalse(); // 普通用户没有系统管理权限
            assertThat(userTaskCheck.isAllowed()).isTrue(); // 普通用户有任务查看权限
        }
    }
}
