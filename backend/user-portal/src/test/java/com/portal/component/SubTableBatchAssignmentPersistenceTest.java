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
        harness.editUnmarkedCaption();
        assertEquals(1, harness.writes.get());
        var tables = (java.util.Map<?, ?>) harness.read().get("__subTables__");
        var rows = (java.util.List<?>) tables.get("dw:lines");
        assertEquals("MUST-NOT-WRITE", ((java.util.Map<?, ?>) rows.get(0)).get("caption"));
        verify(harness.engine, times(1)).getTaskHistory("synthetic-process");
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
