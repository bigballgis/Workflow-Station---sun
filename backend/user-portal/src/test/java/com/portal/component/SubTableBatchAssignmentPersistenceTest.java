package com.portal.component;

import com.portal.exception.PortalException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubTableBatchAssignmentPersistenceTest {
    @Test
    void unmarkedSaveCannotChangeInitialAssignmentAfterTargetHasCompleted() throws Exception {
        var harness = new BatchTaskFormTransactionHarness("renamedEntryKey", false);
        harness.enableAssignmentContract(true);
        var before = harness.read();
        PortalException failure = assertThrows(PortalException.class, harness::editUnmarkedCaption);
        assertTrue(failure.getMessage().contains("already started"));
        assertEquals(0, harness.writes.get());
        assertEquals(before, harness.read());
    }

    @Test
    void originalSingleRowSaveStillWorksBeforeTheConfiguredTargetStarts() throws Exception {
        var harness = new BatchTaskFormTransactionHarness("renamedEntryKey", false);
        harness.enableAssignmentContract(false);
        directory(harness, "ACTIVE");
        harness.editUnmarkedCaption();
        assertEquals(1, harness.writes.get());
        var tables = (java.util.Map<?, ?>) harness.read().get("__subTables__");
        var rows = (java.util.List<?>) tables.get("dw:lines");
        assertEquals("MUST-NOT-WRITE", ((java.util.Map<?, ?>) rows.get(0)).get("caption"));
        verify(harness.engine, times(1)).getTaskHistory("synthetic-process");
    }

    @Test
    void inactivePersonCannotBeSavedByOmittingBatchMarkers() throws Exception {
        var harness = new BatchTaskFormTransactionHarness("renamedEntryKey", false);
        harness.enableAssignmentContract(false);
        directory(harness, "INACTIVE");
        var before = harness.read();
        assertEquals("Initial assignment person target is not currently active",
                assertThrows(PortalException.class, harness::editUnmarkedCaption).getMessage());
        assertEquals(0, harness.writes.get());
        assertEquals(before, harness.read());
    }

    private static void directory(BatchTaskFormTransactionHarness harness, String status) {
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(harness.persistence.getDataSource());
        jdbc.execute("CREATE TABLE sys_users(id VARCHAR(100) PRIMARY KEY, status VARCHAR(20), deleted BOOLEAN)");
        jdbc.update("INSERT INTO sys_users VALUES(?, ?, ?)", "MUST-NOT-WRITE", status, false);
        org.springframework.test.util.ReflectionTestUtils.setField(harness.component, "jdbcTemplate", jdbc);
    }

    @Test
    void completePayloadCannotBypassStageByOmittingTheBatchMarker() throws Exception {
        var harness = new BatchTaskFormTransactionHarness("renamedEntryKey", false);
        harness.enableAssignmentContract(true);
        var submitted = harness.mapper.convertValue(harness.read(), new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() { });
        @SuppressWarnings("unchecked")
        var tables = (java.util.Map<String, Object>) submitted.get("__subTables__");
        @SuppressWarnings("unchecked")
        var rows = (java.util.List<java.util.Map<String, Object>>) tables.get("dw:lines");
        rows.get(0).put("caption", "changed-on-complete");
        PortalException failure = assertThrows(PortalException.class, () -> harness.component.validateSubTableBatchPayload(
                "review", "synthetic-process", null, null, submitted));
        assertTrue(failure.getMessage().contains("already started"));
        assertEquals(0, harness.writes.get());
    }
}
