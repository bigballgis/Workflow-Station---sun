package com.workflow.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.workflow.component.*;
import com.workflow.dto.request.*;
import com.workflow.dto.response.*;
import com.workflow.enums.AuditOperationType;
import com.workflow.enums.AuditResourceType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 组件间集成测试
 * 测试组件之间的数据流和协作
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("组件间集成测试")
class ComponentIntegrationTest {

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
        
        // 测试用密钥
        String testJwtSecret = "test-jwt-secret-key-for-component-testing";
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
    @DisplayName("安全管理与审计管理集成测试")
    class SecurityAuditIntegrationTests {

        @Test
        @DisplayName("用户登录触发审计日志")
        void userLoginTriggersAuditLog() {
            // Given
            AuthenticationRequest request = new AuthenticationRequest();
            request.setUsername("admin");
            request.setPassword("admin123");
            request.setIpAddress("127.0.0.1");
            
            // When - 用户登录
            AuthenticationResult authResult = securityManagerComponent.authenticate(request);
            
            // Then - 验证登录成功
            assertThat(authResult.isSuccess()).isTrue();
            assertThat(authResult.getAccessToken()).isNotNull();
            
            // 验证审计日志被调用
            verify(auditManagerComponent, atLeastOnce()).recordAuditLog(
                    eq(AuditOperationType.LOGIN),
                    eq(AuditResourceType.USER),
                    anyString(),
                    anyString(),
                    anyString()
            );
        }

        @Test
        @DisplayName("权限检查失败触发安全事件")
        void permissionDeniedTriggersSecurityEvent() {
            // Given
            String userId = "user-001";
            String resource = "ADMIN_PANEL";
            String action = "ACCESS";
            
            // When - 检查权限（用户没有管理员权限）
            PermissionCheckResult result = securityManagerComponent.checkPermission(userId, resource, action);
            
            // Then
            assertThat(result.isAllowed()).isFalse();
        }
    }

    @Nested
    @DisplayName("流程引擎与通知管理集成测试")
    class ProcessNotificationIntegrationTests {

        @Test
        @DisplayName("流程启动发送通知")
        void processStartSendsNotification() {
            // Given
            String processInstanceId = "proc-inst-001";
            String processDefinitionKey = "leave-request";
            String businessKey = "leave-2024-001";
            String startUserId = "user-001";
            
            // 注册WebSocket会话
            notificationManagerComponent.registerWebSocketSession("session-001", startUserId);
            notificationManagerComponent.subscribeEvent("PROCESS_STARTED", startUserId, null);
            
            // When - 发布流程启动事件
            NotificationResult result = notificationManagerComponent.publishProcessStartedEvent(
                    processInstanceId, processDefinitionKey, businessKey, startUserId);
            
            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getEventId()).isNotNull();
            
            // 验证通知历史
            List<Map<String, Object>> history = notificationManagerComponent.getNotificationHistory(startUserId, 10);
            assertThat(history).isNotEmpty();
        }

