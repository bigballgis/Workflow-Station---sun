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

/**
 * Resolves user identifiers (UUID id or username) to a display name for portal UI.
 * Priority: fullName &gt; displayName &gt; username &gt; original key.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDisplayNameResolver {

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
     * Batch resolve keys; unresolved keys map to themselves.
     */
    public Map<String, String> resolveBatch(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }

        Set<String> unique = keys.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (unique.isEmpty()) {
            return Map.of();
        }

        Map<String, String> out = new HashMap<>();
        Map<String, User> byId = new HashMap<>();
        userRepository.findAllById(unique).forEach(user -> {
            if (user != null && user.getId() != null) {
                byId.put(user.getId(), user);
            }
        });

        List<String> unresolved = new ArrayList<>();
        for (String key : unique) {
            User user = byId.get(key);
            if (user != null) {
                out.put(key, displayNameForUser(user));
            } else {
                unresolved.add(key);
            }
        }

        for (String key : unresolved) {
            userRepository.findByUsername(key)
                    .map(UserDisplayNameResolver::displayNameForUser)
                    .ifPresentOrElse(
                            name -> out.put(key, name),
                            () -> out.put(key, key)
                    );
        }
        return out;
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
}
