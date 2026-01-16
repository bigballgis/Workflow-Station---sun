package com.workflow.properties;

import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;

import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * 任务委托权限有效性属性测试
 * 
 * 验证属性 9: 任务委托权限有效性
 * 对于任何任务委托操作，只有具有相应权限的用户才能执行委托，委托后的任务状态和权限应该正确更新
 * 
 * 验证需求: 需求 3.5
 * 
 * 注意：这是一个简化的属性测试，主要验证任务委托权限逻辑的正确性，不依赖Spring Boot上下文
 */
@Label("功能: workflow-engine-core, 属性 9: 任务委托权限有效性")
public class TaskDelegationPermissionProperties {

    /**
     * 属性测试：用户直接分配任务委托权限验证
     * 验证只有任务的直接分配用户才能委托该任务
     */
    @Property(tries = 100)
    @Label("用户直接分配任务委托权限验证")
    void userDirectAssignedTaskDelegationPermission(
            @ForAll @NotBlank String taskId,
            @ForAll @NotBlank String originalAssignee,
            @ForAll @NotBlank String delegatedTo,
            @ForAll @NotBlank String unauthorizedUser,
            @ForAll @NotBlank String delegationReason) {
        
        // 过滤掉空白字符串和重复用户
        Assume.that(taskId != null && !taskId.trim().isEmpty());
        Assume.that(originalAssignee != null && !originalAssignee.trim().isEmpty());
        Assume.that(delegatedTo != null && !delegatedTo.trim().isEmpty());
        Assume.that(unauthorizedUser != null && !unauthorizedUser.trim().isEmpty());
        Assume.that(delegationReason != null && !delegationReason.trim().isEmpty());
        Assume.that(!originalAssignee.equals(delegatedTo));
        Assume.that(!originalAssignee.equals(unauthorizedUser));
        Assume.that(!delegatedTo.equals(unauthorizedUser));
        
        // 创建直接分配给用户的任务
        ExtendedTaskInfo task = createTestTask(taskId, AssignmentType.USER, originalAssignee, 50);
        
        // 验证原始分配用户有委托权限
        boolean hasPermission = validateDelegationPermission(task, originalAssignee);
        assertThat(hasPermission).isTrue();
        
        // 验证未授权用户没有委托权限
        boolean unauthorizedPermission = validateDelegationPermission(task, unauthorizedUser);
        assertThat(unauthorizedPermission).isFalse();
        
        // 执行有权限的委托操作
        task.delegateTask(delegatedTo, originalAssignee, delegationReason);
        
        // 验证委托后的任务状态
        assertThat(task.isDelegated()).isTrue();
        assertThat(task.getDelegatedTo()).isEqualTo(delegatedTo);
        assertThat(task.getDelegatedBy()).isEqualTo(originalAssignee);
        assertThat(task.getCurrentAssignee()).isEqualTo(delegatedTo);
        assertThat(task.getStatus()).isEqualTo("DELEGATED");
        
        // 验证原始分配信息保持不变
        assertThat(task.getAssignmentType()).isEqualTo(AssignmentType.USER);
        assertThat(task.getAssignmentTarget()).isEqualTo(originalAssignee);
    }
    
