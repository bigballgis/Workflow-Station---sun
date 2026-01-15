package com.workflow.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AssigneeType 枚举单元测试
 * 验证9种分配类型的属性正确性
 */
@DisplayName("AssigneeType Enum Tests")
class AssigneeTypeTest {
    
    @Test
    @DisplayName("Should have exactly 9 assignee types")
    void shouldHaveNineAssigneeTypes() {
        assertThat(AssigneeType.values()).hasSize(9);
    }
    
    @Test
    @DisplayName("Should contain all expected types")
    void shouldContainAllExpectedTypes() {
        assertThat(AssigneeType.values()).containsExactlyInAnyOrder(
            AssigneeType.FUNCTION_MANAGER,
            AssigneeType.ENTITY_MANAGER,
            AssigneeType.INITIATOR,
            AssigneeType.CURRENT_BU_ROLE,
            AssigneeType.CURRENT_PARENT_BU_ROLE,
            AssigneeType.INITIATOR_BU_ROLE,
            AssigneeType.INITIATOR_PARENT_BU_ROLE,
            AssigneeType.FIXED_BU_ROLE,
            AssigneeType.BU_UNBOUNDED_ROLE
        );
    }
    
    // ==================== Direct Assignment Types ====================
    
    @Test
    @DisplayName("FUNCTION_MANAGER should be direct assignment type")
    void functionManagerShouldBeDirectAssignment() {
        AssigneeType type = AssigneeType.FUNCTION_MANAGER;
        assertThat(type.requiresClaim()).isFalse();
        assertThat(type.requiresRoleId()).isFalse();
        assertThat(type.requiresBusinessUnitId()).isFalse();
        assertThat(type.isDirectAssignment()).isTrue();
    }
    
    @Test
    @DisplayName("ENTITY_MANAGER should be direct assignment type")
    void entityManagerShouldBeDirectAssignment() {
        AssigneeType type = AssigneeType.ENTITY_MANAGER;
        assertThat(type.requiresClaim()).isFalse();
        assertThat(type.requiresRoleId()).isFalse();
        assertThat(type.requiresBusinessUnitId()).isFalse();
        assertThat(type.isDirectAssignment()).isTrue();
    }
    
    @Test
    @DisplayName("INITIATOR should be direct assignment type")
    void initiatorShouldBeDirectAssignment() {
        AssigneeType type = AssigneeType.INITIATOR;
        assertThat(type.requiresClaim()).isFalse();
        assertThat(type.requiresRoleId()).isFalse();
        assertThat(type.requiresBusinessUnitId()).isFalse();
        assertThat(type.isDirectAssignment()).isTrue();
    }
    
    // ==================== Claim Types with Role ID ====================
    
    @Test
    @DisplayName("CURRENT_BU_ROLE should require claim and roleId")
    void currentBuRoleShouldRequireClaimAndRoleId() {
        AssigneeType type = AssigneeType.CURRENT_BU_ROLE;
        assertThat(type.requiresClaim()).isTrue();
        assertThat(type.requiresRoleId()).isTrue();
        assertThat(type.requiresBusinessUnitId()).isFalse();
        assertThat(type.isCurrentUserBased()).isTrue();
        assertThat(type.isBuBoundedRoleType()).isTrue();
    }
    
    @Test
    @DisplayName("CURRENT_PARENT_BU_ROLE should require claim and roleId")
    void currentParentBuRoleShouldRequireClaimAndRoleId() {
        AssigneeType type = AssigneeType.CURRENT_PARENT_BU_ROLE;
        assertThat(type.requiresClaim()).isTrue();
        assertThat(type.requiresRoleId()).isTrue();
        assertThat(type.requiresBusinessUnitId()).isFalse();
        assertThat(type.isCurrentUserBased()).isTrue();
        assertThat(type.isBuBoundedRoleType()).isTrue();
    }
    
    @Test
    @DisplayName("INITIATOR_BU_ROLE should require claim and roleId")
    void initiatorBuRoleShouldRequireClaimAndRoleId() {
        AssigneeType type = AssigneeType.INITIATOR_BU_ROLE;
        assertThat(type.requiresClaim()).isTrue();
        assertThat(type.requiresRoleId()).isTrue();
        assertThat(type.requiresBusinessUnitId()).isFalse();
        assertThat(type.isInitiatorBased()).isTrue(); // Based on initiator's BU
        assertThat(type.isBuBoundedRoleType()).isTrue();
    }
    
    @Test
    @DisplayName("INITIATOR_PARENT_BU_ROLE should require claim and roleId")
    void initiatorParentBuRoleShouldRequireClaimAndRoleId() {
        AssigneeType type = AssigneeType.INITIATOR_PARENT_BU_ROLE;
        assertThat(type.requiresClaim()).isTrue();
        assertThat(type.requiresRoleId()).isTrue();
        assertThat(type.requiresBusinessUnitId()).isFalse();
        assertThat(type.isBuBoundedRoleType()).isTrue();
    }
    
    @Test
    @DisplayName("FIXED_BU_ROLE should require claim, roleId and businessUnitId")
    void fixedBuRoleShouldRequireAllParams() {
        AssigneeType type = AssigneeType.FIXED_BU_ROLE;
        assertThat(type.requiresClaim()).isTrue();
        assertThat(type.requiresRoleId()).isTrue();
        assertThat(type.requiresBusinessUnitId()).isTrue();
        assertThat(type.isBuBoundedRoleType()).isTrue();
    }
    
