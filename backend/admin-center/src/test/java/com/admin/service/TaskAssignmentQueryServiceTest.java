package com.admin.service;

import com.admin.entity.*;
import com.admin.enums.RoleType;
import com.admin.util.EntityTypeConverter;
import com.platform.security.entity.User;
import com.platform.security.entity.Role;
import com.platform.security.entity.BusinessUnit;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.entity.VirtualGroupMember;
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
import static org.mockito.ArgumentMatchers.any;
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
    private VirtualGroupRepository virtualGroupRepository;
    
    @Mock
    private BusinessUnitRoleRepository businessUnitRoleRepository;
    
    @InjectMocks
    private TaskAssignmentQueryService service;
    
    private static final String USER_ID = "user-001";
    private static final String BU_ID = "bu-001";
    private static final String BU_CODE = "BU_FINANCE";
    private static final String PARENT_BU_ID = "bu-parent";
    private static final String PARENT_BU_CODE = "BU_HQ";
    private static final String ROLE_ID = "role-001";
    private static final String ROLE_CODE = "ROLE_MANAGER";
    private static final String VG_ID = "vg-001";

    private static BusinessUnit bu(String id, String code) {
        BusinessUnit b = new BusinessUnit();
        b.setId(id);
        b.setCode(code);
        return b;
    }

    private static Role roleOf(String id, String code, RoleType type) {
        Role r = new Role();
        r.setId(id);
        r.setCode(code);
        r.setType(EntityTypeConverter.fromRoleType(type));
        return r;
    }
    
    @Nested
    @DisplayName("getUserBusinessUnitId Tests")
    class GetUserBusinessUnitIdTests {
        
        @Test
        @DisplayName("Should return business unit CODE when user has single assignment")
        void shouldReturnBusinessUnitCodeWhenUserHasAssignment() {
            UserBusinessUnitRole assignment = new UserBusinessUnitRole();
            assignment.setUserId(USER_ID);
            assignment.setBusinessUnitId(BU_ID);

            when(userBusinessUnitRoleRepository.findByUserId(USER_ID))
                    .thenReturn(Arrays.asList(assignment));
            when(businessUnitRepository.findById(BU_ID)).thenReturn(Optional.of(bu(BU_ID, BU_CODE)));

            String result = service.getUserBusinessUnitId(USER_ID);

            assertThat(result).isEqualTo(BU_CODE);
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
        @DisplayName("Should return null when user has multiple BU assignments without preferred")
        void shouldReturnNullWhenMultipleAssignmentsWithoutPreferred() {
            UserBusinessUnitRole assignment1 = new UserBusinessUnitRole();
            assignment1.setUserId(USER_ID);
            assignment1.setBusinessUnitId(BU_ID);
            
            UserBusinessUnitRole assignment2 = new UserBusinessUnitRole();
            assignment2.setUserId(USER_ID);
            assignment2.setBusinessUnitId("bu-002");
            
            when(userBusinessUnitRoleRepository.findByUserId(USER_ID))
                    .thenReturn(Arrays.asList(assignment1, assignment2));
            
            String result = service.getUserBusinessUnitId(USER_ID);
            
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return preferred business unit CODE when user has UBR for it")
        void shouldReturnPreferredWhenMultiBu() {
            UserBusinessUnitRole assignment1 = new UserBusinessUnitRole();
            assignment1.setUserId(USER_ID);
            assignment1.setBusinessUnitId(BU_ID);

            UserBusinessUnitRole assignment2 = new UserBusinessUnitRole();
            assignment2.setUserId(USER_ID);
            assignment2.setBusinessUnitId("bu-002");

            when(userBusinessUnitRoleRepository.findByUserId(USER_ID))
                    .thenReturn(Arrays.asList(assignment1, assignment2));
            // preferred is a CODE; resolve code → entity then match against UBR ids
            when(businessUnitRepository.findByCode("BU_OTHER")).thenReturn(Optional.of(bu("bu-002", "BU_OTHER")));
            when(businessUnitRepository.findByCode(BU_CODE)).thenReturn(Optional.of(bu(BU_ID, BU_CODE)));

            assertThat(service.getUserBusinessUnitId(USER_ID, "BU_OTHER")).isEqualTo("BU_OTHER");
            assertThat(service.getUserBusinessUnitId(USER_ID, BU_CODE)).isEqualTo(BU_CODE);
        }

        @Test
        @DisplayName("Should return null when preferred code not found")
        void shouldReturnNullWhenPreferredCodeNotFound() {
            UserBusinessUnitRole assignment = new UserBusinessUnitRole();
            assignment.setUserId(USER_ID);
            assignment.setBusinessUnitId(BU_ID);

            when(userBusinessUnitRoleRepository.findByUserId(USER_ID))
                    .thenReturn(Arrays.asList(assignment));
            when(businessUnitRepository.findByCode("BU_UNKNOWN")).thenReturn(Optional.empty());

            assertThat(service.getUserBusinessUnitId(USER_ID, "BU_UNKNOWN")).isNull();
        }
    }

    @Nested
    @DisplayName("getBusinessUnitCodeById Tests")
    class GetBusinessUnitCodeByIdTests {

        @Test
        @DisplayName("Should map id to code")
        void shouldMapIdToCode() {
            when(businessUnitRepository.findById(BU_ID)).thenReturn(Optional.of(bu(BU_ID, BU_CODE)));
            assertThat(service.getBusinessUnitCodeById(BU_ID)).isEqualTo(BU_CODE);
        }

        @Test
        @DisplayName("Should return null for unknown id or blank")
        void shouldReturnNullForUnknownOrBlank() {
            when(businessUnitRepository.findById("nope")).thenReturn(Optional.empty());
            assertThat(service.getBusinessUnitCodeById("nope")).isNull();
            assertThat(service.getBusinessUnitCodeById(" ")).isNull();
            assertThat(service.getBusinessUnitCodeById(null)).isNull();
        }
    }
    
    @Nested
    @DisplayName("getParentBusinessUnitId Tests")
    class GetParentBusinessUnitIdTests {
        
        @Test
        @DisplayName("Should return parent CODE when business unit has parent")
        void shouldReturnParentCodeWhenHasParent() {
            BusinessUnit child = bu(BU_ID, BU_CODE);
            child.setParentId(PARENT_BU_ID);

            when(businessUnitRepository.findByCode(BU_CODE)).thenReturn(Optional.of(child));
            when(businessUnitRepository.findById(PARENT_BU_ID))
                    .thenReturn(Optional.of(bu(PARENT_BU_ID, PARENT_BU_CODE)));

            String result = service.getParentBusinessUnitId(BU_CODE);

            assertThat(result).isEqualTo(PARENT_BU_CODE);
        }

        @Test
        @DisplayName("Should return null when business unit has no parent")
        void shouldReturnNullWhenNoParent() {
            BusinessUnit root = bu(BU_ID, BU_CODE);
            root.setParentId(null);

            when(businessUnitRepository.findByCode(BU_CODE)).thenReturn(Optional.of(root));

            String result = service.getParentBusinessUnitId(BU_CODE);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should throw exception when business unit not found")
        void shouldThrowExceptionWhenNotFound() {
            when(businessUnitRepository.findByCode(BU_CODE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getParentBusinessUnitId(BU_CODE))
                    .isInstanceOf(BusinessUnitNotFoundException.class);
        }
    }
    
    @Nested
    @DisplayName("getUsersByBusinessUnitAndRole Tests")
    class GetUsersByBusinessUnitAndRoleTests {
        
        @Test
        @DisplayName("Should return user IDs when users exist with role in BU (by code)")
        void shouldReturnUserIdsWhenUsersExist() {
            when(businessUnitRepository.findByCode(BU_CODE)).thenReturn(Optional.of(bu(BU_ID, BU_CODE)));
            when(roleRepository.findByCode(ROLE_CODE)).thenReturn(Optional.of(roleOf(ROLE_ID, ROLE_CODE, RoleType.BU_BOUNDED)));
            when(userBusinessUnitRoleRepository.findUserIdsByBusinessUnitIdAndRoleId(BU_ID, ROLE_ID))
                    .thenReturn(Arrays.asList("user-001", "user-002"));

            List<String> result = service.getUsersByBusinessUnitAndRole(BU_CODE, ROLE_CODE);

            assertThat(result).containsExactly("user-001", "user-002");
        }

        @Test
        @DisplayName("Should return empty list when no users with role in BU")
        void shouldReturnEmptyListWhenNoUsers() {
            when(businessUnitRepository.findByCode(BU_CODE)).thenReturn(Optional.of(bu(BU_ID, BU_CODE)));
            when(roleRepository.findByCode(ROLE_CODE)).thenReturn(Optional.of(roleOf(ROLE_ID, ROLE_CODE, RoleType.BU_BOUNDED)));
            when(userBusinessUnitRoleRepository.findUserIdsByBusinessUnitIdAndRoleId(BU_ID, ROLE_ID))
                    .thenReturn(Collections.emptyList());

            List<String> result = service.getUsersByBusinessUnitAndRole(BU_CODE, ROLE_CODE);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list when role is not BU_BOUNDED")
        void shouldReturnEmptyListWhenRoleNotBuBounded() {
            when(businessUnitRepository.findByCode(BU_CODE)).thenReturn(Optional.of(bu(BU_ID, BU_CODE)));
            when(roleRepository.findByCode(ROLE_CODE)).thenReturn(Optional.of(roleOf(ROLE_ID, ROLE_CODE, RoleType.BU_UNBOUNDED)));

            List<String> result = service.getUsersByBusinessUnitAndRole(BU_CODE, ROLE_CODE);

            assertThat(result).isEmpty();
            verify(userBusinessUnitRoleRepository, never()).findUserIdsByBusinessUnitIdAndRoleId(any(), any());
        }

        @Test
        @DisplayName("Should throw exception when business unit code not found")
        void shouldThrowExceptionWhenBuNotFound() {
            when(businessUnitRepository.findByCode(BU_CODE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getUsersByBusinessUnitAndRole(BU_CODE, ROLE_CODE))
                    .isInstanceOf(BusinessUnitNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw exception when role code not found")
        void shouldThrowExceptionWhenRoleNotFound() {
            when(businessUnitRepository.findByCode(BU_CODE)).thenReturn(Optional.of(bu(BU_ID, BU_CODE)));
            when(roleRepository.findByCode(ROLE_CODE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getUsersByBusinessUnitAndRole(BU_CODE, ROLE_CODE))
                    .isInstanceOf(RoleNotFoundException.class);
        }
    }
    
    @Nested
    @DisplayName("getUsersByUnboundedRole Tests")
    class GetUsersByUnboundedRoleTests {
        
        @Test
        @DisplayName("Should return user IDs through virtual groups (by role code)")
        void shouldReturnUserIdsThroughVirtualGroups() {
            when(roleRepository.findByCode(ROLE_CODE)).thenReturn(Optional.of(roleOf(ROLE_ID, ROLE_CODE, RoleType.BU_UNBOUNDED)));
            when(virtualGroupRoleRepository.findVirtualGroupIdsByRoleId(ROLE_ID))
                    .thenReturn(Arrays.asList(VG_ID, "vg-002"));
            when(virtualGroupMemberRepository.findUserIdsByVirtualGroupIds(anyList()))
                    .thenReturn(Arrays.asList("user-001", "user-002", "user-003"));

            List<String> result = service.getUsersByUnboundedRole(ROLE_CODE);

            assertThat(result).containsExactly("user-001", "user-002", "user-003");
        }

        @Test
        @DisplayName("Should return empty list when no virtual groups bound to role")
        void shouldReturnEmptyListWhenNoVirtualGroups() {
            when(roleRepository.findByCode(ROLE_CODE)).thenReturn(Optional.of(roleOf(ROLE_ID, ROLE_CODE, RoleType.BU_UNBOUNDED)));
            when(virtualGroupRoleRepository.findVirtualGroupIdsByRoleId(ROLE_ID))
                    .thenReturn(Collections.emptyList());

            List<String> result = service.getUsersByUnboundedRole(ROLE_CODE);

            assertThat(result).isEmpty();
            verify(virtualGroupMemberRepository, never()).findUserIdsByVirtualGroupIds(anyList());
        }

        @Test
        @DisplayName("Should return empty list when role is not BU_UNBOUNDED")
        void shouldReturnEmptyListWhenRoleNotBuUnbounded() {
            when(roleRepository.findByCode(ROLE_CODE)).thenReturn(Optional.of(roleOf(ROLE_ID, ROLE_CODE, RoleType.BU_BOUNDED)));

            List<String> result = service.getUsersByUnboundedRole(ROLE_CODE);

            assertThat(result).isEmpty();
            verify(virtualGroupRoleRepository, never()).findVirtualGroupIdsByRoleId(any());
        }

        @Test
        @DisplayName("Should throw exception when role code not found")
        void shouldThrowExceptionWhenRoleNotFound() {
            when(roleRepository.findByCode(ROLE_CODE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getUsersByUnboundedRole(ROLE_CODE))
                    .isInstanceOf(RoleNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getUsersByVirtualGroupCode Tests")
    class GetUsersByVirtualGroupCodeTests {

        @Test
        @DisplayName("Should return distinct user IDs for virtual group code")
        void shouldReturnUserIdsForCode() {
            VirtualGroup vg = new VirtualGroup();
            vg.setId("vg-doc-verifiers");
            vg.setCode("DOCUMENT_VERIFIERS");

            VirtualGroupMember m1 = new VirtualGroupMember();
            m1.setUserId("user-001");
            m1.setGroupId(vg.getId());
            VirtualGroupMember m2 = new VirtualGroupMember();
            m2.setUserId("user-002");
            m2.setGroupId(vg.getId());

            when(virtualGroupRepository.findByCode("DOCUMENT_VERIFIERS")).thenReturn(Optional.of(vg));
            when(virtualGroupMemberRepository.findByGroupId(vg.getId())).thenReturn(Arrays.asList(m1, m2));

            List<String> result = service.getUsersByVirtualGroupCode("DOCUMENT_VERIFIERS");

            assertThat(result).containsExactly("user-001", "user-002");
        }

        @Test
        @DisplayName("Should return empty list when code unknown")
        void shouldReturnEmptyWhenCodeUnknown() {
            when(virtualGroupRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

            List<String> result = service.getUsersByVirtualGroupCode("UNKNOWN");

            assertThat(result).isEmpty();
            verify(virtualGroupMemberRepository, never()).findByGroupId(any());
        }

        @Test
        @DisplayName("Should return empty for blank code")
        void shouldReturnEmptyForBlankCode() {
            assertThat(service.getUsersByVirtualGroupCode(" ")).isEmpty();
            assertThat(service.getUsersByVirtualGroupCode(null)).isEmpty();
            verifyNoInteractions(virtualGroupRepository);
        }
    }
    
    @Nested
    @DisplayName("getEligibleRoleIds Tests")
    class GetEligibleRoleIdsTests {
        
        @Test
        @DisplayName("Should return eligible role CODES")
        void shouldReturnEligibleRoleCodes() {
            BusinessUnitRole bur1 = new BusinessUnitRole();
            bur1.setBusinessUnitId(BU_ID);
            bur1.setRoleId("role-001");

            BusinessUnitRole bur2 = new BusinessUnitRole();
            bur2.setBusinessUnitId(BU_ID);
            bur2.setRoleId("role-002");

            when(businessUnitRepository.findByCode(BU_CODE)).thenReturn(Optional.of(bu(BU_ID, BU_CODE)));
            when(businessUnitRoleRepository.findByBusinessUnitId(BU_ID))
                    .thenReturn(Arrays.asList(bur1, bur2));
            when(roleRepository.findByIdIn(Arrays.asList("role-001", "role-002")))
                    .thenReturn(Arrays.asList(
                            roleOf("role-001", "ROLE_A", RoleType.BU_BOUNDED),
                            roleOf("role-002", "ROLE_B", RoleType.BU_BOUNDED)));

            List<String> result = service.getEligibleRoleIds(BU_CODE);

            assertThat(result).containsExactlyInAnyOrder("ROLE_A", "ROLE_B");
        }

        @Test
        @DisplayName("Should return empty list when no eligible roles")
        void shouldReturnEmptyListWhenNoEligibleRoles() {
            when(businessUnitRepository.findByCode(BU_CODE)).thenReturn(Optional.of(bu(BU_ID, BU_CODE)));
            when(businessUnitRoleRepository.findByBusinessUnitId(BU_ID))
                    .thenReturn(Collections.emptyList());
            when(roleRepository.findByIdIn(Collections.emptyList())).thenReturn(Collections.emptyList());

            List<String> result = service.getEligibleRoleIds(BU_CODE);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should throw exception when business unit code not found")
        void shouldThrowExceptionWhenBuNotFound() {
            when(businessUnitRepository.findByCode(BU_CODE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getEligibleRoleIds(BU_CODE))
                    .isInstanceOf(BusinessUnitNotFoundException.class);
        }
    }
    
    @Nested
    @DisplayName("isEligibleRole Tests")
    class IsEligibleRoleTests {
        
        @Test
        @DisplayName("Should return true when role is eligible (by code)")
        void shouldReturnTrueWhenEligible() {
            when(businessUnitRepository.findByCode(BU_CODE)).thenReturn(Optional.of(bu(BU_ID, BU_CODE)));
            when(roleRepository.findByCode(ROLE_CODE)).thenReturn(Optional.of(roleOf(ROLE_ID, ROLE_CODE, RoleType.BU_BOUNDED)));
            when(businessUnitRoleRepository.existsByBusinessUnitIdAndRoleId(BU_ID, ROLE_ID))
                    .thenReturn(true);

            boolean result = service.isEligibleRole(BU_CODE, ROLE_CODE);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false when role is not eligible")
        void shouldReturnFalseWhenNotEligible() {
            when(businessUnitRepository.findByCode(BU_CODE)).thenReturn(Optional.of(bu(BU_ID, BU_CODE)));
            when(roleRepository.findByCode(ROLE_CODE)).thenReturn(Optional.of(roleOf(ROLE_ID, ROLE_CODE, RoleType.BU_BOUNDED)));
            when(businessUnitRoleRepository.existsByBusinessUnitIdAndRoleId(BU_ID, ROLE_ID))
                    .thenReturn(false);

            boolean result = service.isEligibleRole(BU_CODE, ROLE_CODE);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false when BU or role code not found")
        void shouldReturnFalseWhenCodeNotFound() {
            when(businessUnitRepository.findByCode(BU_CODE)).thenReturn(Optional.empty());

            assertThat(service.isEligibleRole(BU_CODE, ROLE_CODE)).isFalse();
            verify(businessUnitRoleRepository, never()).existsByBusinessUnitIdAndRoleId(any(), any());
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
            
            when(roleRepository.findByType("BU_BOUNDED"))
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
            
            when(roleRepository.findByType("BU_UNBOUNDED"))
                    .thenReturn(Arrays.asList(role1));
            
            List<Role> result = service.getBuUnboundedRoles();
            
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo("role-001");
        }
    }
}
