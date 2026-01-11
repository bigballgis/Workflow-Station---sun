package com.platform.security.repository;

import com.platform.security.model.User;
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
     * Find all users in a specific department.
     *
     * @param departmentId the department ID
     * @return list of users in the department
     */
    List<User> findByDepartmentId(String departmentId);

    /**
     * Find all users with a specific role.
     *
     * @param roleCode the role code
     * @return list of users with the role
     */
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r = :roleCode")
    List<User> findByRole(@Param("roleCode") String roleCode);

    /**
     * Count active users.
     *
     * @return count of active users
     */
    long countByStatus(UserStatus status);
}
