package com.admin.audit;

import com.admin.entity.AuditLog;
import com.admin.repository.UserRepository;
import com.platform.security.entity.User;
import com.platform.security.util.SecurityContextUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AuditActorResolverTest {

    @AfterEach
    void clearSecurityContext() {
        try (MockedStatic<SecurityContextUtils> ignored = mockStatic(SecurityContextUtils.class)) {
            // no-op: static mock scope ends here
        }
    }

    @Test
    void normalizeOperator_prefersDbUsernameByUserId() {
        UserRepository userRepository = mock(UserRepository.class);
        User user = new User();
        user.setId("user-abc");
        user.setUsername("alice");
        when(userRepository.findById("user-abc")).thenReturn(Optional.of(user));

        AuditActorResolver.OperatorIdentity op = AuditActorResolver.normalizeOperator(
                "user-abc", "wrong-display-name", userRepository);

        assertThat(op.userId()).isEqualTo("user-abc");
        assertThat(op.userName()).isEqualTo("alice");
    }

    @Test
    void normalizeOperator_fallsBackToSystemWhenUnauthenticated() {
        UserRepository userRepository = mock(UserRepository.class);

        try (MockedStatic<SecurityContextUtils> security = mockStatic(SecurityContextUtils.class)) {
            security.when(SecurityContextUtils::getCurrentUserId).thenReturn(Optional.empty());
            security.when(SecurityContextUtils::getCurrentUsername).thenReturn(Optional.empty());

            AuditActorResolver.OperatorIdentity op = AuditActorResolver.normalizeOperator(
                    null, null, userRepository);

            assertThat(op.userId()).isEqualTo("system");
            assertThat(op.userName()).isEqualTo("system");
        }
    }

    @Test
    void resolveAuthOperator_looksUpUserByLoginUsername() {
        UserRepository userRepository = mock(UserRepository.class);
        User user = new User();
        user.setId("user-login");
        user.setUsername("bob");
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));

        AuditActorResolver.OperatorIdentity op = AuditActorResolver.resolveAuthOperator("bob", userRepository);

        assertThat(op.userId()).isEqualTo("user-login");
        assertThat(op.userName()).isEqualTo("bob");
    }

    @Test
    void operatorDisplayName_prefersUsernameOverUnknown() {
        AuditLog log = AuditLog.builder()
                .userId("user-1")
                .userName("developer")
                .build();
        assertThat(AuditActorResolver.operatorDisplayName(log)).isEqualTo("developer");
    }

    @Test
    void operatorDisplayName_fallsBackToUserIdWhenNameUnknown() {
        AuditLog log = AuditLog.builder()
                .userId("user-1")
                .userName("unknown")
                .build();
        assertThat(AuditActorResolver.operatorDisplayName(log)).isEqualTo("user-1");
    }
}
