package com.portal.component;

import com.platform.common.list.ListColumnFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.util.MainTableViewColumnSpec;
import com.portal.util.MainTableViewColumnSpec.FieldSource;
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
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Captures the SQL MAIN views send for true pagination — COUNT and page share one predicate,
 * and undeclared filters are refused.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MainTableViewRowQuerySqlTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private MainTableViewRowQueryComponent component;
    private final List<String> preparedSql = new ArrayList<>();

    private static final List<FieldSource> FIELDS = List.of(
            new FieldSource("amount", "Amount", false, "field", "DECIMAL"),
            new FieldSource("status", "Status", false, "field", "VARCHAR"),
            new FieldSource("active", "Active", false, "field", "BOOLEAN"));

    private String pageSql() {
        return preparedSql.get(preparedSql.size() - 1);
    }

    @BeforeEach
    void setUp() throws Exception {
        component = new MainTableViewRowQueryComponent(jdbcTemplate, new ObjectMapper());

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

    private MainTableViewRowQueryComponent.Query query(List<ListColumnFilter> filters,
                                                       MainTableViewInvolvementScope.Predicate involvement) {
        return new MainTableViewRowQueryComponent.Query(
                "fu-atm",
                MainTableViewColumnSpec.sqlFor(FIELDS, List.of()),
                SqlFragment.EMPTY,
                filters,
                null,
                null,
                null,
                List.of(),
                involvement,
                0,
                20);
    }

    @Test
    void theTotalCountsTheSamePredicateThePageReads() {
        component.query(query(List.of(), null));

        assertThat(preparedSql.get(0)).startsWith("SELECT COUNT(*) FROM up_process_instance pi");
        assertThat(pageSql())
                .contains("FROM up_process_instance pi")
                .contains("LIMIT ?")
                .contains("OFFSET ?");
        assertThat(preparedSql.get(0)).contains("pi.function_unit_code = ?");
        assertThat(pageSql()).contains("pi.function_unit_code = ?");
    }

    @Test
    void involvementAndFiltersAreInsideTheSharedPredicate() {
        component.query(query(
                List.of(new ListColumnFilter("amount", "gt", "10", null)),
                new MainTableViewInvolvementScope.Predicate(" AND (pi.start_user_id = ?)", List.of("u1"))));

        String sql = pageSql();
        assertThat(sql).contains("pi.start_user_id = ?");
        assertThat(sql).contains("pi.variables->>'amount'");
    }

    @Test
    void aFilterOnAColumnTheViewDoesNotDeclareIsRefused() {
        assertThatThrownBy(() -> component.query(
                query(List.of(new ListColumnFilter("secret", "contains", "x", null)), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void projectedRowsRetainTheirPersistedReleaseContext() throws Exception {
        var rs = mock(java.sql.ResultSet.class);
        when(rs.next()).thenReturn(true, false);
        when(rs.getString("id")).thenReturn("request-old");
        when(rs.getString("function_unit_catalog_id")).thenReturn("catalog-v1");
        when(rs.getString("function_unit_code")).thenReturn("fu-atm");
        when(rs.getString("function_unit_version_label")).thenReturn("1.0.0");
        when(rs.getString("process_definition_id")).thenReturn("definition-v1");

        when(jdbcTemplate.query(any(PreparedStatementCreator.class), any(ResultSetExtractor.class)))
                .thenReturn(1L)
                .thenAnswer(call -> call.<ResultSetExtractor<?>>getArgument(1).extractData(rs));
        var page = component.query(query(List.of(), null));
        var instance = page.instances().get(0);
        assertThat(instance.getFunctionUnitCatalogId()).isEqualTo("catalog-v1");
        assertThat(instance.getFunctionUnitCode()).isEqualTo("fu-atm");
        assertThat(instance.getFunctionUnitVersionLabel()).isEqualTo("1.0.0");
        assertThat(instance.getProcessDefinitionId()).isEqualTo("definition-v1");
    }

}
