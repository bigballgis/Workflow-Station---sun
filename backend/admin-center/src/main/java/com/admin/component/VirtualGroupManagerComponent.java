package com.admin.component;

import com.admin.dto.request.VirtualGroupCreateRequest;
import com.admin.dto.request.VirtualGroupMemberRequest;
import com.admin.dto.response.VirtualGroupInfo;
import com.admin.dto.response.VirtualGroupMemberInfo;
import com.admin.dto.response.VirtualGroupResult;
import com.admin.entity.User;
import com.admin.entity.VirtualGroup;
import com.admin.entity.VirtualGroupMember;
import com.admin.enums.VirtualGroupMemberRole;
import com.admin.enums.VirtualGroupType;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.UserNotFoundException;
import com.admin.exception.VirtualGroupNotFoundException;
import com.admin.repository.UserRepository;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 虚拟组管理组件
 * 负责虚拟组的创建、配置、成员管理和生命周期管理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VirtualGroupManagerComponent {
    
    private final VirtualGroupRepository virtualGroupRepository;
    private final VirtualGroupMemberRepository virtualGroupMemberRepository;
    private final UserRepository userRepository;

    
    /**
     * 创建虚拟组
     */
    @Transactional
    public VirtualGroupResult createVirtualGroup(VirtualGroupCreateRequest request) {
        log.info("Creating virtual group: {}", request.getName());
        
        // 验证名称唯一性
        if (virtualGroupRepository.existsByName(request.getName())) {
            throw new AdminBusinessException("NAME_EXISTS", "虚拟组名称已存在: " + request.getName());
        }
        
        // 验证有效期
        validateValidPeriod(request.getValidFrom(), request.getValidTo());
        
        String groupId = UUID.randomUUID().toString();
        
        VirtualGroup group = VirtualGroup.builder()
                .id(groupId)
                .name(request.getName())
                .type(request.getType())
                .description(request.getDescription())
                .validFrom(request.getValidFrom())
                .validTo(request.getValidTo())
                .status("ACTIVE")
                .build();
        
        virtualGroupRepository.save(group);
        
        log.info("Virtual group created successfully: {}", groupId);
        return VirtualGroupResult.success(group);
    }
    
    /**
     * 更新虚拟组
     */
    @Transactional
    public VirtualGroupResult updateVirtualGroup(String groupId, VirtualGroupCreateRequest request) {
        log.info("Updating virtual group: {}", groupId);
        
        VirtualGroup group = virtualGroupRepository.findById(groupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(groupId));
        
        // 验证名称唯一性（排除自身）
        if (!group.getName().equals(request.getName()) && 
            virtualGroupRepository.existsByName(request.getName())) {
            throw new AdminBusinessException("NAME_EXISTS", "虚拟组名称已存在: " + request.getName());
        }
        
        // 验证有效期
        validateValidPeriod(request.getValidFrom(), request.getValidTo());
        
        group.setName(request.getName());
        group.setType(request.getType());
        group.setDescription(request.getDescription());
        group.setValidFrom(request.getValidFrom());
        group.setValidTo(request.getValidTo());
        
        virtualGroupRepository.save(group);
        
        log.info("Virtual group updated successfully: {}", groupId);
        return VirtualGroupResult.success(group);
    }
    
    /**
     * 删除虚拟组
     */
    @Transactional
    public void deleteVirtualGroup(String groupId) {
        log.info("Deleting virtual group: {}", groupId);
        
        VirtualGroup group = virtualGroupRepository.findById(groupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(groupId));
        
        // 删除所有成员关系
        virtualGroupMemberRepository.deleteByVirtualGroupId(groupId);
        
        // 删除虚拟组
        virtualGroupRepository.delete(group);
        
        log.info("Virtual group deleted successfully: {}", groupId);
    }
    
    /**
     * 获取虚拟组详情
     */
    public VirtualGroupInfo getVirtualGroup(String groupId) {
        VirtualGroup group = virtualGroupRepository.findById(groupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(groupId));
        return VirtualGroupInfo.fromEntity(group);
    }
    
    /**
     * 获取虚拟组列表（带过滤条件）
     */
    public List<VirtualGroupInfo> listVirtualGroups(String type, String status) {
        List<VirtualGroup> groups;
        if (type != null && status != null) {
            VirtualGroupType groupType = VirtualGroupType.valueOf(type);
            groups = virtualGroupRepository.findByTypeAndStatus(groupType, status);
        } else if (type != null) {
            VirtualGroupType groupType = VirtualGroupType.valueOf(type);
            groups = virtualGroupRepository.findByType(groupType);
        } else if (status != null) {
            groups = virtualGroupRepository.findByStatus(status);
        } else {
            groups = virtualGroupRepository.findAll();
        }
        return groups.stream()
                .map(VirtualGroupInfo::fromEntity)
                .collect(Collectors.toList());
    }
    
    /**
     * 分页查询虚拟组
     */
    public Page<VirtualGroupInfo> listVirtualGroups(VirtualGroupType type, String status, 
                                                     String keyword, Pageable pageable) {
        return virtualGroupRepository.findByConditions(type, status, keyword, pageable)
                .map(VirtualGroupInfo::fromEntity);
    }
    
    /**
     * 获取有效的虚拟组列表
     */
    public List<VirtualGroupInfo> getValidGroups() {
        return virtualGroupRepository.findValidGroups(Instant.now()).stream()
                .map(VirtualGroupInfo::fromEntity)
                .collect(Collectors.toList());
    }

    
    // ==================== 成员管理 ====================
    
    /**
     * 添加成员到虚拟组
     */
    @Transactional
    public VirtualGroupResult addMember(String groupId, VirtualGroupMemberRequest request) {
        log.info("Adding member {} to virtual group {}", request.getUserId(), groupId);
        
        VirtualGroup group = virtualGroupRepository.findById(groupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(groupId));
        
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new UserNotFoundException(request.getUserId()));
        
        // 检查是否已是成员
        if (virtualGroupMemberRepository.existsByVirtualGroupIdAndUserId(groupId, request.getUserId())) {
            throw new AdminBusinessException("MEMBER_EXISTS", "用户已是该虚拟组成员");
        }
        
        String memberId = UUID.randomUUID().toString();
        
        VirtualGroupMember member = VirtualGroupMember.builder()
                .id(memberId)
                .virtualGroup(group)
                .user(user)
                .role(request.getRole() != null ? request.getRole() : VirtualGroupMemberRole.MEMBER)
                .joinedAt(Instant.now())
                .build();
        
        virtualGroupMemberRepository.save(member);
        
        log.info("Member added successfully: {} to group {}", request.getUserId(), groupId);
        return VirtualGroupResult.success(group);
    }
    
    /**
     * 批量添加成员
     */
    @Transactional
    public List<VirtualGroupResult> addMembers(String groupId, List<VirtualGroupMemberRequest> requests) {
        log.info("Adding {} members to virtual group {}", requests.size(), groupId);
        
        return requests.stream()
                .map(request -> {
                    try {
                        return addMember(groupId, request);
                    } catch (AdminBusinessException e) {
                        log.warn("Failed to add member {}: {}", request.getUserId(), e.getMessage());
                        return null;
                    }
                })
                .filter(result -> result != null)
                .collect(Collectors.toList());
    }
    
    /**
     * 移除成员
     */
    @Transactional
    public VirtualGroupResult removeMember(String groupId, String userId) {
        log.info("Removing member {} from virtual group {}", userId, groupId);
        
        VirtualGroup group = virtualGroupRepository.findById(groupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(groupId));
        
        VirtualGroupMember member = virtualGroupMemberRepository
                .findByVirtualGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new AdminBusinessException("MEMBER_NOT_FOUND", "用户不是该虚拟组成员"));
        
        virtualGroupMemberRepository.delete(member);
        
        log.info("Member removed successfully: {} from group {}", userId, groupId);
        return VirtualGroupResult.success(group);
    }
    
    /**
     * 更新成员角色
     */
    @Transactional
    public VirtualGroupResult updateMemberRole(String groupId, VirtualGroupMemberRequest request) {
        log.info("Updating member {} role to {} in group {}", request.getUserId(), request.getRole(), groupId);
        
        VirtualGroup group = virtualGroupRepository.findById(groupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(groupId));
        
        VirtualGroupMember member = virtualGroupMemberRepository
                .findByVirtualGroupIdAndUserId(groupId, request.getUserId())
                .orElseThrow(() -> new AdminBusinessException("MEMBER_NOT_FOUND", "用户不是该虚拟组成员"));
        
        member.setRole(request.getRole());
        virtualGroupMemberRepository.save(member);
        
        log.info("Member role updated successfully: {} to {} in group {}", request.getUserId(), request.getRole(), groupId);
        return VirtualGroupResult.success(group);
    }
    
    /**
     * 获取虚拟组成员列表
     */
    public List<VirtualGroupMemberInfo> getGroupMembers(String groupId) {
        if (!virtualGroupRepository.existsById(groupId)) {
            throw new VirtualGroupNotFoundException(groupId);
        }
        
        return virtualGroupMemberRepository.findByVirtualGroupId(groupId).stream()
                .map(VirtualGroupMemberInfo::fromEntity)
                .collect(Collectors.toList());
    }
    
    /**
     * 分页获取虚拟组成员
     */
    public Page<VirtualGroupMemberInfo> getGroupMembers(String groupId, Pageable pageable) {
        if (!virtualGroupRepository.existsById(groupId)) {
            throw new VirtualGroupNotFoundException(groupId);
        }
        
        return virtualGroupMemberRepository.findByVirtualGroupId(groupId, pageable)
                .map(VirtualGroupMemberInfo::fromEntity);
    }
    
    /**
     * 检查用户是否是虚拟组成员
     */
    public boolean isGroupMember(String groupId, String userId) {
        return virtualGroupMemberRepository.existsByVirtualGroupIdAndUserId(groupId, userId);
    }
    
    /**
     * 获取虚拟组的组长列表
     */
    public List<VirtualGroupMemberInfo> getGroupLeaders(String groupId) {
        return virtualGroupMemberRepository.findLeadersByGroupId(groupId).stream()
                .map(VirtualGroupMemberInfo::fromEntity)
                .collect(Collectors.toList());
    }

    
    // ==================== 生命周期管理 ====================
    
    /**
     * 激活虚拟组
     */
    @Transactional
    public VirtualGroupResult activateGroup(String groupId) {
        log.info("Activating virtual group: {}", groupId);
        
        VirtualGroup group = virtualGroupRepository.findById(groupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(groupId));
        
        group.setStatus("ACTIVE");
        virtualGroupRepository.save(group);
        
        log.info("Virtual group activated: {}", groupId);
        return VirtualGroupResult.success(group);
    }
    
    /**
     * 停用虚拟组
     */
    @Transactional
    public VirtualGroupResult deactivateGroup(String groupId) {
        log.info("Deactivating virtual group: {}", groupId);
        
        VirtualGroup group = virtualGroupRepository.findById(groupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(groupId));
        
        group.setStatus("INACTIVE");
        virtualGroupRepository.save(group);
        
        log.info("Virtual group deactivated: {}", groupId);
        return VirtualGroupResult.success(group);
    }
    
    /**
     * 解散虚拟组
     */
    @Transactional
    public void dissolveGroup(String groupId) {
        log.info("Dissolving virtual group: {}", groupId);
        
        VirtualGroup group = virtualGroupRepository.findById(groupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(groupId));
        
        group.setStatus("DISSOLVED");
        virtualGroupRepository.save(group);
        
        log.info("Virtual group dissolved: {}", groupId);
    }
    
    /**
     * 处理过期的虚拟组
     */
    @Transactional
    public int processExpiredGroups() {
        log.info("Processing expired virtual groups");
        
        List<VirtualGroup> expiredGroups = virtualGroupRepository.findExpiredGroups(Instant.now());
        
        for (VirtualGroup group : expiredGroups) {
            if ("ACTIVE".equals(group.getStatus())) {
                group.setStatus("EXPIRED");
                virtualGroupRepository.save(group);
                log.info("Virtual group marked as expired: {}", group.getId());
            }
        }
        
        log.info("Processed {} expired virtual groups", expiredGroups.size());
        return expiredGroups.size();
    }
    
    /**
     * 获取用户所属的虚拟组
     */
    public List<VirtualGroupInfo> getUserGroups(String userId) {
        return virtualGroupRepository.findByUserId(userId).stream()
                .map(VirtualGroupInfo::fromEntity)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取用户作为组长的虚拟组
     */
    public List<VirtualGroupInfo> getUserLeadingGroups(String userId) {
        return virtualGroupRepository.findByLeaderId(userId).stream()
                .map(VirtualGroupInfo::fromEntity)
                .collect(Collectors.toList());
    }
    
    // ==================== 私有方法 ====================
    
    /**
     * 验证有效期
     */
    private void validateValidPeriod(Instant validFrom, Instant validTo) {
        if (validFrom != null && validTo != null && validFrom.isAfter(validTo)) {
            throw new AdminBusinessException("INVALID_PERIOD", "有效期开始时间不能晚于结束时间");
        }
    }
}
