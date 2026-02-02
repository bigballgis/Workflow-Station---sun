package com.admin.component;

import com.admin.dto.request.VirtualGroupCreateRequest;
import com.admin.dto.request.VirtualGroupMemberRequest;
import com.admin.dto.response.VirtualGroupInfo;
import com.admin.dto.response.VirtualGroupMemberInfo;
import com.admin.dto.response.VirtualGroupResult;
import com.platform.security.entity.Role;
import com.platform.security.entity.User;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.entity.VirtualGroupMember;
import com.platform.security.entity.VirtualGroupRole;
import com.admin.enums.VirtualGroupType;
import com.admin.exception.AdminBusinessException;
import com.admin.util.EntityTypeConverter;
import com.admin.exception.UserNotFoundException;
import com.admin.exception.VirtualGroupNotFoundException;
import com.admin.repository.RoleRepository;
import com.admin.repository.UserRepository;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.repository.VirtualGroupRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    private final VirtualGroupRoleRepository virtualGroupRoleRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final com.admin.repository.UserBusinessUnitRepository userBusinessUnitRepository;
    private final com.admin.helper.VirtualGroupHelper virtualGroupHelper;

    
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
        
        String groupId = UUID.randomUUID().toString();
        
        // 生成代码（如果未提供）
        String code = request.getCode();
        if (code == null || code.isBlank()) {
            code = generateCode(request.getName());
        }
        
        // 验证代码唯一性
        if (virtualGroupRepository.existsByCode(code)) {
            throw new AdminBusinessException("CODE_EXISTS", "虚拟组代码已存在: " + code);
        }
        
        VirtualGroup group = VirtualGroup.builder()
                .id(groupId)
                .name(request.getName())
                .code(code)
                .type(EntityTypeConverter.fromVirtualGroupType(request.getType()))
                .description(request.getDescription())
                .adGroup(request.getAdGroup())
                .status("ACTIVE")
                .build();
        
        virtualGroupRepository.save(group);
        
        log.info("Virtual group created successfully: {}", groupId);
        return VirtualGroupResult.success(group);
    }
    
    /**
     * 根据名称生成代码
     */
    private String generateCode(String name) {
        return name.toUpperCase()
                .replaceAll("[^A-Z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }
    
    /**
     * 更新虚拟组
     */
    @Transactional
    public VirtualGroupResult updateVirtualGroup(String groupId, VirtualGroupCreateRequest request) {
        log.info("Updating virtual group: {}", groupId);
        
        VirtualGroup group = virtualGroupRepository.findById(groupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(groupId));
        
        // 系统内置虚拟组只能修改 AD Group
        if (EntityTypeConverter.toVirtualGroupType(group.getType()) == VirtualGroupType.SYSTEM) {
            group.setAdGroup(request.getAdGroup());
            virtualGroupRepository.save(group);
            log.info("System virtual group AD group updated: {}", groupId);
            return VirtualGroupResult.success(group);
        }
        
        // 验证名称唯一性（排除自身）
        if (!group.getName().equals(request.getName()) && 
            virtualGroupRepository.existsByName(request.getName())) {
            throw new AdminBusinessException("NAME_EXISTS", "虚拟组名称已存在: " + request.getName());
        }
        
        group.setName(request.getName());
        group.setType(EntityTypeConverter.fromVirtualGroupType(request.getType()));
        group.setDescription(request.getDescription());
        group.setAdGroup(request.getAdGroup());
        
        virtualGroupRepository.save(group);
        
        log.info("Virtual group updated successfully: {}", groupId);
        return VirtualGroupResult.success(group);
    }
    
    /**
     * 删除虚拟组
     * 系统内置虚拟组（type = SYSTEM）不可删除
     */
    @Transactional
    public void deleteVirtualGroup(String groupId) {
        log.info("Deleting virtual group: {}", groupId);
        
        VirtualGroup group = virtualGroupRepository.findById(groupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(groupId));
        
        // 系统内置虚拟组不可删除
        if (EntityTypeConverter.toVirtualGroupType(group.getType()) == VirtualGroupType.SYSTEM) {
            throw new AdminBusinessException("SYSTEM_GROUP_CANNOT_DELETE", 
                    "系统内置虚拟组不可删除: " + group.getName());
        }
        
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
            // type is already a String from platform-security entity, no conversion needed
            groups = virtualGroupRepository.findByTypeAndStatus(type, status);
        } else if (type != null) {
            // type is already a String from platform-security entity, no conversion needed
            groups = virtualGroupRepository.findByType(type);
        } else if (status != null) {
            groups = virtualGroupRepository.findByStatus(status);
        } else {
            groups = virtualGroupRepository.findAll();
        }
        
        // 获取所有虚拟组的角色绑定
        List<String> groupIds = groups.stream().map(VirtualGroup::getId).collect(Collectors.toList());
        Map<String, Role> roleBindings = getRoleBindingsForGroups(groupIds);
        
        return groups.stream()
                .map(group -> {
                    VirtualGroupInfo info = VirtualGroupInfo.fromEntity(group);
                    Role boundRole = roleBindings.get(group.getId());
                    if (boundRole != null) {
                        info.setBoundRoleId(boundRole.getId());
                        info.setBoundRoleName(boundRole.getName());
                        info.setBoundRoleCode(boundRole.getCode());
                        info.setBoundRoleType(boundRole.getType());
                    }
                    return info;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 获取多个虚拟组的角色绑定
     */
    private Map<String, Role> getRoleBindingsForGroups(List<String> groupIds) {
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        
        List<VirtualGroupRole> bindings = virtualGroupRoleRepository.findByVirtualGroupIdIn(groupIds);
        List<String> roleIds = bindings.stream()
                .map(VirtualGroupRole::getRoleId)
                .distinct()
                .collect(Collectors.toList());
        
        Map<String, Role> roleMap = roleRepository.findAllById(roleIds).stream()
                .collect(Collectors.toMap(Role::getId, r -> r));
        
        return bindings.stream()
                .collect(Collectors.toMap(
                        VirtualGroupRole::getVirtualGroupId,
                        binding -> roleMap.get(binding.getRoleId()),
                        (a, b) -> a
                ));
    }
    
    /**
     * 分页查询虚拟组
     */
    public Page<VirtualGroupInfo> listVirtualGroups(String type, String status, 
                                                     String keyword, Pageable pageable) {
        return virtualGroupRepository.findByConditions(type, status, keyword, pageable)
                .map(VirtualGroupInfo::fromEntity);
    }
    
    /**
     * 获取有效的虚拟组列表
     */
    public List<VirtualGroupInfo> getValidGroups() {
        return virtualGroupRepository.findValidGroups().stream()
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
                .groupId(groupId)
                .userId(request.getUserId())
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
     * System Administrators 组必须至少保留一个成员
     */
    @Transactional
    public VirtualGroupResult removeMember(String groupId, String userId) {
        log.info("Removing member {} from virtual group {}", userId, groupId);
        
        VirtualGroup group = virtualGroupRepository.findById(groupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(groupId));
        
        VirtualGroupMember member = virtualGroupMemberRepository
                .findByVirtualGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new AdminBusinessException("MEMBER_NOT_FOUND", "用户不是该虚拟组成员"));
        
        // System Administrators 组必须至少保留一个成员
        if (EntityTypeConverter.toVirtualGroupType(group.getType()) == VirtualGroupType.SYSTEM && "SYS_ADMINS".equals(group.getCode())) {
            long memberCount = virtualGroupMemberRepository.countByVirtualGroupId(groupId);
            if (memberCount <= 1) {
                throw new AdminBusinessException("LAST_ADMIN_CANNOT_REMOVE", 
                        "System Administrators 组必须至少保留一个成员");
            }
        }
        
        virtualGroupMemberRepository.delete(member);
        
        log.info("Member removed successfully: {} from group {}", userId, groupId);
        return VirtualGroupResult.success(group);
    }
    
    /**
     * 获取虚拟组成员列表
     */
    public List<VirtualGroupMemberInfo> getGroupMembers(String groupId) {
        if (!virtualGroupRepository.existsById(groupId)) {
            throw new VirtualGroupNotFoundException(groupId);
        }
        
        // 获取虚拟组
        VirtualGroup group = virtualGroupRepository.findById(groupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(groupId));
        
        // 获取业务单元名称映射
        Map<String, String> businessUnitNameMap = getBusinessUnitNameMap();
        
        List<VirtualGroupMember> members = virtualGroupMemberRepository.findByVirtualGroupId(groupId);
        
        // 批量获取用户
        List<String> userIds = members.stream()
                .map(VirtualGroupMember::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<String, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        
        return members.stream()
                .map(member -> {
                    User user = userMap.get(member.getUserId());
                    VirtualGroupMemberInfo info = VirtualGroupMemberInfo.fromEntity(member, group, user);
                    // 通过关联表获取用户的业务单元
                    if (member.getUserId() != null) {
                        List<com.platform.security.entity.UserBusinessUnit> userBusinessUnits = 
                                userBusinessUnitRepository.findByUserId(member.getUserId());
                        if (!userBusinessUnits.isEmpty()) {
                            String businessUnitId = userBusinessUnits.get(0).getBusinessUnitId();
                            info.setBusinessUnitId(businessUnitId);
                            info.setBusinessUnitName(businessUnitNameMap.get(businessUnitId));
                        }
                    }
                    return info;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 获取业务单元ID到名称的映射
     */
    private Map<String, String> getBusinessUnitNameMap() {
        try {
            return jdbcTemplate.query(
                "SELECT id, name FROM sys_business_units",
                (rs, rowNum) -> Map.entry(rs.getString("id"), rs.getString("name"))
            ).stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
        } catch (Exception e) {
            log.warn("Failed to get business unit names", e);
            return Map.of();
        }
    }
    
    /**
     * 分页获取虚拟组成员
     */
    public Page<VirtualGroupMemberInfo> getGroupMembers(String groupId, Pageable pageable) {
        if (!virtualGroupRepository.existsById(groupId)) {
            throw new VirtualGroupNotFoundException(groupId);
        }
        
        // 获取虚拟组
        VirtualGroup group = virtualGroupRepository.findById(groupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(groupId));
        
        Page<VirtualGroupMember> membersPage = virtualGroupMemberRepository.findByVirtualGroupId(groupId, pageable);
        
        // 批量获取用户
        List<String> userIds = membersPage.getContent().stream()
                .map(VirtualGroupMember::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<String, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        
        return membersPage.map(member -> {
            User user = userMap.get(member.getUserId());
            return VirtualGroupMemberInfo.fromEntity(member, group, user);
        });
    }
    
    /**
     * 检查用户是否是虚拟组成员
     */
    public boolean isGroupMember(String groupId, String userId) {
        return virtualGroupMemberRepository.existsByVirtualGroupIdAndUserId(groupId, userId);
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
     * 获取用户所属的虚拟组
     */
    public List<VirtualGroupInfo> getUserGroups(String userId) {
        return virtualGroupRepository.findByUserId(userId).stream()
                .map(VirtualGroupInfo::fromEntity)
                .collect(Collectors.toList());
    }
}
