package com.workflow.service;

import com.workflow.client.AdminCenterClient;
import com.workflow.enums.AssigneeType;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 任务处理人解析服务
 * 根据 AssigneeType 解析实际的处理人或候选人列表
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskAssigneeResolver {
    
    private final AdminCenterClient adminCenterClient;
    
    /**
     * 解析结果
     */
    @Data
    @Builder
    public static class ResolveResult {
        /** 直接分配的处理人ID（如果是直接分配类型） */
        private String assignee;
        /** 候选人ID列表（如果是认领类型） */
        private List<String> candidateUsers;
        /** 候选组ID（虚拟组ID） */
        private String candidateGroup;
        /** 是否需要认领 */
        private boolean requiresClaim;
        /** 分配类型 */
        private AssigneeType assigneeType;
        /** 错误信息（如果解析失败） */
        private String errorMessage;
    }
    
    /**
     * 解析任务处理人
     * 
     * @param assigneeTypeCode 分配类型代码
     * @param assigneeValue 分配值（部门ID或虚拟组ID，仅 FIXED_DEPT 和 VIRTUAL_GROUP 需要）
     * @param initiatorId 流程发起人ID
     * @return 解析结果
     */
    public ResolveResult resolve(String assigneeTypeCode, String assigneeValue, String initiatorId) {
        AssigneeType assigneeType = AssigneeType.fromCode(assigneeTypeCode);
        
        if (assigneeType == null) {
            log.warn("Unknown assignee type: {}", assigneeTypeCode);
            return ResolveResult.builder()
                    .errorMessage("未知的分配类型: " + assigneeTypeCode)
                    .build();
        }
        
        return resolve(assigneeType, assigneeValue, initiatorId);
    }
    
    /**
     * 解析任务处理人
     */
    public ResolveResult resolve(AssigneeType assigneeType, String assigneeValue, String initiatorId) {
        log.info("Resolving assignee: type={}, value={}, initiator={}", assigneeType, assigneeValue, initiatorId);
        
        try {
            return switch (assigneeType) {
                case FUNCTION_MANAGER -> resolveFunctionManager(initiatorId);
                case ENTITY_MANAGER -> resolveEntityManager(initiatorId);
                case INITIATOR -> resolveInitiator(initiatorId);
                case DEPT_OTHERS -> resolveDeptOthers(initiatorId);
                case PARENT_DEPT -> resolveParentDept(initiatorId);
                case FIXED_DEPT -> resolveFixedDept(assigneeValue);
                case VIRTUAL_GROUP -> resolveVirtualGroup(assigneeValue);
            };
        } catch (Exception e) {
            log.error("Failed to resolve assignee: type={}, error={}", assigneeType, e.getMessage());
            return ResolveResult.builder()
                    .assigneeType(assigneeType)
                    .requiresClaim(assigneeType.requiresClaim())
                    .errorMessage("解析处理人失败: " + e.getMessage())
                    .build();
        }
    }

    
    /**
     * 1. 解析职能经理
     */
    private ResolveResult resolveFunctionManager(String initiatorId) {
        Map<String, Object> userInfo = adminCenterClient.getUserInfo(initiatorId);
        if (userInfo == null) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.FUNCTION_MANAGER)
                    .requiresClaim(false)
                    .errorMessage("无法获取用户信息: " + initiatorId)
                    .build();
        }
        
        String functionManagerId = (String) userInfo.get("functionManagerId");
        if (functionManagerId == null || functionManagerId.isEmpty()) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.FUNCTION_MANAGER)
                    .requiresClaim(false)
                    .errorMessage("用户没有设置职能经理: " + initiatorId)
                    .build();
        }
        
        log.info("Resolved function manager: {} for user: {}", functionManagerId, initiatorId);
        return ResolveResult.builder()
                .assignee(functionManagerId)
                .assigneeType(AssigneeType.FUNCTION_MANAGER)
                .requiresClaim(false)
                .build();
    }
    
    /**
     * 2. 解析实体经理
     */
    private ResolveResult resolveEntityManager(String initiatorId) {
        Map<String, Object> userInfo = adminCenterClient.getUserInfo(initiatorId);
        if (userInfo == null) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.ENTITY_MANAGER)
                    .requiresClaim(false)
                    .errorMessage("无法获取用户信息: " + initiatorId)
                    .build();
        }
        
        String entityManagerId = (String) userInfo.get("entityManagerId");
        if (entityManagerId == null || entityManagerId.isEmpty()) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.ENTITY_MANAGER)
                    .requiresClaim(false)
                    .errorMessage("用户没有设置实体经理: " + initiatorId)
                    .build();
        }
        
        log.info("Resolved entity manager: {} for user: {}", entityManagerId, initiatorId);
        return ResolveResult.builder()
                .assignee(entityManagerId)
                .assigneeType(AssigneeType.ENTITY_MANAGER)
                .requiresClaim(false)
                .build();
    }
    
    /**
     * 3. 解析流程发起人
     */
    private ResolveResult resolveInitiator(String initiatorId) {
        log.info("Resolved initiator: {}", initiatorId);
        return ResolveResult.builder()
                .assignee(initiatorId)
                .assigneeType(AssigneeType.INITIATOR)
                .requiresClaim(false)
                .build();
    }
    
    /**
     * 4. 解析本部门其他人（需要认领）
     */
    private ResolveResult resolveDeptOthers(String initiatorId) {
        Map<String, Object> userInfo = adminCenterClient.getUserInfo(initiatorId);
        if (userInfo == null) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.DEPT_OTHERS)
                    .requiresClaim(true)
                    .errorMessage("无法获取用户信息: " + initiatorId)
                    .build();
        }
        
        String departmentId = (String) userInfo.get("departmentId");
        if (departmentId == null || departmentId.isEmpty()) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.DEPT_OTHERS)
                    .requiresClaim(true)
                    .errorMessage("用户没有所属部门: " + initiatorId)
                    .build();
        }
        
        // 获取部门成员（排除发起人自己）
        List<String> members = adminCenterClient.getDepartmentMembers(departmentId);
        List<String> candidates = new ArrayList<>();
        for (String memberId : members) {
            if (!memberId.equals(initiatorId)) {
                candidates.add(memberId);
            }
        }
        
        if (candidates.isEmpty()) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.DEPT_OTHERS)
                    .requiresClaim(true)
                    .errorMessage("部门中没有其他成员")
                    .build();
        }
        
        log.info("Resolved dept others: {} candidates for dept: {}", candidates.size(), departmentId);
        return ResolveResult.builder()
                .candidateUsers(candidates)
                .assigneeType(AssigneeType.DEPT_OTHERS)
                .requiresClaim(true)
                .build();
    }
    
    /**
     * 5. 解析上级部门（需要认领）
     */
    private ResolveResult resolveParentDept(String initiatorId) {
        Map<String, Object> userInfo = adminCenterClient.getUserInfo(initiatorId);
        if (userInfo == null) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.PARENT_DEPT)
                    .requiresClaim(true)
                    .errorMessage("无法获取用户信息: " + initiatorId)
                    .build();
        }
        
        String departmentId = (String) userInfo.get("departmentId");
        if (departmentId == null || departmentId.isEmpty()) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.PARENT_DEPT)
                    .requiresClaim(true)
                    .errorMessage("用户没有所属部门: " + initiatorId)
                    .build();
        }
        
        // 获取部门信息
        Map<String, Object> deptInfo = adminCenterClient.getDepartmentInfo(departmentId);
        if (deptInfo == null) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.PARENT_DEPT)
                    .requiresClaim(true)
                    .errorMessage("无法获取部门信息: " + departmentId)
                    .build();
        }
        
        String parentDeptId = (String) deptInfo.get("parentId");
        if (parentDeptId == null || parentDeptId.isEmpty()) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.PARENT_DEPT)
                    .requiresClaim(true)
                    .errorMessage("部门没有上级部门: " + departmentId)
                    .build();
        }
        
        // 获取上级部门成员
        List<String> members = adminCenterClient.getDepartmentMembers(parentDeptId);
        if (members.isEmpty()) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.PARENT_DEPT)
                    .requiresClaim(true)
                    .errorMessage("上级部门没有成员")
                    .build();
        }
        
        log.info("Resolved parent dept: {} candidates for parent dept: {}", members.size(), parentDeptId);
        return ResolveResult.builder()
                .candidateUsers(members)
                .assigneeType(AssigneeType.PARENT_DEPT)
                .requiresClaim(true)
                .build();
    }

    
    /**
     * 6. 解析指定部门（需要认领）
     */
    private ResolveResult resolveFixedDept(String departmentId) {
        if (departmentId == null || departmentId.isEmpty()) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.FIXED_DEPT)
                    .requiresClaim(true)
                    .errorMessage("未指定部门ID")
                    .build();
        }
        
        // 获取部门成员
        List<String> members = adminCenterClient.getDepartmentMembers(departmentId);
        if (members.isEmpty()) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.FIXED_DEPT)
                    .requiresClaim(true)
                    .errorMessage("指定部门没有成员: " + departmentId)
                    .build();
        }
        
        log.info("Resolved fixed dept: {} candidates for dept: {}", members.size(), departmentId);
        return ResolveResult.builder()
                .candidateUsers(members)
                .assigneeType(AssigneeType.FIXED_DEPT)
                .requiresClaim(true)
                .build();
    }
    
    /**
     * 7. 解析虚拟组（需要认领）
     */
    private ResolveResult resolveVirtualGroup(String groupId) {
        if (groupId == null || groupId.isEmpty()) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.VIRTUAL_GROUP)
                    .requiresClaim(true)
                    .errorMessage("未指定虚拟组ID")
                    .build();
        }
        
        // 获取虚拟组成员
        List<String> members = adminCenterClient.getVirtualGroupMembers(groupId);
        if (members.isEmpty()) {
            // 虚拟组可能没有成员，但仍然可以设置为候选组
            log.warn("Virtual group has no members: {}", groupId);
        }
        
        log.info("Resolved virtual group: {} candidates for group: {}", members.size(), groupId);
        return ResolveResult.builder()
                .candidateUsers(members.isEmpty() ? null : members)
                .candidateGroup(groupId)
                .assigneeType(AssigneeType.VIRTUAL_GROUP)
                .requiresClaim(true)
                .build();
    }
}
