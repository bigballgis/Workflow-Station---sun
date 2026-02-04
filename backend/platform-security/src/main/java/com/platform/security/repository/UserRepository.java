package com.platform.security.repository;

import com.platform.security.entity.User;
import com.platform.security.model.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for User entity operations.
 * Uses String ID to match sys_users table.
 * Validates: Requirements 1.1
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    /**
     * Find user by username.
     *
     * @param username the username to search
     * @return Optional containing the user if found
     */
    Optional<User> findByUsername(String username);

    /**
     * Check if a user exists with the given username.
     *
     * @param username the username to check
     * @return true if user exists
     */
    boolean existsByUsername(String username);

    /**
     * Check if a user exists with the given email.
     *
     * @param email the email to check
     * @return true if user exists
     */
    boolean existsByEmail(String email);

    /**
     * Find user by email.
     *
     * @param email the email to search
     * @return Optional containing the user if found
     */
    Optional<User> findByEmail(String email);

    /**
     * Find all users with a specific status.
     *
     * @param status the user status
     * @return list of users with the given status
     */
    List<User> findByStatus(UserStatus status);

    /**
     * Find all users with a specific role.
     * Uses user_roles and roles join tables since User entity no longer has roles field.
     *
     * @param roleCode the role code
     * @return list of users with the role
     */
    @Query("SELECT DISTINCT u FROM User u " +
           "JOIN UserRole ur ON ur.userId = u.id " +
           "JOIN Role r ON r.id = ur.roleId " +
           "WHERE r.code = :roleCode")
    List<User> findByRole(@Param("roleCode") String roleCode);

    /**
     * Count active users.
     *
     * @return count of active users
     */
    long countByStatus(UserStatus status);
}
