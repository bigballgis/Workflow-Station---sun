package com.developer.security;

import com.developer.repository.FunctionUnitDevGroupAssignmentRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.RoleRepository;
import com.developer.repository.VirtualGroupMembershipDao;
import com.platform.common.dto.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 二维模型（团队 scope × 能力角色）鉴权矩阵单测。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FunctionUnitWorkspaceAccessServiceTest {

    private static final String USER_ID = "u-1";
    private static final Long FU_ID = 100L;

    @Mock private RoleRepository roleRepository;
    @Mock private FunctionUnitRepository functionUnitRepository;
    @Mock private FunctionUnitDevGroupAssignmentRepository devGroupAssignmentRepository;
    @Mock private VirtualGroupMembershipDao virtualGroupMembershipDao;

    private FunctionUnitWorkspaceAccessService service;

    @BeforeEach
    void setUp() {
        service = new FunctionUnitWorkspaceAccessService(
                roleRepository, functionUnitRepository, devGroupAssignmentRepository, virtualGroupMembershipDao);
        when(functionUnitRepository.existsById(FU_ID)).thenReturn(true);
        authenticateAs(USER_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String userId) {
        UserPrincipal principal = UserPrincipal.builder().userId(userId).username(userId).build();
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, "n/a", Collections.emptyList());
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    /** FU 已分配到用户所属团队组（inScope）。 */
    private void inScope() {
        when(virtualGroupMembershipDao.findVirtualGroupIdsByUserId(USER_ID)).thenReturn(List.of("vg-team-a"));
        when(devGroupAssignmentRepository.findDistinctFunctionUnitIdsByVirtualGroupIdIn(List.of("vg-team-a")))
                .thenReturn(List.of(FU_ID));
    }

    @Test
    void anonymous_deniedAndSeesNothing() {
        SecurityContextHolder.clearContext();
        assertFalse(service.canAccess(FU_ID, WorkspaceAccessAction.VIEW));
        assertEquals(Set.of(), service.visibleFunctionUnitIds());
    }

    @Test
    void admin_hasGlobalAccess() {
        when(roleRepository.userHasActiveAdminTypeRole(USER_ID)).thenReturn(true);
        for (WorkspaceAccessAction a : WorkspaceAccessAction.values()) {
            assertTrue(service.canAccess(FU_ID, a), "admin should pass " + a);
        }
        assertNull(service.visibleFunctionUnitIds(), "admin sees all (null)");
    }

    @Test
    void techLead_hasGlobalAccessWithoutTeamMembership() {
        when(roleRepository.hasRoleByUserId(USER_ID, "TECH_LEAD")).thenReturn(true);
        for (WorkspaceAccessAction a : WorkspaceAccessAction.values()) {
            assertTrue(service.canAccess(FU_ID, a), "tech lead should pass " + a);
        }
        assertNull(service.visibleFunctionUnitIds());
    }

    @Test
    void teamLeadInScope_canDoEverythingWithinTeam() {
        inScope();
        when(roleRepository.hasRoleByUserId(USER_ID, "TEAM_LEAD")).thenReturn(true);
        assertTrue(service.canAccess(FU_ID, WorkspaceAccessAction.VIEW));
        assertTrue(service.canAccess(FU_ID, WorkspaceAccessAction.MODIFY));
        assertTrue(service.canAccess(FU_ID, WorkspaceAccessAction.DELETE));
        assertTrue(service.canAccess(FU_ID, WorkspaceAccessAction.ASSIGN_DEV_GROUPS));
        assertEquals(Set.of(FU_ID), service.visibleFunctionUnitIds());
    }

    @Test
    void developerInScope_canViewAndModifyButNotDeleteOrAssign() {
        inScope();
        when(roleRepository.hasRoleByUserId(USER_ID, "DEVELOPER")).thenReturn(true);
        assertTrue(service.canAccess(FU_ID, WorkspaceAccessAction.VIEW));
        assertTrue(service.canAccess(FU_ID, WorkspaceAccessAction.MODIFY));
        assertFalse(service.canAccess(FU_ID, WorkspaceAccessAction.DELETE));
        assertFalse(service.canAccess(FU_ID, WorkspaceAccessAction.ASSIGN_DEV_GROUPS));
    }

    @Test
    void viewerOnlyInScope_isReadOnly() {
        inScope();
        // no TEAM_LEAD / DEVELOPER capability role
        assertTrue(service.canAccess(FU_ID, WorkspaceAccessAction.VIEW));
        assertFalse(service.canAccess(FU_ID, WorkspaceAccessAction.MODIFY));
        assertFalse(service.canAccess(FU_ID, WorkspaceAccessAction.DELETE));
        assertFalse(service.canAccess(FU_ID, WorkspaceAccessAction.ASSIGN_DEV_GROUPS));
    }

    @Test
    void developerOutOfScope_deniedEvenView() {
        when(virtualGroupMembershipDao.findVirtualGroupIdsByUserId(USER_ID)).thenReturn(List.of("vg-team-b"));
        when(devGroupAssignmentRepository.findDistinctFunctionUnitIdsByVirtualGroupIdIn(List.of("vg-team-b")))
                .thenReturn(Collections.emptyList());
        when(roleRepository.hasRoleByUserId(USER_ID, "DEVELOPER")).thenReturn(true);
        assertFalse(service.canAccess(FU_ID, WorkspaceAccessAction.VIEW));
        assertFalse(service.canAccess(FU_ID, WorkspaceAccessAction.MODIFY));
    }

    @Test
    void noTeamMembership_seesNothing() {
        when(virtualGroupMembershipDao.findVirtualGroupIdsByUserId(USER_ID)).thenReturn(Collections.emptyList());
        assertEquals(Set.of(), service.visibleFunctionUnitIds());
        assertFalse(service.canAccess(FU_ID, WorkspaceAccessAction.VIEW));
    }

    @Test
    void missingFunctionUnit_denied() {
        when(functionUnitRepository.existsById(FU_ID)).thenReturn(false);
        assertFalse(service.canAccess(FU_ID, WorkspaceAccessAction.VIEW));
    }
}
