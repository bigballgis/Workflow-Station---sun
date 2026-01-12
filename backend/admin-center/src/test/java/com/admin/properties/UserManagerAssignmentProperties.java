package com.admin.properties;

import com.admin.component.UserManagerComponent;
import com.admin.dto.request.UserUpdateRequest;
import com.admin.entity.User;
import com.admin.enums.UserStatus;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.DepartmentRepository;
import com.admin.repository.PasswordHistoryRepository;
import com.admin.repository.UserRepository;
import com.admin.service.AuditService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Feature: manager-assignment, Property 1: User Manager Fields Persistence
 * For any User entity with valid entityManagerId and functionManagerId values,
 * saving and then retrieving the user SHALL return the same manager IDs.
 * 
 * Feature: manager-assignment, Property 2: User Manager Reference Validation
 * For any User update request with entityManagerId or functionManagerId set to
 * a non-existent user ID, the Admin_Center SHALL reject the request.
 * 
 * Validates: Requirements 1.1, 1.2, 1.3, 1.4
 */
public class UserManagerAssignmentProperties {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private DepartmentRepository departmentRepository;
    
    @Mock
    private PasswordHistoryRepository passwordHistoryRepository;
    
    @Mock
    private AuditService auditService;
    
    private PasswordEncoder passwordEncoder;
    private UserManagerComponent userManager;
    
    @BeforeProperty
    void setUp() {
        MockitoAnnotations.openMocks(this);
        passwordEncoder = new BCryptPasswordEncoder();
        userManager = new UserManagerComponent(
                userRepository,
                departmentRepository,
                passwordHistoryRepository, 
                passwordEncoder, 
                auditService);
    }
    
    /**
     * Feature: manager-assignment, Property 1: User Manager Fields Persistence
     * For any valid manager IDs, updating user should persist them correctly
     */
    @Property(tries = 100)
    @Label("Feature: manager-assignment, Property 1: Valid manager IDs should be persisted")
    void validManagerIdsShouldBePersisted(
            @ForAll("userIds") String userId,
            @ForAll("userIds") String entityManagerId,
            @ForAll("userIds") String functionManagerId) {
        
        // Given: User exists and managers exist
        User existingUser = createTestUser(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsById(entityManagerId)).thenReturn(true);
        when(userRepository.existsById(functionManagerId)).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        
        UserUpdateRequest request = UserUpdateRequest.builder()
                .entityManagerId(entityManagerId)
                .functionManagerId(functionManagerId)
                .build();
        
        // When: Update user
        userManager.updateUser(userId, request);
        
        // Then: Manager IDs should be set
        assertThat(existingUser.getEntityManagerId()).isEqualTo(entityManagerId);
        assertThat(existingUser.getFunctionManagerId()).isEqualTo(functionManagerId);
    }
    
    /**
     * Feature: manager-assignment, Property 2: User Manager Reference Validation
     * Non-existent entity manager ID should be rejected
     */
    @Property(tries = 100)
    @Label("Feature: manager-assignment, Property 2: Non-existent entity manager should be rejected")
    void nonExistentEntityManagerShouldBeRejected(
            @ForAll("userIds") String userId,
            @ForAll("userIds") String nonExistentManagerId) {
        
        // Given: User exists but entity manager does not exist
        User existingUser = createTestUser(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsById(nonExistentManagerId)).thenReturn(false);
        
        UserUpdateRequest request = UserUpdateRequest.builder()
                .entityManagerId(nonExistentManagerId)
                .build();
        
        // When & Then: Should throw exception
        assertThatThrownBy(() -> userManager.updateUser(userId, request))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("实体管理者不存在");
    }
    
    /**
     * Feature: manager-assignment, Property 2: User Manager Reference Validation
     * Non-existent function manager ID should be rejected
     */
    @Property(tries = 100)
    @Label("Feature: manager-assignment, Property 2: Non-existent function manager should be rejected")
    void nonExistentFunctionManagerShouldBeRejected(
            @ForAll("userIds") String userId,
            @ForAll("userIds") String nonExistentManagerId) {
        
        // Given: User exists but function manager does not exist
        User existingUser = createTestUser(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsById(nonExistentManagerId)).thenReturn(false);
        
        UserUpdateRequest request = UserUpdateRequest.builder()
                .functionManagerId(nonExistentManagerId)
                .build();
        
        // When & Then: Should throw exception
        assertThatThrownBy(() -> userManager.updateUser(userId, request))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("职能管理者不存在");
    }
    
    /**
     * Feature: manager-assignment, Property 1: Null manager IDs should be allowed
     * Empty string should clear the manager ID
     */
    @Property(tries = 100)
    @Label("Feature: manager-assignment, Property 1: Empty manager ID should clear the field")
    void emptyManagerIdShouldClearField(
            @ForAll("userIds") String userId) {
        
        // Given: User exists with existing manager IDs
        User existingUser = createTestUser(userId);
        existingUser.setEntityManagerId(UUID.randomUUID().toString());
        existingUser.setFunctionManagerId(UUID.randomUUID().toString());
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        
        UserUpdateRequest request = UserUpdateRequest.builder()
                .entityManagerId("")
                .functionManagerId("")
                .build();
        
        // When: Update user with empty manager IDs
        userManager.updateUser(userId, request);
        
        // Then: Manager IDs should be null
        assertThat(existingUser.getEntityManagerId()).isNull();
        assertThat(existingUser.getFunctionManagerId()).isNull();
    }
    
    @Provide
    Arbitrary<String> userIds() {
        return Arbitraries.create(UUID::randomUUID).map(UUID::toString);
    }
    
    private User createTestUser(String userId) {
        return User.builder()
                .id(userId)
                .username("testuser-" + userId.substring(0, 8))
                .email("test@example.com")
                .fullName("Test User")
                .passwordHash("$2a$10$test")
                .status(UserStatus.ACTIVE)
                .userRoles(new HashSet<>())
                .build();
    }
}
