package com.admin.component;

import com.admin.dto.request.RoleListQueryRequest;
import com.admin.repository.RoleRepository;
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
class RoleListQuerySqlTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private RoleRepository roleRepository;

    private RoleListQueryComponent component;
    private final List<String> preparedSql = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        component = new RoleListQueryComponent(jdbcTemplate, roleRepository);
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
    void systemTabRestrictsToNamedPlatformRoles() {
        component.query(request("SYSTEM", null, List.of()));

        assertThat(preparedSql.get(0)).contains("r.is_system = TRUE");
        assertThat(preparedSql.get(0)).contains("r.code IN (");
        assertThat(preparedSql.get(0)).doesNotContain("FU_VIEWER");
        assertThat(pageSql()).contains("LIMIT ?").contains("OFFSET ?");
    }

    @Test
    void customTabIsNonSystemBuBounded() {
        component.query(request("CUSTOM", null, List.of()));

        assertThat(preparedSql.get(0)).contains("r.is_system IS NOT TRUE");
        assertThat(preparedSql.get(0)).contains("r.type = ?");
    }

    @Test
    void toolbarTypeAndsWithTheTab() {
        component.query(request("SYSTEM", "ADMIN", List.of()));

        assertThat(preparedSql.get(0)).contains("r.type = ?");
    }

    @Test
    void anUnknownTabIsRefused() {
        assertThatThrownBy(() -> component.query(request("ALL", null, List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aFilterOnAColumnTheListDoesNotDeclareIsRefused() {
        assertThatThrownBy(() -> component.query(request("CUSTOM", null,
                List.of(new ListColumnFilter("secret", "contains", "x", null)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RoleListQueryRequest request(String tab, String type, List<ListColumnFilter> filters) {
        return new RoleListQueryRequest(0, 20, tab, type, filters, null, null, null);
    }

    private String pageSql() {
        return preparedSql.stream()
                .filter(sql -> sql.contains("LIMIT ?"))
                .findFirst()
                .orElse(preparedSql.get(preparedSql.size() - 1));
    }
}