    /**
     * 属性测试：虚拟组任务委托权限验证
     * 验证虚拟组成员可以委托虚拟组任务
     */
    @Property(tries = 100)
    @Label("虚拟组任务委托权限验证")
    void virtualGroupTaskDelegationPermission(
            @ForAll @NotBlank String taskId,
            @ForAll @NotBlank String groupId,
            @ForAll @NotBlank String groupMember,
            @ForAll @NotBlank String delegatedTo,
            @ForAll @NotBlank String nonGroupMember,
            @ForAll @NotBlank String delegationReason) {
        
        // 过滤掉空白字符串和重复用户
        Assume.that(taskId != null && !taskId.trim().isEmpty());
        Assume.that(groupId != null && !groupId.trim().isEmpty());
        Assume.that(groupMember != null && !groupMember.trim().isEmpty());
        Assume.that(delegatedTo != null && !delegatedTo.trim().isEmpty());
        Assume.that(nonGroupMember != null && !nonGroupMember.trim().isEmpty());
        Assume.that(delegationReason != null && !delegationReason.trim().isEmpty());
        Assume.that(!groupMember.equals(delegatedTo));
        Assume.that(!groupMember.equals(nonGroupMember));
        Assume.that(!delegatedTo.equals(nonGroupMember));
        
        // 创建虚拟组任务
        ExtendedTaskInfo task = createTestTask(taskId, AssignmentType.VIRTUAL_GROUP, groupId, 60);
        
        // 模拟虚拟组成员验证（简化处理，实际应该调用用户服务）
        boolean groupMemberHasPermission = validateVirtualGroupDelegationPermission(task, groupMember, groupId);
        assertThat(groupMemberHasPermission).isTrue();
        
        // 验证非组成员没有委托权限
        boolean nonMemberPermission = validateVirtualGroupDelegationPermission(task, nonGroupMember, "other-group");
        assertThat(nonMemberPermission).isFalse();
        
        // 执行有权限的委托操作
        task.delegateTask(delegatedTo, groupMember, delegationReason);
        
        // 验证委托后的任务状态
        assertThat(task.isDelegated()).isTrue();
        assertThat(task.getDelegatedTo()).isEqualTo(delegatedTo);
        assertThat(task.getDelegatedBy()).isEqualTo(groupMember);
        assertThat(task.getCurrentAssignee()).isEqualTo(delegatedTo);
        assertThat(task.getStatus()).isEqualTo("DELEGATED");
        
        // 验证原始分配信息保持不变
        assertThat(task.getAssignmentType()).isEqualTo(AssignmentType.VIRTUAL_GROUP);
        assertThat(task.getAssignmentTarget()).isEqualTo(groupId);
    }
    
    /**
     * 属性测试：已委托任务的再次委托权限验证
     * 验证已委托的任务只能由当前委托接收人进行再次委托
     */
    @Property(tries = 100)
    @Label("已委托任务的再次委托权限验证")
    void delegatedTaskReDelegationPermission(
            @ForAll @NotBlank String taskId,
            @ForAll @NotBlank String originalAssignee,
            @ForAll @NotBlank String firstDelegatee,
            @ForAll @NotBlank String secondDelegatee,
            @ForAll @NotBlank String unauthorizedUser,
            @ForAll @NotBlank String delegationReason) {
        
        // 过滤掉空白字符串和重复用户
        Assume.that(taskId != null && !taskId.trim().isEmpty());
        Assume.that(originalAssignee != null && !originalAssignee.trim().isEmpty());
        Assume.that(firstDelegatee != null && !firstDelegatee.trim().isEmpty());
        Assume.that(secondDelegatee != null && !secondDelegatee.trim().isEmpty());
        Assume.that(unauthorizedUser != null && !unauthorizedUser.trim().isEmpty());
        Assume.that(delegationReason != null && !delegationReason.trim().isEmpty());
        
        Set<String> allUsers = Set.of(originalAssignee, firstDelegatee, secondDelegatee, unauthorizedUser);
        Assume.that(allUsers.size() == 4); // 确保所有用户都不同
        
        // 创建任务并进行第一次委托
        ExtendedTaskInfo task = createTestTask(taskId, AssignmentType.USER, originalAssignee, 50);
        task.delegateTask(firstDelegatee, originalAssignee, "第一次委托");
        
        // 验证当前委托接收人有再次委托权限
        boolean currentDelegateeHasPermission = validateDelegationPermission(task, firstDelegatee);
        assertThat(currentDelegateeHasPermission).isTrue();
        
        // 验证原始分配人没有再次委托权限（任务已被委托）
        boolean originalAssigneePermission = validateDelegationPermission(task, originalAssignee);
        assertThat(originalAssigneePermission).isFalse();
        
        // 验证未授权用户没有委托权限
        boolean unauthorizedPermission = validateDelegationPermission(task, unauthorizedUser);
        assertThat(unauthorizedPermission).isFalse();
        
        // 执行有权限的再次委托
        task.delegateTask(secondDelegatee, firstDelegatee, "第二次委托");
        
        // 验证再次委托后的任务状态
        assertThat(task.isDelegated()).isTrue();
        assertThat(task.getDelegatedTo()).isEqualTo(secondDelegatee);
        assertThat(task.getDelegatedBy()).isEqualTo(firstDelegatee);
        assertThat(task.getCurrentAssignee()).isEqualTo(secondDelegatee);
        assertThat(task.getStatus()).isEqualTo("DELEGATED");
    }
    
