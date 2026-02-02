package com.admin.service;

import com.admin.entity.*;
import com.admin.enums.RoleType;
import com.admin.util.EntityTypeConverter;
import com.platform.security.entity.User;
import com.platform.security.entity.Role;
import com.platform.security.entity.BusinessUnit;
import com.platform.security.entity.UserBusinessUnitRole;
import com.platform.security.entity.BusinessUnitRole;
import com.admin.exception.BusinessUnitNotFoundException;
import com.admin.exception.RoleNotFoundException;
import com.admin.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * TaskAssignmentQueryService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskAssignmentQueryService Tests")
class TaskAssignmentQueryServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private BusinessUnitRepository businessUnitRepository;
    
    @Mock
    private RoleRepository roleRepository;
    
    @Mock
    private UserBusinessUnitRoleRepository userBusinessUnitRoleRepository;
    
    @Mock
    private VirtualGroupRoleRepository virtualGroupRoleRepository;
    
    @Mock
    private VirtualGroupMemberRepository virtualGroupMemberRepository;
    
    @Mock
    private BusinessUnitRoleRepository businessUnitRoleRepository;
    
    @InjectMocks
    private TaskAssignmentQueryService service;
    
    private static final String USER_ID = "user-001";
    private static final String BU_ID = "bu-001";
    private static final String PARENT_BU_ID = "bu-parent";
    private static final String ROLE_ID = "role-001";
    private static final String VG_ID = "vg-001";
    
    @Nested
    @DisplayName("getUserBusinessUnitId Tests")
    class GetUserBusinessUnitIdTests {
        
        @Test
        @DisplayName("Should return business unit ID when user has assignment")
        void shouldReturnBusinessUnitIdWhenUserHasAssignment() {
            UserBusinessUnitRole assignment = new UserBusinessUnitRole();
            assignment.setUserId(USER_ID);
            assignment.setBusinessUnitId(BU_ID);
            
            when(userBusinessUnitRoleRepository.findByUserId(USER_ID))
                    .thenReturn(Arrays.asList(assignment));
            
            String result = service.getUserBusinessUnitId(USER_ID);
            
            assertThat(result).isEqualTo(BU_ID);
        }
        
        @Test
        @DisplayName("Should return null when user has no assignment")
        void shouldReturnNullWhenUserHasNoAssignment() {
            when(userBusinessUnitRoleRepository.findByUserId(USER_ID))
                    .thenReturn(Collections.emptyList());
            
            String result = service.getUserBusinessUnitId(USER_ID);
            
            assertThat(result).isNull();
        }
        
        @Test
        @DisplayName("Should return first business unit when user has multiple assignments")
        void shouldReturnFirstBusinessUnitWhenMultipleAssignments() {
            UserBusinessUnitRole assignment1 = new UserBusinessUnitRole();
            assignment1.setUserId(USER_ID);
            assignment1.setBusinessUnitId(BU_ID);
            
            UserBusinessUnitRole assignment2 = new UserBusinessUnitRole();
            assignment2.setUserId(USER_ID);
            assignment2.setBusinessUnitId("bu-002");
            
            when(userBusinessUnitRoleRepository.findByUserId(USER_ID))
                    .thenReturn(Arrays.asList(assignment1, assignment2));
            
            String result = service.getUserBusinessUnitId(USER_ID);
            
            assertThat(result).isEqualTo(BU_ID);
        }
    }
    
    @Nested
    @DisplayName("getParentBusinessUnitId Tests")
    class GetParentBusinessUnitIdTests {
        
        @Test
        @DisplayName("Should return parent ID when business unit has parent")
        void shouldReturnParentIdWhenHasParent() {
            BusinessUnit bu = new BusinessUnit();
            bu.setId(BU_ID);
            bu.setParentId(PARENT_BU_ID);
            
            when(businessUnitRepository.findById(BU_ID)).thenReturn(Optional.of(bu));
            
            String result = service.getParentBusinessUnitId(BU_ID);
            
            assertThat(result).isEqualTo(PARENT_BU_ID);
        }
        
        @Test
        @DisplayName("Should return null when business unit has no parent")
        void shouldReturnNullWhenNoParent() {
            BusinessUnit bu = new BusinessUnit();
            bu.setId(BU_ID);
            bu.setParentId(null);
            
            when(businessUnitRepository.findById(BU_ID)).thenReturn(Optional.of(bu));
            
            String result = service.getParentBusinessUnitId(BU_ID);
            
            assertThat(result).isNull();
        }
        
        @Test
        @DisplayName("Should throw exception when business unit not found")
        void shouldThrowExceptionWhenNotFound() {
            when(businessUnitRepository.findById(BU_ID)).thenReturn(Optional.empty());
            
            assertThatThrownBy(() -> service.getParentBusinessUnitId(BU_ID))
                    .isInstanceOf(BusinessUnitNotFoundException.class);
        }
    }
    
    @Nested
    @DisplayName("getUsersByBusinessUnitAndRole Tests")
    class GetUsersByBusinessUnitAndRoleTests {
        
        @Test
        @DisplayName("Should return user IDs when users exist with role in BU")
        void shouldReturnUserIdsWhenUsersExist() {
            Role role = new Role();
            role.setId(ROLE_ID);
            role.setType(EntityTypeConverter.fromRoleType(RoleType.BU_BOUNDED));
            
            when(businessUnitRepository.existsById(BU_ID)).thenReturn(true);
            when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
            when(userBusinessUnitRoleRepository.findUserIdsByBusinessUnitIdAndRoleId(BU_ID, ROLE_ID))
                    .thenReturn(Arrays.asList("user-001", "user-002"));
            
            List<String> result = service.getUsersByBusinessUnitAndRole(BU_ID, ROLE_ID);
            
            assertThat(result).containsExactly("user-001", "user-002");
        }
        
        @Test
        @DisplayName("Should return empty list when no users with role in BU")
        void shouldReturnEmptyListWhenNoUsers() {
            Role role = new Role();
            role.setId(ROLE_ID);
            role.setType(EntityTypeConverter.fromRoleType(RoleType.BU_BOUNDED));
            
            when(businessUnitRepository.existsById(BU_ID)).thenReturn(true);
            when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
            when(userBusinessUnitRoleRepository.findUserIdsByBusinessUnitIdAndRoleId(BU_ID, ROLE_ID))
                    .thenReturn(Collections.emptyList());
            
            List<String> result = service.getUsersByBusinessUnitAndRole(BU_ID, ROLE_ID);
            
            assertThat(result).isEmpty();
        }
        
        @Test
        @DisplayName("Should return empty list when role is not BU_BOUNDED")
        void shouldReturnEmptyListWhenRoleNotBuBounded() {
            Role role = new Role();
            role.setId(ROLE_ID);
            role.setType(EntityTypeConverter.fromRoleType(RoleType.BU_UNBOUNDED));
            
            when(businessUnitRepository.existsById(BU_ID)).thenReturn(true);
            when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
            
            List<String> result = service.getUsersByBusinessUnitAndRole(BU_ID, ROLE_ID);
            
            assertThat(result).isEmpty();
            verify(userBusinessUnitRoleRepository, never()).findUserIdsByBusinessUnitIdAndRoleId(any(), any());
        }
        
        @Test
        @DisplayName("Should throw exception when business unit not found")
        void shouldThrowExceptionWhenBuNotFound() {
            when(businessUnitRepository.existsById(BU_ID)).thenReturn(false);
            
            assertThatThrownBy(() -> service.getUsersByBusinessUnitAndRole(BU_ID, ROLE_ID))
                    .isInstanceOf(BusinessUnitNotFoundException.class);
        }
        
        @Test
        @DisplayName("Should throw exception when role not found")
        void shouldThrowExceptionWhenRoleNotFound() {
            when(businessUnitRepository.existsById(BU_ID)).thenReturn(true);
            when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.empty());
            
            assertThatThrownBy(() -> service.getUsersByBusinessUnitAndRole(BU_ID, ROLE_ID))
                    .isInstanceOf(RoleNotFoundException.class);
        }
    }
    
    @Nested
    @DisplayName("getUsersByUnboundedRole Tests")
    class GetUsersByUnboundedRoleTests {
        
        @Test
        @DisplayName("Should return user IDs through virtual groups")
        void shouldReturnUserIdsThroughVirtualGroups() {
            Role role = new Role();
            role.setId(ROLE_ID);
            role.setType(EntityTypeConverter.fromRoleType(RoleType.BU_UNBOUNDED));
            
            when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
            when(virtualGroupRoleRepository.findVirtualGroupIdsByRoleId(ROLE_ID))
                    .thenReturn(Arrays.asList(VG_ID, "vg-002"));
            when(virtualGroupMemberRepository.findUserIdsByVirtualGroupIds(anyList()))
                    .thenReturn(Arrays.asList("user-001", "user-002", "user-003"));
            
            List<String> result = service.getUsersByUnboundedRole(ROLE_ID);
            
            assertThat(result).containsExactly("user-001", "user-002", "user-003");
        }
        
        @Test
        @DisplayName("Should return empty list when no virtual groups bound to role")
        void shouldReturnEmptyListWhenNoVirtualGroups() {
            Role role = new Role();
            role.setId(ROLE_ID);
            role.setType(EntityTypeConverter.fromRoleType(RoleType.BU_UNBOUNDED));
            
            when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
            when(virtualGroupRoleRepository.findVirtualGroupIdsByRoleId(ROLE_ID))
                    .thenReturn(Collections.emptyList());
            
            List<String> result = service.getUsersByUnboundedRole(ROLE_ID);
            
            assertThat(result).isEmpty();
            verify(virtualGroupMemberRepository, never()).findUserIdsByVirtualGroupIds(anyList());
        }
        
        @Test
        @DisplayName("Should return empty list when role is not BU_UNBOUNDED")
        void shouldReturnEmptyListWhenRoleNotBuUnbounded() {
            Role role = new Role();
            role.setId(ROLE_ID);
            role.setType(EntityTypeConverter.fromRoleType(RoleType.BU_BOUNDED));
            
            when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
            
            List<String> result = service.getUsersByUnboundedRole(ROLE_ID);
            
            assertThat(result).isEmpty();
            verify(virtualGroupRoleRepository, never()).findVirtualGroupIdsByRoleId(any());
        }
        
        @Test
        @DisplayName("Should throw exception when role not found")
        void shouldThrowExceptionWhenRoleNotFound() {
            when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.empty());
            
            assertThatThrownBy(() -> service.getUsersByUnboundedRole(ROLE_ID))
                    .isInstanceOf(RoleNotFoundException.class);
        }
    }
    
    @Nested
    @DisplayName("getEligibleRoleIds Tests")
    class GetEligibleRoleIdsTests {
        
        @Test
        @DisplayName("Should return eligible role IDs")
        void shouldReturnEligibleRoleIds() {
            BusinessUnitRole bur1 = new BusinessUnitRole();
            bur1.setBusinessUnitId(BU_ID);
            bur1.setRoleId("role-001");
            
            BusinessUnitRole bur2 = new BusinessUnitRole();
            bur2.setBusinessUnitId(BU_ID);
            bur2.setRoleId("role-002");
            
            when(businessUnitRepository.existsById(BU_ID)).thenReturn(true);
            when(businessUnitRoleRepository.findByBusinessUnitId(BU_ID))
                    .thenReturn(Arrays.asList(bur1, bur2));
            
            List<String> result = service.getEligibleRoleIds(BU_ID);
            
            assertThat(result).containsExactly("role-001", "role-002");
        }
        
        @Test
        @DisplayName("Should return empty list when no eligible roles")
        void shouldReturnEmptyListWhenNoEligibleRoles() {
            when(businessUnitRepository.existsById(BU_ID)).thenReturn(true);
            when(businessUnitRoleRepository.findByBusinessUnitId(BU_ID))
                    .thenReturn(Collections.emptyList());
            
            List<String> result = service.getEligibleRoleIds(BU_ID);
            
            assertThat(result).isEmpty();
        }
        
        @Test
        @DisplayName("Should throw exception when business unit not found")
        void shouldThrowExceptionWhenBuNotFound() {
            when(businessUnitRepository.existsById(BU_ID)).thenReturn(false);
            
            assertThatThrownBy(() -> service.getEligibleRoleIds(BU_ID))
                    .isInstanceOf(BusinessUnitNotFoundException.class);
        }
    }
    
    @Nested
    @DisplayName("isEligibleRole Tests")
    class IsEligibleRoleTests {
        
        @Test
        @DisplayName("Should return true when role is eligible")
        void shouldReturnTrueWhenEligible() {
            when(businessUnitRoleRepository.existsByBusinessUnitIdAndRoleId(BU_ID, ROLE_ID))
                    .thenReturn(true);
            
            boolean result = service.isEligibleRole(BU_ID, ROLE_ID);
            
            assertThat(result).isTrue();
        }
        
        @Test
        @DisplayName("Should return false when role is not eligible")
        void shouldReturnFalseWhenNotEligible() {
            when(businessUnitRoleRepository.existsByBusinessUnitIdAndRoleId(BU_ID, ROLE_ID))
                    .thenReturn(false);
            
            boolean result = service.isEligibleRole(BU_ID, ROLE_ID);
            
            assertThat(result).isFalse();
        }
    }
    
    @Nested
    @DisplayName("getBuBoundedRoles Tests")
    class GetBuBoundedRolesTests {
        
        @Test
        @DisplayName("Should return BU bounded roles")
        void shouldReturnBuBoundedRoles() {
            Role role1 = new Role();
            role1.setId("role-001");
            role1.setType(EntityTypeConverter.fromRoleType(RoleType.BU_BOUNDED));
            
            Role role2 = new Role();
            role2.setId("role-002");
            role2.setType(EntityTypeConverter.fromRoleType(RoleType.BU_BOUNDED));
            
            when(roleRepository.findByType(RoleType.BU_BOUNDED))
                    .thenReturn(Arrays.asList(role1, role2));
            
            List<Role> result = service.getBuBoundedRoles();
            
            assertThat(result).hasSize(2);
            assertThat(result).extracting(Role::getId).containsExactly("role-001", "role-002");
        }
    }
    
    @Nested
    @DisplayName("getBuUnboundedRoles Tests")
    class GetBuUnboundedRolesTests {
        
        @Test
        @DisplayName("Should return BU unbounded roles")
        void shouldReturnBuUnboundedRoles() {
            Role role1 = new Role();
            role1.setId("role-001");
            role1.setType(EntityTypeConverter.fromRoleType(RoleType.BU_UNBOUNDED));
            
            when(roleRepository.findByType(RoleType.BU_UNBOUNDED))
                    .thenReturn(Arrays.asList(role1));
            
            List<Role> result = service.getBuUnboundedRoles();
            
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo("role-001");
        }
    }
}
