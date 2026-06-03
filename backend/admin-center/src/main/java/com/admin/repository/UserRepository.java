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
 * User repository — uses the shared {@code sys_users} table.
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {
    
    /**
     * Find user by username (includes soft-deleted rows; supports reviving legacy data).
     */
    Optional<User> findByUsername(String username);
    
    /**
     * Find user by email.
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Whether the username is already taken by a non-deleted user.
     */
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.username = :username " +
           "AND (u.deleted = false OR u.deleted IS NULL)")
    boolean existsByUsername(@Param("username") String username);
    
    /**
     * Whether the email is already taken by a non-deleted user.
     */
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.email = :email " +
           "AND (u.deleted = false OR u.deleted IS NULL)")
    boolean existsByEmail(@Param("email") String email);
    
    /**
     * Find users by account status.
     */
    List<User> findByStatus(UserStatus status);
    
    /**
     * Search users by username, full name, display name, or email.
     */
    @Query("SELECT u FROM User u WHERE " +
           "u.username LIKE CONCAT('%', :keyword, '%') OR " +
           "u.fullName LIKE CONCAT('%', :keyword, '%') OR " +
           "u.displayName LIKE CONCAT('%', :keyword, '%') OR " +
           "u.email LIKE CONCAT('%', :keyword, '%')")
    Page<User> searchUsers(@Param("keyword") String keyword, Pageable pageable);
    
    /**
     * Query users by filters ({@code businessUnitId} via join table, not a column on User).
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
     * Non-deleted users (paged).
     */
    @Query("SELECT u FROM User u WHERE u.deleted = false OR u.deleted IS NULL")
    Page<User> findAllActive(Pageable pageable);
    
    /**
     * Count active users who have SYS_ADMIN or AUDITOR.
     * Note: platform-security {@code User} has no {@code userRoles}; use native SQL.
     */
    @Query(value = "SELECT COUNT(DISTINCT u.id) FROM sys_users u " +
           "JOIN sys_user_roles ur ON u.id = ur.user_id " +
           "JOIN sys_roles r ON ur.role_id = r.id " +
           "WHERE (u.deleted = false OR u.deleted IS NULL) AND u.status = 'ACTIVE' AND r.code IN ('SYS_ADMIN', 'AUDITOR')",
           nativeQuery = true)
    long countActiveAdmins();
    
    /**
     * Whether the user has SYS_ADMIN or AUDITOR.
     */
    @Query(value = "SELECT COUNT(*) > 0 FROM sys_user_roles ur " +
           "JOIN sys_roles r ON ur.role_id = r.id " +
           "WHERE ur.user_id = :userId AND r.code IN ('SYS_ADMIN', 'AUDITOR')",
           nativeQuery = true)
    boolean isUserAdmin(@Param("userId") String userId);
    
    /**
     * Find user by id.
     * Note: platform-security {@code User} has no {@code userRoles} relationship.
     */
    Optional<User> findById(String userId);
    
    /**
     * Whether the email is used by another non-deleted user (excluding {@code excludeUserId}).
     */
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.email = :email AND u.id != :excludeUserId AND (u.deleted = false OR u.deleted IS NULL)")
    boolean existsByEmailExcludingUser(@Param("email") String email, @Param("excludeUserId") String excludeUserId);
    
    /**
     * Users who logged in after the timestamp (approx. online users).
     */
    long countByLastLoginAtAfter(LocalDateTime timestamp);
    
    /**
     * Users created after the timestamp (e.g. today's new users).
     */
    long countByCreatedAtAfter(LocalDateTime timestamp);

    /**
     * Count non-deleted users (excludes soft-deleted rows).
     * Dashboard 与用户列表保持同一软删口径。
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.deleted = false OR u.deleted IS NULL")
    long countActive();
    
    /**
     * Count users created in the time range.
     */
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT CAST(u.createdAt AS DATE), COUNT(u) FROM User u " +
           "WHERE u.createdAt >= :start AND u.createdAt < :end " +
           "AND (u.deleted = false OR u.deleted IS NULL) " +
           "GROUP BY CAST(u.createdAt AS DATE)")
    List<Object[]> countDailyNewUsers(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    
    /**
     * Business unit members via join table (many-to-many).
     */
    @Query("SELECT u FROM User u WHERE u.id IN " +
           "(SELECT ub.userId FROM UserBusinessUnit ub WHERE ub.businessUnitId = :businessUnitId) " +
           "AND (u.deleted = false OR u.deleted IS NULL)")
    Page<User> findMembersByBusinessUnitId(@Param("businessUnitId") String businessUnitId, Pageable pageable);
    
    /**
     * Count business unit members via join table (many-to-many).
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.id IN " +
           "(SELECT ub.userId FROM UserBusinessUnit ub WHERE ub.businessUnitId = :businessUnitId) " +
           "AND (u.deleted = false OR u.deleted IS NULL)")
    long countMembersByBusinessUnitId(@Param("businessUnitId") String businessUnitId);
}
