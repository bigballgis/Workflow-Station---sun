package com.admin.properties;

import com.admin.component.UserManagerComponent;
import com.admin.dto.request.UserCreateRequest;
import com.admin.dto.request.UserUpdateRequest;
import com.admin.entity.User;
import com.admin.entity.UserRole;
import com.admin.enums.UserStatus;
import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.PasswordHistoryRepository;
import com.admin.repository.UserBusinessUnitRepository;
import com.admin.repository.UserBusinessUnitRoleRepository;
import com.admin.repository.UserRepository;
import com.admin.service.AuditService;
import com.admin.service.UserPermissionService;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property tests for User Audit Trail
 * 
 * Properties tested:
 * - Property 15: Audit Trail Completeness
 * 
 * **Validates: Requirements 4.4, 5.5, 8.4**
 */
public class UserAuditProperties {
    
    private UserRepository userRepository;
    private BusinessUnitRepository businessUnitRepository;
    private PasswordHistoryRepository passwordHistoryRepository;
    private UserBusinessUnitRepository userBusinessUnitRepository;
    private PasswordEncoder passwordEncoder;
    private AuditService auditService;
    private UserManagerComponent userManagerComponent;
    
    @BeforeTry
    void setUp() {
        userRepository = mock(UserRepository.class);
        businessUnitRepository = mock(BusinessUnitRepository.class);
        passwordHistoryRepository = mock(PasswordHistoryRepository.class);
        userBusinessUnitRepository = mock(UserBusinessUnitRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        auditService = mock(AuditService.class);
        UserPermissionService userPermissionService = mock(UserPermissionService.class);
        UserBusinessUnitRoleRepository userBusinessUnitRoleRepository = mock(UserBusinessUnitRoleRepository.class);
        
        userManagerComponent = new UserManagerComponent(
                userRepository,
                businessUnitRepository,
                passwordHistoryRepository,
                passwordEncoder,
                auditService,
                userBusinessUnitRepository,
                userPermissionService,
                userBusinessUnitRoleRepository);
        
        // Default mock behaviors
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "encoded_" + inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }
    
    // ==================== Property 15: Audit Trail Completeness ====================
    
    /**
     * Feature: user-management, Property 15: Audit Trail Completeness
     * User creation should always be audited
     * **Validates: Requirements 4.4**
     */
    @Property(tries = 100)
    @Label("Feature: user-management, Property 15: User creation is audited")
    void userCreationIsAudited(
            @ForAll("validUsernames") String username,
            @ForAll("validEmails") String email) {
        
        // Given: Username and email don't exist
        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(userRepository.existsByEmail(email)).thenReturn(false);
        
        // When: Create user
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setInitialPassword("Password123!");
        
        userManagerComponent.createUser(request);
        
        // Then: Audit should be recorded
        verify(auditService).recordUserCreation(any(User.class));
    }
    
    /**
     * Feature: user-management, Property 15: Audit Trail Completeness
     * User update should always be audited
     * **Validates: Requirements 4.4**
     */
    @Property(tries = 100)
    @Label("Feature: user-management, Property 15: User update is audited")
    void userUpdateIsAudited(
            @ForAll("validUserIds") String userId,
            @ForAll("validFullNames") String newFullName) {
        
        // Given: Existing user
        User existingUser = createUser(userId, "testuser", "test@example.com", "Old Name");
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        
        // When: Update user
        UserUpdateRequest request = new UserUpdateRequest();
        request.setFullName(newFullName);
        
        userManagerComponent.updateUser(userId, request);
        
        // Then: Audit should be recorded with old and new values
        verify(auditService).recordUserUpdate(any(User.class), any(User.class));
    }
    
    /**
     * Feature: user-management, Property 15: Audit Trail Completeness
     * Status change should always be audited with reason
     * **Validates: Requirements 4.4**
     */
    @Property(tries = 100)
    @Label("Feature: user-management, Property 15: Status change is audited with reason")
    void statusChangeIsAuditedWithReason(
            @ForAll("validUserIds") String userId,
            @ForAll("statusChangeReasons") String reason) {
        
        // Given: Existing active user
        User existingUser = createUser(userId, "testuser", "test@example.com", "Test User");
        existingUser.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        
        // When: Change status to DISABLED
        userManagerComponent.updateUserStatus(userId, UserStatus.DISABLED, reason);
        
        // Then: Audit should be recorded with old status, new status, and reason
        verify(auditService).recordStatusChange(
                any(User.class), 
                eq(UserStatus.ACTIVE), 
                eq(UserStatus.DISABLED), 
                eq(reason));
    }
    
    /**
     * Feature: user-management, Property 15: Audit Trail Completeness
     * User deletion should always be audited
     * **Validates: Requirements 5.5**
     */
    @Property(tries = 100)
    @Label("Feature: user-management, Property 15: User deletion is audited")
    void userDeletionIsAudited(
            @ForAll("validUserIds") String userId) {
        
        // Given: Existing non-admin user
        User existingUser = createUser(userId, "testuser", "test@example.com", "Test User");
        existingUser.setUserRoles(new HashSet<>()); // No admin role
        when(userRepository.findByIdWithRoles(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.countActiveAdmins()).thenReturn(2L); // More than 1 admin
        
        // When: Delete user
        userManagerComponent.deleteUser(userId);
        
        // Then: Audit should be recorded
        verify(auditService).recordUserDeletion(any(User.class));
    }
    
    /**
     * Feature: user-management, Property 15: Audit Trail Completeness
     * Password reset should always be audited
     * **Validates: Requirements 8.4**
     */
    @Property(tries = 100)
    @Label("Feature: user-management, Property 15: Password reset is audited")
    void passwordResetIsAudited(
            @ForAll("validUserIds") String userId) {
        
        // Given: Existing user
        User existingUser = createUser(userId, "testuser", "test@example.com", "Test User");
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        
        // When: Reset password
        userManagerComponent.resetPassword(userId);
        
        // Then: Audit should be recorded
        verify(auditService).recordPasswordReset(any(User.class));
    }
    
    /**
     * Feature: user-management, Property 15: Audit Trail Completeness
     * All audit operations should include the affected user
     * **Validates: Requirements 4.4, 5.5, 8.4**
     */
    @Property(tries = 100)
    @Label("Feature: user-management, Property 15: Audit includes affected user")
    void auditIncludesAffectedUser(
            @ForAll("validUsernames") String username,
            @ForAll("validEmails") String email) {
        
        // Given: Username and email don't exist
        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(userRepository.existsByEmail(email)).thenReturn(false);
        
        // Capture the user passed to audit
        User[] auditedUser = new User[1];
        doAnswer(inv -> {
            auditedUser[0] = inv.getArgument(0);
            return null;
        }).when(auditService).recordUserCreation(any(User.class));
        
        // When: Create user
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setInitialPassword("Password123!");
        
        userManagerComponent.createUser(request);
        
        // Then: Audited user should have correct username
        assertThat(auditedUser[0]).isNotNull();
        assertThat(auditedUser[0].getUsername()).isEqualTo(username);
    }
    
    // ==================== Helper Methods ====================
    
    private User createUser(String id, String username, String email, String fullName) {
        return User.builder()
                .id(id)
                .username(username)
                .email(email)
                .fullName(fullName)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .userRoles(new HashSet<>())
                .build();
    }
    
    // ==================== Data Generators ====================
    
    @Provide
    Arbitrary<String> validUserIds() {
        return Arbitraries.create(UUID::randomUUID).map(UUID::toString);
    }
    
    @Provide
    Arbitrary<String> validUsernames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(20)
                .map(String::toLowerCase);
    }
    
    @Provide
    Arbitrary<String> validEmails() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(10)
                .map(s -> s.toLowerCase() + "@example.com");
    }
    
    @Provide
    Arbitrary<String> validFullNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(2)
                .ofMaxLength(30)
                .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase());
    }
    
    @Provide
    Arbitrary<String> statusChangeReasons() {
        return Arbitraries.oneOf(
                Arbitraries.just("User requested account suspension"),
                Arbitraries.just("Security policy violation"),
                Arbitraries.just("Extended leave of absence"),
                Arbitraries.just("Account reactivation approved"),
                Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(100)
        );
    }
}
