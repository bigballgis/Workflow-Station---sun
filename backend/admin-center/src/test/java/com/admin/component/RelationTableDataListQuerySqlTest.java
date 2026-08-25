package com.admin.component;

import com.admin.dto.request.RelationTableDataListQueryRequest;
import com.admin.service.RelationTableDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.RelationFieldDTO;
import com.platform.common.enums.RelationDataType;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RelationTableDataListQuerySqlTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private RelationTableDataService dataService;

    private RelationTableDataListQueryComponent component;
    private final List<String> preparedSql = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        when(dataService.loadDeployedFieldsForQuery(anyLong())).thenReturn(List.of(
                field("name", RelationDataType.VARCHAR),
                field("qty", RelationDataType.INTEGER),
                field("created_by", RelationDataType.VARCHAR)));
        component = new RelationTableDataListQueryComponent(jdbcTemplate, dataService, new ObjectMapper());
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
    void countAndPageShareTableIdAndDoNotGroup() {
        component.query(12L, request(null, List.of()));

        assertThat(preparedSql.get(0)).contains("FROM rt_table_data_rows WHERE table_id = ?");
        assertThat(preparedSql.get(0)).doesNotContain("LIMIT ?");
        assertThat(preparedSql.get(0)).doesNotContain("GROUP BY");
        assertThat(pageSql()).contains("LIMIT ?").contains("OFFSET ?");
        assertThat(pageSql()).contains("SELECT row_id, data, status");
    }

    @Test
    void keywordKeepsTheTrgmGuardAndPerFieldIlike() {
        component.query(12L, request("acme", List.of()));

        assertThat(preparedSql.get(0)).contains("data::text ILIKE ? ESCAPE '\\'");
        assertThat(preparedSql.get(0)).contains("data->>'name' ILIKE ? ESCAPE '\\'");
        assertThat(countSql()).isEqualTo(countPredicateOf(pageSql()));
    }

    @Test
    void aFilterOnAColumnTheListDoesNotDeclareIsRefused() {
        assertThatThrownBy(() -> component.query(12L, request(null,
                List.of(new ListColumnFilter("secret", "contains", "x", null)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownOperatorIsRefused() {
        assertThatThrownBy(() -> component.query(12L, request(null,
                List.of(new ListColumnFilter("name", "regex", "x", null)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RelationTableDataListQueryRequest request(String search, List<ListColumnFilter> filters) {
        return new RelationTableDataListQueryRequest(0, 20, search, filters, null, null, null);
    }

    private static RelationFieldDTO field(String name, RelationDataType type) {
        return RelationFieldDTO.builder().fieldName(name).dataType(type).displayName(name).build();
    }

    private String countSql() {
        return preparedSql.stream()
                .filter(sql -> sql.startsWith("SELECT COUNT(*)"))
                .findFirst()
                .orElse(preparedSql.get(0));
    }

    private String pageSql() {
        return preparedSql.stream()
                .filter(sql -> sql.contains("LIMIT ?"))
                .findFirst()
                .orElse(preparedSql.get(preparedSql.size() - 1));
    }

    private static String countPredicateOf(String pageSql) {
        int from = pageSql.indexOf(" FROM ");
        int order = pageSql.indexOf(" ORDER BY ");
        return "SELECT COUNT(*)" + pageSql.substring(from, order);
    }
}
