package com.portal.component;

import com.platform.common.list.ListColumnFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.util.MainTableViewColumnSpec;
import com.portal.util.MainTableViewColumnSpec.FieldSource;
import com.portal.util.MainTableViewColumnSpec.SqlSource;
import com.portal.util.SqlFragment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What the SUB row query asks the database, captured from the statements it prepares. The SQL
 * itself was checked against the dev database; this pins the decisions that would otherwise be
 * easy to undo by accident.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MainTableViewSubRowQuerySqlTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private MainTableViewSubRowQueryComponent component;
    private final List<String> preparedSql = new ArrayList<>();

    private static final List<FieldSource> FIELDS = List.of(
            new FieldSource("line_amount", "Amount", false, "field", "DECIMAL"),
            new FieldSource("line_status", "Line status", false, "field", "VARCHAR"),
            new FieldSource("reconciled", "Reconciled", false, "field", "BOOLEAN"));

    /** The statement that reads the page, which is the last one the component prepares. */
    private String pageSql() {
        return preparedSql.get(preparedSql.size() - 1);
    }

    @BeforeEach
    void setUp() throws Exception {
        component = new MainTableViewSubRowQueryComponent(jdbcTemplate, new ObjectMapper());

        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenAnswer(call -> {
            preparedSql.add(call.getArgument(0));
            return statement;
        });
        when(jdbcTemplate.query(any(PreparedStatementCreator.class), any(ResultSetExtractor.class)))
                .thenAnswer(call -> {
                    call.<PreparedStatementCreator>getArgument(0).createPreparedStatement(connection);
                    return null;
                });
    }

    private MainTableViewSubRowQueryComponent.Query query(String groupBy, List<ListColumnFilter> filters,
                                                          MainTableViewInvolvementScope.Predicate involvement) {
        return new MainTableViewSubRowQueryComponent.Query(
                7L,
                "fu-atm",
                List.of("50522", "50527"),
                MainTableViewColumnSpec.sqlFor(FIELDS, List.of(), SqlSource.EXPANDED_SUB_ROW,
                        "pi.id, pi.row_identity"),
                SqlFragment.EMPTY,
                filters,
                null,
                null,
                groupBy,
                null,
                List.of(),
                involvement,
                0,
                20);
    }

    @Test
    void eachBindingContributesItsOwnExpansionAndTheRowIsDeduplicatedAcrossThemAll() {
        component.query(query(null, List.of(), null));

        String sql = preparedSql.get(0);
        assertThat(sql).contains("jsonb_array_elements(pi.variables->'__subTables__'->?::text)");
        assertThat(sql.split("jsonb_array_elements", -1)).as("one expansion per binding key").hasSize(3);
        assertThat(sql)
                .as("binding a table into two forms stores the same row twice; keying the "
                        + "de-duplication on the binding would show it twice")
                .contains("DISTINCT ON (pi.id, COALESCE(expanded.elem->>'row_id'");
    }

    @Test
    void whoMaySeeARowIsSettledBeforeTheRowsAreExpanded() {
        component.query(query(null, List.of(),
                new MainTableViewInvolvementScope.Predicate(" AND (pi.start_user_id = ?)", List.of("u1"))));

        String sql = pageSql();
        assertThat(sql.indexOf("pi.start_user_id = ?"))
                .as("visibility is a property of the instance, so an instance the user may not "
                        + "see should never be expanded at all — the clause belongs inside the "
                        + "expansion, ahead of the outer WHERE")
                .isGreaterThan(sql.indexOf("DISTINCT ON"))
                .isLessThan(sql.indexOf("WHERE TRUE"));
    }

    @Test
    void theTotalCountsSubTableRowsRatherThanTheInstancesTheyCameFrom() {
        component.query(query(null, List.of(), null));

        assertThat(preparedSql.get(0))
                .as("counting instances would report a smaller total than the rows on screen")
                .startsWith("SELECT COUNT(*) FROM (SELECT DISTINCT ON");
    }

    @Test
    void groupCountsAreTakenOverTheWholeResultSetNotOverThePage() {
        component.query(query("reconciled", List.of(), null));

        String groupSql = preparedSql.stream()
                .filter(sql -> sql.contains("GROUP BY"))
                .findFirst()
                .orElseThrow();
        assertThat(groupSql).doesNotContain("LIMIT");
        assertThat(groupSql).contains("COUNT(*) AS group_count");
    }

    @Test
    void aFilterOnAColumnTheViewDoesNotDeclareIsRefused() {
        assertThatThrownBy(() -> component.query(
                query(null, List.of(new ListColumnFilter("secret", "contains", "x", null)), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aViewBoundToNoFormHasNowhereToReadItsRowsFromAndSaysSo() {
        MainTableViewSubRowQueryComponent.Query unbound = new MainTableViewSubRowQueryComponent.Query(
                7L, "fu-atm", List.of(),
                MainTableViewColumnSpec.sqlFor(FIELDS, List.of(), SqlSource.EXPANDED_SUB_ROW,
                        "pi.id, pi.row_identity"),
                SqlFragment.EMPTY, List.of(), null, null, null, null, List.of(), null, 0, 20);

        assertThatThrownBy(() -> component.query(unbound))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one form");
    }

    @Test
    void aRowWithNoIdentityIsReportedRatherThanMergedIntoItsNeighbour() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        // The first row goes to the COUNT extractor; the second is the page's one row.
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getString("row_identity")).thenReturn(null);
        when(rs.getString("id")).thenReturn("proc-1");
        when(rs.getString("slice_key")).thenReturn("50522");
        when(rs.getLong("ord")).thenReturn(3L);

        when(jdbcTemplate.query(any(PreparedStatementCreator.class), any(ResultSetExtractor.class)))
                .thenAnswer(call -> call.<ResultSetExtractor<?>>getArgument(1).extractData(rs));

        assertThatThrownBy(() -> component.query(query(null, List.of(), null)))
                .as("DISTINCT ON treats two missing identities as the same row, so one of them "
                        + "would vanish and the total would be short by one")
                .hasMessageContaining("proc-1")
                .hasMessageContaining("50522");
    }
}
