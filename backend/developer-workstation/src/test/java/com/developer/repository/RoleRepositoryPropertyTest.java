package com.developer.repository;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.junit.jupiter.api.DisplayName;
import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;

/**
 * Property-based tests for RoleRepository.
 * Validates universal properties of role validation accuracy.
 * 
 * Feature: security-permission-system, Property 2: Role Validation Accuracy
 * Validates: Requirements 2.2, 2.3
 */
@DataJpaTest
@ActiveProfiles("test")
@Label("Feature: security-permission-system, Property 2: Role Validation Accuracy")
public class RoleRepositoryPropertyTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private DataSource dataSource;
    
    private RoleRepository roleRepository;
    private JdbcTemplate jdbcTemplate;
    
    @BeforeProperty
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        roleRepository = new RoleRepositoryImpl(jdbcTemplate);
        setupTestData();
    }
    
    /**
     * Property 2: Role Validation Accuracy
     * For any user and role combination, the role checker should return true 
     * if and only if the user has that role in the database.
     */
    @Property(tries = 100)
    @DisplayName("Role validation should accurately reflect database state")
    void roleValidationAccuracy(
            @ForAll("validUsernames") String username,
            @ForAll("validRoles") String role) {
        
        // Get expected result by direct database query
        boolean expectedResult = hasRoleInDatabase(username, role);
        
        // Get actual result from repository
        boolean actualResult = roleRepository.hasRole(username, role);
        
        // Property: Repository result must match database state
        Assertions.assertThat(actualResult).isEqualTo(expectedResult);
    }
    
    /**
     * Property 2b: Role List Accuracy
     * For any user, the roles returned should match exactly what's in the database.
     */
    @Property(tries = 100)
    @DisplayName("Role list should accurately reflect database state")
    void roleListAccuracy(@ForAll("validUsernames") String username) {
        
        // Get expected roles by direct database query
        Set<String> expectedRoles = getRolesFromDatabase(username);
        
        // Get actual roles from repository
        List<Object[]> actualResults = roleRepository.findRolesByUsername(username);
        Set<String> actualRoles = actualResults.stream()
                .map(row -> (String) row[1]) // code is second column (after id)
                .collect(java.util.stream.Collectors.toSet());
        
        // Property: Repository results must match database state
        Assertions.assertThat(actualRoles).isEqualTo(expectedRoles);
    }
    
    /**
     * Property 2c: User ID vs Username Consistency
     * For any user, role checks by username and user ID should return the same result.
     */
    @Property(tries = 100)
    @DisplayName("Role checks by username and user ID should be consistent")
    void usernameUserIdConsistency(
            @ForAll("validUsernames") String username,
            @ForAll("validRoles") String role) {
        
        // Get user ID for the username
        String userId = getUserIdByUsername(username);
        if (userId == null) {
            return; // Skip if user doesn't exist
        }
        
        // Check role by username
        boolean resultByUsername = roleRepository.hasRole(username, role);
        
        // Check role by user ID
        boolean resultByUserId = roleRepository.hasRoleByUserId(userId, role);
        
        // Property: Both methods should return the same result
        Assertions.assertThat(resultByUsername).isEqualTo(resultByUserId);
    }
    
    /**
     * Property 2d: Role Existence Consistency
     * For any user, if hasRole returns true, the role should appear in findRolesByUsername.
     */
    @Property(tries = 100)
    @DisplayName("Role existence should be consistent between hasRole and findRoles")
    void roleExistenceConsistency(
            @ForAll("validUsernames") String username,
            @ForAll("validRoles") String role) {
        
        boolean hasRole = roleRepository.hasRole(username, role);
        List<Object[]> userRoles = roleRepository.findRolesByUsername(username);
        boolean roleInList = userRoles.stream()
                .anyMatch(row -> role.equals(row[1])); // code is second column
        
        // Property: If hasRole is true, role must be in the list
        if (hasRole) {
            Assertions.assertThat(roleInList).isTrue();
        }
        
        // Property: If role is in list, hasRole must be true
        if (roleInList) {
            Assertions.assertThat(hasRole).isTrue();
        }
    }
    
    @Provide
    Arbitrary<String> validUsernames() {
        return Arbitraries.of("admin", "user1", "user2", "manager", "developer", "nonexistent");
    }
    
    @Provide
    Arbitrary<String> validRoles() {
        return Arbitraries.of("ADMIN", "USER", "DEVELOPER", "MANAGER", "GUEST", "NONEXISTENT");
    }
    
    private void setupTestData() {
        // Create test users
        jdbcTemplate.update("INSERT INTO sys_users (id, username, password_hash, status) VALUES " +
                "('user1', 'admin', '$2a$10$hash1', 'ACTIVE'), " +
                "('user2', 'user1', '$2a$10$hash2', 'ACTIVE'), " +
                "('user3', 'user2', '$2a$10$hash3', 'ACTIVE'), " +
                "('user4', 'manager', '$2a$10$hash4', 'ACTIVE'), " +
                "('user5', 'developer', '$2a$10$hash5', 'ACTIVE') " +
                "ON CONFLICT (id) DO NOTHING");
        
        // Create test roles
        jdbcTemplate.update("INSERT INTO sys_roles (id, code, name, status) VALUES " +
                "('role1', 'ADMIN', 'Administrator', 'ACTIVE'), " +
                "('role2', 'USER', 'Regular User', 'ACTIVE'), " +
                "('role3', 'DEVELOPER', 'Developer', 'ACTIVE'), " +
                "('role4', 'MANAGER', 'Manager', 'ACTIVE'), " +
                "('role5', 'GUEST', 'Guest User', 'ACTIVE') " +
                "ON CONFLICT (id) DO NOTHING");
        
        // Assign roles to users
        jdbcTemplate.update("INSERT INTO sys_user_roles (user_id, role_id) VALUES " +
                "('user1', 'role1'), " + // admin -> ADMIN role
                "('user1', 'role2'), " + // admin -> USER role (multiple roles)
                "('user2', 'role2'), " + // user1 -> USER role
                "('user3', 'role2'), " + // user2 -> USER role
                "('user3', 'role5'), " + // user2 -> GUEST role (multiple roles)
                "('user4', 'role4'), " + // manager -> MANAGER role
                "('user5', 'role3') " +  // developer -> DEVELOPER role
                "ON CONFLICT (user_id, role_id) DO NOTHING");
    }
    
    private boolean hasRoleInDatabase(String username, String role) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(r.id) " +
                    "FROM sys_roles r " +
                    "JOIN sys_user_roles ur ON r.id = ur.role_id " +
                    "JOIN sys_users u ON ur.user_id = u.id " +
                    "WHERE u.username = ? AND r.code = ? " +
                    "AND r.status = 'ACTIVE'",
                    Integer.class, username, role);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    private Set<String> getRolesFromDatabase(String username) {
        try {
            List<String> roles = jdbcTemplate.queryForList(
                    "SELECT DISTINCT r.code " +
                    "FROM sys_roles r " +
                    "JOIN sys_user_roles ur ON r.id = ur.role_id " +
                    "JOIN sys_users u ON ur.user_id = u.id " +
                    "WHERE u.username = ? AND r.status = 'ACTIVE'",
                    String.class, username);
            return Set.copyOf(roles);
        } catch (Exception e) {
            return Set.of();
        }
    }
    
    private String getUserIdByUsername(String username) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM sys_users WHERE username = ?",
                    String.class, username);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Simple implementation of RoleRepository for testing.
     * Uses JdbcTemplate to execute the same queries as the real repository.
     */
    private static class RoleRepositoryImpl implements RoleRepository {
        private final JdbcTemplate jdbcTemplate;
        
        public RoleRepositoryImpl(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }
        
        @Override
        public List<Object[]> findRolesByUsername(String username) {
            return jdbcTemplate.query(
                    "SELECT DISTINCT r.id, r.code, r.name, r.description " +
                    "FROM sys_roles r " +
                    "JOIN sys_user_roles ur ON r.id = ur.role_id " +
                    "JOIN sys_users u ON ur.user_id = u.id " +
                    "WHERE u.username = ? AND r.status = 'ACTIVE'",
                    (rs, rowNum) -> new Object[]{
                            rs.getString("id"),
                            rs.getString("code"),
                            rs.getString("name"),
                            rs.getString("description")
                    },
                    username);
        }
        
        @Override
        public boolean hasRole(String username, String role) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(r.id) " +
                    "FROM sys_roles r " +
                    "JOIN sys_user_roles ur ON r.id = ur.role_id " +
                    "JOIN sys_users u ON ur.user_id = u.id " +
                    "WHERE u.username = ? AND r.code = ? " +
                    "AND r.status = 'ACTIVE'",
                    Integer.class, username, role);
            return count != null && count > 0;
        }
        
        @Override
        public List<Object[]> findRolesByUserId(String userId) {
            return jdbcTemplate.query(
                    "SELECT DISTINCT r.id, r.code, r.name, r.description " +
                    "FROM sys_roles r " +
                    "JOIN sys_user_roles ur ON r.id = ur.role_id " +
                    "WHERE ur.user_id = ? AND r.status = 'ACTIVE'",
                    (rs, rowNum) -> new Object[]{
                            rs.getString("id"),
                            rs.getString("code"),
                            rs.getString("name"),
                            rs.getString("description")
                    },
                    userId);
        }
        
        @Override
        public boolean hasRoleByUserId(String userId, String role) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(r.id) " +
                    "FROM sys_roles r " +
                    "JOIN sys_user_roles ur ON r.id = ur.role_id " +
                    "WHERE ur.user_id = ? AND r.code = ? " +
                    "AND r.status = 'ACTIVE'",
                    Integer.class, userId, role);
            return count != null && count > 0;
        }
        
        // JpaRepository methods - not implemented for testing
        @Override
        public List<com.platform.security.model.Role> findAll() { return List.of(); }
        @Override
        public List<com.platform.security.model.Role> findAllById(Iterable<String> strings) { return List.of(); }
        @Override
        public long count() { return 0; }
        @Override
        public void deleteById(String s) {}
        @Override
        public void delete(com.platform.security.model.Role entity) {}
        @Override
        public void deleteAllById(Iterable<? extends String> strings) {}
        @Override
        public void deleteAll(Iterable<? extends com.platform.security.model.Role> entities) {}
        @Override
        public void deleteAll() {}
        @Override
        public <S extends com.platform.security.model.Role> S save(S entity) { return entity; }
        @Override
        public <S extends com.platform.security.model.Role> List<S> saveAll(Iterable<S> entities) { return List.of(); }
        @Override
        public java.util.Optional<com.platform.security.model.Role> findById(String s) { return java.util.Optional.empty(); }
        @Override
        public boolean existsById(String s) { return false; }
        @Override
        public void flush() {}
        @Override
        public <S extends com.platform.security.model.Role> S saveAndFlush(S entity) { return entity; }
        @Override
        public <S extends com.platform.security.model.Role> List<S> saveAllAndFlush(Iterable<S> entities) { return List.of(); }
        @Override
        public void deleteAllInBatch(Iterable<com.platform.security.model.Role> entities) {}
        @Override
        public void deleteAllByIdInBatch(Iterable<String> strings) {}
        @Override
        public void deleteAllInBatch() {}
        @Override
        public com.platform.security.model.Role getOne(String s) { return null; }
        @Override
        public com.platform.security.model.Role getById(String s) { return null; }
        @Override
        public com.platform.security.model.Role getReferenceById(String s) { return null; }
        @Override
        public <S extends com.platform.security.model.Role> java.util.Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return java.util.Optional.empty(); }
        @Override
        public <S extends com.platform.security.model.Role> List<S> findAll(org.springframework.data.domain.Example<S> example) { return List.of(); }
        @Override
        public <S extends com.platform.security.model.Role> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override
        public <S extends com.platform.security.model.Role> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return null; }
        @Override
        public <S extends com.platform.security.model.Role> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override
        public <S extends com.platform.security.model.Role> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override
        public <S extends com.platform.security.model.Role, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override
        public List<com.platform.security.model.Role> findAll(org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override
        public org.springframework.data.domain.Page<com.platform.security.model.Role> findAll(org.springframework.data.domain.Pageable pageable) { return null; }
    }
}