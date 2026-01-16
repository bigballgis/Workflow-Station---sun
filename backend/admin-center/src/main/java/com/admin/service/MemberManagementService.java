package com.admin.service;

import com.admin.entity.*;
import com.admin.enums.ApproverTargetType;
import com.admin.enums.MemberChangeType;
import com.admin.enums.PermissionRequestType;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.UserNotFoundException;
import com.admin.exception.VirtualGroupNotFoundException;
import com.admin.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 成员管理服务
 * 处理审批通过后的成员变更、审批人清退成员、用户主动退出等操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberManagementService {
    
    private final VirtualGroupMemberRepository virtualGroupMemberRepository;
    private final VirtualGroupRepository virtualGroupRepository;
    private final UserRepository userRepository;
    private final UserBusinessUnitRoleRepository userBusinessUnitRoleRepository;
    private final UserBusinessUnitRepository userBusinessUnitRepository;
    private final VirtualGroupRoleRepository virtualGroupRoleRepository;
    private final MemberChangeLogRepository memberChangeLogRepository;
    private final ApproverService approverService;
    private final ObjectMapper objectMapper;
    
    /**
     * 处理审批通过的申请
     */
    @Transactional
    public void processApprovedRequest(PermissionRequest request) {
        log.info("Processing approved request: {}", request.getId());
        
        if (request.getRequestType() == PermissionRequestType.VIRTUAL_GROUP) {
            addUserToVirtualGroup(request.getApplicantId(), request.getTargetId(), request.getApproverId());
        } else if (request.getRequestType() == PermissionRequestType.BUSINESS_UNIT) {
            // 新版本：只添加用户到业务单元，不分配角色（角色通过虚拟组获取）
            addUserToBusinessUnit(request.getApplicantId(), request.getTargetId(), request.getApproverId());
        } else if (request.getRequestType() == PermissionRequestType.BUSINESS_UNIT_ROLE) {
            // 旧版本：兼容处理，添加用户到业务单元并分配角色
            List<String> roleIds = parseRoleIds(request.getRoleIds());
            addUserToBusinessUnitWithRoles(request.getApplicantId(), request.getTargetId(), roleIds, request.getApproverId());
        }
    }
    
    /**
     * 添加用户到业务单元（新版本：不分配角色）
     */
    @Transactional
    public void addUserToBusinessUnit(String userId, String businessUnitId, String operatorId) {
        log.info("Adding user {} to business unit {}", userId, businessUnitId);
        
        // 检查是否已是成员（通过 UserBusinessUnit 表判断）
        if (userBusinessUnitRepository.existsByUserIdAndBusinessUnitId(userId, businessUnitId)) {
            log.warn("User {} is already a member of business unit {}", userId, businessUnitId);
            return;
        }
        
        // 创建用户业务单元成员关系
        UserBusinessUnit membership = UserBusinessUnit.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .businessUnitId(businessUnitId)
                .build();
        userBusinessUnitRepository.save(membership);
        
        log.info("User {} added to business unit {} successfully", userId, businessUnitId);
        
        // 记录变更日志
        logMemberChange(MemberChangeType.JOIN, ApproverTargetType.BUSINESS_UNIT, businessUnitId, userId, null, operatorId, "审批通过加入业务单元");
    }
    
    /**
     * 添加用户到虚拟组
     */
    @Transactional
    public void addUserToVirtualGroup(String userId, String virtualGroupId, String operatorId) {
        log.info("Adding user {} to virtual group {}", userId, virtualGroupId);
        
        // 检查是否已是成员
        if (virtualGroupMemberRepository.existsByVirtualGroupIdAndUserId(virtualGroupId, userId)) {
            log.warn("User {} is already a member of virtual group {}", userId, virtualGroupId);
            return;
        }
        
        // 获取虚拟组和用户实体
        VirtualGroup virtualGroup = virtualGroupRepository.findById(virtualGroupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(virtualGroupId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        
        // 添加到虚拟组
        VirtualGroupMember member = VirtualGroupMember.builder()
                .id(UUID.randomUUID().toString())
                .virtualGroup(virtualGroup)
                .user(user)
                .build();
        virtualGroupMemberRepository.save(member);
        
        // 记录变更日志
        logMemberChange(MemberChangeType.JOIN, ApproverTargetType.VIRTUAL_GROUP, virtualGroupId, userId, null, operatorId, "审批通过加入");
        
        log.info("User {} added to virtual group {} successfully", userId, virtualGroupId);
    }
    
    /**
     * 添加用户到业务单元并分配角色
     */
    @Transactional
    public void addUserToBusinessUnitWithRoles(String userId, String businessUnitId, List<String> roleIds, String operatorId) {
        log.info("Adding user {} to business unit {} with roles {}", userId, businessUnitId, roleIds);
        
        for (String roleId : roleIds) {
            // 检查是否已有该角色
            if (userBusinessUnitRoleRepository.existsByUserIdAndBusinessUnitIdAndRoleId(userId, businessUnitId, roleId)) {
                log.warn("User {} already has role {} in business unit {}", userId, roleId, businessUnitId);
                continue;
            }
            
            // 分配角色
            UserBusinessUnitRole assignment = UserBusinessUnitRole.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .businessUnitId(businessUnitId)
                    .roleId(roleId)
                    .build();
            userBusinessUnitRoleRepository.save(assignment);
        }
        
        // 记录变更日志
        String roleIdsJson = serializeRoleIds(roleIds);
        logMemberChange(MemberChangeType.JOIN, ApproverTargetType.BUSINESS_UNIT, businessUnitId, userId, roleIdsJson, operatorId, "审批通过加入");
        
        log.info("User {} added to business unit {} with roles {} successfully", userId, businessUnitId, roleIds);
    }
    
    /**
     * 审批人清退虚拟组成员
     */
    @Transactional
    public void removeVirtualGroupMember(String virtualGroupId, String userId, String approverId) {
        log.info("Removing user {} from virtual group {} by approver {}", userId, virtualGroupId, approverId);
        
        // 验证审批人权限
        if (!approverService.isApprover(approverId, ApproverTargetType.VIRTUAL_GROUP, virtualGroupId)) {
            throw new AdminBusinessException("NOT_APPROVER", "您不是该虚拟组的审批人");
        }
        
        // 删除成员
        virtualGroupMemberRepository.deleteByVirtualGroupIdAndUserId(virtualGroupId, userId);
        
        // 记录变更日志
        logMemberChange(MemberChangeType.REMOVED, ApproverTargetType.VIRTUAL_GROUP, virtualGroupId, userId, null, approverId, "审批人清退");
        
        log.info("User {} removed from virtual group {} by approver {}", userId, virtualGroupId, approverId);
    }
    
    /**
     * 审批人清退业务单元成员的角色（旧版本，保留向后兼容）
     * @deprecated 使用 {@link #removeBusinessUnitMember(String, String, String)} 代替
     */
    @Deprecated
    @Transactional
    public void removeBusinessUnitRole(String businessUnitId, String userId, String roleId, String approverId) {
        log.info("Removing role {} from user {} in business unit {} by approver {}", roleId, userId, businessUnitId, approverId);
        
        // 验证审批人权限
        if (!approverService.isApprover(approverId, ApproverTargetType.BUSINESS_UNIT, businessUnitId)) {
            throw new AdminBusinessException("NOT_APPROVER", "您不是该业务单元的审批人");
        }
        
        // 删除角色分配
        userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitIdAndRoleId(userId, businessUnitId, roleId)
                .ifPresent(userBusinessUnitRoleRepository::delete);
        
        // 记录变更日志
        logMemberChange(MemberChangeType.REMOVED, ApproverTargetType.BUSINESS_UNIT, businessUnitId, userId, 
                serializeRoleIds(List.of(roleId)), approverId, "审批人清退");
        
        log.info("Role {} removed from user {} in business unit {} by approver {}", roleId, userId, businessUnitId, approverId);
    }
    
    /**
     * 审批人清退业务单元成员
     * 新版本：移除用户的业务单元成员身份，同时清除该业务单元下的所有角色分配
     * 这将导致用户的 BU-Bounded 角色在该业务单元内失效
     */
    @Transactional
    public void removeBusinessUnitMember(String businessUnitId, String userId, String approverId) {
        log.info("Removing user {} from business unit {} by approver {}", userId, businessUnitId, approverId);
        
        // 验证审批人权限
        if (!approverService.isApprover(approverId, ApproverTargetType.BUSINESS_UNIT, businessUnitId)) {
            throw new AdminBusinessException("NOT_APPROVER", "您不是该业务单元的审批人");
        }
        
        // 删除用户业务单元成员关系
        userBusinessUnitRepository.findByUserIdAndBusinessUnitId(userId, businessUnitId)
                .ifPresent(userBusinessUnitRepository::delete);
        
        // 同时删除该业务单元下的所有角色分配（旧版本数据兼容）
        List<UserBusinessUnitRole> roleAssignments = userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitId(userId, businessUnitId);
        if (!roleAssignments.isEmpty()) {
            userBusinessUnitRoleRepository.deleteAll(roleAssignments);
            log.info("Also removed {} role assignments for user {} in business unit {}", roleAssignments.size(), userId, businessUnitId);
        }
        
        // 记录变更日志
        logMemberChange(MemberChangeType.REMOVED, ApproverTargetType.BUSINESS_UNIT, businessUnitId, userId, 
                null, approverId, "审批人清退业务单元成员");
        
        log.info("User {} removed from business unit {} by approver {}", userId, businessUnitId, approverId);
    }
    
    /**
     * 用户主动退出虚拟组
     */
    @Transactional
    public void exitVirtualGroup(String virtualGroupId, String userId) {
        log.info("User {} exiting virtual group {}", userId, virtualGroupId);
        
        // 删除成员
        virtualGroupMemberRepository.deleteByVirtualGroupIdAndUserId(virtualGroupId, userId);
        
        // 记录变更日志
        logMemberChange(MemberChangeType.EXIT, ApproverTargetType.VIRTUAL_GROUP, virtualGroupId, userId, null, userId, "用户主动退出");
        
        log.info("User {} exited virtual group {} successfully", userId, virtualGroupId);
    }
    
    /**
     * 用户主动退出业务单元角色（旧版本，保留向后兼容）
     * @deprecated 使用 {@link #exitBusinessUnit(String, String)} 代替
     */
    @Deprecated
    @Transactional
    public void exitBusinessUnitRoles(String businessUnitId, String userId, List<String> roleIds) {
        log.info("User {} exiting roles {} from business unit {}", userId, roleIds, businessUnitId);
        
        for (String roleId : roleIds) {
            userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitIdAndRoleId(userId, businessUnitId, roleId)
                    .ifPresent(userBusinessUnitRoleRepository::delete);
        }
        
        // 检查是否还有其他角色，如果没有则自动移除用户与业务单元的关联
        List<UserBusinessUnitRole> remainingRoles = userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitId(userId, businessUnitId);
        if (remainingRoles.isEmpty()) {
            log.info("User {} has no more roles in business unit {}, removing from business unit", userId, businessUnitId);
        }
        
        // 记录变更日志
        logMemberChange(MemberChangeType.EXIT, ApproverTargetType.BUSINESS_UNIT, businessUnitId, userId, 
                serializeRoleIds(roleIds), userId, "用户主动退出");
        
        log.info("User {} exited roles {} from business unit {} successfully", userId, roleIds, businessUnitId);
    }
    
    /**
     * 用户主动退出业务单元
     * 新版本：移除用户的业务单元成员身份，同时清除该业务单元下的所有角色分配
     * 这将导致用户的 BU-Bounded 角色在该业务单元内失效
     */
    @Transactional
    public void exitBusinessUnit(String businessUnitId, String userId) {
        log.info("User {} exiting business unit {}", userId, businessUnitId);
        
        // 删除用户业务单元成员关系
        userBusinessUnitRepository.findByUserIdAndBusinessUnitId(userId, businessUnitId)
                .ifPresent(userBusinessUnitRepository::delete);
        
        // 同时删除该业务单元下的所有角色分配（旧版本数据兼容）
        List<UserBusinessUnitRole> roleAssignments = userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitId(userId, businessUnitId);
        if (!roleAssignments.isEmpty()) {
            userBusinessUnitRoleRepository.deleteAll(roleAssignments);
            log.info("Also removed {} role assignments for user {} in business unit {}", roleAssignments.size(), userId, businessUnitId);
        }
        
        // 记录变更日志
        logMemberChange(MemberChangeType.EXIT, ApproverTargetType.BUSINESS_UNIT, businessUnitId, userId, 
                null, userId, "用户主动退出业务单元");
        
        log.info("User {} exited business unit {} successfully", userId, businessUnitId);
    }
    
    /**
     * 获取虚拟组成员列表
     */
    public List<User> getVirtualGroupMembers(String virtualGroupId) {
        return virtualGroupMemberRepository.findByVirtualGroupIdWithUser(virtualGroupId)
                .stream()
                .map(VirtualGroupMember::getUser)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取业务单元成员列表（包含角色信息）
     * @deprecated 使用 {@link #getBusinessUnitMemberUsers(String)} 代替
     */
    @Deprecated
    public List<UserBusinessUnitRole> getBusinessUnitMembers(String businessUnitId) {
        return userBusinessUnitRoleRepository.findByBusinessUnitIdWithDetails(businessUnitId);
    }
    
    /**
     * 获取业务单元成员列表（新版本：从 UserBusinessUnit 表获取）
     */
    public List<UserBusinessUnit> getBusinessUnitMemberUsers(String businessUnitId) {
        return userBusinessUnitRepository.findByBusinessUnitIdWithUser(businessUnitId);
    }
    
    /**
     * 检查用户是否是业务单元的成员
     */
    public boolean isBusinessUnitMember(String userId, String businessUnitId) {
        return userBusinessUnitRepository.existsByUserIdAndBusinessUnitId(userId, businessUnitId);
    }
    
    /**
     * 获取用户的业务单元角色列表
     */
    public List<UserBusinessUnitRole> getUserBusinessUnitRoles(String userId) {
        return userBusinessUnitRoleRepository.findByUserIdWithDetails(userId);
    }
    
    /**
     * 获取用户加入的所有业务单元ID
     * 新版本：从 UserBusinessUnit 表获取
     */
    public List<String> getUserBusinessUnitIds(String userId) {
        return userBusinessUnitRepository.findByUserId(userId)
                .stream()
                .map(UserBusinessUnit::getBusinessUnitId)
                .distinct()
                .collect(Collectors.toList());
    }
    
    /**
     * 获取用户加入的所有业务单元ID（旧版本，从角色分配表获取）
     * @deprecated 使用 {@link #getUserBusinessUnitIds(String)} 代替
     */
    @Deprecated
    public List<String> getUserBusinessUnitIdsFromRoles(String userId) {
        return userBusinessUnitRoleRepository.findByUserId(userId)
                .stream()
                .map(UserBusinessUnitRole::getBusinessUnitId)
                .distinct()
                .collect(Collectors.toList());
    }
    
    /**
     * 获取虚拟组绑定的角色ID（单角色绑定）
     * @return 角色ID列表（最多一个元素，为了向后兼容返回List）
     */
    public List<String> getVirtualGroupRoleIds(String virtualGroupId) {
        return virtualGroupRoleRepository.findByVirtualGroupId(virtualGroupId)
                .map(binding -> List.of(binding.getRoleId()))
                .orElse(List.of());
    }
    
    /**
     * 记录成员变更日志
     */
    private void logMemberChange(MemberChangeType changeType, ApproverTargetType targetType, 
                                  String targetId, String userId, String roleIds, String operatorId, String reason) {
        MemberChangeLog log = MemberChangeLog.builder()
                .id(UUID.randomUUID().toString())
                .changeType(changeType)
                .targetType(targetType)
                .targetId(targetId)
                .userId(userId)
                .roleIds(roleIds)
                .operatorId(operatorId)
                .reason(reason)
                .build();
        memberChangeLogRepository.save(log);
    }
    
    /**
     * 解析角色ID列表
     */
    private List<String> parseRoleIds(String roleIdsJson) {
        if (roleIdsJson == null || roleIdsJson.isEmpty()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(roleIdsJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            throw new AdminBusinessException("JSON_ERROR", "角色ID解析失败");
        }
    }
    
    /**
     * 序列化角色ID列表
     */
    private String serializeRoleIds(List<String> roleIds) {
        try {
            return objectMapper.writeValueAsString(roleIds);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