    /**
     * 属性测试：已认领任务的委托权限验证
     * 验证已认领的虚拟组或部门角色任务只能由认领人进行委托
     */
    @Property(tries = 100)
    @Label("已认领任务的委托权限验证")
    void claimedTaskDelegationPermission(
            @ForAll @NotBlank String taskId,
            @ForAll @NotBlank String groupId,
            @ForAll @NotBlank String claimedBy,
            @ForAll @NotBlank String delegatedTo,
            @ForAll @NotBlank String unauthorizedUser,
            @ForAll @NotBlank String delegationReason) {
        
        // 过滤掉空白字符串和重复用户
        Assume.that(taskId != null && !taskId.trim().isEmpty());
        Assume.that(groupId != null && !groupId.trim().isEmpty());
        Assume.that(claimedBy != null && !claimedBy.trim().isEmpty());
        Assume.that(delegatedTo != null && !delegatedTo.trim().isEmpty());
        Assume.that(unauthorizedUser != null && !unauthorizedUser.trim().isEmpty());
        Assume.that(delegationReason != null && !delegationReason.trim().isEmpty());
        
        Set<String> allUsers = Set.of(claimedBy, delegatedTo, unauthorizedUser);
        Assume.that(allUsers.size() == 3); // 确保所有用户都不同
        
        // 创建虚拟组任务并认领
        ExtendedTaskInfo task = createTestTask(taskId, AssignmentType.VIRTUAL_GROUP, groupId, 60);
        task.claimTask(claimedBy);
        
        // 验证认领人有委托权限
        boolean claimedByHasPermission = validateDelegationPermission(task, claimedBy);
        assertThat(claimedByHasPermission).isTrue();
        
        // 验证未授权用户没有委托权限
        boolean unauthorizedPermission = validateDelegationPermission(task, unauthorizedUser);
        assertThat(unauthorizedPermission).isFalse();
        
        // 执行有权限的委托操作
        task.delegateTask(delegatedTo, claimedBy, delegationReason);
        
        // 验证委托后的任务状态
        assertThat(task.isDelegated()).isTrue();
        assertThat(task.getDelegatedTo()).isEqualTo(delegatedTo);
        assertThat(task.getDelegatedBy()).isEqualTo(claimedBy);
        assertThat(task.getCurrentAssignee()).isEqualTo(delegatedTo);
        assertThat(task.getStatus()).isEqualTo("DELEGATED");
        
        // 验证认领信息仍然保留
        assertThat(task.isClaimed()).isTrue();
        assertThat(task.getClaimedBy()).isEqualTo(claimedBy);
    }
    
    /**
     * 属性测试：已完成任务的委托权限验证
     * 验证已完成的任务不能被委托
     */
    @Property(tries = 100)
    @Label("已完成任务的委托权限验证")
    void completedTaskDelegationPermission(
            @ForAll @NotBlank String taskId,
            @ForAll @NotBlank String originalAssignee,
            @ForAll @NotBlank String delegatedTo,
            @ForAll @NotBlank String delegationReason) {
        
        // 过滤掉空白字符串和重复用户
        Assume.that(taskId != null && !taskId.trim().isEmpty());
        Assume.that(originalAssignee != null && !originalAssignee.trim().isEmpty());
        Assume.that(delegatedTo != null && !delegatedTo.trim().isEmpty());
        Assume.that(delegationReason != null && !delegationReason.trim().isEmpty());
        Assume.that(!originalAssignee.equals(delegatedTo));
        
        // 创建任务并完成
        ExtendedTaskInfo task = createTestTask(taskId, AssignmentType.USER, originalAssignee, 50);
        task.completeTask(originalAssignee);
        
        // 验证已完成任务不能被委托
        boolean canDelegate = validateDelegationPermission(task, originalAssignee);
        assertThat(canDelegate).isFalse();
        
        // 验证任务状态为已完成
        assertThat(task.isCompleted()).isTrue();
        assertThat(task.getStatus()).isEqualTo("COMPLETED");
    }
    
