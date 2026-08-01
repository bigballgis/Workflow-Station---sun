package com.workflow.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The repair used to catch every exception and log a warning, so a failed widen left the app
 * running against a schema that rejects the first task completion. These tests lock in the
 * fail-fast behaviour and the one case that is still allowed to pass quietly.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FlowableActHiCommentSchemaRepair")
class FlowableActHiCommentSchemaRepairTest {

    private static final String EXISTS_SQL = "SELECT EXISTS";
    private static final String DATA_TYPE_SQL = "SELECT data_type";

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData metaData;

    private FlowableActHiCommentSchemaRepair repair;

    @BeforeEach
    void setUp() throws Exception {
        repair = new FlowableActHiCommentSchemaRepair(jdbcTemplate);
        when(jdbcTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
    }

    private void stubTableExists(boolean exists) {
        when(jdbcTemplate.queryForObject(contains(EXISTS_SQL), eq(Boolean.class))).thenReturn(exists);
    }

    private void stubColumnType(String column, String type) {
        when(jdbcTemplate.queryForObject(contains(DATA_TYPE_SQL), eq(String.class), eq(column)))
                .thenReturn(type);
    }

    private void stubAllColumnsWidened() {
        stubColumnType("message_", "text");
        stubColumnType("action_", "character varying");
        stubColumnType("type_", "character varying");
        stubColumnType("user_id_", "character varying");
        stubColumnType("full_msg_", "bytea");
    }

    @Test
    @DisplayName("skips quietly on non-PostgreSQL (e.g. H2 in tests)")
    void skipsOnNonPostgres() throws Exception {
        when(metaData.getDatabaseProductName()).thenReturn("H2");

        assertThatCode(() -> repair.run(null)).doesNotThrowAnyException();

        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    @DisplayName("skips quietly when act_hi_comment does not exist yet (normal first boot)")
    void skipsWhenTableAbsent() {
        stubTableExists(false);

        assertThatCode(() -> repair.run(null)).doesNotThrowAnyException();

        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    @DisplayName("runs the widen and passes when every column ends up with the expected type")
    void widensAndVerifies() {
        stubTableExists(true);
        stubAllColumnsWidened();

        assertThatCode(() -> repair.run(null)).doesNotThrowAnyException();

        verify(jdbcTemplate).execute(contains("ALTER COLUMN message_ TYPE TEXT"));
        verify(jdbcTemplate).execute(contains("ALTER COLUMN user_id_ TYPE VARCHAR(4000)"));
    }

    @Test
    @DisplayName("fails startup when a column is still varchar(255) after the widen")
    void failsWhenColumnNotWidened() {
        stubTableExists(true);
        stubAllColumnsWidened();
        stubColumnType("message_", "character varying"); // widen silently did not apply

        assertThatThrownBy(() -> repair.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("act_hi_comment.message_")
                .hasMessageContaining("value too long");
    }

    @Test
    @DisplayName("propagates ALTER failures instead of swallowing them")
    void propagatesAlterFailure() {
        stubTableExists(true);
        when(jdbcTemplate.queryForObject(contains(DATA_TYPE_SQL), eq(String.class), any()))
                .thenReturn("text");
        org.mockito.Mockito.doThrow(new RuntimeException("permission denied for table act_hi_comment"))
                .when(jdbcTemplate).execute(contains("ALTER COLUMN message_"));

        assertThatThrownBy(() -> repair.run(null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("permission denied");
    }
}
