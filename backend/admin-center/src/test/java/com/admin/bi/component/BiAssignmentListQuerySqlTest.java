package com.admin.bi.component;

import com.admin.bi.repository.BiDashboardAssignmentRepository;
import com.admin.bi.repository.BiDashboardRegistryRepository;
import com.admin.dto.request.BiAssignmentListQueryRequest;
import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.RoleRepository;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BiAssignmentListQuerySqlTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private BiDashboardAssignmentRepository assignmentRepository;
    @Mock private BiDashboardRegistryRepository registryRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private BusinessUnitRepository businessUnitRepository;

    private BiAssignmentListQueryComponent component;
    private final List<String> preparedSql = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        component = new BiAssignmentListQueryComponent(
                jdbcTemplate, assignmentRepository, registryRepository,
                userRepository, roleRepository, businessUnitRepository);
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
    void countAndPageShareTheJoinPredicate() {
        component.query(request(null, null, List.of()));

        assertThat(preparedSql.get(0)).startsWith("SELECT COUNT(*) FROM bi_dashboard_assignment a");
        assertThat(preparedSql.get(0)).contains("JOIN bi_dashboard_registry d");
        assertThat(pageSql()).contains("LIMIT ?").contains("OFFSET ?").contains("JOIN bi_dashboard_registry d");
    }

    @Test
    void toolbarTitleIsInsideTheSharedPredicate() {
        component.query(request(null, "sales", List.of()));

        assertThat(preparedSql.get(0)).contains("d.dashboard_title ILIKE ?");
        assertThat(pageSql()).contains("d.dashboard_title ILIKE ?");
    }

    @Test
    void aFilterOnAColumnTheListDoesNotDeclareIsRefused() {
        assertThatThrownBy(() -> component.query(request(null, null,
                List.of(new ListColumnFilter("secret", "contains", "x", null)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static BiAssignmentListQueryRequest request(String targetType, String title,
                                                        List<ListColumnFilter> filters) {
        return new BiAssignmentListQueryRequest(0, 20, targetType, title, filters, null, null);
    }

    private String pageSql() {
        return preparedSql.get(preparedSql.size() - 1);
    }
}
