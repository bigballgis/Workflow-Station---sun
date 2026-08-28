package com.admin.component;

import com.admin.dto.request.UserListQueryRequest;
import com.admin.repository.UserRepository;
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

/**
 * User list pages in SQL: COUNT and the page share soft-delete + toolbar filters,
 * and undeclared filters are refused.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserListQuerySqlTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private UserRepository userRepository;
    @Mock private UserManagerComponent userManager;

    private UserListQueryComponent component;
    private final List<String> preparedSql = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        component = new UserListQueryComponent(jdbcTemplate, userRepository, userManager);
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
    void countAndPageShareTheSoftDeletePredicate() {
        component.query(request(null, List.of()));

        assertThat(preparedSql.get(0)).startsWith("SELECT COUNT(*) FROM sys_users su");
        assertThat(preparedSql.get(0)).contains("su.deleted = false");
        assertThat(pageSql()).contains("LIMIT ?").contains("OFFSET ?").contains("su.deleted = false");
    }

    @Test
    void toolbarStatusIsInsideTheSharedPredicateAsStoredEnum() {
        component.query(request("DISABLED", List.of()));

        assertThat(preparedSql.get(0)).contains("su.status = ?");
        assertThat(pageSql()).contains("su.status = ?");
    }

    @Test
    void toolbarKeywordSearchesTheSameFieldsAsTheLegacyList() {
        component.query(new UserListQueryRequest(
                0, 20, "ann", null, List.of(), null, null));

        assertThat(preparedSql.get(0)).contains("su.username ILIKE ?");
        assertThat(preparedSql.get(0)).contains("su.full_name ILIKE ?");
        assertThat(preparedSql.get(0)).contains("su.display_name ILIKE ?");
        assertThat(preparedSql.get(0)).contains("su.email ILIKE ?");
    }

    @Test
    void aFilterOnAColumnTheListDoesNotDeclareIsRefused() {
        assertThatThrownBy(() -> component.query(request(null,
                List.of(new ListColumnFilter("secret", "contains", "x", null)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void outerAliasIsNotUSoUserFiltersDoNotShadowSysUsers() {
        component.query(request(null,
                List.of(new ListColumnFilter("entityManagerName", "eq", "user-1", null))));
        assertThat(preparedSql.get(0)).contains("su.entity_manager_id");
        assertThat(preparedSql.get(0)).contains("FROM sys_users u");
        assertThat(preparedSql.get(0)).contains("FROM sys_users su");
    }

    private static UserListQueryRequest request(String status,
                                                List<ListColumnFilter> filters) {
        return new UserListQueryRequest(0, 20, null, status, filters, null, null);
    }

    private String pageSql() {
        return preparedSql.get(preparedSql.size() - 1);
    }
}
