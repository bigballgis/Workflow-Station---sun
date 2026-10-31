package com.workflow.assignment;

import com.platform.common.assignment.*;
import com.platform.common.dto.UserPrincipal;
import com.workflow.client.AdminCenterClient;
import com.workflow.dto.request.StartProcessRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssignmentRootQualificationTest {
    final AssignmentIdentityClient identities = mock(AssignmentIdentityClient.class);
    final AdminCenterClient admin = mock(AdminCenterClient.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final AssignmentRootQualification qualification = new AssignmentRootQualification(identities, admin, jdbc);
    final Instant now = Instant.parse("2026-10-27T00:00:00Z");
    final String serviceToken = java.util.UUID.randomUUID().toString();

    @BeforeEach void principal() {
        var principal = UserPrincipal.builder().userId("actor").activeBusinessUnitId("workspace-bu").build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(7L);
        when(jdbc.queryForObject(eq("SELECT clock_timestamp()"), org.mockito.ArgumentMatchers.<RowMapper<Instant>>any())).thenReturn(now);
        when(identities.resolve(anyList())).thenReturn(new AssignmentIdentitySnapshot(7,
                Map.of("actor", new AssignmentIdentitySnapshot.User("actor", "display-name", true)), Set.of()));
        when(admin.getBusinessUnitCodeById("workspace-bu")).thenReturn("FROZEN-BU");
        ReflectionTestUtils.setField(qualification, "serviceToken", serviceToken);
    }

    @Test void aRoleExpiryCapsTheFinalCheckWithoutAnyQualificationRowWrite() {
        when(identities.resolve(anyList())).thenReturn(new AssignmentIdentitySnapshot(7,
                Map.of("actor",new AssignmentIdentitySnapshot.User("actor","actor",true)),Set.of(),
                new AssignmentIdentitySnapshot.TimeWindow(null,now.plusSeconds(1),now)));
        when(jdbc.queryForObject(eq("SELECT clock_timestamp()"),org.mockito.ArgumentMatchers.<RowMapper<Instant>>any()))
                .thenReturn(now,now,now.plusSeconds(2));
        var prepared=qualification.prepare(new StartProcessRequest());
        assertEquals(now.plusSeconds(1),prepared.validUntil());
        var failure=assertThrows(AssignmentFailure.class,()->qualification.check(prepared));
        assertEquals("ASSIGNMENT_STATE_CHANGED",failure.getDetails().get("reason"));
    }
    @AfterEach void clear() {
        SecurityContextHolder.clearContext();
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    }

    @Test void publicRequestCannotChooseAnotherRootInitiator() {
        var request = request(); request.setStartUserId("someone-else");
        assertThrows(AssignmentFailure.class, () -> qualification.prepare(request));
        verifyNoInteractions(identities, admin);
    }

    @Test void originalBuComesFromTheAuthenticatedWorkspaceInsteadOfClientVariables() {
        var prepared = qualification.prepare(request());
        assertEquals("actor", prepared.actor());
        assertEquals("workspace-bu", prepared.activeBu());
        assertEquals("FROZEN-BU", prepared.startBuCode());
        verify(admin, never()).getBusinessUnitCodeById("client-selected-bu");
    }

    @Test void aRevokedActorCannotCreateAnotherProtectedRound() {
        when(identities.resolve(anyList())).thenReturn(new AssignmentIdentitySnapshot(7,
                Map.of("actor", new AssignmentIdentitySnapshot.User("actor", "display-name", false)), Set.of()));
        assertThrows(AssignmentFailure.class, () -> qualification.prepare(request()));
    }

    @Test void finalCheckRejectsARevocationCommittedAfterPreparation() {
        var prepared = qualification.prepare(request());
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(8L);
        assertThrows(AssignmentFailure.class, () -> qualification.check(prepared));
    }

    @Test void anonymousCallerCannotConvertPostedContextIntoATrustedStart() {
        SecurityContextHolder.clearContext();
        assertThrows(com.platform.common.exception.BusinessException.class, () -> qualification.prepare(request()));
        verifyNoInteractions(identities, admin);
    }

    @Test void explicitPortalServiceAuthenticationSupportsAServerInitiatedStart() {
        SecurityContextHolder.clearContext();
        var servlet = new org.springframework.mock.web.MockHttpServletRequest();
        servlet.addHeader("X-Internal-Token", serviceToken);
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(servlet));
        when(admin.getBusinessUnitCodeById("client-selected-bu")).thenReturn("SERVER-BU");
        var prepared = qualification.prepare(request());
        assertEquals("SERVER-BU", prepared.startBuCode());
    }

    private StartProcessRequest request() {
        var request = new StartProcessRequest(); request.setStartUserId("actor");
        request.setVariables(Map.of("activeBusinessUnitId", "client-selected-bu", "initiator", "someone-else"));
        return request;
    }
}
