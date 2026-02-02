package com.platform.security.dto;

import java.util.List;

/**
 * User information DTO for authentication responses.
 * Validates: Requirements 2.6
 */
public record UserInfo(
    String userId,
    String username,
    String displayName,
    String email,
    List<String> roles,
    List<String> permissions,
    String language
) {
    /**
     * Create UserInfo from User entity.
     */
    public static UserInfo fromUser(com.platform.security.entity.User user, List<String> roles, List<String> permissions) {
        return new UserInfo(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getEmail(),
            roles != null ? List.copyOf(roles) : List.of(),
            permissions != null ? permissions : List.of(),
            user.getLanguage()
        );
    }
}
