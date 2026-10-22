package com.workflow.component;

import com.platform.common.functionunit.FunctionUnitVersionException;
import com.platform.common.functionunit.RuntimeVersionContext;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuntimeVersionWriteGuardTest {
    private final RuntimeService runtime = mock(RuntimeService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RuntimeVersionWriteGuard guard = new RuntimeVersionWriteGuard(runtime, jdbc);

    @Test void directCompletionCannotChangeAnExistingRelease() {
        when(runtime.getVariables("old", RuntimeVersionContext.metadataKeys())).thenReturn(Map.of("functionUnitId", "v1"));
        assertThatThrownBy(() -> guard.validate("old", Map.of("functionUnitId", "v2")))
                .isInstanceOf(FunctionUnitVersionException.class);
        guard.validate("old", Map.of("functionUnitId", "v1"));
        verifyNoInteractions(jdbc);
    }

    @Test void trustedPortalAssociationCanRestoreMissingEngineMetadata() {
        when(runtime.getVariables("legacy", RuntimeVersionContext.metadataKeys())).thenReturn(Map.of());
        when(jdbc.queryForList(anyString(), eq("legacy"))).thenReturn(List.of(Map.of(
                "function_unit_catalog_id", "old-catalog", "function_unit_code", "purchase", "function_unit_version_label", "1.0.0")));
        guard.validate("legacy", Map.of("functionUnitId", "old-catalog", "functionUnitCode", "purchase", "functionUnitVersionLabel", "1.0.0"));
        assertThatThrownBy(() -> guard.validate("legacy", Map.of("functionUnitId", "new-catalog")))
                .isInstanceOf(FunctionUnitVersionException.class);
    }

    @Test void requestStartedBeforeVersionLabelsTakesTheMissingLabelFromThePortalPinOfTheSameRelease() {
        // Started before runtime versioning: the engine holds the release id and code, not the label.
        when(runtime.getVariables("partial", RuntimeVersionContext.metadataKeys())).thenReturn(Map.of(
                "functionUnitId", "old-catalog", "functionUnitCode", "purchase"));
        when(jdbc.queryForList(anyString(), eq("partial"))).thenReturn(List.of(Map.of(
                "function_unit_catalog_id", "old-catalog", "function_unit_code", "purchase", "function_unit_version_label", "1.0.0")));

        guard.validate("partial", Map.of("functionUnitId", "old-catalog", "functionUnitCode", "purchase", "functionUnitVersionLabel", "1.0.0"));
        assertThatThrownBy(() -> guard.validate("partial", Map.of("functionUnitVersionLabel", "1.0.1")))
                .isInstanceOf(FunctionUnitVersionException.class);
    }

    @Test void portalPinOfAnotherReleaseDoesNotFillTheEngineGaps() {
        when(runtime.getVariables("partial", RuntimeVersionContext.metadataKeys())).thenReturn(Map.of(
                "functionUnitId", "old-catalog", "functionUnitCode", "purchase"));
        when(jdbc.queryForList(anyString(), eq("partial"))).thenReturn(List.of(Map.of(
                "function_unit_catalog_id", "other-catalog", "function_unit_code", "purchase", "function_unit_version_label", "1.0.0")));

        assertThatThrownBy(() -> guard.validate("partial", Map.of("functionUnitVersionLabel", "1.0.0")))
                .isInstanceOf(FunctionUnitVersionException.class);
    }
}
