package com.workflow.service;

import com.workflow.client.AdminCenterClient;
import com.workflow.enums.AssigneeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * TaskAssigneeResolver 单元测试
 * 验证9种分配类型的解析逻辑
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskAssigneeResolver Tests")
class TaskAssigneeResolverTest {
    
    @Mock
    private AdminCenterClient adminCenterClient;
    
    @InjectMocks
    private TaskAssigneeResolver resolver;
    
    private static final String INITIATOR_ID = "initiator-001";
    private static final String CURRENT_USER_ID = "current-user-001";
    private static final String ROLE_ID = "role-001";
    private static final String BU_ID = "bu-001";
    private static final String PARENT_BU_ID = "bu-parent";
    private static final String FUNCTION_MANAGER_ID = "manager-func-001";
    private static final String ENTITY_MANAGER_ID = "manager-entity-001";
    
    // ==================== Property 1: Direct Assignment Resolution ====================
    
    @Nested
    @DisplayName("Direct Assignment Types")
    class DirectAssignmentTests {
        
        @Test
        @DisplayName("FUNCTION_MANAGER should resolve to initiator's function manager")
        void functionManagerShouldResolveToFunctionManager() {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("functionManagerId", FUNCTION_MANAGER_ID);
            when(adminCenterClient.getUserInfo(INITIATOR_ID)).thenReturn(userInfo);
            
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.FUNCTION_MANAGER, null, null, INITIATOR_ID, null);
            
            assertThat(result.getAssignee()).isEqualTo(FUNCTION_MANAGER_ID);
            assertThat(result.isRequiresClaim()).isFalse();
            assertThat(result.getCandidateUsers()).isNull();
            assertThat(result.getErrorMessage()).isNull();
        }
        
        @Test
        @DisplayName("FUNCTION_MANAGER should return error when no function manager set")
        void functionManagerShouldReturnErrorWhenNotSet() {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("functionManagerId", null);
            when(adminCenterClient.getUserInfo(INITIATOR_ID)).thenReturn(userInfo);
            
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.FUNCTION_MANAGER, null, null, INITIATOR_ID, null);
            