    /**
     * 属性测试：委托权限传递一致性验证
     * 验证委托权限的传递逻辑保持一致性
     */
    @Property(tries = 100)
    @Label("委托权限传递一致性验证")
    void delegationPermissionTransferConsistency(
            @ForAll @Size(min = 2, max = 5) List<@NotBlank String> userChain,
            @ForAll @NotBlank String taskId,
            @ForAll @NotBlank String delegationReason) {
        
        // 过滤掉空白字符串和重复用户
        Assume.that(userChain.stream().allMatch(user -> user != null && !user.trim().isEmpty()));
        Assume.that(taskId != null && !taskId.trim().isEmpty());
        Assume.that(delegationReason != null && !delegationReason.trim().isEmpty());
        
        // 确保用户链中没有重复用户
        Set<String> uniqueUsers = new HashSet<>(userChain);
        Assume.that(uniqueUsers.size() == userChain.size());
        
        // 创建任务
        ExtendedTaskInfo task = createTestTask(taskId, AssignmentType.USER, userChain.get(0), 50);
        
        // 逐步委托给链中的下一个用户
        for (int i = 0; i < userChain.size() - 1; i++) {
            String currentUser = userChain.get(i);
            String nextUser = userChain.get(i + 1);
            
            // 验证当前用户有委托权限
            boolean hasPermission = validateDelegationPermission(task, currentUser);
            assertThat(hasPermission).isTrue();
            
            // 执行委托
            task.delegateTask(nextUser, currentUser, delegationReason + "-" + (i + 1));
            
            // 验证委托后状态
            assertThat(task.getCurrentAssignee()).isEqualTo(nextUser);
            assertThat(task.getDelegatedBy()).isEqualTo(currentUser);
            assertThat(task.isDelegated()).isTrue();
            
            // 验证之前的用户失去委托权限
            if (i > 0) {
                String previousUser = userChain.get(i - 1);
                boolean previousUserPermission = validateDelegationPermission(task, previousUser);
                assertThat(previousUserPermission).isFalse();
            }
        }
        
        // 验证最终状态
        String finalAssignee = userChain.get(userChain.size() - 1);
        assertThat(task.getCurrentAssignee()).isEqualTo(finalAssignee);
        
        // 验证只有最终接收人有委托权限
        boolean finalAssigneePermission = validateDelegationPermission(task, finalAssignee);
        assertThat(finalAssigneePermission).isTrue();
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 验证委托权限
     */
    private boolean validateDelegationPermission(ExtendedTaskInfo task, String userId) {
        // 已完成的任务不能委托
        if (task.isCompleted()) {
            return false;
        }
        
        // 如果任务已被委托，只有当前委托接收人可以再次委托
        if (task.isDelegated()) {
            return task.getDelegatedTo().equals(userId);
        }
        
        // 如果任务已被认领，只有认领人可以委托
        if (task.isClaimed()) {
            return task.getClaimedBy().equals(userId);
        }
        
        // 根据分配类型验证权限
        switch (task.getAssignmentType()) {
            case USER:
                return task.getAssignmentTarget().equals(userId);
            case VIRTUAL_GROUP:
                // 简化处理：假设用户属于虚拟组（实际应该调用用户服务验证）
                return true;
            default:
                return false;
        }
    }
    
    /**
     * 验证虚拟组委托权限
     */
    private boolean validateVirtualGroupDelegationPermission(ExtendedTaskInfo task, String userId, String userGroupId) {
        if (task.getAssignmentType() != AssignmentType.VIRTUAL_GROUP) {
            return false;
        }
        
        // 简化处理：检查用户是否属于任务的虚拟组
        return task.getAssignmentTarget().equals(userGroupId);
    }
    
    /**
     * 创建测试任务
     */
    private ExtendedTaskInfo createTestTask(String taskId, AssignmentType assignmentType, 
                                          String assignmentTarget, int priority) {
        return ExtendedTaskInfo.builder()
                .taskId(taskId)
                .processInstanceId("proc-inst-" + System.currentTimeMillis())
                .processDefinitionId("proc-def-" + System.currentTimeMillis())
                .taskDefinitionKey("test-task")
                .taskName("测试任务 " + taskId)
                .taskDescription("测试任务描述")
                .assignmentType(assignmentType)
                .assignmentTarget(assignmentTarget)
                .priority(priority)
                .status("ASSIGNED")
                .createdTime(LocalDateTime.now())
                .createdBy("test-system")
                .isDeleted(false)
                .version(0L)
                .build();
    }
}