    @Test
    @DisplayName("BU_UNBOUNDED_ROLE should require claim and roleId but not businessUnitId")
    void buUnboundedRoleShouldRequireClaimAndRoleId() {
        AssigneeType type = AssigneeType.BU_UNBOUNDED_ROLE;
        assertThat(type.requiresClaim()).isTrue();
        assertThat(type.requiresRoleId()).isTrue();
        assertThat(type.requiresBusinessUnitId()).isFalse();
        assertThat(type.isBuBoundedRoleType()).isFalse();
    }
    
    // ==================== Property 5: Claim Mode Consistency ====================
    
    @ParameterizedTest
    @EnumSource(value = AssigneeType.class, names = {"FUNCTION_MANAGER", "ENTITY_MANAGER", "INITIATOR"})
    @DisplayName("Direct assignment types should not require claim")
    void directAssignmentTypesShouldNotRequireClaim(AssigneeType type) {
        assertThat(type.requiresClaim()).isFalse();
        assertThat(type.isDirectAssignment()).isTrue();
    }
    
    @ParameterizedTest
    @EnumSource(value = AssigneeType.class, names = {
        "CURRENT_BU_ROLE", "CURRENT_PARENT_BU_ROLE", 
        "INITIATOR_BU_ROLE", "INITIATOR_PARENT_BU_ROLE",
        "FIXED_BU_ROLE", "BU_UNBOUNDED_ROLE"
    })
    @DisplayName("Claim types should require claim")
    void claimTypesShouldRequireClaim(AssigneeType type) {
        assertThat(type.requiresClaim()).isTrue();
        assertThat(type.isDirectAssignment()).isFalse();
    }
    
    // ==================== fromCode Tests ====================
    
    @ParameterizedTest
    @EnumSource(AssigneeType.class)
    @DisplayName("fromCode should return correct type for all codes")
    void fromCodeShouldReturnCorrectType(AssigneeType expected) {
        AssigneeType actual = AssigneeType.fromCode(expected.getCode());
        assertThat(actual).isEqualTo(expected);
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"FUNCTION_MANAGER", "function_manager", "FuNcTiOn_MaNaGeR"})
    @DisplayName("fromCode should be case insensitive")
    void fromCodeShouldBeCaseInsensitive(String code) {
        AssigneeType type = AssigneeType.fromCode(code);
        assertThat(type).isEqualTo(AssigneeType.FUNCTION_MANAGER);
    }
    
    @Test
    @DisplayName("fromCode should return null for null input")
    void fromCodeShouldReturnNullForNullInput() {
        assertThat(AssigneeType.fromCode(null)).isNull();
    }
    
    @Test
    @DisplayName("fromCode should return null for unknown code")
    void fromCodeShouldReturnNullForUnknownCode() {
        assertThat(AssigneeType.fromCode("UNKNOWN_TYPE")).isNull();
    }
    
    // ==================== Legacy Code Compatibility ====================
    
    @Test
    @DisplayName("Legacy DEPT_OTHERS should map to CURRENT_BU_ROLE")
    void legacyDeptOthersShouldMapToCurrentBuRole() {
        assertThat(AssigneeType.fromCode("DEPT_OTHERS")).isEqualTo(AssigneeType.CURRENT_BU_ROLE);
        assertThat(AssigneeType.fromCode("dept_others")).isEqualTo(AssigneeType.CURRENT_BU_ROLE);
    }
    
    @Test
    @DisplayName("Legacy PARENT_DEPT should map to CURRENT_PARENT_BU_ROLE")
    void legacyParentDeptShouldMapToCurrentParentBuRole() {
        assertThat(AssigneeType.fromCode("PARENT_DEPT")).isEqualTo(AssigneeType.CURRENT_PARENT_BU_ROLE);
        assertThat(AssigneeType.fromCode("parent_dept")).isEqualTo(AssigneeType.CURRENT_PARENT_BU_ROLE);
    }
    
    @Test
    @DisplayName("Legacy FIXED_DEPT should map to FIXED_BU_ROLE")
    void legacyFixedDeptShouldMapToFixedBuRole() {
        assertThat(AssigneeType.fromCode("FIXED_DEPT")).isEqualTo(AssigneeType.FIXED_BU_ROLE);
        assertThat(AssigneeType.fromCode("fixed_dept")).isEqualTo(AssigneeType.FIXED_BU_ROLE);
    }
    
    @Test
    @DisplayName("Legacy VIRTUAL_GROUP should map to BU_UNBOUNDED_ROLE")
    void legacyVirtualGroupShouldMapToBuUnboundedRole() {
        assertThat(AssigneeType.fromCode("VIRTUAL_GROUP")).isEqualTo(AssigneeType.BU_UNBOUNDED_ROLE);
        assertThat(AssigneeType.fromCode("virtual_group")).isEqualTo(AssigneeType.BU_UNBOUNDED_ROLE);
    }
    
    // ==================== Code and Name Tests ====================
    
    @ParameterizedTest
    @EnumSource(AssigneeType.class)
    @DisplayName("All types should have non-null code and name")
    void allTypesShouldHaveNonNullCodeAndName(AssigneeType type) {
        assertThat(type.getCode()).isNotNull().isNotEmpty();
        assertThat(type.getName()).isNotNull().isNotEmpty();
    }
    
    @Test
    @DisplayName("All codes should be unique")
    void allCodesShouldBeUnique() {
        String[] codes = new String[AssigneeType.values().length];
        for (int i = 0; i < AssigneeType.values().length; i++) {
            codes[i] = AssigneeType.values()[i].getCode();
        }
        assertThat(codes).doesNotHaveDuplicates();
    }
}
