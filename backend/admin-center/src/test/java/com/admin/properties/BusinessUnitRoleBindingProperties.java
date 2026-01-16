package com.admin.properties;

import com.admin.entity.BusinessUnitRole;
import com.admin.entity.Role;
import com.admin.enums.RoleType;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.BusinessUnitRoleRepository;
import com.admin.repository.RoleRepository;
import com.admin.service.BusinessUnitRoleService;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 1: Role Type Validation for Binding
 * *For any* role binding operation (to Virtual_Group or Business_Unit), 
 * the system shall accept only roles with type BU_BOUNDED or BU_UNBOUNDED, 
 * and reject any role with type ADMIN or DEVELOPER.
 * 
 * **Validates: Requirements 5.5**
 */
public class BusinessUnitRoleBindingProperties {
    
    private BusinessUnitRoleRepository businessUnitRoleRepository;
    private BusinessUnitRepository businessUnitRepository;
    private RoleRepository roleRepository;
    private BusinessUnitRoleService businessUnitRoleService;
    
    @BeforeTry
    void setUp() {
        businessUnitRoleRepository = mock(BusinessUnitRoleRepository.class);
        businessUnitRepository = mock(BusinessUnitRepository.class);
        roleRepository = mock(RoleRepository.class);
        businessUnitRoleService = new BusinessUnitRoleService(
                businessUnitRoleRepository,
                businessUnitRepository,
                roleRepository);
    }
    
    // ==================== Property Tests ====================
    
