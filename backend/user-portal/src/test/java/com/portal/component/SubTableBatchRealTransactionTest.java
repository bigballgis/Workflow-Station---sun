package com.portal.component;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SubTableBatchRealTransactionTest {
    @ParameterizedTest
    @ValueSource(strings = {"entryKey", "renamedBusinessKey"})
    void ordinaryEditsRetainHistoricJsonTypesWithoutBatchRules(String pk) throws Exception {
        var harness = new BatchTaskFormTransactionHarness(pk, false);
        harness.editUnmarkedCaption(12);
        assertEquals(1, harness.writes.get());
        var rows = (List<?>) ((Map<?,?>) harness.read().get("__subTables__")).get("dw:lines");
        assertEquals(12, ((Map<?,?>) rows.get(0)).get("caption"));
        harness.editUnmarkedCaption("normal-single-form-value");
        assertEquals(2, harness.writes.get(), "Ordinary edit remains legal outside a batch form");
    }
    @ParameterizedTest
    @ValueSource(strings = {"entryKey", "renamedBusinessKey"})
    void invalidSecondEditedRowRejectsBeforeTheFirstDatabaseWrite(String pk) throws Exception {
        var harness = new BatchTaskFormTransactionHarness(pk, false);
        harness.enableEditContract();
        Map<String, Object> before = harness.read();
        assertThrows(com.platform.common.exception.BusinessException.class, () -> harness.editBothCaptions(12));
        assertEquals(0, harness.writes.get());
        assertEquals(before, harness.read());
        harness.editBothCaptions("valid-second-change");
        assertEquals(1, harness.writes.get());
    }
    @ParameterizedTest
    @ValueSource(strings = {"entryKey", "renamedBusinessKey"})
    void unmarkedRowsRequireCompleteUniqueConfiguredIdentities(String pk) throws Exception {
        var harness = new BatchTaskFormTransactionHarness(pk, false);
        for (List<Map<String, Object>> rows : List.of(
                List.of(Map.<String, Object>of("caption", "missing-key")),
                List.of(Map.<String, Object>of(pk, "one"), Map.<String, Object>of(pk, "one")))) {
            Map<String, Object> changed = new java.util.HashMap<>(harness.baseline);
            changed.put("__subTables__", Map.of("dw:lines", rows));
            assertThrows(com.portal.exception.PortalException.class, () -> harness.component.prepareSubTableSubmission(
                    "synthetic-process", "review", changed, "synthetic-user", List.of()));
            assertEquals(harness.baseline, harness.read());
        }
    }
    @ParameterizedTest
    @ValueSource(strings = {"entryKey", "renamedBusinessKey"})
    void omittingTheBatchMarkerCannotBypassPublishedReadonlyFields(String pk) throws Exception {
        var harness = new BatchTaskFormTransactionHarness(pk, false, true);
        assertThrows(com.portal.exception.PortalException.class, harness::editUnmarkedCaption);
        assertEquals(0, harness.writes.get());
        assertEquals(harness.baseline, harness.read());
    }

    @ParameterizedTest
    @ValueSource(strings = {"entryKey", "renamedBusinessKey"})
    void completePreflightRejectsReadonlyChangesEvenWithoutBatchDeclarations(String pk) throws Exception {
        var harness = new BatchTaskFormTransactionHarness(pk, false, true);
        Map<String, Object> changed = new java.util.HashMap<>(harness.baseline);
        changed.put("__subTables__", Map.of("dw:lines", List.of(Map.of(pk, "one", "caption", "forged"))));
        assertThrows(com.portal.exception.PortalException.class, () -> harness.component.prepareSubTableSubmission(
                "synthetic-process", "review", changed, "synthetic-user", List.of()));
        assertEquals(harness.baseline, harness.read());
    }
    @ParameterizedTest
    @ValueSource(strings = {"entryKey", "renamedBusinessKey"})
    void lateDatabaseFailureRollsBackAlreadyExecutedCanonicalWrite(String pk) throws Exception {
        var harness = new BatchTaskFormTransactionHarness(pk, true);
        assertThrows(DataIntegrityViolationException.class, () -> harness.deleteBoth(2));
        assertEquals(1, harness.writes.get(), "An actual SQL update must execute before the injected failure");
        assertEquals(harness.baseline, harness.read(), "Both rows, versions and capability metadata must roll back together");
    }

    @ParameterizedTest
    @ValueSource(strings = {"entryKey", "renamedBusinessKey"})
    void secondRowConflictRejectsTheWholeBatchBeforePersistence(String pk) throws Exception {
        var harness = new BatchTaskFormTransactionHarness(pk, false);
        assertThrows(com.portal.exception.PortalException.class, () -> harness.deleteBoth(1));
        assertEquals(0, harness.writes.get());
        assertEquals(harness.baseline, harness.read());
    }

    @ParameterizedTest
    @ValueSource(strings = {"entryKey", "renamedBusinessKey"})
    void validDeleteCommitsBothRowsAndServerDeletionIdentitiesInOneTransaction(String pk) throws Exception {
        var harness = new BatchTaskFormTransactionHarness(pk, false);
        harness.deleteBoth(2);
        assertEquals(1, harness.writes.get());
        Map<String, Object> saved = harness.read();
        assertEquals(List.of(), ((Map<?, ?>) saved.get("__subTables__")).get("dw:lines"));
        assertEquals(2, SubTableBatchDeletedRows.read(saved).get("dw:lines").identities().size());
        assertEquals(harness.baseline.get(SubTableWriteIsolation.BATCH_RUNTIME_FIELD), saved.get(SubTableWriteIsolation.BATCH_RUNTIME_FIELD));
    }
}
