package com.admin.bi.component;

import com.admin.bi.dto.response.DataViewAssignmentResponse;
import com.admin.bi.repository.BiDataViewMetadataRepository;
import com.admin.dto.request.BiDataViewAssignmentListQueryRequest;
import com.platform.common.list.ListColumnFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class BiDataViewAssignmentListQuerySqlTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BiDataViewMetadataRepository metadata = mock(BiDataViewMetadataRepository.class);
    private final List<String> sql = new ArrayList<>();
    private final List<Object> bound = new ArrayList<>();
    private BiDataViewAssignmentListQueryComponent component;

    @BeforeEach
    void setup() throws Exception {
        component = new BiDataViewAssignmentListQueryComponent(jdbc, metadata);
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenAnswer(call -> {
            sql.add(call.getArgument(0));
            PreparedStatement statement = mock(PreparedStatement.class);
            doAnswer(bind -> { bound.add(bind.getArgument(1)); return null; }).when(statement).setObject(anyInt(), any());
            return statement;
        });
        when(jdbc.query(any(PreparedStatementCreator.class), any(ResultSetExtractor.class))).thenAnswer(call -> {
            call.<PreparedStatementCreator>getArgument(0).createPreparedStatement(connection);
            ResultSet rs = mock(ResultSet.class);
            return call.<ResultSetExtractor<?>>getArgument(1).extractData(rs);
        });
        when(metadata.responses(List.of())).thenReturn(List.of());
    }

    @Test
    void allFiltersApplyToCountAndPageWithBoundValuesAndUnavailableTargetsRetained() {
        String probe = "O'Brien_%";
        var request = new BiDataViewAssignmentListQueryRequest(2, 20, probe, 50029L,
                List.of(new ListColumnFilter("viewName", "contains", "QA_%", null),
                        new ListColumnFilter("enabled", "eq", "false", null)), "functionUnitName", "DESC");
        var result = component.query(request);
        assertThat(sql).hasSize(2);
        String countWhere = sql.get(0).substring("SELECT COUNT(*)".length());
        assertThat(sql.get(1)).startsWith("SELECT a.id" + countWhere);
        assertThat(countWhere).contains("LEFT JOIN dw_table_definitions", "LEFT JOIN dw_main_table_view_configs",
                "a.function_unit_id = ?", "v.view_name", "a.enabled::text").doesNotContain(probe, "QA_%");
        assertThat(sql.get(1)).contains("ORDER BY fu.name DESC NULLS LAST, a.id LIMIT ? OFFSET ?");
        assertThat(bound).contains("%O'Brien\\_\\%%", "%QA\\_\\%%", "false", 50029L, 20, 40);
        assertThat(result.columns()).hasSize(5).allSatisfy(c -> {
            assertThat(c.filterable()).isTrue(); assertThat(c.sortable()).isTrue();
        });
        assertThat(result.columns().get(4).options()).extracting(o -> o.value()).containsExactly("true", "false");
    }

    @Test
    void unknownColumnsAndOperatorsFailBeforeAnySqlRuns() {
        for (var filter : List.of(new ListColumnFilter("password", "eq", "x", null),
                new ListColumnFilter("enabled", "contains", "false", null))) {
            assertThatIllegalArgumentException().isThrownBy(() -> component.query(
                    new BiDataViewAssignmentListQueryRequest(0, 20, null, null, List.of(filter), null, null)));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> component.query(
                new BiDataViewAssignmentListQueryRequest(0, 20, null, null, List.of(), "a.id;DROP", "ASC")));
        assertThat(sql).isEmpty();
    }

    @Test
    void invalidPagingOrFuIdentityCannotReachTheDatabase() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BiDataViewAssignmentListQueryRequest(-1, 20, null, null, null, null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new BiDataViewAssignmentListQueryRequest(0, 201, null, null, null, null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new BiDataViewAssignmentListQueryRequest(0, 20, null, -1L, null, null, null));
    }

    @Test
    void metadataHydrationDoesNotReplaceTheRequestedSortOrder() {
        // A populated page can be hydrated in created-at order; the SQL page order stays authoritative.
        var ids = List.of("second", "first");
        when(jdbc.query(any(PreparedStatementCreator.class), any(ResultSetExtractor.class))).thenAnswer(call -> {
            call.<PreparedStatementCreator>getArgument(0).createPreparedStatement(mockConnection());
            ResultSet rs = mock(ResultSet.class);
            if (sql.size() == 1) { when(rs.next()).thenReturn(true); when(rs.getLong(1)).thenReturn(2L); }
            else { when(rs.next()).thenReturn(true, true, false); when(rs.getString("id")).thenReturn(ids.get(0), ids.get(1)); }
            return call.<ResultSetExtractor<?>>getArgument(1).extractData(rs);
        });
        when(metadata.responses(ids)).thenReturn(List.of(DataViewAssignmentResponse.builder().id("first").build(),
                DataViewAssignmentResponse.builder().id("second").build()));
        var result = component.query(new BiDataViewAssignmentListQueryRequest(0, 20, null, null, null, "viewName", "ASC"));
        assertThat(result.content()).extracting(DataViewAssignmentResponse::getId).containsExactlyElementsOf(ids);
        assertThat(result.totalElements()).isEqualTo(2);
    }

    private Connection mockConnection() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenAnswer(call -> { sql.add(call.getArgument(0)); return mock(PreparedStatement.class); });
        return connection;
    }
}
