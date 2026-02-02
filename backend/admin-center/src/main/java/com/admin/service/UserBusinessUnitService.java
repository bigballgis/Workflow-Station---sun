package com.admin.service;

import com.platform.security.entity.BusinessUnit;
import com.platform.security.entity.UserBusinessUnit;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.BusinessUnitNotFoundException;
import com.admin.exception.UserNotFoundException;
import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.UserBusinessUnitRepository;
import com.admin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户业务单元成员服务
 * 管理用户与业务单元的成员关系（不包含角色）
 * 用户通过加入虚拟组获取角色，通过加入业务单元激活 BU-Bounded 角色
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserBusinessUnitService {
    
    private final UserBusinessUnitRepository userBusinessUnitRepository;
    private final UserRepository userRepository;
    private final BusinessUnitRepository businessUnitRepository;
    
    /**
     * 添加用户到业务单元
     * @param userId 用户ID
     * @param businessUnitId 业务单元ID
     * @return 创建的成员关系
     */
    @Transactional
    public UserBusinessUnit addUserToBusinessUnit(String userId, String businessUnitId) {
        log.info("Adding user {} to business unit {}", userId, businessUnitId);
        
        // 验证用户存在
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }
        
        // 验证业务单元存在
        if (!businessUnitRepository.existsById(businessUnitId)) {
            throw new BusinessUnitNotFoundException(businessUnitId);
        }
        
        // 检查是否已经是成员
        if (userBusinessUnitRepository.existsByUserIdAndBusinessUnitId(userId, businessUnitId)) {
            throw new AdminBusinessException("ALREADY_MEMBER", "用户已经是该业务单元的成员");
        }
        
        UserBusinessUnit membership = UserBusinessUnit.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .businessUnitId(businessUnitId)
                .build();
        
        UserBusinessUnit saved = userBusinessUnitRepository.save(membership);
        log.info("User {} added to business unit {} successfully", userId, businessUnitId);
        
        return saved;
    }
    
    /**
     * 从业务单元移除用户
     * @param userId 用户ID
     * @param businessUnitId 业务单元ID
     */
    @Transactional
    public void removeUserFromBusinessUnit(String userId, String businessUnitId) {
        log.info("Removing user {} from business unit {}", userId, businessUnitId);
        
        UserBusinessUnit membership = userBusinessUnitRepository
                .findByUserIdAndBusinessUnitId(userId, businessUnitId)
                .orElseThrow(() -> new AdminBusinessException("NOT_MEMBER", "用户不是该业务单元的成员"));
        
        userBusinessUnitRepository.delete(membership);
        log.info("User {} removed from business unit {} successfully", userId, businessUnitId);
    }
    
    /**
     * 获取用户的所有业务单元
     * @param userId 用户ID
     * @return 业务单元列表
     */
    public List<BusinessUnit> getUserBusinessUnits(String userId) {
        List<UserBusinessUnit> memberships = userBusinessUnitRepository.findByUserId(userId);
        
        // Extract business unit IDs
        List<String> businessUnitIds = memberships.stream()
                .map(UserBusinessUnit::getBusinessUnitId)
                .distinct()
                .collect(Collectors.toList());
        
        // Batch fetch business units
        return businessUnitRepository.findAllById(businessUnitIds);
    }
    
    /**
     * 获取用户的所有业务单元ID
     * @param userId 用户ID
     * @return 业务单元ID列表
     */
    public List<String> getUserBusinessUnitIds(String userId) {
        return userBusinessUnitRepository.findByUserId(userId)
                .stream()
                .map(UserBusinessUnit::getBusinessUnitId)
                .collect(Collectors.toList());
    }
    
    /**
     * 检查用户是否是业务单元的成员
     * @param userId 用户ID
     * @param businessUnitId 业务单元ID
     * @return 是否是成员
     */
    public boolean isMember(String userId, String businessUnitId) {
        return userBusinessUnitRepository.existsByUserIdAndBusinessUnitId(userId, businessUnitId);
    }
    
    /**
     * 获取业务单元的所有成员ID
     * @param businessUnitId 业务单元ID
     * @return 用户ID列表
     */
    public List<String> getBusinessUnitMemberIds(String businessUnitId) {
        return userBusinessUnitRepository.findByBusinessUnitId(businessUnitId)
                .stream()
                .map(UserBusinessUnit::getUserId)
                .collect(Collectors.toList());
    }
    
    /**
     * 统计业务单元的成员数量
     * @param businessUnitId 业务单元ID
     * @return 成员数量
     */
    public long countMembers(String businessUnitId) {
        return userBusinessUnitRepository.countByBusinessUnitId(businessUnitId);
    }
    
    /**
     * 统计用户加入的业务单元数量
     * @param userId 用户ID
     * @return 业务单元数量
     */
    public long countUserBusinessUnits(String userId) {
        return userBusinessUnitRepository.countByUserId(userId);
    }
}
