package com.platform.security.service;

import com.platform.security.service.LostEntryTaskPolicy.PoolKind;
import com.platform.security.service.LostEntryTaskPolicy.TaskHolding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Tasks whose holder lost Portal entry or left (platform access model §7.3, D-07, D-17, D-39, D-42). */
class LostEntryTaskPolicyTest {

    private static final Set<String> NOBODY = Set.of();

    @Nested
    class TakeOver {

        @Test
        void aPoolMemberWithEntryMayActOnceTheHolderLostIt() {
            TaskHolding task = new TaskHolding("a", PoolKind.MEMBERSHIP, Set.of("a", "b", "c"));

            assertThat(LostEntryTaskPolicy.mayTakeOver("b", task, Set.of("b", "c"))).isTrue();
            assertThat(LostEntryTaskPolicy.takeoverTargets(task, Set.of("b", "c"))).containsExactlyInAnyOrder("b", "c");
        }

        @Test
        void nobodyTakesOverWhileTheHolderStillHasEntry() {
            TaskHolding task = new TaskHolding("a", PoolKind.PEOPLE, Set.of("a", "b"));

            assertThat(LostEntryTaskPolicy.mayTakeOver("b", task, Set.of("a", "b"))).isFalse();
        }

        @Test
        void theActorNeedsEntryAndPoolMembership() {
            TaskHolding task = new TaskHolding("a", PoolKind.MEMBERSHIP, Set.of("a", "b"));

            assertThat(LostEntryTaskPolicy.mayTakeOver("b", task, NOBODY)).isFalse();
            assertThat(LostEntryTaskPolicy.mayTakeOver("outsider", task, Set.of("outsider"))).isFalse();
        }

        @Test
        void aTaskAssignedToOnePersonHasNoPoolToTakeItOver() {
            TaskHolding task = new TaskHolding("a", PoolKind.NONE, Set.of());

            assertThat(LostEntryTaskPolicy.mayTakeOver("b", task, Set.of("b"))).isFalse();
        }

        @Test
        void anUnclaimedTaskIsClaimedNotTakenOver() {
            TaskHolding task = new TaskHolding(null, PoolKind.MEMBERSHIP, Set.of("b"));

            assertThat(LostEntryTaskPolicy.mayTakeOver("b", task, Set.of("b"))).isFalse();
        }
    }

    @Nested
    class Departure {

        @Test
        void aDirectTaskOfAHolderWhoLeftGoesToTheirManager() {
            TaskHolding task = new TaskHolding("a", PoolKind.NONE, Set.of());

            assertThat(LostEntryTaskPolicy.departedOwners(task, NOBODY, Set.of("a"))).containsExactly("a");
        }

        @Test
        void aHolderWhoOnlyLostEntryKeepsTheTask() {
            TaskHolding task = new TaskHolding("a", PoolKind.NONE, Set.of());

            assertThat(LostEntryTaskPolicy.departedOwners(task, NOBODY, NOBODY)).isEmpty();
        }

        @Test
        void aHeldPoolTaskGoesToTheManagerOnlyWhenNobodyElseInThePoolHasEntry() {
            TaskHolding task = new TaskHolding("a", PoolKind.MEMBERSHIP, Set.of("a", "b"));

            assertThat(LostEntryTaskPolicy.departedOwners(task, Set.of("b"), Set.of("a"))).isEmpty();
            assertThat(LostEntryTaskPolicy.departedOwners(task, NOBODY, Set.of("a"))).containsExactly("a");
            assertThat(LostEntryTaskPolicy.departedOwners(task, NOBODY, NOBODY)).isEmpty();
        }

        @Test
        void anUnclaimedListOfNamedPeopleGoesToAManagerOnceAllOfThemLeftInTheirOrder() {
            TaskHolding task = new TaskHolding(null, PoolKind.PEOPLE, new LinkedHashSet<>(List.of("b", "a")));

            assertThat(LostEntryTaskPolicy.departedOwners(task, NOBODY, Set.of("a", "b"))).containsExactly("b", "a");
            assertThat(LostEntryTaskPolicy.departedOwners(task, NOBODY, Set.of("a"))).isEmpty();
        }

        @Test
        void anUnclaimedMembershipPoolWaitsForANewMember() {
            TaskHolding task = new TaskHolding(null, PoolKind.MEMBERSHIP, Set.of("a"));

            assertThat(LostEntryTaskPolicy.departedOwners(task, NOBODY, Set.of("a"))).isEmpty();
        }
    }

