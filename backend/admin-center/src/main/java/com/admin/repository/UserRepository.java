package com.admin.repository;

import com.platform.security.entity.User;
import com.platform.security.model.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 用户仓库接口 - 使用统一的 sys_users 表
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {
    
    /**
     * 根据用户名查找用户
     */
    Optional<User> findByUsername(String username);
    
    /**
     * 根据邮箱查找用户
     */
    Optional<User> findByEmail(String email);
    
    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(String username);
    
    /**
     * 检查邮箱是否存在
     */
    boolean existsByEmail(String email);
    
    /**
     * 根据状态查找用户
     */
    List<User> findByStatus(UserStatus status);
    
    /**
     * 搜索用户（用户名、姓名、邮箱）
     */
    @Query("SELECT u FROM User u WHERE " +
           "u.username LIKE CONCAT('%', :keyword, '%') OR " +
           "u.fullName LIKE CONCAT('%', :keyword, '%') OR " +
           "u.displayName LIKE CONCAT('%', :keyword, '%') OR " +
           "u.email LIKE CONCAT('%', :keyword, '%')")
    Page<User> searchUsers(@Param("keyword") String keyword, Pageable pageable);
    
    /**
     * 根据条件查询用户（不再使用 businessUnitId 字段，改用关联表）
     */
    @Query("SELECT DISTINCT u FROM User u WHERE " +
           "(u.deleted = false OR u.deleted IS NULL) AND " +
           "(:businessUnitId IS NULL OR u.id IN (SELECT ub.userId FROM UserBusinessUnit ub WHERE ub.businessUnitId = :businessUnitId)) AND " +
           "(:status IS NULL OR u.status = :status) AND " +
           "(:keyword IS NULL OR :keyword = '' OR " +
           "u.username LIKE CONCAT('%', :keyword, '%') OR " +
           "u.fullName LIKE CONCAT('%', :keyword, '%') OR " +
           "u.displayName LIKE CONCAT('%', :keyword, '%') OR " +
           "u.email LIKE CONCAT('%', :keyword, '%'))")
    Page<User> findByConditions(
            @Param("businessUnitId") String businessUnitId,
            @Param("status") UserStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);
    
    /**
     * 查询未删除的用户
     */
    @Query("SELECT u FROM User u WHERE u.deleted = false OR u.deleted IS NULL")
    Page<User> findAllActive(Pageable pageable);
    
    /**
     * 统计活跃管理员数量
     * Note: User entity from platform-security doesn't have userRoles relationship, use native query instead
     */
    @Query(value = "SELECT COUNT(DISTINCT u.id) FROM sys_users u " +
           "JOIN sys_user_roles ur ON u.id = ur.user_id " +
           "JOIN sys_roles r ON ur.role_id = r.id " +
           "WHERE (u.deleted = false OR u.deleted IS NULL) AND u.status = 'ACTIVE' AND r.code = 'ADMIN'",
           nativeQuery = true)
    long countActiveAdmins();
    
    /**
     * 根据ID查找用户
     * Note: User entity from platform-security doesn't have userRoles relationship
     */
    Optional<User> findById(String userId);
    
    /**
     * 检查邮箱是否被其他用户使用
     */
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.email = :email AND u.id != :excludeUserId AND (u.deleted = false OR u.deleted IS NULL)")
    boolean existsByEmailExcludingUser(@Param("email") String email, @Param("excludeUserId") String excludeUserId);
    
    /**
     * 统计指定时间之后登录的用户数（在线用户）
     */
    long countByLastLoginAtAfter(LocalDateTime timestamp);
    
    /**
     * 统计指定时间之后创建的用户数（今日新增）
     */
    long countByCreatedAtAfter(LocalDateTime timestamp);
    
    /**
     * 统计指定时间范围内创建的用户数
     */
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    /**
     * 通过关联表查询业务单元成员（多对多关系）
     */
    @Query("SELECT u FROM User u WHERE u.id IN " +
           "(SELECT ub.userId FROM UserBusinessUnit ub WHERE ub.businessUnitId = :businessUnitId) " +
           "AND (u.deleted = false OR u.deleted IS NULL)")
    Page<User> findMembersByBusinessUnitId(@Param("businessUnitId") String businessUnitId, Pageable pageable);
    
    /**
     * 通过关联表统计业务单元成员数量（多对多关系）
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.id IN " +
           "(SELECT ub.userId FROM UserBusinessUnit ub WHERE ub.businessUnitId = :businessUnitId) " +
           "AND (u.deleted = false OR u.deleted IS NULL)")
    long countMembersByBusinessUnitId(@Param("businessUnitId") String businessUnitId);
}
