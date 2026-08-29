package com.admin.component;

import com.admin.dto.request.VirtualGroupListQueryRequest;
import com.admin.repository.RoleRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.repository.VirtualGroupRoleRepository;
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
class VirtualGroupListQuerySqlTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private VirtualGroupRepository virtualGroupRepository;
    @Mock private VirtualGroupRoleRepository virtualGroupRoleRepository;
    @Mock private RoleRepository roleRepository;

    private VirtualGroupListQueryComponent component;
    private final List<String> preparedSql = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        component = new VirtualGroupListQueryComponent(
                jdbcTemplate, virtualGroupRepository, virtualGroupRoleRepository, roleRepository);
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
    void countAndPageShareTheTabTypeAndLimitOffset() {
        component.query(request("CUSTOM", null, List.of()));

        assertThat(preparedSql.get(0)).contains("SELECT COUNT(*)");
        assertThat(preparedSql.get(0)).contains("vg.type = ?");
        assertThat(preparedSql.get(0)).contains("sys_virtual_groups vg");
        assertThat(pageSql()).contains("LIMIT ?").contains("OFFSET ?");
    }

    @Test
    void toolbarKeywordSearchesNameCodeAdGroupRoleAndDescription() {
        component.query(request("CUSTOM", "ops", List.of()));

        assertThat(preparedSql.get(0)).contains("vg.name ILIKE ?");
        assertThat(preparedSql.get(0)).contains("vg.code ILIKE ?");
        assertThat(preparedSql.get(0)).contains("vg.ad_group ILIKE ?");
        assertThat(preparedSql.get(0)).contains("r.name ILIKE ?");
        assertThat(preparedSql.get(0)).contains("vg.display_name ILIKE ?");
    }

    @Test
    void aFilterOnAColumnTheListDoesNotDeclareIsRefused() {
        assertThatThrownBy(() -> component.query(request("CUSTOM", null,
                List.of(new ListColumnFilter("secret", "contains", "x", null)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUnknownTabTypeIsRefused() {
        assertThatThrownBy(() -> component.query(request("NOT_A_TYPE", null, List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static VirtualGroupListQueryRequest request(
            String type, String keyword, List<ListColumnFilter> filters) {
        return new VirtualGroupListQueryRequest(0, 20, type, keyword, filters, null, null);
    }

    private String pageSql() {
        return preparedSql.stream()
                .filter(sql -> sql.contains("LIMIT ?"))
                .findFirst()
                .orElse(preparedSql.get(preparedSql.size() - 1));
    }
}
