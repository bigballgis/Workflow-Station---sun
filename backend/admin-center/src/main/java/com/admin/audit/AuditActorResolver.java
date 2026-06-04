package com.admin.audit;

import com.admin.entity.AuditLog;
import com.admin.repository.UserRepository;
import com.platform.security.entity.User;
import com.platform.security.util.SecurityContextUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves audit operator identity from request context, Spring Security, headers, or user lookup.
 */
public final class AuditActorResolver {

    private AuditActorResolver() {
    }

    public static boolean isUnknown(String value) {
        return value == null || value.isBlank() || "unknown".equalsIgnoreCase(value);
    }

    public static String resolveUserId(AuditContextHolder.AuditContext ctx) {
        if (ctx != null && !isUnknown(ctx.getUserId())) {
            return ctx.getUserId();
        }
        return SecurityContextUtils.getCurrentUserId()
                .filter(id -> !isUnknown(id))
                .orElse(null);
    }

    public static String resolveUserName(AuditContextHolder.AuditContext ctx, String userId,
                                         UserRepository userRepository) {
        if (ctx != null && !isUnknown(ctx.getUserName())) {
            return ctx.getUserName();
        }
        String fromSecurity = SecurityContextUtils.getCurrentUsername().orElse(null);
        if (!isUnknown(fromSecurity)) {
            return fromSecurity;
        }
        if (!isUnknown(userId) && userRepository != null) {
            return userRepository.findById(userId)
                    .map(AuditActorResolver::usernameForUser)
                    .orElse(userId);
        }
        return null;
    }

    /** Operator column shows login username ({@code sys_users.username}), not display name. */
    public static String usernameForUser(User user) {
        if (user == null || user.getUsername() == null || user.getUsername().isBlank()) {
            return null;
        }
        return user.getUsername();
    }

    /**
     * Resolve operator display for list/detail: always prefer {@code sys_users.username} by userId.
     */
    public static void enrichOperatorUsernames(List<AuditLog> logs, UserRepository userRepository) {
        if (logs == null || logs.isEmpty() || userRepository == null) {
            return;
        }
        Set<String> userIds = logs.stream()
                .map(AuditLog::getUserId)
                .filter(id -> !isUnknown(id))
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return;
        }
        Map<String, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));
        for (AuditLog log : logs) {
            if (isUnknown(log.getUserId())) {
                continue;
            }
            User user = users.get(log.getUserId());
            if (user != null) {
                String username = usernameForUser(user);
                if (username != null) {
                    log.setUserName(username);
                }
            } else if (isUnknown(log.getUserName())) {
                log.setUserName(log.getUserId());
            }
        }
    }
}
