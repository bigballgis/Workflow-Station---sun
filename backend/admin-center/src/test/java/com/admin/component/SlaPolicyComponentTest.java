package com.admin.component;

import com.admin.dto.SlaPolicyListQueryRequest;
import com.admin.dto.SlaPolicyResponse;
import com.admin.dto.SlaPolicyUpdateRequest;
import com.admin.entity.SlaPolicy;
import com.admin.entity.SlaPolicyHistory;
import com.admin.repository.SlaPolicyHistoryRepository;
import com.admin.repository.SlaPolicyRepository;
import com.platform.common.exception.BusinessException;
import com.platform.common.exception.ResourceNotFoundException;
import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.transaction.PlatformTransactionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlaPolicyComponentTest {

    private static final String FU = "FU_CASES";

    private SlaPolicyRepository policyRepository;
    private SlaPolicyHistoryRepository historyRepository;
    private PortalSlaRecalcClient portalClient;
    private JdbcTemplate jdbcTemplate;
    private SlaPolicyComponent component;

    @BeforeEach
    void setUp() {
        policyRepository = mock(SlaPolicyRepository.class);
        historyRepository = mock(SlaPolicyHistoryRepository.class);
        portalClient = mock(PortalSlaRecalcClient.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        component = new SlaPolicyComponent(policyRepository, historyRepository, portalClient, jdbcTemplate,
                mock(I18nService.class), mock(PlatformTransactionManager.class));
        when(historyRepository.save(any(SlaPolicyHistory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(policyRepository.save(any(SlaPolicy.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void firstChangeCreatesVersionOneAndDispatchesTheJob() {
        mapped(true);
        insertCreatesRow(true);
        when(policyRepository.findForUpdate(FU)).thenReturn(Optional.of(policy(30, 0)));
        when(portalClient.submit(FU, 1, "u-1")).thenReturn("job-1");
        when(policyRepository.findById(FU)).thenReturn(Optional.of(policy(30, 1)));

        SlaPolicyResponse response = component.update(FU, request(30, "initial"), "u-1");

        assertThat(response.getVersion()).isEqualTo(1);
        assertThat(response.getDispatchStatus()).isEqualTo(SlaPolicyHistory.DispatchStatus.DISPATCHED);
        assertThat(response.getRecalcJobId()).isEqualTo("job-1");
        SlaPolicyHistory history = lastSavedHistory();
        assertThat(history.getOldLeadTimeDays()).isNull();
        assertThat(history.getNewLeadTimeDays()).isEqualTo(30);
        assertThat(history.getChangeReason()).isEqualTo("initial");
        assertThat(history.getChangedBy()).isEqualTo("u-1");
    }

    @Test
    void changeBumpsVersionAndRecordsOldAndNewValue() {
        mapped(true);
        when(policyRepository.findForUpdate(FU)).thenReturn(Optional.of(policy(30, 3)));
        when(portalClient.submit(FU, 4, "u-1")).thenReturn("job-4");
        when(policyRepository.findById(FU)).thenReturn(Optional.of(policy(45, 4)));

        component.update(FU, request(45, null), "u-1");

        SlaPolicyHistory history = lastSavedHistory();
        assertThat(history.getOldLeadTimeDays()).isEqualTo(30);
        assertThat(history.getNewLeadTimeDays()).isEqualTo(45);
        assertThat(history.getOldVersion()).isEqualTo(3);
        assertThat(history.getNewVersion()).isEqualTo(4);
    }

    @Test
    void concurrentFirstSaveThatLostTheInsertBuildsOnTheWinnersValue() {
        // The other request created the row first: ON CONFLICT DO NOTHING reports 0 rows, and after the
        // row lock this save must record the winner's value as its "old" value instead of failing.
        mapped(true);
        insertCreatesRow(false);
        when(policyRepository.findForUpdate(FU)).thenReturn(Optional.of(policy(30, 1)));
        when(portalClient.submit(FU, 2, "u-2")).thenReturn("job-2");
        when(policyRepository.findById(FU)).thenReturn(Optional.of(policy(45, 2)));

        component.update(FU, request(45, null), "u-2");

        SlaPolicyHistory history = lastSavedHistory();
        assertThat(history.getOldLeadTimeDays()).isEqualTo(30);
        assertThat(history.getOldVersion()).isEqualTo(1);
        assertThat(history.getNewVersion()).isEqualTo(2);
    }

    @Test
    void portalOutageKeepsTheChangeAndRecordsDispatchFailure() {
        mapped(true);
        when(policyRepository.findForUpdate(FU)).thenReturn(Optional.of(policy(30, 1)));
        when(portalClient.submit(anyString(), eq(2), anyString()))
                .thenThrow(new IllegalStateException("connection refused"));
        when(policyRepository.findById(FU)).thenReturn(Optional.of(policy(45, 2)));

        SlaPolicyResponse response = component.update(FU, request(45, null), "u-1");

        assertThat(response.getDispatchStatus()).isEqualTo(SlaPolicyHistory.DispatchStatus.DISPATCH_FAILED);
        // A fixed code, never the exception text (which names internal hosts).
        assertThat(response.getDispatchError()).isEqualTo(SlaPolicyComponent.DISPATCH_ERROR_CODE);
        assertThat(response.getLeadTimeDays()).isEqualTo(45);
    }

    @Test
    void unmappedFunctionUnitIsRejectedBeforeAnyWrite() {
        mapped(false);
        assertThatThrownBy(() -> component.update(FU, request(10, null), "u-1"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(policyRepository, never()).save(any());
    }

    @Test
    void recalculateWithoutLeadTimeIsRejected() {
        mapped(true);
        when(policyRepository.findById(FU)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> component.recalculate(FU, "u-1")).isInstanceOf(BusinessException.class);
        verify(portalClient, never()).submit(anyString(), any(Integer.class), anyString());
    }

    @Test
    void recalculateResubmitsCurrentVersion() {
        mapped(true);
        when(policyRepository.findById(FU)).thenReturn(Optional.of(policy(45, 7)));
        when(portalClient.submit(FU, 7, "u-1")).thenReturn("job-7");
        assertThat(component.recalculate(FU, "u-1")).isEqualTo("job-7");
    }

    @Test
    @SuppressWarnings("unchecked")
    void listSqlKeepsKeywordsSeparated() throws Exception {
        when(jdbcTemplate.query(any(PreparedStatementCreator.class), any(ResultSetExtractor.class)))
                .thenReturn(0L, List.of());
        component.query(new SlaPolicyListQueryRequest(0, 20, null, null, null));

        ArgumentCaptor<PreparedStatementCreator> creators = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        verify(jdbcTemplate, atLeastOnce()).query(creators.capture(), any(ResultSetExtractor.class));
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));
        for (PreparedStatementCreator creator : creators.getAllValues()) {
            creator.createPreparedStatement(connection);
        }
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection, atLeastOnce()).prepareStatement(sql.capture());
        // Text blocks strip leading indentation: a glued "latest_jobFROM" only fails against a real database.
        assertThat(sql.getAllValues()).hasSize(2)
                .allSatisfy(s -> assertThat(s).doesNotContainPattern("[A-Za-z_)]FROM"));
    }

    private void insertCreatesRow(boolean created) {
        when(jdbcTemplate.update(org.mockito.ArgumentMatchers.contains("ON CONFLICT"), any(Object[].class)))
                .thenReturn(created ? 1 : 0);
    }

    private void mapped(boolean mapped) {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(FU))).thenReturn(mapped ? 1L : 0L);
    }

    private SlaPolicyHistory lastSavedHistory() {
        ArgumentCaptor<SlaPolicyHistory> captor = ArgumentCaptor.forClass(SlaPolicyHistory.class);
        verify(historyRepository, atLeastOnce()).save(captor.capture());
        List<SlaPolicyHistory> all = captor.getAllValues();
        return all.get(all.size() - 1);
    }

    private static SlaPolicy policy(int days, int version) {
        return SlaPolicy.builder().functionUnitCode(FU).leadTimeDays(days).version(version).build();
    }

    private static SlaPolicyUpdateRequest request(int days, String reason) {
        SlaPolicyUpdateRequest request = new SlaPolicyUpdateRequest();
        request.setLeadTimeDays(java.math.BigDecimal.valueOf(days));
        request.setChangeReason(reason);
        return request;
    }
}
