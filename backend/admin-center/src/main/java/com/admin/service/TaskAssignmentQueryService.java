package com.admin.service;

import com.platform.security.entity.BusinessUnit;
import com.platform.security.entity.Role;
import com.platform.security.entity.User;
import com.platform.security.entity.UserBusinessUnitRole;
import com.admin.enums.RoleType;
import com.admin.util.EntityTypeConverter;
import com.admin.exception.BusinessUnitNotFoundException;
import com.admin.exception.RoleNotFoundException;
import com.admin.exception.UserNotFoundException;
import com.admin.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 任务分配查询服务
 * 提供工作流任务分配所需的用户查询功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskAssignmentQueryService {
    
    private final UserRepository userRepository;
    private final BusinessUnitRepository businessUnitRepository;
    private final RoleRepository roleRepository;
    private final UserBusinessUnitRoleRepository userBusinessUnitRoleRepository;
    private final VirtualGroupRoleRepository virtualGroupRoleRepository;
    private final VirtualGroupMemberRepository virtualGroupMemberRepository;
    private final BusinessUnitRoleRepository businessUnitRoleRepository;
    
    /**
     * 获取用户的业务单元ID
     * @param userId 用户ID
     * @return 业务单元ID，如果用户没有业务单元则返回null
     */
    public String getUserBusinessUnitId(String userId) {
        log.debug("Getting business unit ID for user: {}", userId);
        
        // 查找用户的业务单元角色分配，取第一个业务单元
        List<UserBusinessUnitRole> assignments = userBusinessUnitRoleRepository.findByUserId(userId);
        if (assignments.isEmpty()) {
            log.debug("User {} has no business unit assignment", userId);
            return null;
        }
        
        // 返回第一个业务单元ID
        String businessUnitId = assignments.get(0).getBusinessUnitId();
        log.debug("User {} belongs to business unit: {}", userId, businessUnitId);
        return businessUnitId;
    }
    
    /**
     * 获取业务单元的父业务单元ID
     * @param businessUnitId 业务单元ID
     * @return 父业务单元ID，如果没有父级则返回null
     */
    public String getParentBusinessUnitId(String businessUnitId) {
        log.debug("Getting parent business unit ID for: {}", businessUnitId);
        
        BusinessUnit businessUnit = businessUnitRepository.findById(businessUnitId)
                .orElseThrow(() -> new BusinessUnitNotFoundException(businessUnitId));
        
        String parentId = businessUnit.getParentId();
        log.debug("Business unit {} has parent: {}", businessUnitId, parentId);
        return parentId;
    }
    
    /**
     * 获取业务单元中拥有指定角色的用户ID列表
     * @param businessUnitId 业务单元ID
     * @param roleId 角色ID（BU_BOUNDED类型）
     * @return 用户ID列表
     */
    public List<String> getUsersByBusinessUnitAndRole(String businessUnitId, String roleId) {
        log.debug("Getting users by business unit {} and role {}", businessUnitId, roleId);
        
        // 验证业务单元存在
        if (!businessUnitRepository.existsById(businessUnitId)) {
            throw new BusinessUnitNotFoundException(businessUnitId);
        }
        
        // 验证角色存在且为BU_BOUNDED类型
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        
        if (EntityTypeConverter.toRoleType(role.getType()) != RoleType.BU_BOUNDED) {
            log.warn("Role {} is not BU_BOUNDED type, actual type: {}", roleId, role.getType());
            return Collections.emptyList();
        }
        
        List<String> userIds = userBusinessUnitRoleRepository.findUserIdsByBusinessUnitIdAndRoleId(businessUnitId, roleId);
        log.debug("Found {} users with role {} in business unit {}", userIds.size(), roleId, businessUnitId);
        return userIds;
    }
    
    /**
     * 获取拥有指定BU无关型角色的用户ID列表
     * 通过查询绑定了该角色的虚拟组的所有成员
     * @param roleId 角色ID（BU_UNBOUNDED类型）
     * @return 用户ID列表
     */
    public List<String> getUsersByUnboundedRole(String roleId) {
        log.debug("Getting users by unbounded role: {}", roleId);
        
        // 验证角色存在且为BU_UNBOUNDED类型
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        
        if (EntityTypeConverter.toRoleType(role.getType()) != RoleType.BU_UNBOUNDED) {
            log.warn("Role {} is not BU_UNBOUNDED type, actual type: {}", roleId, role.getType());
            return Collections.emptyList();
        }
        
        // 查找绑定了该角色的所有虚拟组
        List<String> virtualGroupIds = virtualGroupRoleRepository.findVirtualGroupIdsByRoleId(roleId);
        if (virtualGroupIds.isEmpty()) {
            log.debug("No virtual groups bound to role {}", roleId);
            return Collections.emptyList();
        }
        
        // 查找这些虚拟组的所有成员
        List<String> userIds = virtualGroupMemberRepository.findUserIdsByVirtualGroupIds(virtualGroupIds);
        log.debug("Found {} users with unbounded role {} through {} virtual groups", 
                userIds.size(), roleId, virtualGroupIds.size());
        return userIds;
    }
    
    /**
     * 获取业务单元的准入角色ID列表
     * @param businessUnitId 业务单元ID
     * @return 角色ID列表
     */
    public List<String> getEligibleRoleIds(String businessUnitId) {
        log.debug("Getting eligible role IDs for business unit: {}", businessUnitId);
        
        // 验证业务单元存在
        if (!businessUnitRepository.existsById(businessUnitId)) {
            throw new BusinessUnitNotFoundException(businessUnitId);
        }
        
        List<String> roleIds = businessUnitRoleRepository.findByBusinessUnitId(businessUnitId)
                .stream()
                .map(bur -> bur.getRoleId())
                .collect(Collectors.toList());
        
        log.debug("Business unit {} has {} eligible roles", businessUnitId, roleIds.size());
        return roleIds;
    }
    
    /**
     * 检查角色是否是业务单元的准入角色
     * @param businessUnitId 业务单元ID
     * @param roleId 角色ID
     * @return 是否是准入角色
     */
    public boolean isEligibleRole(String businessUnitId, String roleId) {
        return businessUnitRoleRepository.existsByBusinessUnitIdAndRoleId(businessUnitId, roleId);
    }
    
    /**
     * 获取所有BU绑定型角色
     * @return 角色列表
     */
    public List<Role> getBuBoundedRoles() {
        return roleRepository.findByType("BU_BOUNDED");
    }
    
    /**
     * 获取所有BU无关型角色
     * @return 角色列表
     */
    public List<Role> getBuUnboundedRoles() {
        return roleRepository.findByType("BU_UNBOUNDED");
    }
}
