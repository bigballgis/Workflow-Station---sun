package com.admin.component;

import com.admin.dto.request.UserCreateRequest;
import com.admin.dto.response.UserCreateResult;
import com.admin.entity.User;
import com.admin.enums.UserStatus;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.InvalidEmailException;
import com.admin.exception.UserNotFoundException;
import com.admin.exception.UsernameAlreadyExistsException;
import com.admin.repository.DepartmentRepository;
import com.admin.repository.PasswordHistoryRepository;
import com.admin.repository.UserRepository;
import com.admin.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 用户管理组件单元测试
 */
@ExtendWith(MockitoExtension.class)
class UserManagerComponentTest {
    
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
    
    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        userManager = new UserManagerComponent(
                userRepository,
                departmentRepository,
                passwordHistoryRepository,
                passwordEncoder,
                auditService);
    }
    
    @Nested
    @DisplayName("用户创建测试")
    class CreateUserTests {
        
        @Test
        @DisplayName("应该成功创建有效用户")
        void shouldCreateValidUser() {
            // Given
            UserCreateRequest request = UserCreateRequest.builder()
                    .username("testuser")
                    .email("test@example.com")
                    .fullName("Test User")
                    .initialPassword("Password123!")
                    .build();
            
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
            
            // When
            UserCreateResult result = userManager.createUser(request);
            
            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getUsername()).isEqualTo("testuser");
            verify(userRepository).save(any(User.class));
            verify(auditService).recordUserCreation(any(User.class));
        }
        
        @Test
        @DisplayName("应该拒绝重复的用户名")
        void shouldRejectDuplicateUsername() {
            // Given
            when(userRepository.existsByUsername("existinguser")).thenReturn(true);
            
            // When & Then
            assertThatThrownBy(() -> userManager.validateUsernameUnique("existinguser"))
                    .isInstanceOf(UsernameAlreadyExistsException.class);
        }
        
        @Test
        @DisplayName("应该拒绝无效的邮箱格式")
        void shouldRejectInvalidEmail() {
            // When & Then
            assertThatThrownBy(() -> userManager.validateEmailFormat("invalid-email"))
                    .isInstanceOf(InvalidEmailException.class);
        }
        
        @Test
        @DisplayName("应该接受有效的邮箱格式")
        void shouldAcceptValidEmail() {
            // When & Then - 不应该抛出异常
            userManager.validateEmailFormat("valid@example.com");
        }
    }
    
    @Nested
    @DisplayName("用户状态管理测试")
    class StatusManagementTests {
        
        @Test
        @DisplayName("应该成功禁用活跃用户")
        void shouldDisableActiveUser() {
            // Given
            String userId = UUID.randomUUID().toString();
            User user = User.builder()
                    .id(userId)
                    .username("testuser")
                    .status(UserStatus.ACTIVE)
                    .build();
            
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
            
            // When
            userManager.updateUserStatus(userId, UserStatus.DISABLED, "测试禁用");
            
            // Then
            assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
            verify(auditService).recordStatusChange(
                    eq(user), eq(UserStatus.ACTIVE), eq(UserStatus.DISABLED), anyString());
        }
        
        @Test
        @DisplayName("应该成功解锁锁定用户并重置失败次数")
        void shouldUnlockLockedUserAndResetFailedCount() {
            // Given
            String userId = UUID.randomUUID().toString();
            User user = User.builder()
                    .id(userId)
                    .username("testuser")
                    .status(UserStatus.LOCKED)
                    .failedLoginCount(5)
                    .build();
            
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
            
            // When
            userManager.updateUserStatus(userId, UserStatus.ACTIVE, "管理员解锁");
            
            // Then
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(user.getFailedLoginCount()).isEqualTo(0);
            assertThat(user.getLockedUntil()).isNull();
        }
        
        @Test
        @DisplayName("应该拒绝无效的状态转换")
        void shouldRejectInvalidStatusTransition() {
            // Given
            String userId = UUID.randomUUID().toString();
            User user = User.builder()
                    .id(userId)
                    .username("testuser")
                    .status(UserStatus.DISABLED)
                    .build();
            
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            
            // When & Then
            assertThatThrownBy(() -> 
                    userManager.updateUserStatus(userId, UserStatus.LOCKED, "测试"))
                    .isInstanceOf(AdminBusinessException.class)
                    .hasMessageContaining("无效的状态转换");
        }
    }
    
    @Nested
    @DisplayName("密码重置测试")
    class PasswordResetTests {
        
        @Test
        @DisplayName("应该成功重置密码")
        void shouldResetPassword() {
            // Given
            String userId = UUID.randomUUID().toString();
            User user = User.builder()
                    .id(userId)
                    .username("testuser")
                    .passwordHash("oldHash")
                    .build();
            
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
            
            // When
            String newPassword = userManager.resetPassword(userId);
            
            // Then
            assertThat(newPassword).isNotNull();
            assertThat(newPassword.length()).isGreaterThanOrEqualTo(8);
            assertThat(user.getMustChangePassword()).isTrue();
            verify(passwordHistoryRepository).save(any());
            verify(auditService).recordPasswordReset(user);
        }
        
        @Test
        @DisplayName("应该在用户不存在时抛出异常")
        void shouldThrowExceptionWhenUserNotFound() {
            // Given
            String userId = UUID.randomUUID().toString();
            when(userRepository.findById(userId)).thenReturn(Optional.empty());
            
            // When & Then
            assertThatThrownBy(() -> userManager.resetPassword(userId))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }
    
    @Nested
    @DisplayName("用户删除测试")
    class DeleteUserTests {
        
        @Test
        @DisplayName("应该成功删除用户")
        void shouldDeleteUser() {
            // Given
            String userId = UUID.randomUUID().toString();
            User user = User.builder()
                    .id(userId)
                    .username("testuser")
                    .userRoles(new java.util.HashSet<>())
                    .build();
            
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
            
            // When
            userManager.deleteUser(userId);
            
            // Then
            assertThat(user.getDeleted()).isTrue();
            verify(userRepository).save(user);
            verify(auditService).recordUserDeletion(user);
        }
    }
}