    /**
     * Feature: permission-request-approval, Property 1: Role Type Validation for Binding
     * BU_BOUNDED type roles should be accepted for binding to business unit
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 1: BU_BOUNDED roles should be accepted for business unit binding")
    void buBoundedRolesShouldBeAcceptedForBinding(
            @ForAll("validBusinessUnitIds") String businessUnitId,
            @ForAll("validRoleIds") String roleId) {
        
        // Given: Business unit exists
        when(businessUnitRepository.existsById(businessUnitId)).thenReturn(true);
        
        // Given: Role exists and is BU_BOUNDED type
        Role businessRole = createRole(roleId, RoleType.BU_BOUNDED);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(businessRole));
        
        // Given: Role is not already bound
        when(businessUnitRoleRepository.existsByBusinessUnitIdAndRoleId(businessUnitId, roleId))
                .thenReturn(false);
        
        // Given: Save succeeds
        when(businessUnitRoleRepository.save(any(BusinessUnitRole.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Bind role
        businessUnitRoleService.bindRole(businessUnitId, roleId);
        
        // Then: Role should be saved
        verify(businessUnitRoleRepository).save(any(BusinessUnitRole.class));
    }
    
    /**
     * Feature: permission-request-approval, Property 1: Role Type Validation for Binding
     * BU_UNBOUNDED type roles should be accepted for binding to business unit
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 1: BU_UNBOUNDED roles should be accepted for business unit binding")
    void buUnboundedRolesShouldBeAcceptedForBinding(
            @ForAll("validBusinessUnitIds") String businessUnitId,
            @ForAll("validRoleIds") String roleId) {
        
        // Given: Business unit exists
        when(businessUnitRepository.existsById(businessUnitId)).thenReturn(true);
        
        // Given: Role exists and is BU_UNBOUNDED type
        Role businessRole = createRole(roleId, RoleType.BU_UNBOUNDED);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(businessRole));
        
        // Given: Role is not already bound
        when(businessUnitRoleRepository.existsByBusinessUnitIdAndRoleId(businessUnitId, roleId))
                .thenReturn(false);
        
        // Given: Save succeeds
        when(businessUnitRoleRepository.save(any(BusinessUnitRole.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Bind role
        businessUnitRoleService.bindRole(businessUnitId, roleId);
        
        // Then: Role should be saved
        verify(businessUnitRoleRepository).save(any(BusinessUnitRole.class));
    }
    
    /**
     * Feature: permission-request-approval, Property 1: Role Type Validation for Binding
     * ADMIN type roles should be rejected for binding to business unit
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 1: ADMIN roles should be rejected for business unit binding")
    void adminRolesShouldBeRejectedForBinding(
            @ForAll("validBusinessUnitIds") String businessUnitId,
            @ForAll("validRoleIds") String roleId) {
        
        // Given: Business unit exists
        when(businessUnitRepository.existsById(businessUnitId)).thenReturn(true);
        
        // Given: Role exists and is ADMIN type
        Role adminRole = createRole(roleId, RoleType.ADMIN);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(adminRole));
        
        // When & Then: Binding should throw exception
        assertThatThrownBy(() -> businessUnitRoleService.bindRole(businessUnitId, roleId))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("只能绑定业务角色");
        
        // Then: Role should not be saved
        verify(businessUnitRoleRepository, never()).save(any(BusinessUnitRole.class));
    }
    
    /**
     * Feature: permission-request-approval, Property 1: Role Type Validation for Binding
     * DEVELOPER type roles should be rejected for binding to business unit
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 1: DEVELOPER roles should be rejected for business unit binding")
    void developerRolesShouldBeRejectedForBinding(
            @ForAll("validBusinessUnitIds") String businessUnitId,
            @ForAll("validRoleIds") String roleId) {
        
        // Given: Business unit exists
        when(businessUnitRepository.existsById(businessUnitId)).thenReturn(true);
        
        // Given: Role exists and is DEVELOPER type
        Role developerRole = createRole(roleId, RoleType.DEVELOPER);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(developerRole));
        
        // When & Then: Binding should throw exception
        assertThatThrownBy(() -> businessUnitRoleService.bindRole(businessUnitId, roleId))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("只能绑定业务角色");
        
        // Then: Role should not be saved
        verify(businessUnitRoleRepository, never()).save(any(BusinessUnitRole.class));
    }
    
    /**
     * Feature: permission-request-approval, Property 1: Role Type Validation for Binding
     * For any role type, only BU_BOUNDED and BU_UNBOUNDED should pass validation
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 1: Only business types pass validation for business unit")
    void onlyBusinessTypePassesValidation(
            @ForAll("allRoleTypes") RoleType roleType,
            @ForAll("validRoleIds") String roleId) {
        
        // Given: Create role with the given type
        Role role = createRole(roleId, roleType);
        
        // When: Validate role type
        boolean shouldPass = roleType.isBusinessRole();
        
        // Then: Validation result should match expectation
        if (shouldPass) {
            // Should not throw
            businessUnitRoleService.validateBusinessRole(role);
        } else {
            // Should throw
            assertThatThrownBy(() -> businessUnitRoleService.validateBusinessRole(role))
                    .isInstanceOf(AdminBusinessException.class);
        }
    }
    
    /**
     * Feature: permission-request-approval, Property 1: Role Type Validation for Binding
     * Validation should be consistent - same role type always produces same result
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 1: Validation is consistent for same role type in business unit")
    void validationIsConsistentForSameRoleType(
            @ForAll("allRoleTypes") RoleType roleType,
            @ForAll("validRoleIds") String roleId1,
            @ForAll("validRoleIds") String roleId2) {
        
        // Given: Two roles with the same type
        Role role1 = createRole(roleId1, roleType);
        Role role2 = createRole(roleId2, roleType);
        
        // When: Validate both roles
        boolean result1 = isValidBusinessRole(role1);
        boolean result2 = isValidBusinessRole(role2);
        
        // Then: Results should be the same
        assertThat(result1).isEqualTo(result2);
    }
    
    /**
     * Feature: permission-request-approval, Property 1: Role Type Validation for Binding
     * Non-business roles should always be rejected regardless of business unit
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 1: Non-business roles rejected for any business unit")
    void nonBusinessRolesRejectedForAnyBusinessUnit(
            @ForAll("validBusinessUnitIds") String businessUnitId1,
            @ForAll("validBusinessUnitIds") String businessUnitId2,
            @ForAll("nonBusinessRoleTypes") RoleType roleType,
            @ForAll("validRoleIds") String roleId) {
        
        // Given: Both business units exist
        when(businessUnitRepository.existsById(businessUnitId1)).thenReturn(true);
        when(businessUnitRepository.existsById(businessUnitId2)).thenReturn(true);
        
        // Given: Role exists with non-business type
        Role role = createRole(roleId, roleType);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        
        // When & Then: Both bindings should fail
        assertThatThrownBy(() -> businessUnitRoleService.bindRole(businessUnitId1, roleId))
                .isInstanceOf(AdminBusinessException.class);
        
        assertThatThrownBy(() -> businessUnitRoleService.bindRole(businessUnitId2, roleId))
                .isInstanceOf(AdminBusinessException.class);
    }
    
    // ==================== Helper Methods ====================
    
    private Role createRole(String roleId, RoleType type) {
        return Role.builder()
                .id(roleId)
                .name("Test Role " + roleId)
                .code("ROLE_" + roleId.toUpperCase().replace("-", "_"))
                .type(type)
                .status("ACTIVE")
                .build();
    }
    
    private boolean isValidBusinessRole(Role role) {
        try {
            businessUnitRoleService.validateBusinessRole(role);
            return true;
        } catch (AdminBusinessException e) {
            return false;
        }
    }
    
    // ==================== Data Generators ====================
    
    @Provide
    Arbitrary<String> validBusinessUnitIds() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(5)
                .ofMaxLength(20)
                .map(s -> "bu-" + s.toLowerCase());
    }
    
    @Provide
    Arbitrary<String> validRoleIds() {
        return Arbitraries.create(UUID::randomUUID).map(UUID::toString);
    }
    
    @Provide
    Arbitrary<RoleType> allRoleTypes() {
        return Arbitraries.of(RoleType.values());
    }
    
    @Provide
    Arbitrary<RoleType> nonBusinessRoleTypes() {
        return Arbitraries.of(RoleType.ADMIN, RoleType.DEVELOPER);
    }
}
