package com.admin.bi.component;

import com.platform.common.dto.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BiAdminAccessGuardTest {

    private final BiAdminAccessGuard guard = new BiAdminAccessGuard();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsMissingAuthentication() {
        assertThatThrownBy(guard::requireAdminUserId)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void rejectsAuthenticatedPortalOrServiceUserWithoutAnAdminRole() {
        authenticate("portal-user", List.of());

        assertThatThrownBy(guard::requireAdminUserId)
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void acceptsARealAdminCenterRole() {
        authenticate("admin-user", List.of("SYS_ADMIN"));

        assertThat(guard.requireAdminUserId()).isEqualTo("admin-user");
    }

    private static void authenticate(String userId, List<String> roles) {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(userId)
                .username(userId)
                .roles(roles)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
