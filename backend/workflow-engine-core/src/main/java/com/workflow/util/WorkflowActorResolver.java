package com.workflow.util;

import com.platform.common.dto.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Resolves current logged-in user ID from Spring Security context,
 * used for task-related APIs and JWT principal binding.
 */
public final class WorkflowActorResolver {

    private WorkflowActorResolver() {
    }

    /**
     * @return Business user ID of the authenticated user; empty if unauthenticated or unresolvable
     */
    public static Optional<String> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal up && StringUtils.hasText(up.getUserId())) {
            return Optional.of(up.getUserId().trim());
        }
        if (principal instanceof String s && StringUtils.hasText(s)) {
            return Optional.of(s.trim());
        }
        String name = authentication.getName();
        if (StringUtils.hasText(name) && !"anonymousUser".equalsIgnoreCase(name)) {
            return Optional.of(name.trim());
        }
        return Optional.empty();
    }
}
