package com.developer.controller;

import com.developer.component.UserPreferenceComponent;
import com.developer.security.DeveloperPermissionChecker;
import com.platform.common.dto.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPreferenceControllerTest {

    @Mock private UserPreferenceComponent userPreferenceComponent;
    @Mock private DeveloperPermissionChecker developerPermissionChecker;

    private UserPreferenceController controller;

    @BeforeEach
    void setUp() {
        controller = new UserPreferenceController(userPreferenceComponent, developerPermissionChecker);
        UserPrincipal principal = UserPrincipal.builder().userId("u-1").username("auditor").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sharedSaveWithoutUpdatePermissionIsForbidden() {
        when(developerPermissionChecker.hasPermission("u-1", "function_unit:update")).thenReturn(false);
        UserPreferenceController.PreferenceValueRequest body = new UserPreferenceController.PreferenceValueRequest();
        body.setValue("{\"entries\":[]}");
        ResponseEntity<?> response = controller.save("launchpad-layout", "shared", body);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(userPreferenceComponent, never()).save(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void userScopeSaveDoesNotRequireUpdatePermission() {
        UserPreferenceController.PreferenceValueRequest body = new UserPreferenceController.PreferenceValueRequest();
        body.setValue("{\"entries\":[]}");
        ResponseEntity<?> response = controller.save("launchpad-layout", "user", body);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userPreferenceComponent).save("u-1", "launchpad-layout", "{\"entries\":[]}");
    }
}
