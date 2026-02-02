package com.admin.helper;

import com.admin.enums.BusinessUnitStatus;
import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.UserBusinessUnitRepository;
import com.admin.repository.UserRepository;
import com.platform.security.entity.BusinessUnit;
import com.platform.security.entity.User;
import com.platform.security.entity.UserBusinessUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BusinessUnitHelper service.
 * Tests business unit operations with mocked repository dependencies.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BusinessUnitHelper Tests")
class BusinessUnitHelperTest {
    
    @Mock
    private BusinessUnitRepository businessUnitRepository;
    
    @Mock
    private UserBusinessUnitRepository userBusinessUnitRepository;
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private BusinessUnitHelper businessUnitHelper;
    
    private BusinessUnit activeUnit;
    private BusinessUnit disabledUnit;
    private BusinessUnit parentUnit;
    private BusinessUnit childUnit1;
    private BusinessUnit childUnit2;
    
    private User user1;
    private User user2;
    private User user3;
    
    private UserBusinessUnit userBu1;
    private UserBusinessUnit userBu2;
    private UserBusinessUnit userBu3;
    
    @BeforeEach
    void setUp() {
        // Create test business units
        activeUnit = BusinessUnit.builder()
                .id("bu-1")
                .code("BU001")
                .name("Active Business Unit")
                .status("ACTIVE")
                .sortOrder(1)
                .build();
        
        disabledUnit = BusinessUnit.builder()
                .id("bu-2")
                .code("BU002")
                .name("Disabled Business Unit")
                .status("DISABLED")
                .sortOrder(2)
                .build();
        
        parentUnit = BusinessUnit.builder()
                .id("bu-parent")
                .code("BU_PARENT")
                .name("Parent Business Unit")
                .status("ACTIVE")
                .parentId(null)
                .sortOrder(1)
                .build();
        
        childUnit1 = BusinessUnit.builder()
                .id("bu-child-1")
                .code("BU_CHILD_1")
                .name("Child Business Unit 1")
                .status("ACTIVE")
                .parentId("bu-parent")
                .sortOrder(1)
                .build();
        
        childUnit2 = BusinessUnit.builder()
                .id("bu-child-2")
                .code("BU_CHILD_2")
                .name("Child Business Unit 2")
                .status("ACTIVE")
                .parentId("bu-parent")
                .sortOrder(2)
                .build();
        
        // Create test users
        user1 = User.builder()
                .id("user-1")
                .username("user1")
                .fullName("User One")
                .build();
        
        user2 = User.builder()
                .id("user-2")
                .username("user2")
                .fullName("User Two")
                .build();
        
        user3 = User.builder()
                .id("user-3")
                .username("user3")
                .fullName("User Three")
                .build();
        
        // Create test user-business unit relationships
        userBu1 = UserBusinessUnit.builder()
                .id("ubu-1")
                .userId("user-1")
                .businessUnitId("bu-1")
                .createdAt(LocalDateTime.now())
                .build();
        
        userBu2 = UserBusinessUnit.builder()
                .id("ubu-2")
                .userId("user-2")
                .businessUnitId("bu-1")
                .createdAt(LocalDateTime.now())
                .build();
        
        userBu3 = UserBusinessUnit.builder()
                .id("ubu-3")
                .userId("user-3")
                .businessUnitId("bu-1")
                .createdAt(LocalDateTime.now())
                .build();
    }
    
    // ========== getMemberCount Tests ==========
    
    @Test
    @DisplayName("getMemberCount: Should return correct count")
    void testGetMemberCount_ReturnsCorrectCount() {
        when(userBusinessUnitRepository.countByBusinessUnitId("bu-1")).thenReturn(3L);
        
        long count = businessUnitHelper.getMemberCount("bu-1");
        
        assertEquals(3L, count);
        verify(userBusinessUnitRepository, times(1)).countByBusinessUnitId("bu-1");
    }
    
    @Test
    @DisplayName("getMemberCount: Should return 0 for unit with no members")
    void testGetMemberCount_NoMembers() {
        when(userBusinessUnitRepository.countByBusinessUnitId("bu-2")).thenReturn(0L);
        
        long count = businessUnitHelper.getMemberCount("bu-2");
        
        assertEquals(0L, count);
        verify(userBusinessUnitRepository, times(1)).countByBusinessUnitId("bu-2");
    }
    
    @Test
    @DisplayName("getMemberCount: Should return 0 for null businessUnitId")
    void testGetMemberCount_NullId() {
        long count = businessUnitHelper.getMemberCount(null);
        
        assertEquals(0L, count);
        verify(userBusinessUnitRepository, never()).countByBusinessUnitId(any());
    }
    
    // ========== getMembers Tests ==========
    
