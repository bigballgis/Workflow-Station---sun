package com.admin.service;

import com.admin.entity.*;
import com.admin.enums.RoleType;
import com.admin.helper.RoleHelper;
import com.admin.util.EntityTypeConverter;
import com.admin.repository.*;
import com.platform.security.entity.Role;
import com.platform.security.entity.BusinessUnit;
import com.platform.security.entity.UserBusinessUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户权限查询服务
 * 提供用户角色和权限的查询功能
 * 
 * **Validates: Requirements 17.1, 17.2, 17.3, 17.4, 18.1, 18.2, 18.6, 18.7**
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserPermissionService {
    
    private final VirtualGroupMemberRepository virtualGroupMemberRepository;
    private final VirtualGroupRoleRepository virtualGroupRoleRepository;
    private final UserBusinessUnitRepository userBusinessUnitRepository;
    private final RoleRepository roleRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final BusinessUnitRepository businessUnitRepository;
    private final RoleHelper roleHelper;
    
    /**
     * 获取用户的所有角色（通过虚拟组继承）
     * 
     * @param userId 用户ID
     * @return 用户的所有角色列表
     */
    public List<Role> getUserRoles(String userId) {
        // 获取用户所属的所有虚拟组
        List<String> virtualGroupIds = virtualGroupMemberRepository.findVirtualGroupIdsByUserId(userId);
        
        if (virtualGroupIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 获取每个虚拟组绑定的角色
        Set<String> roleIds = new HashSet<>();
        for (String vgId : virtualGroupIds) {
            virtualGroupRoleRepository.findByVirtualGroupId(vgId)
                    .ifPresent(binding -> roleIds.add(binding.getRoleId()));
        }
        
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        return roleRepository.findAllById(roleIds).stream()
                .filter(role -> "ACTIVE".equals(role.getStatus()))
                .collect(Collectors.toList());
    }
    
    /**
     * 获取用户的 BU-Bounded 角色及其关联的业务单元
     * 
     * @param userId 用户ID
     * @return 角色到业务单元列表的映射
     */
    public Map<Role, List<BusinessUnit>> getUserBuBoundedRoles(String userId) {
        List<Role> allRoles = getUserRoles(userId);
        
        // 过滤出 BU_BOUNDED 类型的角色 - 使用 RoleHelper
        List<Role> buBoundedRoles = allRoles.stream()
                .filter(role -> "BU_BOUNDED".equals(role.getType()))
                .collect(Collectors.toList());
        
        if (buBoundedRoles.isEmpty()) {
            return Collections.emptyMap();
        }
        
        // 获取用户加入的所有业务单元
        List<UserBusinessUnit> userBusinessUnits = userBusinessUnitRepository.findByUserId(userId);
        List<String> businessUnitIds = userBusinessUnits.stream()
                .map(UserBusinessUnit::getBusinessUnitId)
                .collect(Collectors.toList());
        
        List<BusinessUnit> businessUnits = businessUnitIds.isEmpty() 
                ? Collections.emptyList() 
                : businessUnitRepository.findAllById(businessUnitIds);
        
        // 构建角色到业务单元的映射
        // 注意：BU-Bounded 角色在用户加入的所有业务单元中都生效
        Map<Role, List<BusinessUnit>> result = new HashMap<>();
        for (Role role : buBoundedRoles) {
            result.put(role, new ArrayList<>(businessUnits));
        }
        
        return result;
    }
    
    /**
     * 获取用户的 BU-Unbounded 角色
     * 
     * @param userId 用户ID
     * @return BU-Unbounded 角色列表
     */
    public List<Role> getUserBuUnboundedRoles(String userId) {
        return getUserRoles(userId).stream()
                .filter(role -> "BU_UNBOUNDED".equals(role.getType()))
                .collect(Collectors.toList());
    }

    
    /**
     * 检查用户是否在指定业务单元中拥有指定角色
     * 
     * @param userId 用户ID
     * @param roleId 角色ID
     * @param businessUnitId 业务单元ID
     * @return 是否拥有该角色
     */
    public boolean hasRoleInBusinessUnit(String userId, String roleId, String businessUnitId) {
        // 获取用户的所有角色
        List<Role> userRoles = getUserRoles(userId);
        
        // 检查用户是否拥有该角色
        Optional<Role> targetRole = userRoles.stream()
                .filter(role -> role.getId().equals(roleId))
                .findFirst();
        
        if (targetRole.isEmpty()) {
            return false;
        }
        
        Role role = targetRole.get();
        
        // 如果是 BU-Unbounded 角色，直接返回 true（不需要业务单元）
        if (EntityTypeConverter.toRoleType(role.getType()) == RoleType.BU_UNBOUNDED) {
            return true;
        }
        
        // 如果是 BU-Bounded 角色，检查用户是否是该业务单元的成员
        if (EntityTypeConverter.toRoleType(role.getType()) == RoleType.BU_BOUNDED) {
            return userBusinessUnitRepository.existsByUserIdAndBusinessUnitId(userId, businessUnitId);
        }
        
        // 其他类型角色（ADMIN, DEVELOPER）不需要业务单元
        return true;
    }
    
    /**
     * 获取用户未在任何业务单元激活的 BU-Bounded 角色
     * 即用户拥有 BU-Bounded 角色，但未加入任何业务单元
     * 
     * **Validates: Requirements 18.1, 18.2**
     * 
     * @param userId 用户ID
     * @return 未激活的 BU-Bounded 角色列表
     */
    public List<Role> getUnactivatedBuBoundedRoles(String userId) {
        // 获取用户的所有 BU-Bounded 角色
        List<Role> buBoundedRoles = getUserRoles(userId).stream()
                .filter(role -> EntityTypeConverter.toRoleType(role.getType()) == RoleType.BU_BOUNDED)
                .collect(Collectors.toList());
        
        if (buBoundedRoles.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 检查用户是否加入了任何业务单元
        long businessUnitCount = userBusinessUnitRepository.countByUserId(userId);
        
        // 如果用户没有加入任何业务单元，则所有 BU-Bounded 角色都是未激活的
        if (businessUnitCount == 0) {
            return buBoundedRoles;
        }
        
        // 如果用户已加入业务单元，则所有 BU-Bounded 角色都已激活
        return Collections.emptyList();
    }
    
    /**
     * 检查用户是否需要显示业务单元申请提醒
     * 条件：有未激活的 BU-Bounded 角色 且 未设置"不再提醒"
     * 
     * **Validates: Requirements 18.6, 18.7**
     * 
     * @param userId 用户ID
     * @return 是否需要显示提醒
     */
    public boolean shouldShowBuApplicationReminder(String userId) {
        // 检查是否有未激活的 BU-Bounded 角色
        List<Role> unactivatedRoles = getUnactivatedBuBoundedRoles(userId);
        
        if (unactivatedRoles.isEmpty()) {
            return false;
        }
        
        // 检查用户是否设置了"不再提醒"
        Optional<UserPreference> preference = userPreferenceRepository
                .findByUserIdAndPreferenceKey(userId, UserPreference.KEY_DONT_REMIND_BU_APPLICATION);
        
        if (preference.isPresent() && "true".equals(preference.get().getPreferenceValue())) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 设置用户的"不再提醒"偏好
     * 
     * **Validates: Requirements 18.7**
     * 
     * @param userId 用户ID
     * @param dontRemind 是否不再提醒
     */
    @Transactional
    public void setDontRemindPreference(String userId, boolean dontRemind) {
        Optional<UserPreference> existingPreference = userPreferenceRepository
                .findByUserIdAndPreferenceKey(userId, UserPreference.KEY_DONT_REMIND_BU_APPLICATION);
        
        if (existingPreference.isPresent()) {
            UserPreference preference = existingPreference.get();
            preference.setPreferenceValue(String.valueOf(dontRemind));
            preference.setUpdatedAt(Instant.now());
            userPreferenceRepository.save(preference);
            log.info("Updated dont_remind preference for user {} to {}", userId, dontRemind);
        } else {
            UserPreference preference = UserPreference.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .preferenceKey(UserPreference.KEY_DONT_REMIND_BU_APPLICATION)
                    .preferenceValue(String.valueOf(dontRemind))
                    .createdAt(Instant.now())
                    .build();
            userPreferenceRepository.save(preference);
            log.info("Created dont_remind preference for user {} with value {}", userId, dontRemind);
        }
    }
    
    /**
     * 获取用户的"不再提醒"偏好值
     * 
     * @param userId 用户ID
     * @return 是否设置了不再提醒
     */
    public boolean getDontRemindPreference(String userId) {
        return userPreferenceRepository
                .findByUserIdAndPreferenceKey(userId, UserPreference.KEY_DONT_REMIND_BU_APPLICATION)
                .map(pref -> "true".equals(pref.getPreferenceValue()))
                .orElse(false);
    }
    
    /**
     * 重置用户的"不再提醒"偏好（用于测试或管理员操作）
     * 
     * @param userId 用户ID
     */
    @Transactional
    public void resetDontRemindPreference(String userId) {
        userPreferenceRepository.deleteByUserIdAndPreferenceKey(userId, UserPreference.KEY_DONT_REMIND_BU_APPLICATION);
        log.info("Reset dont_remind preference for user {}", userId);
    }
}
