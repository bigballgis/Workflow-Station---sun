package com.portal.security;

import com.platform.common.dto.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The current user is the authenticated one; a client-supplied X-User-Id is never an identity (§7.2). */
class CurrentUserIdArgumentResolverTest {

    private final CurrentUserIdArgumentResolver resolver = new CurrentUserIdArgumentResolver();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theAuthenticatedUserWinsOverAnyHeader() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                UserPrincipal.builder().userId("u-jwt").username("u-jwt").build(), null, List.of()));

        assertThat(resolver.resolveArgument(null, null, request("spoofed"), null)).isEqualTo("u-jwt");
    }

    @Test
    void aHeaderAloneIsNoIdentity() {
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, request("someone-else"), null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private static ServletWebRequest request(String userIdHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", userIdHeader);
        return new ServletWebRequest(request);
    }
}
