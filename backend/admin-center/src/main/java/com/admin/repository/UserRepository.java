package com.admin.repository;

import com.admin.entity.User;
import com.admin.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户仓库接口
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
     * 根据部门ID查找用户
     */
    List<User> findByDepartmentId(String departmentId);
    
    /**
     * 根据部门ID分页查找用户
     */
    Page<User> findByDepartmentId(String departmentId, Pageable pageable);
    
    /**
     * 根据状态查找用户
     */
    List<User> findByStatus(UserStatus status);
    
    /**
     * 统计部门用户数量
     */
    long countByDepartmentId(String departmentId);
    
    /**
     * 搜索用户（用户名、姓名、邮箱）
     */
    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<User> searchUsers(@Param("keyword") String keyword, Pageable pageable);
    
    /**
     * 根据条件查询用户
     */
    @Query("SELECT u FROM User u WHERE " +
           "u.deleted = false AND " +
           "(:departmentId IS NULL OR u.departmentId = :departmentId) AND " +
           "(:status IS NULL OR u.status = :status) AND " +
           "(:keyword IS NULL OR " +
           "LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<User> findByConditions(
            @Param("departmentId") String departmentId,
            @Param("status") UserStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);
    
    /**
     * 查询未删除的用户
     */
    @Query("SELECT u FROM User u WHERE u.deleted = false")
    Page<User> findAllActive(Pageable pageable);
    
    /**
     * 统计活跃管理员数量
     */
    @Query("SELECT COUNT(u) FROM User u JOIN u.userRoles ur WHERE " +
           "u.deleted = false AND u.status = 'ACTIVE' AND ur.roleCode = 'ADMIN'")
    long countActiveAdmins();
    
    /**
     * 检查邮箱是否被其他用户使用
     */
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.email = :email AND u.id != :excludeUserId AND u.deleted = false")
    boolean existsByEmailExcludingUser(@Param("email") String email, @Param("excludeUserId") String excludeUserId);
}
