package com.admin.component;

import com.admin.dto.response.RecentActivity;
import com.admin.entity.AuditLog;
import com.admin.enums.AuditAction;
import com.admin.repository.AuditLogRepository;
import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.RoleRepository;
import com.admin.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The dashboard's recent activity is read by every Admin user (SHARED_READ): change details are masked (WP9 review). */
class DashboardComponentRecentActivityTest {

    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final DashboardComponent dashboard = new DashboardComponent(mock(UserRepository.class),
            mock(BusinessUnitRepository.class), mock(RoleRepository.class), auditLogRepository, new ObjectMapper());

    @Test
    void aSecretInAChangeDetailIsMaskedAndPlainTextIsKept() {
        AuditLog stored = AuditLog.builder().id("a1").action(AuditAction.UPDATE).resourceType("USER")
                .changeDetails("{\"username\":\"lina\",\"passwordHash\":\"$2a$10$abc\",\"nested\":{\"token\":\"t-1\"}}")
                .build();
        AuditLog plain = AuditLog.builder().id("a2").action(AuditAction.UPDATE).resourceType("USER")
                .changeDetails("Status changed to LOCKED").build();
        when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(stored, plain)));

        List<RecentActivity> activities = dashboard.getRecentActivities(10);

        assertThat(activities.get(0).getDescription()).contains("\"username\":\"lina\"").contains("***")
                .doesNotContain("$2a$10$abc").doesNotContain("t-1");
        assertThat(activities.get(1).getDescription()).isEqualTo("Status changed to LOCKED");
        assertThat(stored.getChangeDetails()).contains("$2a$10$abc");
    }
}
