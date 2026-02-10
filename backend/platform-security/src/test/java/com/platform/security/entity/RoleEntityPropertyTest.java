package com.platform.security.entity;

import net.jqwik.api.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for Role entity to verify schema alignment.
 * Tests that Role entity queries execute without "column does not exist" errors.
 * 
 * **Feature: entity-database-schema-alignment**
 */
@DataJpaTest
@ActiveProfiles("test")
class RoleEntityPropertyTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    /**
     * Property 3: Query Execution Success
     * 
     * For any Role entity instance, persisting and querying it must not throw 
     * a "column does not exist" SQLException.
     * 
     * **Validates: Requirements 2.4**
     */
    @Test
    void roleEntityQueriesExecuteSuccessfully() {
        // Run property test manually with multiple iterations
        for (int i = 0; i < 100; i++) {
            testRoleEntityWithRandomData();
        }
    }
    
    private void testRoleEntityWithRandomData() {
        // Generate random data
        String name = "Role-" + UUID.randomUUID().toString().substring(0, 8);
        String code = "CODE_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String type = new String[]{"BU_UNBOUNDED", "BU_BOUNDED", "DEVELOPER", "SYSTEM"}[(int)(Math.random() * 4)];
        String description = "Description " + UUID.randomUUID().toString();
        String status = Math.random() > 0.5 ? "ACTIVE" : "INACTIVE";
        Boolean isSystem = Math.random() > 0.5;
        
        // Given: A random Role entity
        Role role = Role.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .code(code)
                .type(type)
                .description(description)
                .status(status)
                .isSystem(isSystem)
                .createdBy("test-user")
                .updatedBy("test-user")
                .build();
        
        // When: Persisting the role using EntityManager
        entityManager.persist(role);
        entityManager.flush();
        
        // Then: The role should be saved successfully without SQL errors
        assertThat(role.getId()).isNotNull();
        
        // When: Querying the role by ID
        entityManager.clear();
        Role foundRole = entityManager.find(Role.class, role.getId());
        
        // Then: The role should be found without SQL errors
        assertThat(foundRole).isNotNull();
        assertThat(foundRole.getName()).isEqualTo(name);
        assertThat(foundRole.getCode()).isEqualTo(code);
        assertThat(foundRole.getType()).isEqualTo(type);
        assertThat(foundRole.getDescription()).isEqualTo(description);
        assertThat(foundRole.getStatus()).isEqualTo(status);
        assertThat(foundRole.getIsSystem()).isEqualTo(isSystem);
        
        // When: Updating the role
        foundRole.setDescription("Updated: " + description);
        entityManager.merge(foundRole);
        entityManager.flush();
        
        // Then: The update should succeed without SQL errors
        entityManager.clear();
        Role updatedRole = entityManager.find(Role.class, role.getId());
        assertThat(updatedRole.getDescription()).startsWith("Updated:");
        
        // When: Deleting the role
        entityManager.remove(updatedRole);
        entityManager.flush();
        
        // Then: The deletion should succeed without SQL errors
        entityManager.clear();
        Role deletedRole = entityManager.find(Role.class, role.getId());
        assertThat(deletedRole).isNull();
    }
}
