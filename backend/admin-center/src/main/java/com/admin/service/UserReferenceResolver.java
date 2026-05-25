package com.admin.service;

import com.platform.security.entity.User;
import com.admin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将用户 ID 或混合引用解析为 username，供列表展示使用。
 */
@Service
@RequiredArgsConstructor
public class UserReferenceResolver {

    private final UserRepository userRepository;

    public String resolveUsername(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        if ("system".equalsIgnoreCase(reference)) {
            return "system";
        }
        return userRepository.findById(reference)
                .map(User::getUsername)
                .or(() -> userRepository.findByUsername(reference).map(User::getUsername))
                .orElse(reference);
    }

    public Map<String, String> resolveUsernames(Collection<String> references) {
        if (references == null || references.isEmpty()) {
            return Map.of();
        }
        Set<String> unique = references.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .filter(s -> !"system".equalsIgnoreCase(s))
                .collect(Collectors.toSet());
        if (unique.isEmpty()) {
            return Map.of();
        }

        Map<String, String> resolved = new HashMap<>();
        for (User user : userRepository.findAllById(unique)) {
            resolved.put(user.getId(), user.getUsername());
        }
        for (String ref : unique) {
            if (!resolved.containsKey(ref)) {
                userRepository.findByUsername(ref)
                        .ifPresent(user -> resolved.put(ref, user.getUsername()));
            }
        }
        return resolved;
    }

    public String resolveWithCache(String reference, Map<String, String> cache) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        if ("system".equalsIgnoreCase(reference)) {
            return "system";
        }
        return cache.getOrDefault(reference, reference);
    }
}
