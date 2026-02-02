package com.admin.properties;

import com.admin.dto.request.TaskClaimRequest;
import com.platform.security.entity.VirtualGroup;
import com.admin.entity.VirtualGroupTaskHistory;
import com.admin.enums.TaskActionType;
import com.admin.enums.VirtualGroupType;
import com.admin.util.EntityTypeConverter;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.repository.VirtualGroupTaskHistoryRepository;
import com.admin.service.impl.VirtualGroupTaskServiceImpl;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 属性 8: 虚拟组任务认领状态一致性
 * 当虚拟组成员认领任务后，该任务应该变为直接任务，不再对其他组成员可见
 * 
 * 验证需求: 需求 4.6
 */
public class VirtualGroupTaskClaimProperties {
    
    private VirtualGroupRepository virtualGroupRepository;
    private VirtualGroupMemberRepository virtualGroupMemberRepository;
    private VirtualGroupTaskHistoryRepository taskHistoryRepository;
    private com.admin.helper.VirtualGroupHelper virtualGroupHelper;
    private VirtualGroupTaskServiceImpl taskService;
    
    @BeforeTry
    void setUp() {
        virtualGroupRepository = mock(VirtualGroupRepository.class);
        virtualGroupMemberRepository = mock(VirtualGroupMemberRepository.class);
        taskHistoryRepository = mock(VirtualGroupTaskHistoryRepository.class);
        virtualGroupHelper = mock(com.admin.helper.VirtualGroupHelper.class);
        
        // Mock virtualGroupHelper to return true for valid groups by default
        when(virtualGroupHelper.isValid(any(VirtualGroup.class))).thenAnswer(invocation -> {
            VirtualGroup group = invocation.getArgument(0);
            return group != null && "ACTIVE".equals(group.getStatus());
        });
        
        taskService = new VirtualGroupTaskServiceImpl(
                virtualGroupRepository,
                virtualGroupMemberRepository,
                taskHistoryRepository,
                virtualGroupHelper);
    }
    
    // ==================== 属性测试 ====================
    
