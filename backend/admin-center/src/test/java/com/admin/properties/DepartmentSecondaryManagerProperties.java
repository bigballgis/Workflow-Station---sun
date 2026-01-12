package com.admin.properties;

import com.admin.component.OrganizationManagerComponent;
import com.admin.dto.request.DepartmentUpdateRequest;
import com.admin.entity.Department;
import com.admin.enums.DepartmentStatus;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.DepartmentRepository;
import com.admin.repository.UserRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Feature: manager-assignment, Property 3: Department Secondary Manager Persistence
 * For any Department entity with a valid secondaryManagerId value,
 * saving and then retrieving the department SHALL return the same secondary manager ID.
 * 
 * Validates: Requirements 2.1, 2.2, 2.3
 */
public class DepartmentSecondaryManagerProperties {
    
    @Mock
    private DepartmentRepository departmentRepository;
    
    @Mock
    private UserRepository userRepository;
    
    private OrganizationManagerComponent organizationManager;
    
    @BeforeProperty
    void setUp() {
        MockitoAnnotations.openMocks(this);
        organizationManager = new OrganizationManagerComponent(departmentRepository, userRepository);
    }
    
    /**
     * Feature: manager-assignment, Property 3: Valid secondary manager ID should be persisted
     */
    @Property(tries = 100)
    @Label("Feature: manager-assignment, Property 3: Valid secondary manager ID should be persisted")
    void validSecondaryManagerIdShouldBePersisted(
            @ForAll("departmentIds") String deptId,
            @ForAll("userIds") String secondaryManagerId) {
        
        // Given: Department exists and secondary manager exists
        Department existingDept = createTestDepartment(deptId);
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(existingDept));
        when(userRepository.existsById(secondaryManagerId)).thenReturn(true);
        when(departmentRepository.existsByNameAndParentIdExcluding(any(), any(), any())).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenAnswer(i -> i.getArgument(0));
        
        DepartmentUpdateRequest request = DepartmentUpdateRequest.builder()
                .name(existingDept.getName())
                .secondaryManagerId(secondaryManagerId)
                .build();
        
        // When: Update department
        organizationManager.updateDepartment(deptId, request);
        
        // Then: Secondary manager ID should be set
        assertThat(existingDept.getSecondaryManagerId()).isEqualTo(secondaryManagerId);
    }
    
    /**
     * Feature: manager-assignment, Property 3: Non-existent secondary manager should be rejected
     */
    @Property(tries = 100)
    @Label("Feature: manager-assignment, Property 3: Non-existent secondary manager should be rejected")
    void nonExistentSecondaryManagerShouldBeRejected(
            @ForAll("departmentIds") String deptId,
            @ForAll("userIds") String nonExistentManagerId) {
        
        // Given: Department exists but secondary manager does not exist
        Department existingDept = createTestDepartment(deptId);
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(existingDept));
        when(userRepository.existsById(nonExistentManagerId)).thenReturn(false);
        when(departmentRepository.existsByNameAndParentIdExcluding(any(), any(), any())).thenReturn(false);
        
        DepartmentUpdateRequest request = DepartmentUpdateRequest.builder()
                .name(existingDept.getName())
                .secondaryManagerId(nonExistentManagerId)
                .build();
        
        // When & Then: Should throw exception
        assertThatThrownBy(() -> organizationManager.updateDepartment(deptId, request))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("副经理不存在");
    }
    
    /**
     * Feature: manager-assignment, Property 3: Empty secondary manager ID should clear the field
     */
    @Property(tries = 100)
    @Label("Feature: manager-assignment, Property 3: Empty secondary manager ID should clear the field")
    void emptySecondaryManagerIdShouldClearField(
            @ForAll("departmentIds") String deptId) {
        
        // Given: Department exists with existing secondary manager
        Department existingDept = createTestDepartment(deptId);
        existingDept.setSecondaryManagerId("existing-manager");
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(existingDept));
        when(departmentRepository.existsByNameAndParentIdExcluding(any(), any(), any())).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenAnswer(i -> i.getArgument(0));
        
        DepartmentUpdateRequest request = DepartmentUpdateRequest.builder()
                .name(existingDept.getName())
                .secondaryManagerId("")
                .build();
        
        // When: Update department with empty secondary manager ID
        organizationManager.updateDepartment(deptId, request);
        
        // Then: Secondary manager ID should be null
        assertThat(existingDept.getSecondaryManagerId()).isNull();
    }
    
    /**
     * Feature: manager-assignment, Property 3: Null secondary manager ID should be allowed
     */
    @Property(tries = 100)
    @Label("Feature: manager-assignment, Property 3: Null secondary manager ID should be allowed")
    void nullSecondaryManagerIdShouldBeAllowed(
            @ForAll("departmentIds") String deptId) {
        
        // Given: Department exists
        Department existingDept = createTestDepartment(deptId);
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(existingDept));
        when(departmentRepository.existsByNameAndParentIdExcluding(any(), any(), any())).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenAnswer(i -> i.getArgument(0));
        
        DepartmentUpdateRequest request = DepartmentUpdateRequest.builder()
                .name(existingDept.getName())
                .secondaryManagerId(null)
                .build();
        
        // When: Update department with null secondary manager ID
        organizationManager.updateDepartment(deptId, request);
        
        // Then: No exception should be thrown
        // Secondary manager ID remains unchanged (null in this case)
    }
    
    @Provide
    Arbitrary<String> departmentIds() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(8)
                .ofMaxLength(32)
                .map(s -> "dept-" + s);
    }
    
    @Provide
    Arbitrary<String> userIds() {
        return Arbitraries.create(UUID::randomUUID).map(UUID::toString);
    }
    
    private Department createTestDepartment(String deptId) {
        return Department.builder()
                .id(deptId)
                .name("Test Department")
                .code("TEST-" + deptId.substring(0, 8).toUpperCase())
                .level(1)
                .path("/" + deptId)
                .status(DepartmentStatus.ACTIVE)
                .sortOrder(0)
                .build();
    }
}
