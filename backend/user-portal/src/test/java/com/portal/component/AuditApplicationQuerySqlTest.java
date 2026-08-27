package com.portal.component;

import com.portal.dto.MyApplicationQueryRequest;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Audit All Requests pages in SQL: COUNT and the page share function_unit_code
 * (+ optional status tab + toolbar keyword across visible columns).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditApplicationQuerySqlTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private MyApplicationListQueryComponent myApplicationListQueryComponent;

    private AuditApplicationListQueryComponent component;
    private final List<String> preparedSql = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        component = new AuditApplicationListQueryComponent(jdbcTemplate, myApplicationListQueryComponent);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenAnswer(call -> {
            preparedSql.add(call.getArgument(0));
            return statement;
        });
        when(jdbcTemplate.query(any(PreparedStatementCreator.class), any(ResultSetExtractor.class)))
                .thenAnswer(call -> {
                    call.<PreparedStatementCreator>getArgument(0).createPreparedStatement(connection);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.next()).thenReturn(false);
                    return call.<ResultSetExtractor<?>>getArgument(1).extractData(rs);
                });
        when(myApplicationListQueryComponent.toListRows(any())).thenReturn(List.of());
    }

    @Test
    void countAndPageShareTheFunctionUnitPredicate() {
        component.query("expense", request(null, null));

        assertThat(preparedSql.get(0)).startsWith("SELECT COUNT(*) FROM up_process_instance pi");
        assertThat(preparedSql.get(0)).contains("pi.function_unit_code = ?");
        assertThat(pageSql()).contains("LIMIT ?").contains("OFFSET ?").contains("pi.function_unit_code = ?");
        assertThat(preparedSql.get(0)).doesNotContain("ILIKE");
    }

    @Test
    void keywordSearchIsInsideTheSharedPredicate() {
        component.query("expense", request(null, "  请假  "));

        assertThat(preparedSql.get(0)).contains("pi.variables->>'__request_id' ILIKE ?");
        assertThat(preparedSql.get(0)).contains("COALESCE(NULLIF(BTRIM(pi.business_key), ''), pi.process_definition_name)");
        assertThat(preparedSql.get(0)).contains("to_char(pi.start_time, 'YYYY-MM-DD HH24:MI')");
        assertThat(preparedSql.get(0)).contains("FROM sys_users u");
        assertThat(preparedSql.get(0)).doesNotContain("pi.status ILIKE");
        assertThat(preparedSql.get(0)).doesNotContain("pi.function_unit_code ILIKE");
        assertThat(preparedSql.get(0)).doesNotContain("pi.start_time::text");
        assertThat(pageSql()).contains("ILIKE ?").contains("pi.function_unit_code = ?");
    }

    @Test
    void typedStatusLabelSearchesTheStoredCode() {
        component.query("expense", request(null, "Running"));

        assertThat(preparedSql.get(0)).contains("pi.status IN (?)");
        assertThat(pageSql()).contains("pi.status IN (?)");
    }

    @Test
    void keywordAndsWithTheStatusTab() {
        component.query("expense", request("RUNNING", "ann"));

        assertThat(preparedSql.get(0)).contains("pi.status = ?");
        assertThat(preparedSql.get(0)).contains("ILIKE");
        assertThat(pageSql()).contains("pi.status = ?").contains("ILIKE");
    }

    private static MyApplicationQueryRequest request(String status, String keyword) {
        return new MyApplicationQueryRequest(0, 20, status, List.of(), null, null, keyword);
    }

    private String pageSql() {
        return preparedSql.get(preparedSql.size() - 1);
    }
}
