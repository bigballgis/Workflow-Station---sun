package com.admin.properties;

import com.admin.component.UserManagerComponent;
import com.admin.entity.User;
import com.admin.enums.UserStatus;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.PasswordHistoryRepository;
import com.admin.repository.UserRepository;
import com.admin.service.AuditService;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
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
 * 属性 2: 用户状态转换正确性
 * 对于任何用户状态变更操作（启用、禁用、锁定、解锁），系统应该正确执行状态转换并记录变更历史
 * 
 * 验证需求: 需求 1.5, 1.8
 */
public class UserStatusTransitionProperties {
    
    @Mock
    private UserRepository userRepository;
    
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
                passwordHistoryRepository, 
                passwordEncoder, 
                auditService);
    }
    
    /**
     * 功能: admin-center, 属性 2: 用户状态转换正确性
     * 从 ACTIVE 状态可以转换到 DISABLED 或 LOCKED
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 2: ACTIVE状态可以转换到DISABLED或LOCKED")
    void activeUserCanBeDisabledOrLocked(
            @ForAll("validTargetStatusFromActive") UserStatus targetStatus) {
        
        // Given: 一个活跃用户
        String userId = UUID.randomUUID().toString();
        User user = User.builder()
                .id(userId)
                .username("testuser")
                .status(UserStatus.ACTIVE)
                .build();
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        
        // When: 更新状态
        userManager.updateUserStatus(userId, targetStatus, "测试原因");
        
        // Then: 状态应该被更新
        assertThat(user.getStatus()).isEqualTo(targetStatus);
        verify(auditService).recordStatusChange(eq(user), eq(UserStatus.ACTIVE), eq(targetStatus), anyString());
    }
    
    /**
     * 功能: admin-center, 属性 2: 用户状态转换正确性
     * 从 DISABLED 状态只能转换到 ACTIVE
     */
    @Property(tries = 50)
    @Label("功能: admin-center, 属性 2: DISABLED状态只能转换到ACTIVE")
    void disabledUserCanOnlyBeActivated() {
        // Given: 一个禁用用户
        String userId = UUID.randomUUID().toString();
        User user = User.builder()
                .id(userId)
                .username("testuser")
                .status(UserStatus.DISABLED)
                .build();
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        
        // When: 更新状态到 ACTIVE
        userManager.updateUserStatus(userId, UserStatus.ACTIVE, "重新激活");
        
        // Then: 状态应该被更新为 ACTIVE
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }
    
    /**
     * 功能: admin-center, 属性 2: 用户状态转换正确性
     * 从 LOCKED 状态只能转换到 ACTIVE（解锁）
     */
    @Property(tries = 50)
    @Label("功能: admin-center, 属性 2: LOCKED状态只能转换到ACTIVE")
    void lockedUserCanOnlyBeUnlocked() {
        // Given: 一个锁定用户
        String userId = UUID.randomUUID().toString();
        User user = User.builder()
                .id(userId)
                .username("testuser")
                .status(UserStatus.LOCKED)
                .failedLoginCount(5)
                .build();
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        
        // When: 解锁用户
        userManager.updateUserStatus(userId, UserStatus.ACTIVE, "管理员解锁");
        
        // Then: 状态应该被更新为 ACTIVE，且登录失败次数重置
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getFailedLoginCount()).isEqualTo(0);
        assertThat(user.getLockedUntil()).isNull();
    }
    
    /**
     * 功能: admin-center, 属性 2: 用户状态转换正确性
     * 无效的状态转换应该被拒绝
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 2: 无效状态转换应该被拒绝")
    void invalidStatusTransitionShouldBeRejected(
            @ForAll("invalidStatusTransitions") StatusTransition transition) {
        
        // Given: 一个用户
        String userId = UUID.randomUUID().toString();
        User user = User.builder()
                .id(userId)
                .username("testuser")
                .status(transition.from)
                .build();
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        
        // When & Then: 无效转换应该抛出异常
        assertThatThrownBy(() -> 
                userManager.updateUserStatus(userId, transition.to, "测试"))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("无效的状态转换");
    }
    
    @Provide
    Arbitrary<UserStatus> validTargetStatusFromActive() {
        return Arbitraries.of(UserStatus.DISABLED, UserStatus.LOCKED);
    }
    
    @Provide
    Arbitrary<StatusTransition> invalidStatusTransitions() {
        return Arbitraries.oneOf(
                // DISABLED 不能直接转到 LOCKED
                Arbitraries.just(new StatusTransition(UserStatus.DISABLED, UserStatus.LOCKED)),
                // LOCKED 不能直接转到 DISABLED
                Arbitraries.just(new StatusTransition(UserStatus.LOCKED, UserStatus.DISABLED)),
                // ACTIVE 不能转到 PENDING
                Arbitraries.just(new StatusTransition(UserStatus.ACTIVE, UserStatus.PENDING))
        );
    }
    
    record StatusTransition(UserStatus from, UserStatus to) {}
}
