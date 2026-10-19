package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubTableBatchWorkspaceContextTest {
    private final SubTableWriteIsolation isolation = new SubTableWriteIsolation(mock(MiSubTaskSubTableRowMerger.class), mock(SubTableBindingScopeGuard.class));
    private static Map<String, Object> baseline() {
        return Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of(), "activeBusinessUnitId", "server-bu-id");
    }
    @Test void headerOnlySaveCannotChangeTheAuthorityForContextBasedRoleAssignment() {
        assertThatThrownBy(() -> SubTableBatchTaskPermissions.validate(baseline(), null,
                Map.of("activeBusinessUnitId", "forged-bu-id"), new ObjectMapper()))
                .hasMessage("Instance workspace context cannot be changed by form data");
    }
    @Test void sharedWriteBoundaryRejectsContextChangesEvenWithoutAnySubTable() {
        assertThatThrownBy(() -> apply(baseline(), Map.of("activeBusinessUnitId", "forged-bu-id")))
                .hasMessage("Instance workspace context cannot be changed by form data");
        assertThatThrownBy(() -> apply(baseline(), Map.of("activeRoleId", "forged-role-id")))
                .hasMessage("Instance workspace context cannot be changed by form data");
    }
    @Test void echoAndOmissionAreLegalButClearOrBootstrapAreNot() {
        assertThatCode(() -> apply(baseline(), Map.of("activeBusinessUnitId", "server-bu-id"))).doesNotThrowAnyException();
        assertThatCode(() -> apply(baseline(), Map.of("caption", "business edit"))).doesNotThrowAnyException();
        var clear = new HashMap<String, Object>(); clear.put("activeBusinessUnitId", null);
        assertThatThrownBy(() -> apply(baseline(), clear)).hasMessageContaining("workspace context");
        var absent = Map.<String, Object>of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of());
        assertThatThrownBy(() -> apply(absent, Map.of("activeBusinessUnitId", "forged-bu-id"))).hasMessageContaining("workspace context");
    }
    @Test void legacyInstancesKeepTheirExistingPath() {
        assertThatCode(() -> apply(Map.of(), Map.of("activeBusinessUnitId", "legacy-value"))).doesNotThrowAnyException();
        assertThatCode(() -> SubTableBatchTaskPermissions.validate(Map.of(), null,
                Map.of("activeBusinessUnitId", "legacy-value"), new ObjectMapper())).doesNotThrowAnyException();
    }
    private void apply(Map<String, Object> baseline, Map<String, Object> incoming) {
        var payload = new HashMap<>(incoming);
        isolation.apply(new SubTableWriteIsolation.Request(payload, payload, baseline, null, null, "synthetic-unit"));
    }
}
