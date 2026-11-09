package com.admin.bi.component;

import com.admin.bi.dto.response.DataViewAssignmentResponse;
import com.admin.bi.repository.BiDataViewMetadataRepository;
import com.admin.dto.request.BiDataViewAssignmentListQueryRequest;
import com.admin.security.AdminTeamScope;
import com.admin.security.TestTeamScopes;
import com.platform.common.list.ListColumnFilter;
import com.admin.component.FunctionUnitLifecycleComponent;
import com.admin.entity.FunctionUnit;
import com.platform.common.functionunit.CatalogSnapshotReader;
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
    private final FunctionUnitLifecycleComponent lifecycle = mock(FunctionUnitLifecycleComponent.class);
    // Real release join (the SQL under test), with the per-row hydration stubbed where a test needs it.
    private final BiDataViewMetadataRepository metadata = spy(new BiDataViewMetadataRepository(
            mock(JdbcTemplate.class), lifecycle, mock(CatalogSnapshotReader.class)));
    private final List<String> sql = new ArrayList<>();
    private final List<Object> bound = new ArrayList<>();
    private final AdminTeamScope teamScope = TestTeamScopes.everything();
    private BiDataViewAssignmentListQueryComponent component;

    @BeforeEach
    void setup() throws Exception {
        when(lifecycle.listLatestDeployedFunctionUnits()).thenReturn(List.of(
                FunctionUnit.builder().id("catalog-a").code("FU_X").build()));
        component = new BiDataViewAssignmentListQueryComponent(jdbc, metadata, teamScope);
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
    }

    @Test
    void allFiltersApplyToCountAndPageWithBoundValuesAndUnavailableTargetsRetained() {
        String probe = "O'Brien_%";
        var request = new BiDataViewAssignmentListQueryRequest(2, 20, probe, "FU_X",
                List.of(new ListColumnFilter("viewName", "contains", "QA_%", null),
                        new ListColumnFilter("enabled", "eq", "false", null)), "functionUnitName", "DESC");
        var result = component.query(request);
        assertThat(sql).hasSize(2);
        String countWhere = sql.get(0).substring("SELECT COUNT(*)".length());
        assertThat(sql.get(1)).startsWith("SELECT a.id" + countWhere);
        // The assignment's View is resolved in the unit's active release; rows whose View is gone stay listed.
        assertThat(countWhere).contains("LEFT JOIN sys_function_units fu", "fu.id IN (?)", "'MAIN_TABLE_VIEW'",
                "a.function_unit_code = ?", "a.view_name", "a.enabled::text").doesNotContain(probe, "QA_%")
                .doesNotContainPattern("\\bdw_");
        assertThat(sql.get(1)).contains(
                "ORDER BY COALESCE(fu.name, a.function_unit_code) DESC NULLS LAST, a.id LIMIT ? OFFSET ?");
        assertThat(bound).contains("catalog-a", "%O'Brien\\_\\%%", "%QA\\_\\%%", "false", "FU_X", 20, 40);
        assertThat(result.columns()).hasSize(5).allSatisfy(c -> {
            assertThat(c.filterable()).isTrue(); assertThat(c.sortable()).isTrue();
        });
        assertThat(result.columns().get(4).options()).extracting(o -> o.value()).containsExactly("true", "false");
    }

    @Test
    void aTechnicalLeadListsOnlyTheAssignmentsOfTheTeamsTheyLead() {
        when(teamScope.visibleFunctionUnitCodes()).thenReturn(java.util.Set.of("FU_A"));
        component.query(new BiDataViewAssignmentListQueryRequest(0, 20, null, null, List.of(), null, null));

        // Count and page both carry the code filter; the code comes from this module's own join (D-46).
        assertThat(sql).hasSize(2).allSatisfy(s -> assertThat(s).contains("AND a.function_unit_code IN (?)"));
        assertThat(bound).contains("FU_A");
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
        doReturn(List.of(DataViewAssignmentResponse.builder().id("first").build(),
                DataViewAssignmentResponse.builder().id("second").build())).when(metadata).responses(ids);
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
