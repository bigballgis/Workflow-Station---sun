package com.portal.component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubTableBatchUserEligibilityTest {
    private final EmbeddedDatabase database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
    private final JdbcTemplate jdbc = spy(new JdbcTemplate(database));
    SubTableBatchUserEligibilityTest() {
        jdbc.execute("CREATE TABLE sys_users(id VARCHAR(100) PRIMARY KEY, status VARCHAR(20), deleted BOOLEAN)");
        jdbc.update("INSERT INTO sys_users VALUES(?, ?, ?)", "synthetic-active", "ACTIVE", false);
        jdbc.update("INSERT INTO sys_users VALUES(?, ?, ?)", "synthetic-inactive", "INACTIVE", false);
        jdbc.update("INSERT INTO sys_users VALUES(?, ?, ?)", "synthetic-deleted", "ACTIVE", true);
        clearInvocations(jdbc);
    }
    @AfterEach void close() { database.shutdown(); }

    @Test void repeatedPeopleAreCheckedWithOneParameterizedDirectoryRead() {
        SubTableBatchUserEligibility.requireActive(List.of("synthetic-active", "synthetic-active"), jdbc);
        verify(jdbc, times(1)).query(any(PreparedStatementCreator.class), any(RowMapper.class));
    }
    @Test void inactiveDeletedMissingAndSqlLikeIdentifiersCannotPassAsTargets() {
        for (String id : List.of("synthetic-inactive", "synthetic-deleted", "synthetic-missing", "' OR '1'='1")) {
            assertThatThrownBy(() -> SubTableBatchUserEligibility.requireActive(List.of("synthetic-active", id), jdbc))
                    .hasMessage("Initial assignment person target is not currently active");
        }
    }
    @Test void savedTargetIsRecheckedAfterItBecomesInactive() {
        SubTableBatchUserEligibility.requireActive(List.of("synthetic-active"), jdbc);
        jdbc.update("UPDATE sys_users SET status=? WHERE id=?", "INACTIVE", "synthetic-active");
        assertThatThrownBy(() -> SubTableBatchUserEligibility.requireActive(List.of("synthetic-active"), jdbc))
                .hasMessage("Initial assignment person target is not currently active");
    }
    @Test void directoryFailureIsNotAnEmptyEligibleResult() {
        jdbc.execute("DROP TABLE sys_users");
        assertThatThrownBy(() -> SubTableBatchUserEligibility.requireActive(List.of("synthetic-active"), jdbc))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void anEmptyPersonSetDoesNotQueryOrInventADefault() {
        SubTableBatchUserEligibility.requireActive(List.of(), jdbc);
        verifyNoInteractions(jdbc);
    }
}
