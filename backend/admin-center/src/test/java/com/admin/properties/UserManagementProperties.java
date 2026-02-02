package com.admin.properties;

import com.admin.component.UserManagerComponent;
import com.admin.dto.request.UserCreateRequest;
import com.admin.dto.request.UserQueryRequest;
import com.admin.dto.request.UserUpdateRequest;
import com.admin.dto.response.UserInfo;
import com.platform.security.entity.User;
import com.platform.security.model.UserStatus;
import com.admin.exception.UsernameAlreadyExistsException;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.PasswordHistoryRepository;
import com.admin.repository.UserBusinessUnitRepository;
import com.admin.repository.UserRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property tests for UserManagerComponent
 * 
 * Properties tested:
 * - Property 1: Pagination Consistency
 * - Property 2: Filter Correctness
 * - Property 3: Sort Order Correctness
 * - Property 4: User Creation Round-Trip
 * - Property 5: Username Uniqueness
 * - Property 6: Email Uniqueness
 * - Property 8: Update Persistence
 * 
 * **Validates: Requirements 1.1, 1.2, 1.3, 1.5, 2.1, 2.2, 2.3, 2.5, 3.1, 3.4**
 */
public class UserManagementProperties {
    
    private UserRepository userRepository;
    private BusinessUnitRepository businessUnitRepository;
    private PasswordHistoryRepository passwordHistoryRepository;
    private UserBusinessUnitRepository userBusinessUnitRepository;
    private PasswordEncoder passwordEncoder;
    private UserManagerComponent userManagerComponent;
    
