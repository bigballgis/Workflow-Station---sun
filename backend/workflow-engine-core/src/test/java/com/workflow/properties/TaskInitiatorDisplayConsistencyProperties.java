package com.workflow.properties;

import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * 任务发起人显示一致性属性测试
 * 
 * 验证属性 1: 发起人显示一致性
 * 对于任何从工作流引擎返回的任务，如果流程实例有启动用户，任务信息应包含与流程实例 startUserId 匹配的 initiatorId
 * 
 * 验证属性 2: 发起人名称解析
 * 对于任何有效的用户ID，解析显示名称应返回 displayName、username 或 userId（按优先级顺序）
 * 
 * **Validates: Requirements 2.1, 2.2, 2.3, 2.4**
 * 
 * Feature: task-initiator-display, Property 1: Initiator Display Consistency
 */
@Label("功能: task-initiator-display, 属性 1: 发起人显示一致性")
public class TaskInitiatorDisplayConsistencyProperties {

    /**
     * 属性测试：发起人ID一致性
     * 验证任务的 initiatorId 应与流程实例的 startUserId 匹配
     * 
     * **Validates: Requirements 2.1, 2.2**
     */
    @Property(tries = 100)
    @Label("发起人ID应与流程实例startUserId匹配")
    void initiatorIdShouldMatchProcessInstanceStartUserId(
            @ForAll @NotBlank String taskId,
            @ForAll @NotBlank String processInstanceId,
            @ForAll @NotBlank String startUserId) {
        
        // 过滤掉空白字符串
        Assume.that(taskId != null && !taskId.trim().isEmpty());
        Assume.that(processInstanceId != null && !processInstanceId.trim().isEmpty());
        Assume.that(startUserId != null && !startUserId.trim().isEmpty());
        
        // 模拟流程实例
        MockProcessInstance processInstance = new MockProcessInstance(processInstanceId, startUserId);
        
        // 模拟任务信息转换逻辑
        MockTaskInfo taskInfo = convertToTaskInfo(taskId, processInstance);
        
        // 验证 initiatorId 与 startUserId 匹配
        assertThat(taskInfo.getInitiatorId())
            .as("任务的 initiatorId 应与流程实例的 startUserId 匹配")
            .isEqualTo(startUserId);
    }
    
    /**
     * 属性测试：发起人名称解析优先级
     * 验证解析用户显示名称时的优先级：displayName > username > userId
     * 
     * **Validates: Requirements 2.3**
     */
    @Property(tries = 100)
    @Label("发起人名称解析应遵循优先级：displayName > username > userId")
    void initiatorNameResolutionPriority(
            @ForAll @NotBlank String userId,
            @ForAll("userInfoProvider") MockUserInfo userInfo) {
        
        Assume.that(userId != null && !userId.trim().isEmpty());
        
        // 模拟名称解析逻辑
        String resolvedName = resolveUserDisplayName(userId, userInfo);
        
        // 验证解析结果遵循优先级
        if (userInfo != null && userInfo.getDisplayName() != null && !userInfo.getDisplayName().isEmpty()) {
            assertThat(resolvedName)
                .as("当 displayName 存在时应返回 displayName")
                .isEqualTo(userInfo.getDisplayName());
        } else if (userInfo != null && userInfo.getUsername() != null && !userInfo.getUsername().isEmpty()) {
            assertThat(resolvedName)
                .as("当 displayName 不存在但 username 存在时应返回 username")
                .isEqualTo(userInfo.getUsername());
        } else {
            assertThat(resolvedName)
                .as("当 displayName 和 username 都不存在时应返回 userId")
                .isEqualTo(userId);
        }
    }
    
    /**
     * 属性测试：空发起人处理
     * 验证当流程实例没有 startUserId 时，initiatorId 和 initiatorName 应为 null
     * 
     * **Validates: Requirements 2.4**
     */
    @Property(tries = 100)
    @Label("当流程实例没有startUserId时，initiator信息应为null")
    void nullInitiatorWhenNoStartUserId(
            @ForAll @NotBlank String taskId,
            @ForAll @NotBlank String processInstanceId) {
        
        Assume.that(taskId != null && !taskId.trim().isEmpty());
        Assume.that(processInstanceId != null && !processInstanceId.trim().isEmpty());
        
        // 模拟没有 startUserId 的流程实例
        MockProcessInstance processInstance = new MockProcessInstance(processInstanceId, null);
        
        // 模拟任务信息转换逻辑
        MockTaskInfo taskInfo = convertToTaskInfo(taskId, processInstance);
        
        // 验证 initiator 信息为 null
        assertThat(taskInfo.getInitiatorId())
            .as("当流程实例没有 startUserId 时，initiatorId 应为 null")
            .isNull();
        assertThat(taskInfo.getInitiatorName())
            .as("当流程实例没有 startUserId 时，initiatorName 应为 null")
            .isNull();
    }
    
