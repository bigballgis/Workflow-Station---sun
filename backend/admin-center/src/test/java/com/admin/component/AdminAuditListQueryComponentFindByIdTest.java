package com.admin.component;

import com.admin.entity.AuditLog;
import com.admin.enums.AuditAction;
import com.admin.repository.AuditLogRepository;
import com.admin.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditListQueryComponentFindByIdTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private UserRepository userRepository;

    private AdminAuditListQueryComponent component;

    @BeforeEach
    void setUp() {
        component = new AdminAuditListQueryComponent(jdbcTemplate, auditLogRepository, userRepository);
    }

    @Test
    void findByIdReturnsEmptyWhenMissing() {
        when(auditLogRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(component.findById("missing")).isEmpty();
    }

    @Test
    void findByIdReturnsEmptyWhenBlank() {
        assertThat(component.findById("  ")).isEmpty();
        assertThat(component.findById(null)).isEmpty();
    }

    @Test
    void findByIdReturnsTheLog() {
        AuditLog log = AuditLog.builder()
                .id("log-1")
                .action(AuditAction.UPDATE)
                .resourceType("USER")
                .userId("u1")
                .userName("alice")
                .success(true)
                .oldValue("{\"name\":\"a\"}")
                .newValue("{\"name\":\"b\"}")
                .build();
        when(auditLogRepository.findById("log-1")).thenReturn(Optional.of(log));

        Optional<AuditLog> found = component.findById("log-1");

        assertThat(found).isPresent();
        assertThat(found.get().getOldValue()).isEqualTo("{\"name\":\"a\"}");
        assertThat(found.get().getNewValue()).isEqualTo("{\"name\":\"b\"}");
    }
}
