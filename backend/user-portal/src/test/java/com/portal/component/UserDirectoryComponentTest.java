package com.portal.component;

import com.platform.security.entity.User;
import com.platform.security.repository.UserRepository;
import com.platform.security.util.SecurityContextUtils;
import com.portal.component.impl.UserDirectoryComponentImpl;
import com.portal.dto.UserSummariesRequest;
import jakarta.validation.Validation;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserDirectoryComponentTest {
    private MockedStatic<SecurityContextUtils> auth;

    @BeforeEach
    void authenticated() {
        auth = mockStatic(SecurityContextUtils.class);
        auth.when(SecurityContextUtils::getCurrentUserId).thenReturn(java.util.Optional.of("portal-user"));
    }

    @AfterEach
    void clearAuthentication() { auth.close(); }

    @Test
    void anonymousLookupDoesNotReadTheDirectory() {
        auth.when(SecurityContextUtils::getCurrentUserId).thenReturn(java.util.Optional.empty());
        var repository = mock(UserRepository.class);
        assertThrows(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class,
                () -> new UserDirectoryComponentImpl(repository).summaries(List.of("a")));
        verifyNoInteractions(repository);
    }
    @Test
    void resolvesDistinctIdsInOneQueryAndReturnsOnlyDirectoryLabels() {
        var repository = mock(UserRepository.class);
        when(repository.findAllById(Set.of("a", "b", "deleted"))).thenReturn(List.of(
                User.builder().id("a").username("alice").displayName("Alice").build(),
                User.builder().id("b").username("bob").fullName("Bob Example").build(),
                User.builder().id("deleted").username("removed").deleted(true).build()));
        var result = new UserDirectoryComponentImpl(repository).summaries(List.of("a", "a", "b", "deleted"));
        assertEquals(List.of("Alice", "Bob Example"), result.stream().map(user -> user.name()).toList());
        assertEquals(List.of("a", "b"), result.stream().map(user -> user.id()).toList());
        verify(repository, times(1)).findAllById(Set.of("a", "b", "deleted"));
    }

    @Test
    void directoryFailurePropagatesInsteadOfInventingAnEmptyResult() {
        var repository = mock(UserRepository.class);
        when(repository.findAllById(Set.of("a"))).thenThrow(new IllegalStateException("directory unavailable"));
        assertThrows(IllegalStateException.class,
                () -> new UserDirectoryComponentImpl(repository).summaries(List.of("a")));
    }

    @Test
    void requestRejectsEmptyBlankAndOversizedIdLists() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new UserSummariesRequest(List.of())).isEmpty());
            assertFalse(validator.validate(new UserSummariesRequest(List.of(" "))).isEmpty());
            assertFalse(validator.validate(new UserSummariesRequest(java.util.Collections.nCopies(201, "a"))).isEmpty());
            assertTrue(validator.validate(new UserSummariesRequest(List.of("a"))).isEmpty());
        }
    }
}
