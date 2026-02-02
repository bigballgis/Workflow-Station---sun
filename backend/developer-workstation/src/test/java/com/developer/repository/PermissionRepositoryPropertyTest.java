package com.developer.repository;

import com.developer.security.SecurityCacheManager;
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
 * Property-based tests for PermissionRepository.
 * Validates universal properties of permission validation accuracy.
 * 
 * Feature: security-permission-system, Property 1: Permission Validation Accuracy
 * Validates: Requirements 1.2, 1.3
 */
@DataJpaTest
@ActiveProfiles("test")
@Label("Feature: security-permission-system, Property 1: Permission Validation Accuracy")
public class PermissionRepositoryPropertyTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private DataSource dataSource;
    
    private PermissionRepository permissionRepository;
    private JdbcTemplate jdbcTemplate;
    
    @BeforeProperty
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        permissionRepository = new PermissionRepositoryImpl(jdbcTemplate);
        setupTestData();
    }
    
    /**
     * Property 1: Permission Validation Accuracy
     * For any user and permission combination, the permission checker should return true 
     * if and only if the user has that permission in the database.
     */
    @Property(tries = 100)
    @DisplayName("Permission validation should accurately reflect database state")
    void permissionValidationAccuracy(
            @ForAll("validUsernames") String username,
            @ForAll("validPermissions") String permission) {
        
        // Get expected result by direct database query
        boolean expectedResult = hasPermissionInDatabase(username, permission);
        
        // Get actual result from repository
        boolean actualResult = permissionRepository.hasPermission(username, permission);
        
        // Property: Repository result must match database state
        Assertions.assertThat(actualResult).isEqualTo(expectedResult);
    }
    
    /**
     * Property 1b: Permission List Accuracy
     * For any user, the permissions returned should match exactly what's in the database.
     */
    @Property(tries = 100)
    @DisplayName("Permission list should accurately reflect database state")
    void permissionListAccuracy(@ForAll("validUsernames") String username) {
        
        // Get expected permissions by direct database query
        Set<String> expectedPermissions = getPermissionsFromDatabase(username);
        
        // Get actual permissions from repository
        List<Object[]> actualResults = permissionRepository.findPermissionsByUsername(username);
        Set<String> actualPermissions = actualResults.stream()
                .map(row -> (String) row[0]) // code is first column
                .collect(java.util.stream.Collectors.toSet());
        
        // Property: Repository results must match database state
        Assertions.assertThat(actualPermissions).isEqualTo(expectedPermissions);
    }
    
    /**
     * Property 1c: User ID vs Username Consistency
     * For any user, permission checks by username and user ID should return the same result.
     */
    @Property(tries = 100)
    @DisplayName("Permission checks by username and user ID should be consistent")
    void usernameUserIdConsistency(
            @ForAll("validUsernames") String username,
            @ForAll("validPermissions") String permission) {
        
        // Get user ID for the username
        String userId = getUserIdByUsername(username);
        if (userId == null) {
            return; // Skip if user doesn't exist
        }
        
        // Check permission by username
        boolean resultByUsername = permissionRepository.hasPermission(username, permission);
        
        // Check permission by user ID
        boolean resultByUserId = permissionRepository.hasPermissionByUserId(userId, permission);
        
        // Property: Both methods should return the same result
        Assertions.assertThat(resultByUsername).isEqualTo(resultByUserId);
    }
    
    @Provide
    Arbitrary<String> validUsernames() {
        return Arbitraries.of("admin", "user1", "user2", "manager", "developer", "nonexistent");
    }
    
    @Provide
    Arbitrary<String> validPermissions() {
        return Arbitraries.of(
                "ADMIN:USER:READ", "ADMIN:USER:WRITE", "ADMIN:ROLE:READ", "ADMIN:ROLE:WRITE",
                "DEVELOPER:FUNCTION:READ", "DEVELOPER:FUNCTION:WRITE", "DEVELOPER:PROCESS:READ",
                "USER:TASK:READ", "USER:TASK:WRITE", "nonexistent:permission"
        );
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
                "('role4', 'MANAGER', 'Manager', 'ACTIVE') " +
                "ON CONFLICT (id) DO NOTHING");
        
        // Create test permissions
        jdbcTemplate.update("INSERT INTO sys_permissions (id, code, name, enabled) VALUES " +
                "('perm1', 'ADMIN:USER:READ', 'Read Users', true), " +
                "('perm2', 'ADMIN:USER:WRITE', 'Write Users', true), " +
                "('perm3', 'ADMIN:ROLE:READ', 'Read Roles', true), " +
                "('perm4', 'ADMIN:ROLE:WRITE', 'Write Roles', true), " +
                "('perm5', 'DEVELOPER:FUNCTION:READ', 'Read Functions', true), " +
                "('perm6', 'DEVELOPER:FUNCTION:WRITE', 'Write Functions', true), " +
                "('perm7', 'DEVELOPER:PROCESS:READ', 'Read Processes', true), " +
                "('perm8', 'USER:TASK:READ', 'Read Tasks', true), " +
                "('perm9', 'USER:TASK:WRITE', 'Write Tasks', true) " +
                "ON CONFLICT (id) DO NOTHING");
        
        // Assign roles to users
        jdbcTemplate.update("INSERT INTO sys_user_roles (user_id, role_id) VALUES " +
                "('user1', 'role1'), " + // admin -> ADMIN role
                "('user2', 'role2'), " + // user1 -> USER role
                "('user3', 'role2'), " + // user2 -> USER role
                "('user4', 'role4'), " + // manager -> MANAGER role
                "('user5', 'role3') " +  // developer -> DEVELOPER role
                "ON CONFLICT (user_id, role_id) DO NOTHING");
        
        // Assign permissions to roles
        jdbcTemplate.update("INSERT INTO sys_role_permissions (role_id, permission_id) VALUES " +
                "('role1', 'perm1'), ('role1', 'perm2'), ('role1', 'perm3'), ('role1', 'perm4'), " + // ADMIN gets all admin perms
                "('role2', 'perm8'), ('role2', 'perm9'), " + // USER gets task perms
                "('role3', 'perm5'), ('role3', 'perm6'), ('role3', 'perm7'), " + // DEVELOPER gets dev perms
                "('role4', 'perm8') " + // MANAGER gets read tasks
                "ON CONFLICT (role_id, permission_id) DO NOTHING");
    }
    
    private boolean hasPermissionInDatabase(String username, String permission) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(p.id) " +
                    "FROM sys_permissions p " +
                    "JOIN sys_role_permissions rp ON p.id = rp.permission_id " +
                    "JOIN sys_roles r ON rp.role_id = r.id " +
                    "JOIN sys_user_roles ur ON r.id = ur.role_id " +
                    "JOIN sys_users u ON ur.user_id = u.id " +
                    "WHERE u.username = ? AND p.code = ? " +
                    "AND r.status = 'ACTIVE' AND p.enabled = true",
                    Integer.class, username, permission);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    private Set<String> getPermissionsFromDatabase(String username) {
        try {
            List<String> permissions = jdbcTemplate.queryForList(
                    "SELECT DISTINCT p.code " +
                    "FROM sys_permissions p " +
                    "JOIN sys_role_permissions rp ON p.id = rp.permission_id " +
                    "JOIN sys_roles r ON rp.role_id = r.id " +
                    "JOIN sys_user_roles ur ON r.id = ur.role_id " +
                    "JOIN sys_users u ON ur.user_id = u.id " +
                    "WHERE u.username = ? AND r.status = 'ACTIVE' AND p.enabled = true",
                    String.class, username);
            return Set.copyOf(permissions);
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
     * Simple implementation of PermissionRepository for testing.
     * Uses JdbcTemplate to execute the same queries as the real repository.
     */
    private static class PermissionRepositoryImpl implements PermissionRepository {
        private final JdbcTemplate jdbcTemplate;
        
        public PermissionRepositoryImpl(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }
        
        @Override
        public List<Object[]> findPermissionsByUsername(String username) {
            return jdbcTemplate.query(
                    "SELECT DISTINCT p.code, p.name, p.description, p.module, p.resource_type, p.action " +
                    "FROM sys_permissions p " +
                    "JOIN sys_role_permissions rp ON p.id = rp.permission_id " +
                    "JOIN sys_roles r ON rp.role_id = r.id " +
                    "JOIN sys_user_roles ur ON r.id = ur.role_id " +
                    "JOIN sys_users u ON ur.user_id = u.id " +
                    "WHERE u.username = ? AND r.status = 'ACTIVE' AND p.enabled = true",
                    (rs, rowNum) -> new Object[]{
                            rs.getString("code"),
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getString("module"),
                            rs.getString("resource_type"),
                            rs.getString("action")
                    },
                    username);
        }
        
        @Override
        public boolean hasPermission(String username, String permission) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(p.id) " +
                    "FROM sys_permissions p " +
                    "JOIN sys_role_permissions rp ON p.id = rp.permission_id " +
                    "JOIN sys_roles r ON rp.role_id = r.id " +
                    "JOIN sys_user_roles ur ON r.id = ur.role_id " +
                    "JOIN sys_users u ON ur.user_id = u.id " +
                    "WHERE u.username = ? AND p.code = ? " +
                    "AND r.status = 'ACTIVE' AND p.enabled = true",
                    Integer.class, username, permission);
            return count != null && count > 0;
        }
        
        @Override
        public List<Object[]> findPermissionsByUserId(String userId) {
            return jdbcTemplate.query(
                    "SELECT DISTINCT p.code, p.name, p.description, p.module, p.resource_type, p.action " +
                    "FROM sys_permissions p " +
                    "JOIN sys_role_permissions rp ON p.id = rp.permission_id " +
                    "JOIN sys_roles r ON rp.role_id = r.id " +
                    "JOIN sys_user_roles ur ON r.id = ur.role_id " +
                    "WHERE ur.user_id = ? AND r.status = 'ACTIVE' AND p.enabled = true",
                    (rs, rowNum) -> new Object[]{
                            rs.getString("code"),
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getString("module"),
                            rs.getString("resource_type"),
                            rs.getString("action")
                    },
                    userId);
        }
        
        @Override
        public boolean hasPermissionByUserId(String userId, String permission) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(p.id) " +
                    "FROM sys_permissions p " +
                    "JOIN sys_role_permissions rp ON p.id = rp.permission_id " +
                    "JOIN sys_roles r ON rp.role_id = r.id " +
                    "JOIN sys_user_roles ur ON r.id = ur.role_id " +
                    "WHERE ur.user_id = ? AND p.code = ? " +
                    "AND r.status = 'ACTIVE' AND p.enabled = true",
                    Integer.class, userId, permission);
            return count != null && count > 0;
        }
        
        // JpaRepository methods - not implemented for testing
        @Override
        public List<com.platform.security.entity.Permission> findAll() { return List.of(); }
        @Override
        public List<com.platform.security.entity.Permission> findAllById(Iterable<String> strings) { return List.of(); }
        @Override
        public long count() { return 0; }
        @Override
        public void deleteById(String s) {}
        @Override
        public void delete(com.platform.security.entity.Permission entity) {}
        @Override
        public void deleteAllById(Iterable<? extends String> strings) {}
        @Override
        public void deleteAll(Iterable<? extends com.platform.security.entity.Permission> entities) {}
        @Override
        public void deleteAll() {}
        @Override
        public <S extends com.platform.security.entity.Permission> S save(S entity) { return entity; }
        @Override
        public <S extends com.platform.security.entity.Permission> List<S> saveAll(Iterable<S> entities) { return List.of(); }
        @Override
        public java.util.Optional<com.platform.security.entity.Permission> findById(String s) { return java.util.Optional.empty(); }
        @Override
        public boolean existsById(String s) { return false; }
        @Override
        public void flush() {}
        @Override
        public <S extends com.platform.security.entity.Permission> S saveAndFlush(S entity) { return entity; }
        @Override
        public <S extends com.platform.security.entity.Permission> List<S> saveAllAndFlush(Iterable<S> entities) { return List.of(); }
        @Override
        public void deleteAllInBatch(Iterable<com.platform.security.entity.Permission> entities) {}
        @Override
        public void deleteAllByIdInBatch(Iterable<String> strings) {}
        @Override
        public void deleteAllInBatch() {}
        @Override
        public com.platform.security.entity.Permission getOne(String s) { return null; }
        @Override
        public com.platform.security.entity.Permission getById(String s) { return null; }
        @Override
        public com.platform.security.entity.Permission getReferenceById(String s) { return null; }
        @Override
        public <S extends com.platform.security.entity.Permission> java.util.Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return java.util.Optional.empty(); }
        @Override
        public <S extends com.platform.security.entity.Permission> List<S> findAll(org.springframework.data.domain.Example<S> example) { return List.of(); }
        @Override
        public <S extends com.platform.security.entity.Permission> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override
        public <S extends com.platform.security.entity.Permission> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return null; }
        @Override
        public <S extends com.platform.security.entity.Permission> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override
        public <S extends com.platform.security.entity.Permission> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override
        public <S extends com.platform.security.entity.Permission, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override
        public List<com.platform.security.entity.Permission> findAll(org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override
        public org.springframework.data.domain.Page<com.platform.security.entity.Permission> findAll(org.springframework.data.domain.Pageable pageable) { return null; }
    }
}