    @BeforeTry
    void setUp() {
        userRepository = mock(UserRepository.class);
        businessUnitRepository = mock(BusinessUnitRepository.class);
        passwordHistoryRepository = mock(PasswordHistoryRepository.class);
        userBusinessUnitRepository = mock(UserBusinessUnitRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        
        userManagerComponent = new UserManagerComponent(
                userRepository,
                businessUnitRepository,
                passwordHistoryRepository,
                passwordEncoder,
                userBusinessUnitRepository);
        
        // Default mock behaviors
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "encoded_" + inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    
    // ==================== Property 1: Pagination Consistency ====================
    
    /**
     * Feature: user-management, Property 1: Pagination Consistency
     * For any valid page request, the returned page should have correct metadata
     * **Validates: Requirements 1.1, 1.5**
     */
    @Property(tries = 100)
    @Label("Feature: user-management, Property 1: Pagination returns correct metadata")
    void paginationReturnsCorrectMetadata(
            @ForAll("validPageNumbers") int page,
            @ForAll("validPageSizes") int size,
            @ForAll("userLists") List<User> allUsers) {
        
        // Given: A list of users in the database
        int totalElements = allUsers.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int start = page * size;
        int end = Math.min(start + size, totalElements);
        List<User> pageContent = start < totalElements ? allUsers.subList(start, end) : List.of();
        
        Page<User> mockPage = new PageImpl<>(pageContent, 
                org.springframework.data.domain.PageRequest.of(page, size), 
                totalElements);
        
        when(userRepository.findByConditions(any(), any(), any(), any(Pageable.class)))
                .thenReturn(mockPage);
        when(userBusinessUnitRepository.findByUserId(anyString())).thenReturn(List.of());
        
        // When: Query users with pagination
        UserQueryRequest request = new UserQueryRequest();
        request.setPage(page);
        request.setSize(size);
        
        Page<UserInfo> result = userManagerComponent.listUsers(request);
        
        // Then: Page metadata should be correct
        assertThat(result.getNumber()).isEqualTo(page);
        assertThat(result.getSize()).isEqualTo(size);
        assertThat(result.getTotalElements()).isEqualTo(totalElements);
        assertThat(result.getTotalPages()).isEqualTo(totalPages);
        assertThat(result.getContent().size()).isEqualTo(pageContent.size());
    }
    
    // ==================== Property 2: Filter Correctness ====================
    
    /**
     * Feature: user-management, Property 2: Filter Correctness
     * For any filter criteria, all returned users should match the filter
     * **Validates: Requirements 1.2**
     */
    @Property(tries = 100)
    @Label("Feature: user-management, Property 2: Filter returns only matching users")
    void filterReturnsOnlyMatchingUsers(
            @ForAll("userStatuses") UserStatus filterStatus,
            @ForAll("userLists") List<User> allUsers) {
        
        // Given: Filter users by status
        List<User> matchingUsers = allUsers.stream()
                .filter(u -> u.getStatus() == filterStatus)
                .collect(Collectors.toList());
        
        Page<User> mockPage = new PageImpl<>(matchingUsers);
        when(userRepository.findByConditions(any(), eq(filterStatus), any(), any(Pageable.class)))
                .thenReturn(mockPage);
        when(userBusinessUnitRepository.findByUserId(anyString())).thenReturn(List.of());
        
        // When: Query with status filter
        UserQueryRequest request = new UserQueryRequest();
        request.setStatus(filterStatus);
        request.setPage(0);
        request.setSize(100);
        
        Page<UserInfo> result = userManagerComponent.listUsers(request);
        
        // Then: All returned users should have the filtered status
        assertThat(result.getContent()).allMatch(u -> u.getStatus() == filterStatus);
    }
    
    // ==================== Property 3: Sort Order Correctness ====================
    
    /**
     * Feature: user-management, Property 3: Sort Order Correctness
     * Results should be sorted by createdAt descending by default
     * **Validates: Requirements 1.3**
     */
    @Property(tries = 100)
    @Label("Feature: user-management, Property 3: Results sorted by createdAt descending")
    void resultsSortedByCreatedAtDescending(
            @ForAll("userLists") List<User> allUsers) {
        
        Assume.that(allUsers.size() >= 2);
        
        // Given: Users sorted by createdAt descending
        List<User> sortedUsers = allUsers.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
        
        Page<User> mockPage = new PageImpl<>(sortedUsers);
        when(userRepository.findByConditions(any(), any(), any(), any(Pageable.class)))
                .thenReturn(mockPage);
        when(userBusinessUnitRepository.findByUserId(anyString())).thenReturn(List.of());
        
        // When: Query users
        UserQueryRequest request = new UserQueryRequest();
        request.setPage(0);
        request.setSize(100);
        
        Page<UserInfo> result = userManagerComponent.listUsers(request);
        
        // Then: Results should be in descending order by createdAt
        List<UserInfo> content = result.getContent();
        for (int i = 0; i < content.size() - 1; i++) {
            assertThat(content.get(i).getCreatedAt())
                    .isAfterOrEqualTo(content.get(i + 1).getCreatedAt());
        }
    }

    
    // ==================== Property 4: User Creation Round-Trip ====================
    
    /**
     * Feature: user-management, Property 4: User Creation Round-Trip
     * For any valid user creation request, the created user should have all the provided data
     * **Validates: Requirements 2.1, 2.5**
     */
    @Property(tries = 100)
    @Label("Feature: user-management, Property 4: Created user has all provided data")
    void createdUserHasAllProvidedData(
            @ForAll("validUsernames") String username,
            @ForAll("validEmails") String email,
            @ForAll("validFullNames") String fullName,
            @ForAll("validPasswords") String password) {
        
        // Given: Username and email don't exist
        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(userRepository.existsByEmail(email)).thenReturn(false);
        
        // Capture the saved user
        User[] savedUser = new User[1];
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            savedUser[0] = inv.getArgument(0);
            return savedUser[0];
        });
        
        // When: Create user
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setFullName(fullName);
        request.setInitialPassword(password);
        
        userManagerComponent.createUser(request);
        
        // Then: Saved user should have all provided data
        assertThat(savedUser[0]).isNotNull();
        assertThat(savedUser[0].getUsername()).isEqualTo(username);
        assertThat(savedUser[0].getEmail()).isEqualTo(email);
        assertThat(savedUser[0].getFullName()).isEqualTo(fullName);
        assertThat(savedUser[0].getPasswordHash()).isEqualTo("encoded_" + password);
        assertThat(savedUser[0].getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(savedUser[0].getMustChangePassword()).isTrue();
    }
    
    // ==================== Property 5: Username Uniqueness ====================
    
    /**
     * Feature: user-management, Property 5: Username Uniqueness
     * Creating a user with an existing username should fail
     * **Validates: Requirements 2.2**
     */
    @Property(tries = 100)
    @Label("Feature: user-management, Property 5: Duplicate username rejected")
    void duplicateUsernameRejected(
            @ForAll("validUsernames") String existingUsername,
            @ForAll("validEmails") String email,
            @ForAll("validPasswords") String password) {
        
        // Given: Username already exists
        when(userRepository.existsByUsername(existingUsername)).thenReturn(true);
        
        // When/Then: Creating user with existing username should throw
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername(existingUsername);
        request.setEmail(email);
        request.setInitialPassword(password);
        
        assertThatThrownBy(() -> userManagerComponent.createUser(request))
                .isInstanceOf(UsernameAlreadyExistsException.class);
        
        // And: No user should be saved
        verify(userRepository, never()).save(any(User.class));
    }
    
    // ==================== Property 6: Email Uniqueness ====================
    
    /**
     * Feature: user-management, Property 6: Email Uniqueness
     * Creating a user with an existing email should fail
     * **Validates: Requirements 2.3**
     */
    @Property(tries = 100)
    @Label("Feature: user-management, Property 6: Duplicate email rejected")
    void duplicateEmailRejected(
            @ForAll("validUsernames") String username,
            @ForAll("validEmails") String existingEmail,
            @ForAll("validPasswords") String password) {
        
        // Given: Username doesn't exist but email does
        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(userRepository.existsByEmail(existingEmail)).thenReturn(true);
        
        // When/Then: Creating user with existing email should throw
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername(username);
        request.setEmail(existingEmail);
        request.setInitialPassword(password);
        
        assertThatThrownBy(() -> userManagerComponent.createUser(request))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("邮箱已被使用");
        
        // And: No user should be saved
        verify(userRepository, never()).save(any(User.class));
    }

    
    // ==================== Property 8: Update Persistence ====================
    
    /**
     * Feature: user-management, Property 8: Update Persistence
     * For any valid update request, the updated fields should be persisted
     * **Validates: Requirements 3.1, 3.4**
     */
    @Property(tries = 100)
    @Label("Feature: user-management, Property 8: Updated fields are persisted")
    void updatedFieldsArePersisted(
            @ForAll("validUserIds") String userId,
            @ForAll("validEmails") String newEmail,
            @ForAll("validFullNames") String newFullName) {
        
        // Given: Existing user
        User existingUser = createUser(userId, "olduser", "old@example.com", "Old Name");
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByEmail(newEmail)).thenReturn(false);
        
        // Capture the saved user
        User[] savedUser = new User[1];
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            savedUser[0] = inv.getArgument(0);
            return savedUser[0];
        });
        
        // When: Update user
        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail(newEmail);
        request.setFullName(newFullName);
        
        userManagerComponent.updateUser(userId, request);
        
        // Then: Updated fields should be persisted
        assertThat(savedUser[0]).isNotNull();
        assertThat(savedUser[0].getEmail()).isEqualTo(newEmail);
        assertThat(savedUser[0].getFullName()).isEqualTo(newFullName);
    }
    
    /**
     * Feature: user-management, Property 8: Update Persistence
     * Updating email to an existing email should fail
     * **Validates: Requirements 3.1**
     */
    @Property(tries = 100)
    @Label("Feature: user-management, Property 8: Update to existing email rejected")
    void updateToExistingEmailRejected(
            @ForAll("validUserIds") String userId,
            @ForAll("validEmails") String existingEmail) {
        
        // Given: Existing user with different email
        User existingUser = createUser(userId, "testuser", "original@example.com", "Test User");
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByEmail(existingEmail)).thenReturn(true);
        
        // When/Then: Updating to existing email should throw
        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail(existingEmail);
        
        assertThatThrownBy(() -> userManagerComponent.updateUser(userId, request))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("邮箱已被使用");
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
    Arbitrary<String> validPasswords() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(8)
                .ofMaxLength(20);
    }
    
    @Provide
    Arbitrary<Integer> validPageNumbers() {
        return Arbitraries.integers().between(0, 10);
    }
    
    @Provide
    Arbitrary<Integer> validPageSizes() {
        return Arbitraries.integers().between(1, 50);
    }
    
    @Provide
    Arbitrary<com.platform.security.model.UserStatus> userStatuses() {
        return Arbitraries.of(com.platform.security.model.UserStatus.ACTIVE, 
                             com.platform.security.model.UserStatus.INACTIVE, 
                             com.platform.security.model.UserStatus.LOCKED);
    }
    
    @Provide
    Arbitrary<List<User>> userLists() {
        return Arbitraries.integers().between(0, 20)
                .flatMap(count -> {
                    List<User> users = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        users.add(createUser(
                                UUID.randomUUID().toString(),
                                "user" + i,
                                "user" + i + "@example.com",
                                "User " + i));
                        // Set different createdAt times
                        users.get(i).setCreatedAt(LocalDateTime.now().minusHours(count - i));
                        // Set random status
                        users.get(i).setStatus(i % 3 == 0 ? com.platform.security.model.UserStatus.INACTIVE : 
                                              i % 3 == 1 ? com.platform.security.model.UserStatus.LOCKED : 
                                              com.platform.security.model.UserStatus.ACTIVE);
                    }
                    return Arbitraries.just(users);
                });
    }
}
