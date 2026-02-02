package com.admin.repository;

import com.platform.security.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 角色仓库接口
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, String> {
    
    /**
     * 根据编码查找角色
     */
    Optional<Role> findByCode(String code);
    
    /**
     * 检查编码是否存在
     */
    boolean existsByCode(String code);
    
    /**
     * 根据类型查找角色
     * Note: Role.type is String, not enum
     */
    List<Role> findByType(String type);
    
    /**
     * 查找所有活跃角色
     */
    @Query("SELECT r FROM Role r WHERE r.status = 'ACTIVE' ORDER BY r.type, r.name")
    List<Role> findAllActive();
    
    /**
     * 分页查询角色
     * Note: Role.type is String, not enum
     */
    @Query("SELECT r FROM Role r WHERE " +
           "(:type IS NULL OR r.type = :type) AND " +
           "(:keyword IS NULL OR LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(r.code) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Role> findByConditions(
            @Param("type") String type,
            @Param("keyword") String keyword,
            Pageable pageable);
    
    /**
     * 查找用户的所有角色
     * Note: Using native query since platform-security Role doesn't have userRoles relationship
     */
    @Query(value = "SELECT r.* FROM sys_roles r " +
           "JOIN sys_user_roles ur ON r.id = ur.role_id " +
           "WHERE ur.user_id = :userId AND r.status = 'ACTIVE'",
           nativeQuery = true)
    List<Role> findByUserId(@Param("userId") String userId);
}
