package com.portal.service;

import com.platform.security.entity.User;
import com.platform.security.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDisplayNameResolverTest {

    @Mock
    private UserRepository userRepository;

    private UserDisplayNameResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new UserDisplayNameResolver(userRepository);
    }

    @Test
    void resolve_returnsFullNameByUserId() {
        User user = User.builder()
                .id("uuid-1")
                .username("45201959")
                .fullName("Zhang San")
                .build();
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(user));

        assertThat(resolver.resolve("uuid-1")).isEqualTo("Zhang San");
    }

    @Test
    void resolve_fallsBackToUsernameLookup() {
        User user = User.builder()
                .id("uuid-2")
                .username("45201959")
                .fullName("Li Si")
                .build();
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of());
        when(userRepository.findByUsernameIn(anyCollection())).thenReturn(List.of(user));

        assertThat(resolver.resolve("45201959")).isEqualTo("Li Si");
    }

    @Test
    void resolve_fallsBackToEmployeeIdLookup() {
        User user = User.builder()
                .id("uuid-3")
                .username("ls001")
                .employeeId("45201959")
                .fullName("Wang Wu")
                .build();
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of());
        when(userRepository.findByUsernameIn(anyCollection())).thenReturn(List.of());
        when(userRepository.findByEmployeeIdIn(anyCollection())).thenReturn(List.of(user));

        assertThat(resolver.resolve("45201959")).isEqualTo("Wang Wu");
    }

    @Test
    void resolveBatch_resolvesMultipleKeys() {
        User u1 = User.builder().id("id-a").username("a").fullName("User A").build();
        User u2 = User.builder().id("id-b").username("b").displayName("User B").build();
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(u1, u2));

        Map<String, String> names = resolver.resolveBatch(List.of("id-a", "id-b"));

        assertThat(names).containsEntry("id-a", "User A");
        assertThat(names).containsEntry("id-b", "User B");
    }

    @Test
    void resolveDelimitedDisplay_joinsMultipleNames() {
        User u1 = User.builder().id("id-a").username("a").fullName("User A").build();
        User u2 = User.builder().id("id-b").username("b").fullName("User B").build();
        User u3 = User.builder().id("id-c").username("c").fullName("User C").build();
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(u1, u2, u3));

        Map<String, String> cache = new HashMap<>();
        String display = resolver.resolveDelimitedDisplay("id-a,id-b,id-c", cache);

        assertThat(display).isEqualTo("User A, User B, User C");
    }

    @Test
    void resolveCurrentAssigneeDisplay_usesCandidatePool() {
        User u1 = User.builder().id("id-a").fullName("User A").build();
        User u2 = User.builder().id("id-b").fullName("User B").build();
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(u1, u2));

        Map<String, String> cache = resolver.resolveBatch(List.of("id-a", "id-b"));
        String display = resolver.resolveCurrentAssigneeDisplay(null, "id-a,id-b", cache);

        assertThat(display).isEqualTo("User A, User B");
    }

    @Test
    void displayNameForUser_prefersFullNameOverDisplayName() {
        User user = User.builder()
                .username("u1")
                .fullName("Full")
                .displayName("Display")
                .build();

        assertThat(UserDisplayNameResolver.displayNameForUser(user)).isEqualTo("Full");
    }
}
