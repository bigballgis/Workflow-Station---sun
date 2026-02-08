package com.admin.helper;

import com.admin.enums.VirtualGroupType;
import com.admin.repository.UserRepository;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.platform.security.entity.User;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.entity.VirtualGroupMember;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for VirtualGroupHelper service.
 * Tests virtual group operations with mocked repository dependencies.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VirtualGroupHelper Tests")
class VirtualGroupHelperTest {
    
    @Mock
    private VirtualGroupRepository virtualGroupRepository;
    
    @Mock
    private VirtualGroupMemberRepository memberRepository;
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private VirtualGroupHelper virtualGroupHelper;
    
    private VirtualGroup activeSystemGroup;
    private VirtualGroup activeCustomGroup;
    private VirtualGroup inactiveGroup;
    private VirtualGroup deletedGroup;
    
    private User user1;
    private User user2;
    private User user3;
    
    private VirtualGroupMember member1;
    private VirtualGroupMember member2;
    private VirtualGroupMember member3;
    
    @BeforeEach
    void setUp() {
        // Create test virtual groups
        activeSystemGroup = VirtualGroup.builder()
                .id("vg-1")
                .code("SYSTEM_GROUP")
                .name("System Group")
                .type("SYSTEM")
                .status("ACTIVE")
                .build();
        
        activeCustomGroup = VirtualGroup.builder()
                .id("vg-2")
                .code("CUSTOM_GROUP")
                .name("Custom Group")
                .type("CUSTOM")
                .status("ACTIVE")
                .build();
        
        inactiveGroup = VirtualGroup.builder()
                .id("vg-3")
                .code("INACTIVE_GROUP")
                .name("Inactive Group")
                .type("CUSTOM")
                .status("DISABLED")
                .build();
        
        deletedGroup = VirtualGroup.builder()
                .id("vg-4")
                .code("DELETED_GROUP")
                .name("Deleted Group")
                .type("CUSTOM")
                .status("DELETED")
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
        
        // Create test members
        member1 = VirtualGroupMember.builder()
                .id("member-1")
                .groupId("vg-1")
                .userId("user-1")
                .joinedAt(LocalDateTime.now())
                .build();
        
        member2 = VirtualGroupMember.builder()
                .id("member-2")
                .groupId("vg-1")
                .userId("user-2")
                .joinedAt(LocalDateTime.now())
                .build();
        
        member3 = VirtualGroupMember.builder()
                .id("member-3")
                .groupId("vg-1")
                .userId("user-3")
                .joinedAt(LocalDateTime.now())
                .build();
    }
    
    // ========== isValid Tests ==========
    
    @Test
    @DisplayName("isValid: Should return true for active group")
    void testIsValid_ActiveGroup() {
        assertTrue(virtualGroupHelper.isValid(activeSystemGroup));
    }
    
    @Test
    @DisplayName("isValid: Should return false for inactive group")
    void testIsValid_InactiveGroup() {
        assertFalse(virtualGroupHelper.isValid(inactiveGroup));
    }
    
    @Test
    @DisplayName("isValid: Should return false for deleted group")
    void testIsValid_DeletedGroup() {
        assertFalse(virtualGroupHelper.isValid(deletedGroup));
    }
    
    @Test
    @DisplayName("isValid: Should return false for null group")
    void testIsValid_Null() {
        assertFalse(virtualGroupHelper.isValid(null));
    }
    
    // ========== isActive Tests ==========
    
    @Test
    @DisplayName("isActive: Should return true for ACTIVE status")
    void testIsActive_ActiveStatus() {
        assertTrue(virtualGroupHelper.isActive(activeSystemGroup));
    }
    
    @Test
    @DisplayName("isActive: Should return false for DISABLED status")
    void testIsActive_DisabledStatus() {
        assertFalse(virtualGroupHelper.isActive(inactiveGroup));
    }
    
    @Test
    @DisplayName("isActive: Should return false for DELETED status")
    void testIsActive_DeletedStatus() {
        assertFalse(virtualGroupHelper.isActive(deletedGroup));
    }
    
    @Test
    @DisplayName("isActive: Should return false for null group")
    void testIsActive_Null() {
        assertFalse(virtualGroupHelper.isActive(null));
    }
    
    @Test
    @DisplayName("isActive: Should return false for null status")
    void testIsActive_NullStatus() {
        VirtualGroup groupWithNullStatus = VirtualGroup.builder()
                .id("vg-5")
                .code("NULL_STATUS")
                .name("Null Status Group")
                .type("CUSTOM")
                .status(null)
                .build();
        
        assertFalse(virtualGroupHelper.isActive(groupWithNullStatus));
    }
    
    // ========== getMemberCount Tests ==========
    
    @Test
    @DisplayName("getMemberCount: Should return correct count")
    void testGetMemberCount_ReturnsCorrectCount() {
        when(memberRepository.countByGroupId("vg-1")).thenReturn(3L);
        
        long count = virtualGroupHelper.getMemberCount("vg-1");
        
        assertEquals(3L, count);
        verify(memberRepository, times(1)).countByGroupId("vg-1");
    }
    
    @Test
    @DisplayName("getMemberCount: Should return 0 for group with no members")
    void testGetMemberCount_NoMembers() {
        when(memberRepository.countByGroupId("vg-2")).thenReturn(0L);
        
        long count = virtualGroupHelper.getMemberCount("vg-2");
        
        assertEquals(0L, count);
        verify(memberRepository, times(1)).countByGroupId("vg-2");
    }
    
