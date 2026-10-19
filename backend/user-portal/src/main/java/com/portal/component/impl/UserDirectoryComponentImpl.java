package com.portal.component.impl;

import com.platform.security.entity.User;
import com.platform.security.repository.UserRepository;
import com.platform.security.util.SecurityContextUtils;
import com.portal.component.UserDirectoryComponent;
import com.portal.dto.UserSummaryResponse;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserDirectoryComponentImpl implements UserDirectoryComponent {
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> summaries(List<String> userIds) {
        if (SecurityContextUtils.getCurrentUserId().isEmpty()) {
            throw new AuthenticationCredentialsNotFoundException("Portal authentication required");
        }
        return userRepository.findAllById(new LinkedHashSet<>(userIds)).stream()
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                .map(user -> new UserSummaryResponse(user.getId(), user.getUsername(), displayName(user)))
                .toList();
    }

    private String displayName(User user) {
        // FALLBACK(ux): read-only directory labels use username when a name is unset.
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) return user.getDisplayName();
        if (user.getFullName() != null && !user.getFullName().isBlank()) return user.getFullName();
        return user.getUsername();
    }
}
