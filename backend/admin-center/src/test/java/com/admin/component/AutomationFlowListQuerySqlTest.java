package com.admin.component;

import com.admin.dto.request.AutomationFlowListQueryRequest;
import com.admin.service.AutomationFlowService;
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
class AutomationFlowListQuerySqlTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private AutomationFlowService automationFlowService;

    private AutomationFlowListQueryComponent component;
    private final List<String> preparedSql = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        component = new AutomationFlowListQueryComponent(jdbcTemplate, automationFlowService);
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
    void countAndPageShareTheSameJoinAndLimitOffset() {
        component.query(request(null, List.of()));

        assertThat(preparedSql.get(0)).contains("SELECT COUNT(*)");
        assertThat(preparedSql.get(0)).contains("FROM flow f");
        assertThat(preparedSql.get(0)).contains("JOIN LATERAL");
        assertThat(pageSql()).contains("LIMIT ?").contains("OFFSET ?");
        assertThat(pageSql()).contains("FROM flow f");
    }

    @Test
    void toolbarKeywordSearchesNameIdFlowKeyAndProject() {
        component.query(request("invoice", List.of()));

        assertThat(preparedSql.get(0)).contains("fv.\"displayName\" ILIKE ?");
        assertThat(preparedSql.get(0)).contains("f.id ILIKE ?");
        assertThat(preparedSql.get(0)).contains("f.metadata->>'hermesFlowKey' ILIKE ?");
        assertThat(preparedSql.get(0)).contains("p.\"displayName\" ILIKE ?");
    }

    @Test
    void aFilterOnAColumnTheListDoesNotDeclareIsRefused() {
        assertThatThrownBy(() -> component.query(request(null,
                List.of(new ListColumnFilter("secret", "contains", "x", null)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static AutomationFlowListQueryRequest request(String keyword, List<ListColumnFilter> filters) {
        return new AutomationFlowListQueryRequest(0, 20, keyword, filters, null, null);
    }

    private String pageSql() {
        return preparedSql.get(preparedSql.size() - 1);
    }
}