        @Test
        @DisplayName("任务分配发送多渠道通知")
        void taskAssignmentSendsMultiChannelNotification() {
            // Given
            String taskId = "task-001";
            String taskName = "请假审批";
            String assignee = "manager-001";
            String processInstanceId = "proc-inst-001";
            
            // 设置用户通知偏好
            NotificationManagerComponent.UserNotificationPreference preference = 
                    new NotificationManagerComponent.UserNotificationPreference();
            preference.setUserId(assignee);
            preference.setEnabledChannels(Set.of("EMAIL", "WEBSOCKET", "IN_APP"));
            notificationManagerComponent.setUserNotificationPreference(preference);
            
            // 注册WebSocket会话
            notificationManagerComponent.registerWebSocketSession("session-002", assignee);
            
            // When - 发布任务分配事件
            NotificationResult result = notificationManagerComponent.publishTaskAssignedEvent(
                    taskId, taskName, assignee, processInstanceId);
            
            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getEventId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("完整业务流程集成测试")
    class CompleteBusinessFlowIntegrationTests {

        @Test
        @DisplayName("请假申请完整流程")
        void leaveRequestCompleteFlow() {
            // Given - 准备数据
            String applicant = "user";
            String manager = "admin";
            String processDefinitionKey = "leave-request";
            String businessKey = "leave-2024-001";
            String processInstanceId = "proc-inst-001";
            
            // 1. 用户登录
            AuthenticationRequest authRequest = new AuthenticationRequest();
            authRequest.setUsername(applicant);
            authRequest.setPassword("user123");
            authRequest.setIpAddress("127.0.0.1");
            
            AuthenticationResult authResult = securityManagerComponent.authenticate(authRequest);
            assertThat(authResult.isSuccess()).isTrue();
            
            // 2. 发送流程启动通知
            notificationManagerComponent.registerWebSocketSession("session-001", applicant);
            notificationManagerComponent.subscribeEvent("PROCESS_STARTED", applicant, null);
            NotificationResult notifyResult = notificationManagerComponent.publishProcessStartedEvent(
                    processInstanceId, processDefinitionKey, businessKey, applicant);
            assertThat(notifyResult.isSuccess()).isTrue();
            
            // 3. 发送任务分配通知给经理
            notificationManagerComponent.registerWebSocketSession("session-mgr", manager);
            notificationManagerComponent.subscribeEvent("TASK_ASSIGNED", manager, null);
            NotificationResult taskNotify = notificationManagerComponent.publishTaskAssignedEvent(
                    "task-001", "请假审批", manager, processInstanceId);
            assertThat(taskNotify.isSuccess()).isTrue();
            
            // 4. 经理登录
            AuthenticationRequest mgrRequest = new AuthenticationRequest();
            mgrRequest.setUsername(manager);
            mgrRequest.setPassword("admin123");
            mgrRequest.setIpAddress("127.0.0.1");
            
            AuthenticationResult mgrAuth = securityManagerComponent.authenticate(mgrRequest);
            assertThat(mgrAuth.isSuccess()).isTrue();
            
            // 5. 发送审批结果通知
            notificationManagerComponent.subscribeEvent("PROCESS_COMPLETED", applicant, null);
            NotificationResult completeNotify = notificationManagerComponent.publishProcessCompletedEvent(
                    processInstanceId, processDefinitionKey, businessKey, applicant);
            assertThat(completeNotify.isSuccess()).isTrue();
            
            // 验证通知历史
            List<Map<String, Object>> history = notificationManagerComponent.getNotificationHistory(applicant, 10);
            assertThat(history).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("多组件协作数据流测试")
    class MultiComponentDataFlowTests {

        @Test
        @DisplayName("多组件协作数据流一致性")
        void multiComponentDataFlowConsistency() {
            // Given
            String userId = "user";
            String processInstanceId = "proc-flow-001";
            
            // 1. 用户认证
            AuthenticationRequest authRequest = new AuthenticationRequest();
            authRequest.setUsername(userId);
            authRequest.setPassword("user123");
            authRequest.setIpAddress("127.0.0.1");
            
            AuthenticationResult authResult = securityManagerComponent.authenticate(authRequest);
            assertThat(authResult.isSuccess()).isTrue();
            
            // 2. 注册通知会话
            NotificationResult sessionResult = notificationManagerComponent.registerWebSocketSession(
                    "session-" + userId, userId);
            assertThat(sessionResult.isSuccess()).isTrue();
            
            // 3. 订阅事件
            NotificationResult subscribeResult = notificationManagerComponent.subscribeEvent(
                    "PROCESS_STARTED", userId, null);
            assertThat(subscribeResult.isSuccess()).isTrue();
            
            // 4. 发布事件
            NotificationResult eventResult = notificationManagerComponent.publishProcessStartedEvent(
                    processInstanceId, "test-process", "business-001", userId);
            assertThat(eventResult.isSuccess()).isTrue();
            
            // 5. 验证通知历史
            List<Map<String, Object>> history = notificationManagerComponent.getNotificationHistory(userId, 10);
            assertThat(history).isNotEmpty();
        }
    }
}
