package com.admin.audit;

import com.admin.entity.AuditLog;
import com.admin.repository.UserRepository;
import com.platform.security.entity.User;
import com.platform.security.util.SecurityContextUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves audit operator identity from request context, Spring Security, headers, or user lookup.
 */
public final class AuditActorResolver {

    public record OperatorIdentity(String userId, String userName) {}

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
     * Canonical operator for persisting audit logs: prefer {@code sys_users.username} by user id.
     * Falls back to {@code system} when no authenticated operator is available.
     */
    public static OperatorIdentity normalizeOperator(String userId, String userName, UserRepository userRepository) {
        String resolvedUserId = userId;
        if (isUnknown(resolvedUserId)) {
            resolvedUserId = SecurityContextUtils.getCurrentUserId().orElse(null);
        }
        if (!isUnknown(resolvedUserId) && userRepository != null) {
            Optional<User> userOpt = userRepository.findById(resolvedUserId);
            if (userOpt.isPresent()) {
                String dbUsername = usernameForUser(userOpt.get());
                if (dbUsername != null) {
                    return new OperatorIdentity(resolvedUserId, dbUsername);
                }
            }
        }
        String resolvedUserName = userName;
        if (isUnknown(resolvedUserName)) {
            resolvedUserName = SecurityContextUtils.getCurrentUsername().orElse(null);
        }
        if (!isUnknown(resolvedUserId) && isUnknown(resolvedUserName) && userRepository != null) {
            resolvedUserName = userRepository.findById(resolvedUserId)
                    .map(AuditActorResolver::usernameForUser)
                    .filter(name -> !isUnknown(name))
                    .orElse(resolvedUserId);
        }
        if (isUnknown(resolvedUserId)) {
            return new OperatorIdentity("system", "system");
        }
        if (isUnknown(resolvedUserName)) {
            resolvedUserName = resolvedUserId;
        }
        return new OperatorIdentity(resolvedUserId, resolvedUserName);
    }

    /**
     * Login usernames keyed by {@code sys_users.id}. Missing users are omitted.
     */
    public static Map<String, String> usernamesByUserId(Set<String> userIds, UserRepository userRepository) {
        if (userIds == null || userIds.isEmpty() || userRepository == null) {
            return Map.of();
        }
        Set<String> known = userIds.stream().filter(id -> !isUnknown(id)).collect(Collectors.toSet());
        if (known.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        for (User user : userRepository.findAllById(known)) {
            String username = usernameForUser(user);
            if (username != null) {
                result.put(user.getId(), username);
            }
        }
        return result;
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
        Map<String, String> usernames = usernamesByUserId(userIds, userRepository);
        for (AuditLog log : logs) {
            if (isUnknown(log.getUserId())) {
                continue;
            }
            String username = usernames.get(log.getUserId());
            if (username != null) {
                log.setUserName(username);
            } else if (isUnknown(log.getUserName())) {
                log.setUserName(log.getUserId());
            }
        }
    }

    /** Operator display for export/read paths: never leave literal {@code unknown} when userId is known. */
    public static String operatorDisplayName(AuditLog log) {
        if (log == null) {
            return "";
        }
        String userName = log.getUserName();
        if (!isUnknown(userName)) {
            return userName;
        }
        if (!isUnknown(log.getUserId())) {
            return log.getUserId();
        }
        return "system";
    }

    /**
     * Resolve login username to user id + username for AUTH audit rows.
     */
    public static OperatorIdentity resolveAuthOperator(String loginUsername, UserRepository userRepository) {
        if (loginUsername == null || loginUsername.isBlank()) {
            return normalizeOperator(null, null, userRepository);
        }
        if (userRepository != null) {
            Optional<User> userOpt = userRepository.findByUsername(loginUsername.trim());
            if (userOpt.isPresent()) {
                String username = usernameForUser(userOpt.get());
                return new OperatorIdentity(userOpt.get().getId(),
                        username != null ? username : loginUsername.trim());
            }
        }
        return new OperatorIdentity(loginUsername.trim(), loginUsername.trim());
    }
}
