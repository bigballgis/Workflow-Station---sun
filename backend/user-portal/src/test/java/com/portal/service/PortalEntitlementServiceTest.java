package com.portal.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PortalEntitlementService")
class PortalEntitlementServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private PortalEntitlementService service;

    @BeforeEach
    void setUp() {
        service = new PortalEntitlementService(jdbcTemplate);
    }

    @Test
    void blankUserId_isNotEligible() {
        assertThat(service.hasEligibleVirtualGroupMembership(null)).isFalse();
        assertThat(service.hasEligibleVirtualGroupMembership("  ")).isFalse();
    }

    @Test
    void membershipCountPositive_isEligible() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("user-1"))).thenReturn(2);
        assertThat(service.hasEligibleVirtualGroupMembership("user-1")).isTrue();
    }

    @Test
    void membershipCountZero_isNotEligible() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("user-2"))).thenReturn(0);
        assertThat(service.hasEligibleVirtualGroupMembership("user-2")).isFalse();
    }

    @Test
    void jdbcFailure_failsClosed() {
        when(jdbcTemplate.queryForObject(
                ArgumentMatchers.anyString(),
                eq(Integer.class),
                eq("user-3"))).thenThrow(new RuntimeException("db down"));
        assertThat(service.hasEligibleVirtualGroupMembership("user-3")).isFalse();
    }
}
