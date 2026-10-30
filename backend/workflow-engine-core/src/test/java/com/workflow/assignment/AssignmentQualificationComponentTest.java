package com.workflow.assignment;

import com.platform.common.assignment.*;
import com.workflow.assignment.impl.AssignmentQualificationComponentImpl;
import com.workflow.client.AdminCenterClient;
import com.workflow.service.TaskAssigneeResolver;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssignmentQualificationComponentTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final AssignmentIdentityClient identities = mock(AssignmentIdentityClient.class);
    final AssignmentSourceResolver sources = mock(AssignmentSourceResolver.class);
    final AssignmentDefinitionPolicy definitions = mock(AssignmentDefinitionPolicy.class);
    final AdminCenterClient admin = mock(AdminCenterClient.class);
    final Task task = mock(Task.class);
    final AssignmentQualificationComponent component = new AssignmentQualificationComponentImpl(jdbc, identities, sources, definitions, admin);
    final Instant now = Instant.parse("2026-10-27T00:00:00Z");

    @BeforeEach void configure() {
        when(admin.isEligibleRole(anyString(), anyString())).thenReturn(true);
        when(task.getProcessDefinitionId()).thenReturn("frozen-definition");
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(7L);
        when(jdbc.queryForObject(eq("SELECT clock_timestamp()"), org.mockito.ArgumentMatchers.<RowMapper<Instant>>any())).thenReturn(now);
        when(sources.resolve(eq(task), any(), any())).thenReturn(new AssignmentSourceResolver.Resolution(
                TaskAssigneeResolver.ResolveResult.builder().candidateUsers(List.of("a")).requiresClaim(true).build(), "NODE"));
        when(identities.resolve(anyList())).thenAnswer(call -> {
            List<AssignmentIdentitySnapshot.Reference> requested = call.getArgument(0);
            var users = new java.util.LinkedHashMap<String,AssignmentIdentitySnapshot.User>();
            for (var reference : requested) users.put(reference.value(), new AssignmentIdentitySnapshot.User(reference.value(), reference.value(), true));
            return new AssignmentIdentitySnapshot(7, users, Set.of());
        });
    }

    @Test void healthyPrimaryDoesNotDependOnAnUnusedBackupSource() {
        var prepared = component.prepare(request(Set.of(), false));
        assertEquals(List.of("a"), prepared.primary());
        assertTrue(prepared.backup().isEmpty());
        assertEquals(now.plusSeconds(5), prepared.validUntil());
        verifyNoInteractions(admin);
    }

    @Test void onlyACompletelyExcludedNonemptyPrimaryActivatesBackup() {
        when(admin.getUsersByBusinessUnitAndRole("CASE", "REVIEW")).thenReturn(List.of("b"));
        var prepared = component.prepare(request(Set.of("a"), false));
        assertEquals(List.of("a"), prepared.primary());
        assertEquals(List.of("b"), prepared.backup());
        verify(admin).getUsersByBusinessUnitAndRole("CASE", "REVIEW");
    }

    @Test void originallyEmptyPrimaryDoesNotTriggerBackup() {
        when(sources.resolve(eq(task), any(), any())).thenReturn(new AssignmentSourceResolver.Resolution(
                TaskAssigneeResolver.ResolveResult.builder().candidateUsers(List.of()).requiresClaim(true).build(), "NODE"));
        assertTrue(component.prepare(request(Set.of(), false)).backup().isEmpty());
        verifyNoInteractions(admin);
        verify(identities).resolve(List.of(new AssignmentIdentitySnapshot.Reference("initiator",AssignmentIdentitySnapshot.Kind.ID)));
    }

    @Test void anExistingBackupHolderIsRevalidatedWhenPrimaryRecovers() {
        when(admin.getUsersByBusinessUnitAndRole("CASE", "REVIEW")).thenReturn(List.of("b"));
        var prepared = component.prepare(request(Set.of(), true));
        assertEquals(List.of("a"), prepared.primary());
        assertEquals(List.of("b"), prepared.backup());
    }

    @Test void sourceFailureIsNotConvertedIntoAnEmptyPrimaryOrABackupSuccess() {
        when(sources.resolve(eq(task), any(), any())).thenReturn(new AssignmentSourceResolver.Resolution(
                TaskAssigneeResolver.ResolveResult.builder().errorMessage("synthetic outage").infraFailure(true).build(), null));
        var failure = assertThrows(AssignmentFailure.class, () -> component.prepare(request(Set.of(), false)));
        assertEquals("ASSIGNMENT_DEPENDENCY_UNAVAILABLE", failure.getDetails().get("reason"));
        verifyNoInteractions(admin, identities);
    }

    @Test void qualificationChangesWhilePreparingInvalidateTheSnapshot() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(7L, 8L);
        var failure = assertThrows(AssignmentFailure.class, () -> component.prepare(request(Set.of(), false)));
        assertEquals("ASSIGNMENT_STATE_CHANGED", failure.getDetails().get("reason"));
    }

    @Test void inactiveFixedIdentityRemainsInTheOriginalSourceForLaterReactivation() {
        when(identities.resolve(anyList())).thenReturn(new AssignmentIdentitySnapshot(7,
                Map.of("a", new AssignmentIdentitySnapshot.User("canonical-a", "a", false)), Set.of()));
        var prepared = component.prepare(request(Set.of(), false));
        assertTrue(prepared.primary().isEmpty());
        assertEquals(List.of("canonical-a"), prepared.primaryIdentities());
        verifyNoInteractions(admin);
    }

    @Test void aBackupRoleOutsideTheSelectedBusinessUnitIsAConfigurationError() {
        when(admin.isEligibleRole("CASE", "REVIEW")).thenReturn(false);
        var failure = assertThrows(AssignmentFailure.class, () -> component.prepare(request(Set.of("a"), false)));
        assertEquals("ASSIGNMENT_POLICY_INVALID", failure.getDetails().get("reason"));
        verify(admin, never()).getUsersByBusinessUnitAndRole(anyString(), anyString());
    }

    @Test void stableMembershipKeepsTheProjectionWithoutExtendingTheShortWriteDecision() {
        when(identities.resolve(anyList())).thenReturn(new AssignmentIdentitySnapshot(7,
                Map.of("a",new AssignmentIdentitySnapshot.User("a","a",true)),Set.of(),new AssignmentIdentitySnapshot.TimeWindow(null,null,now)));
        var prepared=component.prepare(request(Set.of(),false));
        assertEquals(now.plusSeconds(5),prepared.validUntil());
        assertTrue(prepared.nextEvaluationAt().isAfter(now.plusSeconds(3600)));
    }
    @Test void aConfiguredMembershipBoundaryLimitsBothTheProjectionAndFinalCheck() {
        when(identities.resolve(anyList())).thenReturn(new AssignmentIdentitySnapshot(7,
                Map.of("a",new AssignmentIdentitySnapshot.User("a","a",true)),Set.of(),new AssignmentIdentitySnapshot.TimeWindow(null,now.plusSeconds(2),now)));
        var prepared=component.prepare(request(Set.of(),false));
        assertEquals(now.plusSeconds(2),prepared.validUntil()); assertEquals(prepared.validUntil(),prepared.nextEvaluationAt());
    }
    @Test void aBoundaryCrossedDuringSourcePreparationCannotAuthorizeTheStalePool() {
        when(identities.resolve(anyList())).thenReturn(new AssignmentIdentitySnapshot(7,
                Map.of("a",new AssignmentIdentitySnapshot.User("a","a",true)),Set.of(),new AssignmentIdentitySnapshot.TimeWindow(now.plusSeconds(1),null,now.plusSeconds(2))));
        var failure=assertThrows(AssignmentFailure.class,()->component.prepare(request(Set.of(),false)));
        assertEquals("ASSIGNMENT_STATE_CHANGED",failure.getDetails().get("reason"));
    }
    private AssignmentQualificationComponent.Request request(Set<String> excluded, boolean retainBackup) {
        var policy = AssignmentExclusionPolicyCodec.decode("""
                {"schemaVersion":1,"excludeInitiator":true,"excludePreviousHandlers":false,"excludeSelectedNodeHandlers":false,
                 "selectedNodeKeys":[],"backup":{"enabled":true,"mode":"BU_ROLE","buSource":"CASE_START_BU","roleCodes":["REVIEW"]}}
                """);
        return new AssignmentQualificationComponent.Request(task, AssignmentSourceSpec.fixedCanonical(List.of("a"), true), policy,
                new AssignmentTaskStateRepository.CaseContext("initiator", "CASE", 0, 7), List.of(), excluded, retainBackup);
    }
}
