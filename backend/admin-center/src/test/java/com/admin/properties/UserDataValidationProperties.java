package com.admin.properties;

import com.admin.component.UserManagerComponent;
import com.admin.dto.request.UserCreateRequest;
import com.admin.exception.InvalidEmailException;
import com.admin.exception.UsernameAlreadyExistsException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 属性 1: 用户数据验证完整性
 * 对于任何用户创建或更新请求，系统应该正确验证用户名唯一性、邮箱格式和必填字段完整性
 * 
 * 验证需求: 需求 1.1, 1.2
 */
public class UserDataValidationProperties {
    
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
     * 功能: admin-center, 属性 1: 用户数据验证完整性
     * 对于任何有效的用户名和邮箱，创建用户应该成功
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 1: 有效用户数据应该通过验证")
    void validUserDataShouldPassValidation(
            @ForAll @AlphaChars @StringLength(min = 3, max = 50) String username,
            @ForAll("validEmails") String email,
            @ForAll @AlphaChars @StringLength(min = 1, max = 50) String fullName) {
        
        // Given: 用户名不存在
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));
        
        UserCreateRequest request = UserCreateRequest.builder()
                .username(username)
                .email(email)
                .fullName(fullName)
                .initialPassword("Password123!")
                .build();
        
        // When & Then: 创建应该成功
        var result = userManager.createUser(request);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getUsername()).isEqualTo(username);
    }
    
    /**
     * 功能: admin-center, 属性 1: 用户数据验证完整性
     * 对于已存在的用户名，创建用户应该失败
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 1: 重复用户名应该被拒绝")
    void duplicateUsernameShouldBeRejected(
            @ForAll @AlphaChars @StringLength(min = 3, max = 50) String username) {
        
        // Given: 用户名已存在
        when(userRepository.existsByUsername(username)).thenReturn(true);
        
        // When & Then: 验证应该抛出异常
        assertThatThrownBy(() -> userManager.validateUsernameUnique(username))
                .isInstanceOf(UsernameAlreadyExistsException.class);
    }
    
    /**
     * 功能: admin-center, 属性 1: 用户数据验证完整性
     * 对于无效的邮箱格式，验证应该失败
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 1: 无效邮箱格式应该被拒绝")
    void invalidEmailShouldBeRejected(
            @ForAll("invalidEmails") String email) {
        
        // When & Then: 验证应该抛出异常
        assertThatThrownBy(() -> userManager.validateEmailFormat(email))
                .isInstanceOf(InvalidEmailException.class);
    }
    
    /**
     * 功能: admin-center, 属性 1: 用户数据验证完整性
     * 对于有效的邮箱格式，验证应该通过
     */
    @Property(tries = 100)
    @Label("功能: admin-center, 属性 1: 有效邮箱格式应该通过验证")
    void validEmailShouldPassValidation(
            @ForAll("validEmails") String email) {
        
        // When & Then: 验证不应该抛出异常
        userManager.validateEmailFormat(email);
        // 如果没有抛出异常，测试通过
    }
    
    @Provide
    Arbitrary<String> validEmails() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(20)
                .map(name -> name.toLowerCase() + "@example.com");
    }
    
    @Provide
    Arbitrary<String> invalidEmails() {
        return Arbitraries.oneOf(
                Arbitraries.just(""),
                Arbitraries.just("invalid"),
                Arbitraries.just("no-at-sign.com"),
                Arbitraries.just("@no-local-part.com"),
                Arbitraries.just("spaces in@email.com"),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10)
                        .filter(s -> !s.contains("@"))
        );
    }
}