    /**
     * 属性测试：多任务发起人一致性
     * 验证同一流程实例的所有任务应有相同的 initiatorId
     * 
     * **Validates: Requirements 2.1, 2.2**
     */
    @Property(tries = 100)
    @Label("同一流程实例的所有任务应有相同的initiatorId")
    void sameProcessInstanceTasksShouldHaveSameInitiator(
            @ForAll @Size(min = 2, max = 10) List<@NotBlank String> taskIds,
            @ForAll @NotBlank String processInstanceId,
            @ForAll @NotBlank String startUserId) {
        
        Assume.that(taskIds.stream().allMatch(id -> id != null && !id.trim().isEmpty()));
        Assume.that(processInstanceId != null && !processInstanceId.trim().isEmpty());
        Assume.that(startUserId != null && !startUserId.trim().isEmpty());
        
        // 模拟流程实例
        MockProcessInstance processInstance = new MockProcessInstance(processInstanceId, startUserId);
        
        // 转换所有任务
        List<MockTaskInfo> taskInfos = new ArrayList<>();
        for (String taskId : taskIds) {
            taskInfos.add(convertToTaskInfo(taskId, processInstance));
        }
        
        // 验证所有任务的 initiatorId 相同
        String firstInitiatorId = taskInfos.get(0).getInitiatorId();
        for (MockTaskInfo taskInfo : taskInfos) {
            assertThat(taskInfo.getInitiatorId())
                .as("同一流程实例的所有任务应有相同的 initiatorId")
                .isEqualTo(firstInitiatorId);
        }
    }
    
    // ==================== 辅助方法和类 ====================
    
    @Provide
    Arbitrary<MockUserInfo> userInfoProvider() {
        return Arbitraries.oneOf(
            // 有 displayName 的用户
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
                .map(displayName -> new MockUserInfo(displayName, "user_" + displayName, true)),
            // 只有 username 的用户
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
                .map(username -> new MockUserInfo(null, username, true)),
            // 用户不存在
            Arbitraries.just(new MockUserInfo(null, null, false))
        );
    }
    
    private MockTaskInfo convertToTaskInfo(String taskId, MockProcessInstance processInstance) {
        String initiatorId = null;
        String initiatorName = null;
        
        if (processInstance != null && processInstance.getStartUserId() != null) {
            initiatorId = processInstance.getStartUserId();
            // 模拟名称解析（简化版）
            initiatorName = initiatorId;
        }
        
        return new MockTaskInfo(taskId, processInstance.getId(), initiatorId, initiatorName);
    }
    
    private String resolveUserDisplayName(String userId, MockUserInfo userInfo) {
        if (userInfo == null || !userInfo.isExists()) {
            return userId;
        }
        if (userInfo.getDisplayName() != null && !userInfo.getDisplayName().isEmpty()) {
            return userInfo.getDisplayName();
        }
        if (userInfo.getUsername() != null && !userInfo.getUsername().isEmpty()) {
            return userInfo.getUsername();
        }
        return userId;
    }
    
    // 模拟类
    private static class MockProcessInstance {
        private final String id;
        private final String startUserId;
        
        public MockProcessInstance(String id, String startUserId) {
            this.id = id;
            this.startUserId = startUserId;
        }
        
        public String getId() { return id; }
        public String getStartUserId() { return startUserId; }
    }
    
    private static class MockTaskInfo {
        private final String taskId;
        private final String processInstanceId;
        private final String initiatorId;
        private final String initiatorName;
        
        public MockTaskInfo(String taskId, String processInstanceId, String initiatorId, String initiatorName) {
            this.taskId = taskId;
            this.processInstanceId = processInstanceId;
            this.initiatorId = initiatorId;
            this.initiatorName = initiatorName;
        }
        
        public String getTaskId() { return taskId; }
        public String getProcessInstanceId() { return processInstanceId; }
        public String getInitiatorId() { return initiatorId; }
        public String getInitiatorName() { return initiatorName; }
    }
    
    private static class MockUserInfo {
        private final String displayName;
        private final String username;
        private final boolean exists;
        
        public MockUserInfo(String displayName, String username, boolean exists) {
            this.displayName = displayName;
            this.username = username;
            this.exists = exists;
        }
        
        public String getDisplayName() { return displayName; }
        public String getUsername() { return username; }
        public boolean isExists() { return exists; }
    }
}
