package com.admin.repository;

import com.admin.entity.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户偏好设置仓库接口
 */
@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, String> {
    
    /**
     * 根据用户ID和偏好键查找偏好设置
     */
    Optional<UserPreference> findByUserIdAndPreferenceKey(String userId, String preferenceKey);
    
    /**
     * 检查用户是否有指定的偏好设置
     */
    boolean existsByUserIdAndPreferenceKey(String userId, String preferenceKey);
    
    /**
     * 根据用户ID查找所有偏好设置
     */
    List<UserPreference> findByUserId(String userId);
    
    /**
     * 删除用户的指定偏好设置
     */
    void deleteByUserIdAndPreferenceKey(String userId, String preferenceKey);
    
    /**
     * 删除用户的所有偏好设置
     */
    void deleteByUserId(String userId);
}
