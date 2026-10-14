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
