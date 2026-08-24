package com.admin.component;

import com.admin.dto.request.RelationTableStructureListQueryRequest;
import com.admin.repository.RelationTableDefinitionRepository;
import com.platform.common.list.ListColumnFilter;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RelationTableStructureListQuerySqlTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private RelationTableDefinitionRepository tableDefinitionRepository;
    @Mock private RelationTableFunctionUnitResolver relationTableFunctionUnitResolver;

    private RelationTableStructureListQueryComponent component;
    private final List<String> preparedSql = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        component = new RelationTableStructureListQueryComponent(
                jdbcTemplate, tableDefinitionRepository, relationTableFunctionUnitResolver);
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
    }

    @Test
    void countAndPageShareTheSamePredicateAndLimitOffset() {
        component.query(request(null, List.of()));

        assertThat(preparedSql.get(0)).contains("SELECT COUNT(*)");
        assertThat(preparedSql.get(0)).contains("FROM rt_table_definitions t");
        assertThat(pageSql()).contains("LIMIT ?").contains("OFFSET ?");
        assertThat(pageSql()).contains("FROM rt_table_definitions t");
    }

    @Test
    void commonRailFiltersTablesWithNoFunctionUnitLink() {
        component.query(request(RelationTableStructureListQueryRequest.COMMON_KEY, List.of()));

        assertThat(preparedSql.get(0)).contains("NOT EXISTS");
        assertThat(preparedSql.get(0)).contains("rt_table_function_units");
        assertThat(preparedSql.get(0)).doesNotContain("l.function_unit_id = ?");
    }

    @Test
    void functionUnitRailUsesExistsOnTheJunction() {
        component.query(request("fu-1", List.of()));

        assertThat(preparedSql.get(0)).contains("EXISTS");
        assertThat(preparedSql.get(0)).contains("l.function_unit_id = ?");
    }

    @Test
    void aFilterOnAColumnTheListDoesNotDeclareIsRefused() {
        assertThatThrownBy(() -> component.query(request(null,
                List.of(new ListColumnFilter("secret", "contains", "x", null)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RelationTableStructureListQueryRequest request(
            String functionUnitId, List<ListColumnFilter> filters) {
        return new RelationTableStructureListQueryRequest(
                0, 20, functionUnitId, filters, null, null, null);
    }

    private String pageSql() {
        return preparedSql.stream()
                .filter(sql -> sql.contains("LIMIT ?"))
                .findFirst()
                .orElse(preparedSql.get(preparedSql.size() - 1));
    }
}
