package com.admin.bi.component;

import com.admin.bi.repository.BiRbacMappingRepository;
import com.admin.bi.repository.BiSupersetRoleRepository;
import com.admin.dto.request.BiRbacListQueryRequest;
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
class BiRbacListQuerySqlTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private RoleRepository roleRepository;
    @Mock private BiRbacMappingRepository mappingRepository;
    @Mock private BiSupersetRoleRepository supersetRoleRepository;

    private BiRbacListQueryComponent component;
    private final List<String> preparedSql = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        component = new BiRbacListQueryComponent(
                jdbcTemplate, roleRepository, mappingRepository, supersetRoleRepository);
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
    void countAndPageShareActiveMappedRolePredicate() {
        component.query(request(null, null, List.of()));

        assertThat(preparedSql.get(0)).startsWith("SELECT COUNT(*) FROM sys_roles r");
        assertThat(preparedSql.get(0)).contains("r.status = 'ACTIVE'");
        assertThat(preparedSql.get(0)).contains("bi_rbac_mapping");
        assertThat(pageSql()).contains("LIMIT ?").contains("OFFSET ?").contains("r.status = 'ACTIVE'");
    }

    @Test
    void toolbarRoleNameIsInsideTheSharedPredicate() {
        component.query(request("admin", null, List.of()));

        assertThat(preparedSql.get(0)).contains("r.name ILIKE ?");
        assertThat(pageSql()).contains("r.name ILIKE ?");
    }

    @Test
    void aFilterOnAColumnTheListDoesNotDeclareIsRefused() {
        assertThatThrownBy(() -> component.query(request(null, null,
                List.of(new ListColumnFilter("secret", "contains", "x", null)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static BiRbacListQueryRequest request(String roleName, String roleType,
                                                  List<ListColumnFilter> filters) {
        return new BiRbacListQueryRequest(0, 20, roleName, roleType, filters, null, null);
    }

    private String pageSql() {
        return preparedSql.get(preparedSql.size() - 1);
    }
}
