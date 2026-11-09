package com.portal.component;

import com.platform.common.list.ListColumnFilter;
import com.portal.dto.UserPortalAuditListQueryRequest;
import com.portal.repository.ChangeHistoryRepository;
import com.portal.repository.ProcessInstanceRepository;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserPortalAuditListQuerySqlTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private ChangeHistoryRepository changeHistoryRepository;
    @Mock private ProcessInstanceRepository processInstanceRepository;
    @Mock private UserPortalAuditEnricher userPortalAuditEnricher;
    @Mock private UserPortalAuditProcessInstanceMatcher processInstanceMatcher;

    private UserPortalAuditListQueryComponent component;
    private final List<String> preparedSql = new ArrayList<>();
    private PreparedStatement statement;

    @BeforeEach
    void setUp() throws Exception {
        component = new UserPortalAuditListQueryComponent(
                jdbcTemplate, changeHistoryRepository, processInstanceRepository,
                userPortalAuditEnricher, processInstanceMatcher);
        Connection connection = mock(Connection.class);
        statement = mock(PreparedStatement.class);
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
    void countAndPageShareTheChangeHistoryTable() {
        component.query(request(null, List.of()));

        assertThat(preparedSql.get(0)).startsWith("SELECT COUNT(*) FROM up_change_history ch");
        assertThat(preparedSql.get(0)).contains("LEFT JOIN up_process_instance pi");
        assertThat(preparedSql.get(0)).contains("ch.field_name NOT IN");
        assertThat(pageSql()).contains("LIMIT ?").contains("OFFSET ?");
    }

    @Test
    void toolbarChangeTypeIsInsideTheSharedPredicate() {
        component.query(request("FIELD_UPDATE", List.of()));

        assertThat(preparedSql.get(0)).contains("ch.change_type = ?");
        assertThat(pageSql()).contains("ch.change_type = ?");
    }

    @Test
    void withoutUnitScopeEveryUnitIsListed() {
        component.query(request(null, List.of(), null));

        assertThat(preparedSql.get(0)).doesNotContain("pi.function_unit_code IN").doesNotContain("1=0");
    }

    @Test
    void anEmptyUnitScopeListsNothing() {
        component.query(request(null, List.of(), List.of()));

        assertThat(preparedSql.get(0)).contains(" AND 1=0");
        assertThat(pageSql()).contains(" AND 1=0");
    }

    @Test
    void aUnitScopeKeepsOnlyThoseUnitsInCountAndPage() throws Exception {
        component.query(request(null, List.of(), List.of("FU_A", "FU_B")));

        assertThat(preparedSql.get(0)).contains("pi.function_unit_code IN (?,?)");
        assertThat(pageSql()).contains("pi.function_unit_code IN (?,?)");
        verify(statement, times(2)).setObject(anyInt(), eq("FU_A"));
        verify(statement, times(2)).setObject(anyInt(), eq("FU_B"));
    }

    @Test
    void aFilterOnAColumnTheListDoesNotDeclareIsRefused() {
        assertThatThrownBy(() -> component.query(request(null,
                List.of(new ListColumnFilter("secret", "contains", "x", null)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static UserPortalAuditListQueryRequest request(String changeType,
                                                           List<ListColumnFilter> filters) {
        return request(changeType, filters, null);
    }

    private static UserPortalAuditListQueryRequest request(String changeType, List<ListColumnFilter> filters,
                                                           List<String> functionUnitCodes) {
        return new UserPortalAuditListQueryRequest(
                0, 20, null, null, changeType, null, null, null, filters, null, null, functionUnitCodes);
    }

    private String pageSql() {
        return preparedSql.get(preparedSql.size() - 1);
    }
}
