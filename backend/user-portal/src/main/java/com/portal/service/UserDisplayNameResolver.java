package com.portal.service;

import com.platform.security.entity.User;
import com.platform.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.portal.service.ProcessAssigneeSnapshot.collectUserKeys;
import static com.portal.service.ProcessAssigneeSnapshot.parseDelimitedUserKeys;

/**
 * Resolves user identifiers (UUID id, username, or employee_id) to a display name for portal UI.
 * Priority: fullName &gt; displayName &gt; username &gt; original key.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDisplayNameResolver {

    /** Separator for multiple assignee display names in list/detail UI. */
    public static final String MULTI_ASSIGNEE_DISPLAY_SEPARATOR = ", ";

    private final UserRepository userRepository;

    public String resolve(String userIdOrKey) {
        if (userIdOrKey == null || userIdOrKey.isBlank()) {
            return null;
        }
        String key = userIdOrKey.trim();
        return resolveBatch(List.of(key)).getOrDefault(key, key);
    }

    public String resolveCached(String userIdOrKey, Map<String, String> cache) {
        if (userIdOrKey == null || userIdOrKey.isBlank()) {
            return null;
        }
        String key = userIdOrKey.trim();
        return cache.computeIfAbsent(key, this::resolve);
    }

    /**
     * Collect all user keys referenced by assignee / candidate columns for batch lookup.
     */
    public Set<String> collectAssigneeUserKeys(String assigneeUserId, String candidateUserIds) {
        return collectUserKeys(assigneeUserId, candidateUserIds);
    }

    /**
     * Batch resolve keys; unresolved keys map to themselves.
     */
    public Map<String, String> resolveBatch(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }

        Set<String> unique = keys.stream()
                .filter(Objects::nonNull)
                .flatMap(key -> parseDelimitedUserKeys(key).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (unique.isEmpty()) {
            return Map.of();
        }

        Map<String, String> aliasToDisplay = new HashMap<>();
        registerUsers(aliasToDisplay, userRepository.findAllById(unique));

        List<String> unresolved = unique.stream()
                .filter(key -> !aliasToDisplay.containsKey(key))
                .toList();
        if (!unresolved.isEmpty()) {
            registerUsers(aliasToDisplay, userRepository.findByUsernameIn(unresolved));
        }

        unresolved = unique.stream()
                .filter(key -> !aliasToDisplay.containsKey(key))
                .toList();
        if (!unresolved.isEmpty()) {
            registerUsers(aliasToDisplay, userRepository.findByEmployeeIdIn(unresolved));
        }

        Map<String, String> out = new HashMap<>();
        for (String key : unique) {
            out.put(key, aliasToDisplay.getOrDefault(key, key));
        }
        return out;
    }

    /**
     * Resolve comma-separated user ids to comma-separated display names.
     */
    public String resolveDelimitedDisplay(String delimitedUserKeys, Map<String, String> cache) {
        List<String> keys = parseDelimitedUserKeys(delimitedUserKeys);
        if (keys.isEmpty()) {
            return null;
        }
        if (keys.size() == 1) {
            return resolveCached(keys.get(0), cache);
        }
        return keys.stream()
                .map(key -> resolveCached(key, cache))
                .collect(Collectors.joining(MULTI_ASSIGNEE_DISPLAY_SEPARATOR));
    }

    /**
     * Build Current Assignee label: single name, or {@code name1, name2, name3} for BU/Role pools.
     */
    public String resolveCurrentAssigneeDisplay(String assigneeUserId, String candidateUserIds,
                                                Map<String, String> cache) {
        if (assigneeUserId != null && !assigneeUserId.isBlank()) {
            List<String> assigneeKeys = parseDelimitedUserKeys(assigneeUserId);
            if (assigneeKeys.size() == 1) {
                String resolved = resolveCached(assigneeKeys.get(0), cache);
                if (!assigneeKeys.get(0).equals(resolved)) {
                    return resolved;
                }
            }
            if (assigneeKeys.size() > 1) {
                return resolveDelimitedDisplay(assigneeUserId, cache);
            }
        }
        if (candidateUserIds != null && !candidateUserIds.isBlank()) {
            return resolveDelimitedDisplay(candidateUserIds, cache);
        }
        if (assigneeUserId != null && !assigneeUserId.isBlank()) {
            return resolveCached(assigneeUserId.trim(), cache);
        }
        return null;
    }

    static String displayNameForUser(User user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName().trim();
        }
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName().trim();
        }
        return user.getUsername();
    }

    private static void registerUsers(Map<String, String> aliasToDisplay, Iterable<User> users) {
        if (users == null) {
            return;
        }
        for (User user : users) {
            if (user == null) {
                continue;
            }
            String display = displayNameForUser(user);
            if (user.getId() != null && !user.getId().isBlank()) {
                aliasToDisplay.putIfAbsent(user.getId().trim(), display);
            }
            if (user.getUsername() != null && !user.getUsername().isBlank()) {
                aliasToDisplay.putIfAbsent(user.getUsername().trim(), display);
            }
            if (user.getEmployeeId() != null && !user.getEmployeeId().isBlank()) {
                aliasToDisplay.putIfAbsent(user.getEmployeeId().trim(), display);
            }
        }
    }
}
