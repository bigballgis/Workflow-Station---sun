package com.portal.component;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.common.dto.UserPrincipal;
import com.portal.component.MainTableViewAccessResolver.AccessRule;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * View visibility follows the signed-in user's current workspace pair (BU + Role) only, with no
 * role bypass (platform access model §7.2, D-05).
 */
class MainTableViewAccessResolverTest {

    private final MainTableViewAccessResolver resolver = new MainTableViewAccessResolver();

    @AfterEach
    void clearSession() {
        SecurityContextHolder.clearContext();
    }

    private static void signIn(String userId, String activeBuId, String activeRoleId) {
        signIn(UserPrincipal.builder().userId(userId).username(userId)
                .activeBusinessUnitId(activeBuId).activeRoleId(activeRoleId).build());
    }

    private static void signInSystemAdministrator(String userId, String activeBuId, String activeRoleId) {
        signIn(UserPrincipal.builder().userId(userId).username(userId)
                .roles(List.of("SYS_ADMIN")).superAdmin(true)
                .activeBusinessUnitId(activeBuId).activeRoleId(activeRoleId).build());
    }

    private static void signIn(UserPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private static List<AccessRule> rules(String bu, String role) {
        return List.of(new AccessRule("BUSINESS_UNIT", bu), new AccessRule("ROLE", role));
    }

    /** D-05: an empty access config means nobody sees the view — a System Administrator included. */
    @Test
    void emptyRulesHiddenFromEveryoneIncludingSystemAdministrator() {
        signInSystemAdministrator("admin", "bu-1", "role-1");

        assertThat(resolver.canUserSeeView("admin", List.of())).isFalse();
        assertThat(resolver.canUserSeeView("admin", null)).isFalse();
    }

    /** D-05: a System Administrator whose current pair is not listed is refused like anyone. */
    @Test
    void systemAdministratorWithoutMatchingPairIsRefused() {
        signInSystemAdministrator("admin", "bu-1", "role-1");

        assertThat(resolver.canUserSeeView("admin", rules("bu-other", "role-other"))).isFalse();
    }

    @Test
    void currentPairListedInBothDimensionsSeesTheView() {
        signIn("user-1", "bu-1", "role-1");

        assertThat(resolver.canUserSeeView("user-1", rules("bu-1", "role-1"))).isTrue();
        assertThat(resolver.canUserSeeView("user-1", rules("bu-1", "role-missing"))).isFalse();
        assertThat(resolver.canUserSeeView("user-1", rules("bu-missing", "role-1"))).isFalse();
    }

    /**
     * §7.2: a user holding BU1+R1 and BU2+R2 never matches a rule BU1+R2 — the BU of one workspace
     * and the role of another are not combined into a pair the user does not hold.
     */
    @Test
    void doesNotCombineBuAndRoleFromDifferentWorkspaces() {
        List<AccessRule> bu1WithR2 = rules("bu-1", "role-2");

        signIn("user-2", "bu-1", "role-1");
        assertThat(resolver.canUserSeeView("user-2", bu1WithR2)).isFalse();

        signIn("user-2", "bu-2", "role-2");
        assertThat(resolver.canUserSeeView("user-2", bu1WithR2)).isFalse();
    }

    @Test
    void anyListedBuAndAnyListedRoleMatchTheCurrentPair() {
        signIn("user-2", "bu-2", "role-2");

        assertThat(resolver.canUserSeeView("user-2", List.of(
                new AccessRule("BUSINESS_UNIT", "bu-1"),
                new AccessRule("BUSINESS_UNIT", "bu-2"),
                new AccessRule("ROLE", "role-2"),
                new AccessRule("ROLE", "role-3")))).isTrue();
    }

    @Test
    void partialConfigHiddenFromEveryone() {
        signIn("user-3", "bu-a", "role-a");

        assertThat(resolver.canUserSeeView("user-3", List.of(new AccessRule("BUSINESS_UNIT", "bu-a")))).isFalse();
        assertThat(resolver.canUserSeeView("user-3", List.of(new AccessRule("ROLE", "role-a")))).isFalse();
    }

    /** Permission self-service sessions carry no workspace pair, so no view is visible. */
    @Test
    void sessionWithoutWorkspacePairSeesNothing() {
        signIn("user-4", null, null);

        assertThat(resolver.canUserSeeView("user-4", rules("bu-1", "role-1"))).isFalse();
    }

    /** The session's pair is the signed-in user's own; it is never applied to another user. */
    @Test
    void anotherUsersIdIsNotJudgedByTheSignedInPair() {
        signIn("user-1", "bu-1", "role-1");

        assertThat(resolver.canUserSeeView("someone-else", rules("bu-1", "role-1"))).isFalse();
    }

    @Test
    void noSessionSeesNothing() {
        assertThat(resolver.canUserSeeView("user-1", rules("bu-1", "role-1"))).isFalse();
    }
}