    @Nested
    class Directory {

        private JdbcTemplate jdbc;
        private LostEntryTaskPolicy policy;

        @BeforeEach
        void createSchema() {
            jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
            jdbc.execute("CREATE TABLE sys_users (id VARCHAR(64) PRIMARY KEY, status VARCHAR(20), deleted BOOLEAN, "
                    + "entity_manager_id VARCHAR(64), function_manager_id VARCHAR(64))");
            jdbc.execute("CREATE TABLE sys_virtual_groups (id VARCHAR(64) PRIMARY KEY, type VARCHAR(20), "
                    + "status VARCHAR(20))");
            jdbc.execute("CREATE TABLE sys_virtual_group_members (group_id VARCHAR(64), user_id VARCHAR(64), "
                    + "list_type VARCHAR(20))");
            jdbc.update("INSERT INTO sys_virtual_groups VALUES ('g', 'CUSTOM', 'ACTIVE')");
            policy = new LostEntryTaskPolicy(new PortalEntryService(jdbc), jdbc);
        }

        private void user(String id, String status, boolean deleted, String entityManager, String functionManager,
                          boolean entry) {
            jdbc.update("INSERT INTO sys_users VALUES (?, ?, ?, ?, ?)", id, status, deleted, entityManager,
                    functionManager);
            if (entry) {
                jdbc.update("INSERT INTO sys_virtual_group_members VALUES ('g', ?, 'MEMBER')", id);
            }
        }

        @Test
        void inactiveOrDeletedAccountsLeftButLockedOnesDidNot() {
            user("inactive", "INACTIVE", false, null, null, false);
            user("deleted", "INACTIVE", true, null, null, false);
            user("deleted-flag-only", "ACTIVE", true, null, null, false);
            user("locked", "LOCKED", false, null, null, false);
            user("active", "ACTIVE", false, null, null, false);

            assertThat(policy.departedAmong(List.of("inactive", "deleted", "deleted-flag-only", "locked", "active",
                    "unknown"))).containsExactlyInAnyOrder("inactive", "deleted", "deleted-flag-only");
        }

        @Test
        void theEntityManagerComesFirstWhenAvailable() {
            user("em", "ACTIVE", false, null, null, true);
            user("fm", "ACTIVE", false, null, null, true);
            user("a", "INACTIVE", false, "em", "fm", false);

            assertThat(policy.managerFor(List.of("a"))).contains("em");
        }

        @Test
        void theFunctionManagerStandsInForAnEntityManagerWithoutEntryOrAccount() {
            user("em-no-entry", "ACTIVE", false, null, null, false);
            user("em-locked", "LOCKED", false, null, null, true);
            user("fm", "ACTIVE", false, null, null, true);
            user("a", "INACTIVE", false, "em-no-entry", "fm", false);
            user("b", "INACTIVE", false, "em-locked", "fm", false);
            user("c", "INACTIVE", false, null, "fm", false);

            assertThat(policy.managerFor(List.of("a"))).contains("fm");
            assertThat(policy.managerFor(List.of("b"))).contains("fm");
            assertThat(policy.managerFor(List.of("c"))).contains("fm");
        }

        @Test
        void theFirstOwnerWithAnAvailableManagerDecides() {
            user("m1", "ACTIVE", false, null, null, false);
            user("m2", "ACTIVE", false, null, null, true);
            user("m3", "ACTIVE", false, null, null, true);
            user("a", "INACTIVE", false, "m1", null, false);
            user("b", "INACTIVE", false, "m2", null, false);
            user("c", "INACTIVE", false, "m3", null, false);

            assertThat(policy.managerFor(List.of("a", "b", "c"))).contains("m2");
            assertThat(policy.managerFor(List.of("c", "b"))).contains("m3");
        }

        @Test
        void nobodyReceivesTheTaskWhenNoManagerIsAvailable() {
            user("m", "INACTIVE", false, null, null, true);
            user("a", "INACTIVE", false, "m", "m", false);
            user("b", "INACTIVE", false, null, null, false);

            assertThat(policy.managerFor(List.of("a", "b", "unknown"))).isEmpty();
            assertThat(policy.managerFor(List.of())).isEmpty();
        }
    }
}
