package com.portal.automation;

import com.platform.common.exception.BusinessException;
import com.portal.component.ProcessStartComponent;
import com.portal.dto.ProcessInstanceInfo;
import com.portal.dto.ProcessStartRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomationProcessStartComponentTest {

    @Mock
    private AutomationAdminClient admin;
    @Mock
    private ProcessStartComponent processes;
    @Mock
    private JdbcTemplate jdbc;

    private AutomationProcessStartComponent component;

    @BeforeEach
    void setUp() {
        component = new AutomationProcessStartComponent(admin, processes,
                new AutomationIdempotency(jdbc, new com.fasterxml.jackson.databind.ObjectMapper()), jdbc);
    }

    @Test
    void rejectsACallerChosenInitiator() {
        assertThrows(BusinessException.class, () -> component.start(Map.of(
                "externalProjectId", "hermes-dg-1", "flowId", "flow-1",
                "functionUnitCode", "FU1", "startUserId", "someone"), "key-1"));
        verify(processes, never()).startProcessFromInternal(any(), any(), any());
    }

    @Test
    void refusesAFlowThatCannotStartOrAnotherFunctionUnit() {
        when(admin.scope("hermes-dg-1", "flow-1"))
                .thenReturn(new AutomationAdminClient.Scope(10L, "cat-1", Set.of("read")));
        when(jdbc.update(contains("INSERT"), any(), any(), any())).thenReturn(1);
        assertThrows(BusinessException.class, () -> component.start(request("FU1"), "key-2"));

        when(admin.scope("hermes-dg-1", "flow-1"))
                .thenReturn(new AutomationAdminClient.Scope(10L, "cat-1", Set.of("start")));
        when(jdbc.query(contains("dw_function_units"), any(RowMapper.class), eq(10L))).thenReturn(List.of("OTHER"));
        assertThrows(BusinessException.class, () -> component.start(request("FU1"), "key-3"));
        verify(processes, never()).startProcessFromInternal(any(), any(), any());
    }

    @Test
    void startsAsTheRuntimeIdentity() {
        when(admin.scope("hermes-dg-1", "flow-1"))
                .thenReturn(new AutomationAdminClient.Scope(10L, "cat-1", Set.of("start")));
        when(admin.identity("hermes-dg-1", "flow-1"))
                .thenReturn(new AutomationAdminClient.Identity("svc-1", "Platform"));
        when(jdbc.query(contains("dw_function_units"), any(RowMapper.class), eq(10L))).thenReturn(List.of("FU1"));
        when(processes.activeCatalogIdForInternalStart("FU1")).thenReturn("cat-1");
        when(jdbc.update(contains("INSERT"), any(), any(), any())).thenReturn(1);
        when(processes.startProcessFromInternal(eq("svc-1"), eq("FU1"), any())).thenReturn(ProcessInstanceInfo.builder()
                .id("pi-1").requestId("HR-1").status("RUNNING").startUserId("svc-1").build());

        Map<String, Object> started = component.start(request("FU1"), "key-4");

        assertEquals("pi-1", started.get("processInstanceId"));
        assertEquals("svc-1", started.get("startUserId"));
        ArgumentCaptor<ProcessStartRequest> captor = ArgumentCaptor.forClass(ProcessStartRequest.class);
        verify(processes).startProcessFromInternal(eq("svc-1"), eq("FU1"), captor.capture());
        assertEquals("OPEN", captor.getValue().getFormData().get("status"));
    }

    @Test
    void refusesADifferentEnabledCatalog() {
        when(admin.scope("hermes-dg-1", "flow-1"))
                .thenReturn(new AutomationAdminClient.Scope(10L, "cat-1", Set.of("start")));
        when(jdbc.query(contains("dw_function_units"), any(RowMapper.class), eq(10L))).thenReturn(List.of("FU1"));
        when(processes.activeCatalogIdForInternalStart("FU1")).thenReturn("cat-2");
        when(jdbc.update(contains("INSERT"), any(), any(), any())).thenReturn(1);
        when(jdbc.update(contains("DELETE"), any(Object.class))).thenReturn(1);

        assertThrows(BusinessException.class, () -> component.start(request("FU1"), "key-catalog"));
        verify(processes, never()).startProcessFromInternal(any(), any(), any());
    }

    @Test
    void replaysTheSameKeyAndRejectsADifferentBody() {
        when(jdbc.update(contains("INSERT"), any(), any(), any()))
                .thenThrow(new DuplicateKeyException("used"));
        when(jdbc.query(contains("up_automation_write_ledger"), any(RowMapper.class), eq("key-5")))
                .thenReturn(List.of("different-hash\n{\"processInstanceId\":\"pi-1\"}"));

        assertThrows(BusinessException.class, () -> component.start(request("FU1"), "key-5"));
        verify(processes, never()).startProcessFromInternal(any(), any(), any());
    }

    private static Map<String, Object> request(String code) {
        return Map.of("externalProjectId", "hermes-dg-1", "flowId", "flow-1",
                "functionUnitCode", code, "fields", Map.of("status", "OPEN"));
    }
}