    /**
     * 功能: admin-center, 属性 8: 虚拟组任务认领状态一致性
     * 组成员认领任务后，应该记录认领历史
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 8: 任务认领应该记录历史")
    void taskClaimShouldRecordHistory(
            @ForAll("validGroupIds") String groupId,
            @ForAll("validUserIds") String userId,
            @ForAll("validTaskIds") String taskId) {
        
        // Given: 创建有效的虚拟组
        VirtualGroup group = createValidVirtualGroup(groupId);
        when(virtualGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        
        // Given: 用户是组成员
        when(virtualGroupMemberRepository.existsByVirtualGroupIdAndUserId(groupId, userId))
                .thenReturn(true);
        
        // Given: 任务未被认领
        when(taskHistoryRepository.findClaimHistoryByTaskId(taskId))
                .thenReturn(Collections.emptyList());
        
        // Given: 保存历史记录
        when(taskHistoryRepository.save(any(VirtualGroupTaskHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: 认领任务
        TaskClaimRequest request = new TaskClaimRequest();
        request.setTaskId(taskId);
        request.setGroupId(groupId);
        request.setComment("Test claim");
        
        taskService.claimTask(userId, request);
        
        // Then: 应该保存认领历史
        ArgumentCaptor<VirtualGroupTaskHistory> historyCaptor = 
                ArgumentCaptor.forClass(VirtualGroupTaskHistory.class);
        verify(taskHistoryRepository).save(historyCaptor.capture());
        
        VirtualGroupTaskHistory savedHistory = historyCaptor.getValue();
        assertThat(savedHistory.getTaskId()).isEqualTo(taskId);
        assertThat(savedHistory.getGroupId()).isEqualTo(groupId);
        assertThat(savedHistory.getToUserId()).isEqualTo(userId);
        assertThat(savedHistory.getActionType()).isEqualTo(TaskActionType.CLAIMED);
    }
    
    /**
     * 功能: admin-center, 属性 8: 虚拟组任务认领状态一致性
     * 非组成员不能认领组任务
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 8: 非组成员不能认领组任务")
    void nonMemberCannotClaimGroupTask(
            @ForAll("validGroupIds") String groupId,
            @ForAll("validUserIds") String userId,
            @ForAll("validTaskIds") String taskId) {
        
        // Given: 创建有效的虚拟组
        VirtualGroup group = createValidVirtualGroup(groupId);
        when(virtualGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        
        // Given: 用户不是组成员
        when(virtualGroupMemberRepository.existsByVirtualGroupIdAndUserId(groupId, userId))
                .thenReturn(false);
        
        // When: 检查是否可以认领
        boolean canClaim = taskService.canUserClaimTask(userId, taskId, groupId);
        
        // Then: 不应该能够认领
        assertThat(canClaim).isFalse();
    }
    
    /**
     * 功能: admin-center, 属性 8: 虚拟组任务认领状态一致性
     * 过期虚拟组的任务不能被认领
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 8: 过期虚拟组的任务不能被认领")
    void expiredGroupTaskCannotBeClaimed(
            @ForAll("validGroupIds") String groupId,
            @ForAll("validUserIds") String userId,
            @ForAll("validTaskIds") String taskId) {
        
        // Given: 创建已过期的虚拟组
        VirtualGroup group = createExpiredVirtualGroup(groupId);
        when(virtualGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        
        // Given: 用户是组成员
        when(virtualGroupMemberRepository.existsByVirtualGroupIdAndUserId(groupId, userId))
                .thenReturn(true);
        
        // When: 检查是否可以认领
        boolean canClaim = taskService.canUserClaimTask(userId, taskId, groupId);
        
        // Then: 不应该能够认领
        assertThat(canClaim).isFalse();
    }
    
    /**
     * 功能: admin-center, 属性 8: 虚拟组任务认领状态一致性
     * 已被认领的任务不能再次被认领
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 8: 已认领的任务不能再次被认领")
    void claimedTaskCannotBeClaimedAgain(
            @ForAll("validGroupIds") String groupId,
            @ForAll("validUserIds") String userId,
            @ForAll("validUserIds") String anotherUserId,
            @ForAll("validTaskIds") String taskId) {
        
        // Given: 创建有效的虚拟组
        VirtualGroup group = createValidVirtualGroup(groupId);
        when(virtualGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        
        // Given: 用户是组成员
        when(virtualGroupMemberRepository.existsByVirtualGroupIdAndUserId(groupId, userId))
                .thenReturn(true);
        
        // Given: 任务已被其他用户认领
        VirtualGroupTaskHistory existingClaim = VirtualGroupTaskHistory.builder()
                .id(UUID.randomUUID().toString())
                .taskId(taskId)
                .groupId(groupId)
                .actionType(TaskActionType.CLAIMED)
                .toUserId(anotherUserId)
                .createdAt(Instant.now().minusSeconds(60))
                .build();
        when(taskHistoryRepository.findClaimHistoryByTaskId(taskId))
                .thenReturn(Collections.singletonList(existingClaim));
        
        // When: 检查是否可以认领
        boolean canClaim = taskService.canUserClaimTask(userId, taskId, groupId);
        
        // Then: 不应该能够认领
        assertThat(canClaim).isFalse();
    }

    
    /**
     * 功能: admin-center, 属性 8: 虚拟组任务认领状态一致性
     * 有效组成员应该能够认领未被认领的任务
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 8: 有效组成员可以认领未被认领的任务")
    void validMemberCanClaimUnclaimedTask(
            @ForAll("validGroupIds") String groupId,
            @ForAll("validUserIds") String userId,
            @ForAll("validTaskIds") String taskId) {
        
        // Given: 创建有效的虚拟组
        VirtualGroup group = createValidVirtualGroup(groupId);
        when(virtualGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        
        // Given: 用户是组成员
        when(virtualGroupMemberRepository.existsByVirtualGroupIdAndUserId(groupId, userId))
                .thenReturn(true);
        
        // Given: 任务未被认领
        when(taskHistoryRepository.findClaimHistoryByTaskId(taskId))
                .thenReturn(Collections.emptyList());
        
        // When: 检查是否可以认领
        boolean canClaim = taskService.canUserClaimTask(userId, taskId, groupId);
        
        // Then: 应该能够认领
        assertThat(canClaim).isTrue();
    }
    
    /**
     * 功能: admin-center, 属性 8: 虚拟组任务认领状态一致性
     * 认领操作应该是幂等的（同一用户多次认领同一任务应该失败）
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 8: 同一用户不能重复认领同一任务")
    void sameUserCannotClaimSameTaskTwice(
            @ForAll("validGroupIds") String groupId,
            @ForAll("validUserIds") String userId,
            @ForAll("validTaskIds") String taskId) {
        
        // Given: 创建有效的虚拟组
        VirtualGroup group = createValidVirtualGroup(groupId);
        when(virtualGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        
        // Given: 用户是组成员
        when(virtualGroupMemberRepository.existsByVirtualGroupIdAndUserId(groupId, userId))
                .thenReturn(true);
        
        // Given: 任务已被该用户认领
        VirtualGroupTaskHistory existingClaim = VirtualGroupTaskHistory.builder()
                .id(UUID.randomUUID().toString())
                .taskId(taskId)
                .groupId(groupId)
                .actionType(TaskActionType.CLAIMED)
                .toUserId(userId)
                .createdAt(Instant.now().minusSeconds(60))
                .build();
        when(taskHistoryRepository.findClaimHistoryByTaskId(taskId))
                .thenReturn(Collections.singletonList(existingClaim));
        
        // When: 检查是否可以再次认领
        boolean canClaim = taskService.canUserClaimTask(userId, taskId, groupId);
        
        // Then: 不应该能够再次认领
        assertThat(canClaim).isFalse();
    }
    
    /**
     * 功能: admin-center, 属性 8: 虚拟组任务认领状态一致性
     * 不存在的虚拟组的任务不能被认领
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 8: 不存在的虚拟组的任务不能被认领")
    void nonExistentGroupTaskCannotBeClaimed(
            @ForAll("validGroupIds") String groupId,
            @ForAll("validUserIds") String userId,
            @ForAll("validTaskIds") String taskId) {
        
        // Given: 虚拟组不存在
        when(virtualGroupRepository.findById(groupId)).thenReturn(Optional.empty());
        
        // When: 检查是否可以认领
        boolean canClaim = taskService.canUserClaimTask(userId, taskId, groupId);
        
        // Then: 不应该能够认领
        assertThat(canClaim).isFalse();
    }
    
    /**
     * 功能: admin-center, 属性 8: 虚拟组任务认领状态一致性
     * 认领任务时应该抛出异常如果用户无法认领
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 8: 无法认领时应该抛出异常")
    void claimTaskShouldThrowExceptionWhenCannotClaim(
            @ForAll("validGroupIds") String groupId,
            @ForAll("validUserIds") String userId,
            @ForAll("validTaskIds") String taskId) {
        
        // Given: 创建有效的虚拟组
        VirtualGroup group = createValidVirtualGroup(groupId);
        when(virtualGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        
        // Given: 用户不是组成员
        when(virtualGroupMemberRepository.existsByVirtualGroupIdAndUserId(groupId, userId))
                .thenReturn(false);
        
        // When & Then: 认领应该抛出异常
        TaskClaimRequest request = new TaskClaimRequest();
        request.setTaskId(taskId);
        request.setGroupId(groupId);
        
        assertThatThrownBy(() -> taskService.claimTask(userId, request))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("无法认领");
    }
    
    /**
     * 功能: admin-center, 属性 8: 虚拟组任务认领状态一致性
     * 认领历史应该包含正确的时间戳
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 8: 认领历史应该包含正确的时间戳")
    void claimHistoryShouldHaveCorrectTimestamp(
            @ForAll("validGroupIds") String groupId,
            @ForAll("validUserIds") String userId,
            @ForAll("validTaskIds") String taskId) {
        
        // Given: 创建有效的虚拟组
        VirtualGroup group = createValidVirtualGroup(groupId);
        when(virtualGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        
        // Given: 用户是组成员
        when(virtualGroupMemberRepository.existsByVirtualGroupIdAndUserId(groupId, userId))
                .thenReturn(true);
        
        // Given: 任务未被认领
        when(taskHistoryRepository.findClaimHistoryByTaskId(taskId))
                .thenReturn(Collections.emptyList());
        
        // Given: 保存历史记录
        when(taskHistoryRepository.save(any(VirtualGroupTaskHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        Instant beforeClaim = Instant.now();
        
        // When: 认领任务
        TaskClaimRequest request = new TaskClaimRequest();
        request.setTaskId(taskId);
        request.setGroupId(groupId);
        
        taskService.claimTask(userId, request);
        
        Instant afterClaim = Instant.now();
        
        // Then: 历史记录的时间戳应该在认领前后之间
        ArgumentCaptor<VirtualGroupTaskHistory> historyCaptor = 
                ArgumentCaptor.forClass(VirtualGroupTaskHistory.class);
        verify(taskHistoryRepository).save(historyCaptor.capture());
        
        VirtualGroupTaskHistory savedHistory = historyCaptor.getValue();
        assertThat(savedHistory.getCreatedAt())
                .isAfterOrEqualTo(beforeClaim)
                .isBeforeOrEqualTo(afterClaim);
    }
    
    // ==================== 辅助方法 ====================
    
    private VirtualGroup createValidVirtualGroup(String groupId) {
        return VirtualGroup.builder()
                .id(groupId)
                .name("Test Group " + groupId)
                .type(EntityTypeConverter.fromVirtualGroupType(VirtualGroupType.CUSTOM))
                .status("ACTIVE")
                .build();
    }
    
    private VirtualGroup createExpiredVirtualGroup(String groupId) {
        return VirtualGroup.builder()
                .id(groupId)
                .name("Expired Group " + groupId)
                .type(EntityTypeConverter.fromVirtualGroupType(VirtualGroupType.CUSTOM))
                .status("INACTIVE")
                .build();
    }
    
    // ==================== 数据生成器 ====================
    
    @Provide
    Arbitrary<String> validGroupIds() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(5)
                .ofMaxLength(20)
                .map(s -> "group-" + s.toLowerCase());
    }
    
    @Provide
    Arbitrary<String> validUserIds() {
        return Arbitraries.create(UUID::randomUUID).map(UUID::toString);
    }
    
    @Provide
    Arbitrary<String> validTaskIds() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(5)
                .ofMaxLength(20)
                .map(s -> "task-" + s.toLowerCase());
    }
}
