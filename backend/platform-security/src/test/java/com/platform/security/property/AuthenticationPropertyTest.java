package com.platform.security.property;

import com.platform.security.config.JwtProperties;
import com.platform.security.dto.LoginResponse;
import com.platform.security.dto.TokenResponse;
import com.platform.security.exception.AuthErrorCode;
import com.platform.security.exception.AuthenticationException;
import com.platform.security.model.User;
import com.platform.security.model.UserStatus;
import com.platform.security.repository.LoginAuditRepository;
import com.platform.security.repository.UserRepository;
import com.platform.security.service.JwtTokenService;
import com.platform.security.service.impl.AuthenticationServiceImpl;
import com.platform.security.service.impl.JwtTokenServiceImpl;
import com.platform.security.service.impl.LoginAuditService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.CharRange;
import net.jqwik.api.constraints.StringLength;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for Authentication Service.
 * Feature: authentication, Property 3: Authentication Correctness
 * Validates: Requirements 2.1, 2.2, 2.3, 2.4
 */
class AuthenticationPropertyTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtProperties jwtProperties;
    private final JwtTokenService jwtTokenService;

    AuthenticationPropertyTest() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-key-for-jwt-token-generation-minimum-256-bits-required");
        jwtProperties.setExpirationMs(3600000);
        jwtProperties.setRefreshExpirationMs(604800000);
        jwtProperties.setIssuer("test-platform");
        StringRedisTemplate mockRedisTemplate = mock(StringRedisTemplate.class);
        this.jwtTokenService = new JwtTokenServiceImpl(jwtProperties, mockRedisTemplate);
    }

    /**
     * Property 3: Authentication Correctness
     * For any login attempt with valid credentials and ACTIVE account,
     * the service SHALL return tokens.
     * Validates: Requirements 2.1
     */
    @Property(tries = 100)
    void validCredentialsShouldReturnTokens(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 3, max = 20) String username,
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 6, max = 50) String password
    ) {
        // Setup mocks
        UserRepository userRepository = mock(UserRepository.class);
        LoginAuditRepository auditRepository = mock(LoginAuditRepository.class);
        LoginAuditService auditService = new LoginAuditService(auditRepository);

        String passwordHash = passwordEncoder.encode(password);
        User user = User.builder()
                .id(UUID.randomUUID())
                .username(username)
                .passwordHash(passwordHash)
                .status(UserStatus.ACTIVE)
                .roles(Set.of("USER"))
                .language("zh_CN")
                .build();

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        AuthenticationServiceImpl authService = new AuthenticationServiceImpl(
                userRepository, jwtTokenService, passwordEncoder, jwtProperties, auditService
        );

        // Execute
        LoginResponse response = authService.login(username, password, "127.0.0.1", "Test Agent");

        // Verify
        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user()).isNotNull();
        assertThat(response.user().username()).isEqualTo(username);
    }

    /**
     * Property 3: Authentication Correctness
     * For any login attempt with invalid password,
     * the service SHALL return 401 Unauthorized.
     * Validates: Requirements 2.2
     */
    @Property(tries = 100)
    void invalidPasswordShouldReturn401(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 3, max = 20) String username,
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 6, max = 50) String correctPassword,
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 6, max = 50) String wrongPassword
    ) {
        Assume.that(!correctPassword.equals(wrongPassword));

        // Setup mocks
        UserRepository userRepository = mock(UserRepository.class);
        LoginAuditRepository auditRepository = mock(LoginAuditRepository.class);
        LoginAuditService auditService = new LoginAuditService(auditRepository);

        String passwordHash = passwordEncoder.encode(correctPassword);
        User user = User.builder()
                .id(UUID.randomUUID())
                .username(username)
                .passwordHash(passwordHash)
                .status(UserStatus.ACTIVE)
                .roles(Set.of("USER"))
                .build();

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        AuthenticationServiceImpl authService = new AuthenticationServiceImpl(
                userRepository, jwtTokenService, passwordEncoder, jwtProperties, auditService
        );

        // Execute and verify
        assertThatThrownBy(() -> authService.login(username, wrongPassword, "127.0.0.1", "Test Agent"))
                .isInstanceOf(AuthenticationException.class)
                .satisfies(e -> {
                    AuthenticationException ae = (AuthenticationException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_001);
                    assertThat(ae.getHttpStatus()).isEqualTo(401);
                });
    }

    /**
     * Property 3: Authentication Correctness
     * For any login attempt with LOCKED account,
     * the service SHALL return 403 Forbidden.
     * Validates: Requirements 2.3
     */
    @Property(tries = 100)
    void lockedAccountShouldReturn403(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 3, max = 20) String username,
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 6, max = 50) String password
    ) {
        // Setup mocks
        UserRepository userRepository = mock(UserRepository.class);
        LoginAuditRepository auditRepository = mock(LoginAuditRepository.class);
        LoginAuditService auditService = new LoginAuditService(auditRepository);

        String passwordHash = passwordEncoder.encode(password);
        User user = User.builder()
                .id(UUID.randomUUID())
                .username(username)
                .passwordHash(passwordHash)
                .status(UserStatus.LOCKED)
                .roles(Set.of("USER"))
                .build();

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        AuthenticationServiceImpl authService = new AuthenticationServiceImpl(
                userRepository, jwtTokenService, passwordEncoder, jwtProperties, auditService
        );

        // Execute and verify
        assertThatThrownBy(() -> authService.login(username, password, "127.0.0.1", "Test Agent"))
                .isInstanceOf(AuthenticationException.class)
                .satisfies(e -> {
                    AuthenticationException ae = (AuthenticationException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_002);
                    assertThat(ae.getHttpStatus()).isEqualTo(403);
                });
    }

    /**
     * Property 3: Authentication Correctness
     * For any login attempt with INACTIVE account,
     * the service SHALL return 403 Forbidden.
     * Validates: Requirements 2.4
     */
    @Property(tries = 100)
    void inactiveAccountShouldReturn403(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 3, max = 20) String username,
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 6, max = 50) String password
    ) {
        // Setup mocks
        UserRepository userRepository = mock(UserRepository.class);
        LoginAuditRepository auditRepository = mock(LoginAuditRepository.class);
        LoginAuditService auditService = new LoginAuditService(auditRepository);

        String passwordHash = passwordEncoder.encode(password);
        User user = User.builder()
                .id(UUID.randomUUID())
                .username(username)
                .passwordHash(passwordHash)
                .status(UserStatus.INACTIVE)
                .roles(Set.of("USER"))
                .build();

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        AuthenticationServiceImpl authService = new AuthenticationServiceImpl(
                userRepository, jwtTokenService, passwordEncoder, jwtProperties, auditService
        );

        // Execute and verify
        assertThatThrownBy(() -> authService.login(username, password, "127.0.0.1", "Test Agent"))
                .isInstanceOf(AuthenticationException.class)
                .satisfies(e -> {
                    AuthenticationException ae = (AuthenticationException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_003);
                    assertThat(ae.getHttpStatus()).isEqualTo(403);
                });
    }

    /**
     * Property 3: Authentication Correctness
     * For any login attempt with non-existent user,
     * the service SHALL return 401 Unauthorized.
     * Validates: Requirements 2.2
     */
    @Property(tries = 100)
    void nonExistentUserShouldReturn401(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 3, max = 20) String username,
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 6, max = 50) String password
    ) {
        // Setup mocks
        UserRepository userRepository = mock(UserRepository.class);
        LoginAuditRepository auditRepository = mock(LoginAuditRepository.class);
        LoginAuditService auditService = new LoginAuditService(auditRepository);

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        AuthenticationServiceImpl authService = new AuthenticationServiceImpl(
                userRepository, jwtTokenService, passwordEncoder, jwtProperties, auditService
        );

        // Execute and verify
        assertThatThrownBy(() -> authService.login(username, password, "127.0.0.1", "Test Agent"))
                .isInstanceOf(AuthenticationException.class)
                .satisfies(e -> {
                    AuthenticationException ae = (AuthenticationException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_001);
                    assertThat(ae.getHttpStatus()).isEqualTo(401);
                });
    }
}