    @Test
    @DisplayName("getMembers: Should return all members")
    void testGetMembers_ReturnsAllMembers() {
        List<UserBusinessUnit> userBus = Arrays.asList(userBu1, userBu2, userBu3);
        List<User> users = Arrays.asList(user1, user2, user3);
        List<String> userIds = Arrays.asList("user-1", "user-2", "user-3");
        
        when(userBusinessUnitRepository.findByBusinessUnitId("bu-1")).thenReturn(userBus);
        when(userRepository.findAllById(userIds)).thenReturn(users);
        
        List<User> result = businessUnitHelper.getMembers("bu-1");
        
        assertEquals(3, result.size());
        assertTrue(result.contains(user1));
        assertTrue(result.contains(user2));
        assertTrue(result.contains(user3));
        
        verify(userBusinessUnitRepository, times(1)).findByBusinessUnitId("bu-1");
        verify(userRepository, times(1)).findAllById(userIds);
    }
    
    @Test
    @DisplayName("getMembers: Should return empty list when no members")
    void testGetMembers_NoMembers() {
        when(userBusinessUnitRepository.findByBusinessUnitId("bu-2")).thenReturn(List.of());
        
        List<User> result = businessUnitHelper.getMembers("bu-2");
        
        assertTrue(result.isEmpty());
        verify(userBusinessUnitRepository, times(1)).findByBusinessUnitId("bu-2");
        verify(userRepository, never()).findAllById(any());
    }
    
    @Test
    @DisplayName("getMembers: Should return empty list for null businessUnitId")
    void testGetMembers_NullId() {
        List<User> result = businessUnitHelper.getMembers(null);
        
        assertTrue(result.isEmpty());
        verify(userBusinessUnitRepository, never()).findByBusinessUnitId(any());
        verify(userRepository, never()).findAllById(any());
    }
    
    @Test
    @DisplayName("getMembers: Should handle duplicate user IDs")
    void testGetMembers_DuplicateUserIds() {
        UserBusinessUnit duplicateUserBu = UserBusinessUnit.builder()
                .id("ubu-4")
                .userId("user-1") // Duplicate user ID
                .businessUnitId("bu-1")
                .createdAt(LocalDateTime.now())
                .build();
        
        List<UserBusinessUnit> userBus = Arrays.asList(userBu1, duplicateUserBu);
        List<String> expectedUserIds = List.of("user-1"); // Should be deduplicated
        
        when(userBusinessUnitRepository.findByBusinessUnitId("bu-1")).thenReturn(userBus);
        when(userRepository.findAllById(expectedUserIds)).thenReturn(List.of(user1));
        
        List<User> result = businessUnitHelper.getMembers("bu-1");
        
        assertEquals(1, result.size());
        assertEquals(user1, result.get(0));
        
        verify(userBusinessUnitRepository, times(1)).findByBusinessUnitId("bu-1");
        verify(userRepository, times(1)).findAllById(expectedUserIds);
    }
    
    // ========== getStatus Tests ==========
    
    @Test
    @DisplayName("getStatus: Should return ACTIVE for active unit")
    void testGetStatus_Active() {
        BusinessUnitStatus result = businessUnitHelper.getStatus(activeUnit);
        assertEquals(BusinessUnitStatus.ACTIVE, result);
    }
    
    @Test
    @DisplayName("getStatus: Should return DISABLED for disabled unit")
    void testGetStatus_Disabled() {
        BusinessUnitStatus result = businessUnitHelper.getStatus(disabledUnit);
        assertEquals(BusinessUnitStatus.DISABLED, result);
    }
    
    @Test
    @DisplayName("getStatus: Should return null for null unit")
    void testGetStatus_Null() {
        BusinessUnitStatus result = businessUnitHelper.getStatus(null);
        assertNull(result);
    }
    
    @Test
    @DisplayName("getStatus: Should throw IllegalArgumentException for invalid status")
    void testGetStatus_InvalidStatus() {
        BusinessUnit invalidUnit = BusinessUnit.builder()
                .id("bu-3")
                .code("BU003")
                .name("Invalid Unit")
                .status("INVALID_STATUS")
                .build();
        
        assertThrows(IllegalArgumentException.class, () -> businessUnitHelper.getStatus(invalidUnit));
    }
    
    // ========== isActive Tests ==========
    
    @Test
    @DisplayName("isActive: Should return true for ACTIVE status")
    void testIsActive_ActiveStatus() {
        assertTrue(businessUnitHelper.isActive(activeUnit));
    }
    
    @Test
    @DisplayName("isActive: Should return false for DISABLED status")
    void testIsActive_DisabledStatus() {
        assertFalse(businessUnitHelper.isActive(disabledUnit));
    }
    
    @Test
    @DisplayName("isActive: Should return false for null unit")
    void testIsActive_Null() {
        assertFalse(businessUnitHelper.isActive(null));
    }
    
    @Test
    @DisplayName("isActive: Should return false for null status")
    void testIsActive_NullStatus() {
        BusinessUnit unitWithNullStatus = BusinessUnit.builder()
                .id("bu-4")
                .code("BU004")
                .name("Null Status Unit")
                .status(null)
                .build();
        
        assertFalse(businessUnitHelper.isActive(unitWithNullStatus));
    }
    