            assertThat(result.getAssignee()).isNull();
            assertThat(result.getErrorMessage()).contains("没有设置职能经理");
        }
        
        @Test
        @DisplayName("ENTITY_MANAGER should resolve to initiator's entity manager")
        void entityManagerShouldResolveToEntityManager() {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("entityManagerId", ENTITY_MANAGER_ID);
            when(adminCenterClient.getUserInfo(INITIATOR_ID)).thenReturn(userInfo);
            
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.ENTITY_MANAGER, null, null, INITIATOR_ID, null);
            
            assertThat(result.getAssignee()).isEqualTo(ENTITY_MANAGER_ID);
            assertThat(result.isRequiresClaim()).isFalse();
            assertThat(result.getCandidateUsers()).isNull();
        }
        
        @Test
        @DisplayName("INITIATOR should resolve to initiator ID directly")
        void initiatorShouldResolveToInitiatorId() {
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.INITIATOR, null, null, INITIATOR_ID, null);
            
            assertThat(result.getAssignee()).isEqualTo(INITIATOR_ID);
            assertThat(result.isRequiresClaim()).isFalse();
            assertThat(result.getCandidateUsers()).isNull();
            verifyNoInteractions(adminCenterClient);
        }
        
        @ParameterizedTest
        @EnumSource(value = AssigneeType.class, names = {"FUNCTION_MANAGER", "ENTITY_MANAGER", "INITIATOR"})
        @DisplayName("Direct assignment types should not require claim")
        void directAssignmentTypesShouldNotRequireClaim(AssigneeType type) {
            // Setup mocks for types that need user info
            if (type == AssigneeType.FUNCTION_MANAGER) {
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("functionManagerId", FUNCTION_MANAGER_ID);
                when(adminCenterClient.getUserInfo(INITIATOR_ID)).thenReturn(userInfo);
            } else if (type == AssigneeType.ENTITY_MANAGER) {
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("entityManagerId", ENTITY_MANAGER_ID);
                when(adminCenterClient.getUserInfo(INITIATOR_ID)).thenReturn(userInfo);
            }
            
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    type, null, null, INITIATOR_ID, null);
            
            assertThat(result.isRequiresClaim()).isFalse();
            assertThat(result.getAssignee()).isNotNull();
        }
    }
    
    // ==================== Property 2: BU Role Candidate Resolution ====================
    
    @Nested
    @DisplayName("Current User BU Role Types")
    class CurrentUserBuRoleTests {
        
        @Test
        @DisplayName("CURRENT_BU_ROLE should resolve to candidates in current user's BU")
        void currentBuRoleShouldResolveToCandidates() {
            when(adminCenterClient.getUserBusinessUnitId(CURRENT_USER_ID)).thenReturn(BU_ID);
            when(adminCenterClient.getUsersByBusinessUnitAndRole(BU_ID, ROLE_ID))
                    .thenReturn(Arrays.asList("user-001", "user-002"));
            
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.CURRENT_BU_ROLE, ROLE_ID, null, INITIATOR_ID, CURRENT_USER_ID);
            
            assertThat(result.getCandidateUsers()).containsExactly("user-001", "user-002");
            assertThat(result.isRequiresClaim()).isTrue();
            assertThat(result.getAssignee()).isNull();
        }
        
        @Test
        @DisplayName("CURRENT_BU_ROLE should return error when user has no BU")
        void currentBuRoleShouldReturnErrorWhenNoBu() {
            when(adminCenterClient.getUserBusinessUnitId(CURRENT_USER_ID)).thenReturn(null);
            
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.CURRENT_BU_ROLE, ROLE_ID, null, INITIATOR_ID, CURRENT_USER_ID);
            
            assertThat(result.getCandidateUsers()).isNull();
            assertThat(result.getErrorMessage()).contains("没有所属业务单元");
        }
        
        @Test
        @DisplayName("CURRENT_BU_ROLE should return error when no candidates found")
        void currentBuRoleShouldReturnErrorWhenNoCandidates() {
            when(adminCenterClient.getUserBusinessUnitId(CURRENT_USER_ID)).thenReturn(BU_ID);
            when(adminCenterClient.getUsersByBusinessUnitAndRole(BU_ID, ROLE_ID))
                    .thenReturn(Collections.emptyList());
            
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.CURRENT_BU_ROLE, ROLE_ID, null, INITIATOR_ID, CURRENT_USER_ID);
            
            assertThat(result.getCandidateUsers()).isNull();
            assertThat(result.getErrorMessage()).contains("没有拥有角色");
        }
    }
    
    // ==================== Property 3: Parent BU Role Candidate Resolution ====================
    
    @Nested
    @DisplayName("Parent BU Role Types")
    class ParentBuRoleTests {
        
        @Test
        @DisplayName("CURRENT_PARENT_BU_ROLE should resolve to candidates in parent BU")
        void currentParentBuRoleShouldResolveToCandidates() {
            when(adminCenterClient.getUserBusinessUnitId(CURRENT_USER_ID)).thenReturn(BU_ID);
            when(adminCenterClient.getParentBusinessUnitId(BU_ID)).thenReturn(PARENT_BU_ID);
            when(adminCenterClient.getUsersByBusinessUnitAndRole(PARENT_BU_ID, ROLE_ID))
                    .thenReturn(Arrays.asList("user-003", "user-004"));
            
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.CURRENT_PARENT_BU_ROLE, ROLE_ID, null, INITIATOR_ID, CURRENT_USER_ID);
            
            assertThat(result.getCandidateUsers()).containsExactly("user-003", "user-004");
            assertThat(result.isRequiresClaim()).isTrue();
        }
        
        @Test
        @DisplayName("CURRENT_PARENT_BU_ROLE should return error when no parent BU")
        void currentParentBuRoleShouldReturnErrorWhenNoParent() {
            when(adminCenterClient.getUserBusinessUnitId(CURRENT_USER_ID)).thenReturn(BU_ID);
            when(adminCenterClient.getParentBusinessUnitId(BU_ID)).thenReturn(null);
            
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.CURRENT_PARENT_BU_ROLE, ROLE_ID, null, INITIATOR_ID, CURRENT_USER_ID);
            
            assertThat(result.getCandidateUsers()).isNull();
            assertThat(result.getErrorMessage()).contains("没有上级业务单元");
        }
        
        @Test
        @DisplayName("INITIATOR_PARENT_BU_ROLE should resolve to candidates in initiator's parent BU")
        void initiatorParentBuRoleShouldResolveToCandidates() {
            when(adminCenterClient.getUserBusinessUnitId(INITIATOR_ID)).thenReturn(BU_ID);
            when(adminCenterClient.getParentBusinessUnitId(BU_ID)).thenReturn(PARENT_BU_ID);
            when(adminCenterClient.getUsersByBusinessUnitAndRole(PARENT_BU_ID, ROLE_ID))
                    .thenReturn(Arrays.asList("user-005"));
            
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.INITIATOR_PARENT_BU_ROLE, ROLE_ID, null, INITIATOR_ID, CURRENT_USER_ID);
            
            assertThat(result.getCandidateUsers()).containsExactly("user-005");
            assertThat(result.isRequiresClaim()).isTrue();
        }
    }
    
    // ==================== Initiator BU Role Tests ====================
    
    @Nested
    @DisplayName("Initiator BU Role Types")
    class InitiatorBuRoleTests {
        
        @Test
        @DisplayName("INITIATOR_BU_ROLE should resolve to candidates in initiator's BU")
        void initiatorBuRoleShouldResolveToCandidates() {
            when(adminCenterClient.getUserBusinessUnitId(INITIATOR_ID)).thenReturn(BU_ID);
            when(adminCenterClient.getUsersByBusinessUnitAndRole(BU_ID, ROLE_ID))
                    .thenReturn(Arrays.asList("user-006", "user-007"));
            
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.INITIATOR_BU_ROLE, ROLE_ID, null, INITIATOR_ID, CURRENT_USER_ID);
            
            assertThat(result.getCandidateUsers()).containsExactly("user-006", "user-007");
            assertThat(result.isRequiresClaim()).isTrue();
        }
        
        @Test
        @DisplayName("INITIATOR_BU_ROLE should return error when initiator has no BU")
        void initiatorBuRoleShouldReturnErrorWhenNoBu() {
            when(adminCenterClient.getUserBusinessUnitId(INITIATOR_ID)).thenReturn(null);
            
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.INITIATOR_BU_ROLE, ROLE_ID, null, INITIATOR_ID, CURRENT_USER_ID);
            
            assertThat(result.getCandidateUsers()).isNull();
            assertThat(result.getErrorMessage()).contains("发起人没有所属业务单元");
        }
    }
    
    // ==================== Property 6: Eligible Role Validation ====================
    
    @Nested
    @DisplayName("Fixed BU Role Tests")
    class FixedBuRoleTests {
        
        @Test
        @DisplayName("FIXED_BU_ROLE should resolve to candidates in specified BU")
        void fixedBuRoleShouldResolveToCandidates() {
            when(adminCenterClient.isEligibleRole(BU_ID, ROLE_ID)).thenReturn(true);
            when(adminCenterClient.getUsersByBusinessUnitAndRole(BU_ID, ROLE_ID))
                    .thenReturn(Arrays.asList("user-008", "user-009"));
            
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.FIXED_BU_ROLE, ROLE_ID, BU_ID, INITIATOR_ID, CURRENT_USER_ID);
            
            assertThat(result.getCandidateUsers()).containsExactly("user-008", "user-009");
            assertThat(result.isRequiresClaim()).isTrue();
        }
        
        @Test
        @DisplayName("FIXED_BU_ROLE should return error when role is not eligible")
        void fixedBuRoleShouldReturnErrorWhenNotEligible() {
            when(adminCenterClient.isEligibleRole(BU_ID, ROLE_ID)).thenReturn(false);
            
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.FIXED_BU_ROLE, ROLE_ID, BU_ID, INITIATOR_ID, CURRENT_USER_ID);
            
            assertThat(result.getCandidateUsers()).isNull();
            assertThat(result.getErrorMessage()).contains("不是业务单元").contains("的准入角色");
        }
        
        @Test
        @DisplayName("FIXED_BU_ROLE should require businessUnitId parameter")
        void fixedBuRoleShouldRequireBusinessUnitId() {
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.FIXED_BU_ROLE, ROLE_ID, null, INITIATOR_ID, CURRENT_USER_ID);
            
            assertThat(result.getErrorMessage()).contains("需要指定业务单元ID");
        }
    }
    
    // ==================== Property 4: BU Unbounded Role Candidate Resolution ====================
    
    @Nested
    @DisplayName("BU Unbounded Role Tests")
    class BuUnboundedRoleTests {
        
        @Test
        @DisplayName("BU_UNBOUNDED_ROLE should resolve to candidates through virtual groups")
        void buUnboundedRoleShouldResolveToCandidates() {
            when(adminCenterClient.getUsersByUnboundedRole(ROLE_ID))
                    .thenReturn(Arrays.asList("user-010", "user-011", "user-012"));
            
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.BU_UNBOUNDED_ROLE, ROLE_ID, null, INITIATOR_ID, CURRENT_USER_ID);
            
            assertThat(result.getCandidateUsers()).containsExactly("user-010", "user-011", "user-012");
            assertThat(result.isRequiresClaim()).isTrue();
        }
        
        @Test
        @DisplayName("BU_UNBOUNDED_ROLE should return error when no candidates found")
        void buUnboundedRoleShouldReturnErrorWhenNoCandidates() {
            when(adminCenterClient.getUsersByUnboundedRole(ROLE_ID))
                    .thenReturn(Collections.emptyList());
            
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    AssigneeType.BU_UNBOUNDED_ROLE, ROLE_ID, null, INITIATOR_ID, CURRENT_USER_ID);
            
            assertThat(result.getCandidateUsers()).isNull();
            assertThat(result.getErrorMessage()).contains("没有用户拥有角色");
        }
    }
    
    // ==================== Parameter Validation Tests ====================
    
    @Nested
    @DisplayName("Parameter Validation Tests")
    class ParameterValidationTests {
        
        @ParameterizedTest
        @EnumSource(value = AssigneeType.class, names = {
                "CURRENT_BU_ROLE", "CURRENT_PARENT_BU_ROLE",
                "INITIATOR_BU_ROLE", "INITIATOR_PARENT_BU_ROLE",
                "FIXED_BU_ROLE", "BU_UNBOUNDED_ROLE"
        })
        @DisplayName("Role-based types should require roleId")
        void roleBasedTypesShouldRequireRoleId(AssigneeType type) {
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    type, null, BU_ID, INITIATOR_ID, CURRENT_USER_ID);
            
            assertThat(result.getErrorMessage()).contains("需要指定角色ID");
        }
        
        @ParameterizedTest
        @EnumSource(value = AssigneeType.class, names = {"CURRENT_BU_ROLE", "CURRENT_PARENT_BU_ROLE"})
        @DisplayName("Current user based types should require currentUserId")
        void currentUserBasedTypesShouldRequireCurrentUserId(AssigneeType type) {
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    type, ROLE_ID, null, INITIATOR_ID, null);
            
            assertThat(result.getErrorMessage()).contains("需要当前处理人ID");
        }
        
        @Test
        @DisplayName("Unknown assignee type code should return error")
        void unknownTypeShouldReturnError() {
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    "UNKNOWN_TYPE", ROLE_ID, BU_ID, INITIATOR_ID, CURRENT_USER_ID);
            
            assertThat(result.getErrorMessage()).contains("未知的分配类型");
        }
    }
    
    // ==================== Property 5: Claim Mode Consistency ====================
    
    @Nested
    @DisplayName("Claim Mode Consistency Tests")
    class ClaimModeConsistencyTests {
        
        @ParameterizedTest
        @EnumSource(value = AssigneeType.class, names = {
                "CURRENT_BU_ROLE", "CURRENT_PARENT_BU_ROLE",
                "INITIATOR_BU_ROLE", "INITIATOR_PARENT_BU_ROLE",
                "FIXED_BU_ROLE", "BU_UNBOUNDED_ROLE"
        })
        @DisplayName("Claim types should always set requiresClaim to true")
        void claimTypesShouldAlwaysRequireClaim(AssigneeType type) {
            // Even when there's an error, requiresClaim should be true for claim types
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    type, null, null, INITIATOR_ID, null);
            
            assertThat(result.isRequiresClaim()).isTrue();
        }
    }
    
    // ==================== Legacy Compatibility Tests ====================
    
    @Nested
    @DisplayName("Legacy Compatibility Tests")
    class LegacyCompatibilityTests {
        
        @Test
        @DisplayName("Legacy resolve method should work with old parameters")
        @SuppressWarnings("deprecation")
        void legacyResolveShouldWork() {
            when(adminCenterClient.getUsersByUnboundedRole(ROLE_ID))
                    .thenReturn(Arrays.asList("user-001"));
            
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    "BU_UNBOUNDED_ROLE", ROLE_ID, INITIATOR_ID);
            
            assertThat(result.getCandidateUsers()).containsExactly("user-001");
        }
        
        @Test
        @DisplayName("Legacy type codes should be mapped correctly")
        void legacyTypeCodesShouldBeMapped() {
            when(adminCenterClient.getUserBusinessUnitId(INITIATOR_ID)).thenReturn(BU_ID);
            when(adminCenterClient.getUsersByBusinessUnitAndRole(BU_ID, ROLE_ID))
                    .thenReturn(Arrays.asList("user-001"));
            
            // DEPT_OTHERS should map to CURRENT_BU_ROLE
            TaskAssigneeResolver.ResolveResult result = resolver.resolve(
                    "DEPT_OTHERS", ROLE_ID, null, INITIATOR_ID, INITIATOR_ID);
            
            assertThat(result.getAssigneeType()).isEqualTo(AssigneeType.CURRENT_BU_ROLE);
        }
    }
}
