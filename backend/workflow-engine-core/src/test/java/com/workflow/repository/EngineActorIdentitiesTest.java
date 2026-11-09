package com.workflow.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Engine actor value to user id, run against real SQL: id first, then username, otherwise as it is. */
class EngineActorIdentitiesTest {

    private EmbeddedDatabase database;
    private EngineActorIdentities identities;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).generateUniqueName(true).build();
        JdbcTemplate jdbc = new JdbcTemplate(database);
        jdbc.execute("CREATE TABLE sys_users (id VARCHAR(64) PRIMARY KEY, username VARCHAR(100), status VARCHAR(20),"
                + " deleted BOOLEAN)");
        jdbc.update("INSERT INTO sys_users VALUES ('u-lina', 'e2e_lina', 'ACTIVE', false)");
        jdbc.update("INSERT INTO sys_users VALUES ('u-left', 'left_user', 'INACTIVE', true)");
        // A username that is also someone else's id: the id wins.
        jdbc.update("INSERT INTO sys_users VALUES ('u-other', 'u-lina', 'ACTIVE', false)");
        identities = new EngineActorIdentities(jdbc);
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void anIdStaysAndAUsernameBecomesItsUsersId() {
        assertThat(identities.userIds(List.of("u-lina", "e2e_lina", "left_user")))
                .containsExactly(java.util.Map.entry("u-lina", "u-lina"), java.util.Map.entry("e2e_lina", "u-lina"),
                        java.util.Map.entry("left_user", "u-left"));
    }

    @Test
    void aValueNamingNobodyKnownStaysAsItIs() {
        assertThat(identities.userIds(List.of("ghost", " u-lina "))).containsEntry("ghost", "ghost")
                .containsEntry("u-lina", "u-lina").hasSize(2);
        assertThat(identities.userIds(List.of())).isEmpty();
    }

    @Test
    void usernameMatchingIsExact() {
        assertThat(identities.userIds(List.of("E2E_LINA"))).containsEntry("E2E_LINA", "E2E_LINA");
    }

    @Test
    void actorValuesAreTheIdsAndTheUsernamesOfDeletedUsersToo() {
        assertThat(identities.actorValues(List.of("u-lina", "u-left", "ghost")))
                .isEqualTo(Set.of("u-lina", "e2e_lina", "u-left", "left_user", "ghost"));
    }
}