    // ========== getChildren Tests ==========
    
    @Test
    @DisplayName("getChildren: Should return all children ordered by sortOrder")
    void testGetChildren_ReturnsAllChildren() {
        List<BusinessUnit> children = Arrays.asList(childUnit1, childUnit2);
        
        when(businessUnitRepository.findByParentIdOrderBySortOrder("bu-parent")).thenReturn(children);
        
        List<BusinessUnit> result = businessUnitHelper.getChildren("bu-parent");
        
        assertEquals(2, result.size());
        assertEquals(childUnit1, result.get(0));
        assertEquals(childUnit2, result.get(1));
        
        verify(businessUnitRepository, times(1)).findByParentIdOrderBySortOrder("bu-parent");
    }
    
    @Test
    @DisplayName("getChildren: Should return empty list when no children")
    void testGetChildren_NoChildren() {
        when(businessUnitRepository.findByParentIdOrderBySortOrder("bu-1")).thenReturn(List.of());
        
        List<BusinessUnit> result = businessUnitHelper.getChildren("bu-1");
        
        assertTrue(result.isEmpty());
        verify(businessUnitRepository, times(1)).findByParentIdOrderBySortOrder("bu-1");
    }
    
    @Test
    @DisplayName("getChildren: Should return empty list for null businessUnitId")
    void testGetChildren_NullId() {
        List<BusinessUnit> result = businessUnitHelper.getChildren(null);
        
        assertTrue(result.isEmpty());
        verify(businessUnitRepository, never()).findByParentIdOrderBySortOrder(any());
    }
    
    // ========== getParent Tests ==========
    
    @Test
    @DisplayName("getParent: Should return parent unit")
    void testGetParent_ReturnsParent() {
        when(businessUnitRepository.findById("bu-child-1")).thenReturn(Optional.of(childUnit1));
        when(businessUnitRepository.findById("bu-parent")).thenReturn(Optional.of(parentUnit));
        
        BusinessUnit result = businessUnitHelper.getParent("bu-child-1");
        
        assertNotNull(result);
        assertEquals(parentUnit, result);
        
        verify(businessUnitRepository, times(1)).findById("bu-child-1");
        verify(businessUnitRepository, times(1)).findById("bu-parent");
    }
    
    @Test
    @DisplayName("getParent: Should return null for root unit (no parent)")
    void testGetParent_RootUnit() {
        when(businessUnitRepository.findById("bu-parent")).thenReturn(Optional.of(parentUnit));
        
        BusinessUnit result = businessUnitHelper.getParent("bu-parent");
        
        assertNull(result);
        verify(businessUnitRepository, times(1)).findById("bu-parent");
        verify(businessUnitRepository, never()).findById(null);
    }
    
    @Test
    @DisplayName("getParent: Should return null when child unit not found")
    void testGetParent_ChildNotFound() {
        when(businessUnitRepository.findById("bu-nonexistent")).thenReturn(Optional.empty());
        
        BusinessUnit result = businessUnitHelper.getParent("bu-nonexistent");
        
        assertNull(result);
        verify(businessUnitRepository, times(1)).findById("bu-nonexistent");
    }
    
    @Test
    @DisplayName("getParent: Should return null when parent unit not found")
    void testGetParent_ParentNotFound() {
        when(businessUnitRepository.findById("bu-child-1")).thenReturn(Optional.of(childUnit1));
        when(businessUnitRepository.findById("bu-parent")).thenReturn(Optional.empty());
        
        BusinessUnit result = businessUnitHelper.getParent("bu-child-1");
        
        assertNull(result);
        verify(businessUnitRepository, times(1)).findById("bu-child-1");
        verify(businessUnitRepository, times(1)).findById("bu-parent");
    }
    
    @Test
    @DisplayName("getParent: Should return null for null businessUnitId")
    void testGetParent_NullId() {
        BusinessUnit result = businessUnitHelper.getParent(null);
        
        assertNull(result);
        verify(businessUnitRepository, never()).findById(any());
    }
    
    @Test
    @DisplayName("getParent: Should return null when parentId is empty string")
    void testGetParent_EmptyParentId() {
        BusinessUnit unitWithEmptyParentId = BusinessUnit.builder()
                .id("bu-5")
                .code("BU005")
                .name("Unit with Empty Parent ID")
                .status("ACTIVE")
                .parentId("")
                .build();
        
        when(businessUnitRepository.findById("bu-5")).thenReturn(Optional.of(unitWithEmptyParentId));
        
        BusinessUnit result = businessUnitHelper.getParent("bu-5");
        
        assertNull(result);
        verify(businessUnitRepository, times(1)).findById("bu-5");
        verify(businessUnitRepository, never()).findById("");
    }
}
