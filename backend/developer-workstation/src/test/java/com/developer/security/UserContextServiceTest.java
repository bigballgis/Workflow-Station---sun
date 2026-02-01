package com.developer.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserContextService.
 * Tests SecurityContext integration scenarios, unauthenticated user handling, and session expiration.
 * 
 * Requirements: 3.5, 4.1, 4.2, 4.3
 */
@DisplayName("UserContextService Unit Tests")
public class UserContextServiceTest {
    
    @Mock
    private SecurityContext securityContext;
    
    @Mock
    private Authentication authentication;
    
    private UserContextService userContextService;
    private MockedStatic<SecurityContextHolder> securityContextHolderMock;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userContextService = new UserContextService(null);
        
        // Mock SecurityContextHolder static methods
        securityContextHolderMock = mockStatic(SecurityContextHolder.class);
        securityContextHolderMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);
    }
    
    @AfterEach
    void tearDown() {
        securityContextHolderMock.close();
    }
    
    @Test
    @DisplayName("Should return username when user is authenticated")
    void shouldReturnUsernameWhenUserIsAuthenticated() {
        // Setup
        String expectedUsername = "testUser";
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(expectedUsername);
        
        // Execute
        Optional<String> result = userContextService.getCurrentUsername();
        
        // Verify
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(expectedUsername);
    }
    
    @Test
    @DisplayName("Should return empty when no authentication exists")
    void shouldReturnEmptyWhenNoAuthenticationExists() {
        // Setup
        when(securityContext.getAuthentication()).thenReturn(null);
        
        // Execute
        Optional<String> result = userContextService.getCurrentUsername();
        
        // Verify
        assertThat(result).isEmpty();
    }
    
    @Test
    @DisplayName("Should return empty when user is not authenticated")
    void shouldReturnEmptyWhenUserIsNotAuthenticated() {
        // Setup
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);
        
        // Execute
        Optional<String> result = userContextService.getCurrentUsername();
        
        // Verify
        assertThat(result).isEmpty();
    }
    
    @Test
    @DisplayName("Should return empty when username is null")
    void shouldReturnEmptyWhenUsernameIsNull() {
        // Setup
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(null);
        
        // Execute
        Optional<String> result = userContextService.getCurrentUsername();
        
        // Verify
        assertThat(result).isEmpty();
    }
    
    @Test
    @DisplayName("Should return empty when username is empty")
    void shouldReturnEmptyWhenUsernameIsEmpty() {
        // Setup
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("   ");
        
        // Execute
        Optional<String> result = userContextService.getCurrentUsername();
        
        // Verify
        assertThat(result).isEmpty();
    }
    
    @Test
    @DisplayName("Should return authenticated username when available")
    void shouldReturnAuthenticatedUsernameWhenAvailable() {
        // Setup
        String authenticatedUser = "authenticatedUser";
        String providedUser = "providedUser";
        
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(authenticatedUser);
        
        // Execute
        String result = userContextService.getCurrentUsernameOrFallback(providedUser);
        
        // Verify
        assertThat(result).isEqualTo(authenticatedUser);
    }
    
    @Test
    @DisplayName("Should return provided username when no authentication")
    void shouldReturnProvidedUsernameWhenNoAuthentication() {
        // Setup
        String providedUser = "providedUser";
        when(securityContext.getAuthentication()).thenReturn(null);
        
        // Execute
        String result = userContextService.getCurrentUsernameOrFallback(providedUser);
        
        // Verify
        assertThat(result).isEqualTo(providedUser);
    }
    
    @Test
    @DisplayName("Should return null when no authentication and no provided username")
    void shouldReturnNullWhenNoAuthenticationAndNoProvidedUsername() {
        // Setup
        when(securityContext.getAuthentication()).thenReturn(null);
        
        // Execute
        String result = userContextService.getCurrentUsernameOrFallback(null);
        
        // Verify
        assertThat(result).isNull();
    }
    
    @Test
    @DisplayName("Should return true when user is authenticated")
    void shouldReturnTrueWhenUserIsAuthenticated() {
        // Setup
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("testUser");
        
        // Execute
        boolean result = userContextService.isUserAuthenticated();
        
        // Verify
        assertThat(result).isTrue();
    }
    
    @Test
    @DisplayName("Should return false when user is not authenticated")
    void shouldReturnFalseWhenUserIsNotAuthenticated() {
        // Setup
        when(securityContext.getAuthentication()).thenReturn(null);
        
        // Execute
        boolean result = userContextService.isUserAuthenticated();
        
        // Verify
        assertThat(result).isFalse();
    }
    
    @Test
    @DisplayName("Should return true when username matches current user")
    void shouldReturnTrueWhenUsernameMatchesCurrentUser() {
        // Setup
        String username = "testUser";
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(username);
        
        // Execute
        boolean result = userContextService.isCurrentUser(username);
        
        // Verify
        assertThat(result).isTrue();
    }
    
    @Test
    @DisplayName("Should return false when username does not match current user")
    void shouldReturnFalseWhenUsernameDoesNotMatchCurrentUser() {
        // Setup
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("currentUser");
        
        // Execute
        boolean result = userContextService.isCurrentUser("differentUser");
        
        // Verify
        assertThat(result).isFalse();
    }
    
    @Test
    @DisplayName("Should return false for null username")
    void shouldReturnFalseForNullUsername() {
        // Execute
        boolean result = userContextService.isCurrentUser(null);
        
        // Verify
        assertThat(result).isFalse();
    }
    
    @Test
    @DisplayName("Should validate normal username")
    void shouldValidateNormalUsername() {
        // Execute
        boolean result = userContextService.isValidUserForPermissionCheck("normalUser");
        
        // Verify
        assertThat(result).isTrue();
    }
    
    @Test
    @DisplayName("Should reject null username")
    void shouldRejectNullUsername() {
        // Execute
        boolean result = userContextService.isValidUserForPermissionCheck(null);
        
        // Verify
        assertThat(result).isFalse();
    }
    
    @Test
    @DisplayName("Should reject empty username")
    void shouldRejectEmptyUsername() {
        // Execute
        boolean result = userContextService.isValidUserForPermissionCheck("   ");
        
        // Verify
        assertThat(result).isFalse();
    }
    
    @Test
    @DisplayName("Should reject username with suspicious characters")
    void shouldRejectUsernameWithSuspiciousCharacters() {
        // Test various suspicious characters
        assertThat(userContextService.isValidUserForPermissionCheck("user'name")).isFalse();
        assertThat(userContextService.isValidUserForPermissionCheck("user\"name")).isFalse();
        assertThat(userContextService.isValidUserForPermissionCheck("user;name")).isFalse();
        assertThat(userContextService.isValidUserForPermissionCheck("user--name")).isFalse();
        assertThat(userContextService.isValidUserForPermissionCheck("user/*name")).isFalse();
        assertThat(userContextService.isValidUserForPermissionCheck("user*/name")).isFalse();
    }
    
    @Test
    @DisplayName("Should handle session expiration when no current user")
    void shouldHandleSessionExpirationWhenNoCurrentUser() {
        // Setup
        when(securityContext.getAuthentication()).thenReturn(null);
        
        // Execute
        boolean result = userContextService.handleSessionExpiration("testUser");
        
        // Verify
        assertThat(result).isFalse();
    }
    
    @Test
    @DisplayName("Should handle session expiration when user matches")
    void shouldHandleSessionExpirationWhenUserMatches() {
        // Setup
        String username = "testUser";
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(username);
        
        // Execute
        boolean result = userContextService.handleSessionExpiration(username);
        
        // Verify
        assertThat(result).isTrue();
    }
    
    @Test
    @DisplayName("Should handle session expiration when user differs")
    void shouldHandleSessionExpirationWhenUserDiffers() {
        // Setup
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("currentUser");
        
        // Execute
        boolean result = userContextService.handleSessionExpiration("differentUser");
        
        // Verify
        assertThat(result).isFalse();
    }
    
    @Test
    @DisplayName("Should provide authentication debug info")
    void shouldProvideAuthenticationDebugInfo() {
        // Setup
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn("principal");
        when(authentication.getName()).thenReturn("testUser");
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getAuthorities()).thenReturn(java.util.Collections.emptyList());
        
        // Execute
        String result = userContextService.getAuthenticationDebugInfo();
        
        // Verify
        assertThat(result).contains("Authentication:");
        assertThat(result).contains("Principal: principal");
        assertThat(result).contains("Name: testUser");
        assertThat(result).contains("Authenticated: true");
    }
    
    @Test
    @DisplayName("Should handle null authentication in debug info")
    void shouldHandleNullAuthenticationInDebugInfo() {
        // Setup
        when(securityContext.getAuthentication()).thenReturn(null);
        
        // Execute
        String result = userContextService.getAuthenticationDebugInfo();
        
        // Verify
        assertThat(result).isEqualTo("No authentication in SecurityContext");
    }
}