    @Test
    @DisplayName("getMemberCount: Should return 0 for null virtualGroupId")
    void testGetMemberCount_NullId() {
        long count = virtualGroupHelper.getMemberCount(null);
        
        assertEquals(0L, count);
        verify(memberRepository, never()).countByGroupId(any());
    }
    
    // ========== getMembers Tests ==========
    
    @Test
    @DisplayName("getMembers: Should return all members")
    void testGetMembers_ReturnsAllMembers() {
        List<VirtualGroupMember> members = Arrays.asList(member1, member2, member3);
        List<User> users = Arrays.asList(user1, user2, user3);
        List<String> userIds = Arrays.asList("user-1", "user-2", "user-3");
        
        when(memberRepository.findByGroupId("vg-1")).thenReturn(members);
        when(userRepository.findAllById(userIds)).thenReturn(users);
        
        List<User> result = virtualGroupHelper.getMembers("vg-1");
        
        assertEquals(3, result.size());
        assertTrue(result.contains(user1));
        assertTrue(result.contains(user2));
        assertTrue(result.contains(user3));
        
        verify(memberRepository, times(1)).findByGroupId("vg-1");
        verify(userRepository, times(1)).findAllById(userIds);
    }
    
    @Test
    @DisplayName("getMembers: Should return empty list when no members")
    void testGetMembers_NoMembers() {
        when(memberRepository.findByGroupId("vg-2")).thenReturn(List.of());
        
        List<User> result = virtualGroupHelper.getMembers("vg-2");
        
        assertTrue(result.isEmpty());
        verify(memberRepository, times(1)).findByGroupId("vg-2");
        verify(userRepository, never()).findAllById(any());
    }
    
    @Test
    @DisplayName("getMembers: Should return empty list for null virtualGroupId")
    void testGetMembers_NullId() {
        List<User> result = virtualGroupHelper.getMembers(null);
        
        assertTrue(result.isEmpty());
        verify(memberRepository, never()).findByGroupId(any());
        verify(userRepository, never()).findAllById(any());
    }
    
    @Test
    @DisplayName("getMembers: Should handle duplicate user IDs")
    void testGetMembers_DuplicateUserIds() {
        VirtualGroupMember duplicateMember = VirtualGroupMember.builder()
                .id("member-4")
                .groupId("vg-1")
                .userId("user-1") // Duplicate user ID
                .joinedAt(LocalDateTime.now())
                .build();
        
        List<VirtualGroupMember> members = Arrays.asList(member1, duplicateMember);
        List<String> expectedUserIds = List.of("user-1"); // Should be deduplicated
        
        when(memberRepository.findByGroupId("vg-1")).thenReturn(members);
        when(userRepository.findAllById(expectedUserIds)).thenReturn(List.of(user1));
        
        List<User> result = virtualGroupHelper.getMembers("vg-1");
        
        assertEquals(1, result.size());
        assertEquals(user1, result.get(0));
        
        verify(memberRepository, times(1)).findByGroupId("vg-1");
        verify(userRepository, times(1)).findAllById(expectedUserIds);
    }
    
    // ========== getGroupType Tests ==========
    
    @Test
    @DisplayName("getGroupType: Should return SYSTEM for system group")
    void testGetGroupType_System() {
        VirtualGroupType result = virtualGroupHelper.getGroupType(activeSystemGroup);
        assertEquals(VirtualGroupType.SYSTEM, result);
    }
    
    @Test
    @DisplayName("getGroupType: Should return CUSTOM for custom group")
    void testGetGroupType_Custom() {
        VirtualGroupType result = virtualGroupHelper.getGroupType(activeCustomGroup);
        assertEquals(VirtualGroupType.CUSTOM, result);
    }
    
    @Test
    @DisplayName("getGroupType: Should return null for null group")
    void testGetGroupType_Null() {
        VirtualGroupType result = virtualGroupHelper.getGroupType(null);
        assertNull(result);
    }
    
    @Test
    @DisplayName("getGroupType: Should throw IllegalArgumentException for invalid type")
    void testGetGroupType_InvalidType() {
        VirtualGroup invalidGroup = VirtualGroup.builder()
                .id("vg-6")
                .code("INVALID")
                .name("Invalid Group")
                .type("INVALID_TYPE")
                .status("ACTIVE")
                .build();
        
        assertThrows(IllegalArgumentException.class, () -> virtualGroupHelper.getGroupType(invalidGroup));
    }
    
    // ========== isBusinessGroup Tests ==========
    
    @Test
    @DisplayName("isBusinessGroup: Should return true for CUSTOM group")
    void testIsBusinessGroup_Custom() {
        assertTrue(virtualGroupHelper.isBusinessGroup(activeCustomGroup));
    }
    
    @Test
    @DisplayName("isBusinessGroup: Should return false for SYSTEM group")
    void testIsBusinessGroup_System() {
        assertFalse(virtualGroupHelper.isBusinessGroup(activeSystemGroup));
    }
    
    @Test
    @DisplayName("isBusinessGroup: Should return false for null group")
    void testIsBusinessGroup_Null() {
        assertFalse(virtualGroupHelper.isBusinessGroup(null));
    }
    
    @Test
    @DisplayName("isBusinessGroup: Should return false for invalid type")
    void testIsBusinessGroup_InvalidType() {
        VirtualGroup invalidGroup = VirtualGroup.builder()
                .id("vg-7")
                .code("INVALID")
                .name("Invalid Group")
                .type("INVALID_TYPE")
                .status("ACTIVE")
                .build();
        
        assertFalse(virtualGroupHelper.isBusinessGroup(invalidGroup));
    }
}
