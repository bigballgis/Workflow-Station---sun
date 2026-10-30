package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubTableBatchTaskWriteContextTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={true,false})
    void protectedCompositeIdentityPreservesBooleanPrimaryKeyType(boolean flag) {
        var key=Map.of("tenant","HK","flag",flag);
        when(engine.requireTaskWriteContext("task-one")).thenReturn(Map.of("writeContext",Map.of(
                "taskId","task-one","processInstanceId","process-one","processDefinitionId","definition:1:frozen",
                "taskDefinitionKey","review","multiInstance",true,"currentItem",Map.of("rowKey",key))));
        var result=SubTableBatchTaskWriteContext.resolve(request(Map.of("__subTables__",Map.of()),Map.of()),engine,true);
        assertThat(result).containsEntry("_currentItem",Map.of("rowKey",key));
    }

    @Test void protectedAssignmentUsesServerScopeEvenWithoutBatchOrClientMiMarkers() {
        when(engine.requireTaskWriteContext("task-one")).thenReturn(context(true));
        Map<String,Object> input=Map.of("__subTables__",Map.of("dw:people",java.util.List.of()));
        var result=SubTableBatchTaskWriteContext.resolve(request(input,Map.of()),engine,true);
        assertThat(result).containsEntry("_currentItem",Map.of("rowKey",Map.of("renamedKey","actual-execution-row")));
        assertThat(input).doesNotContainKey("_currentItem");
    }
    @Test void protectedAssignmentCannotChooseAnotherParticipantThroughClientContext() {
        when(engine.requireTaskWriteContext("task-one")).thenReturn(context(true));
        var result=SubTableBatchTaskWriteContext.resolve(request(Map.of("currentItem",Map.of("rowKey",Map.of("key","other"))),Map.of()),engine,true);
        assertThat(result).doesNotContainKey("currentItem")
                .containsEntry("_currentItem",Map.of("rowKey",Map.of("renamedKey","actual-execution-row")));
    }
    private final WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
    private final Map<String, Object> baseline = Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of("schemaVersion", 1));

    @Test
    void oldInstancesAndEmptySubmissionsKeepTheirOriginalPathWithoutAnEngineRequest() {
        Map<String, Object> input = Map.of("__subTables__", Map.of(), "_currentItem", Map.of("rowKey", Map.of("key", "client")));
        assertThat(SubTableBatchTaskWriteContext.resolve(request(input, Map.of()), engine)).isSameAs(input);
        assertThat(SubTableBatchTaskWriteContext.resolve(request(null, baseline), engine)).isNull();
        verifyNoInteractions(engine);
    }

    @Test
    void enhancedMainOnlyCompletionStillRequiresAuthorityForOwnerWrites() {
        when(engine.requireTaskWriteContext("task-one")).thenReturn(context(false));
        assertThat(SubTableBatchTaskWriteContext.resolve(request(Map.of("action", "APPROVE",
                "_currentItem", Map.of("rowKey", Map.of("key", "stale-B"))), baseline), engine))
                .containsOnlyKeys("action");
        verify(engine).requireTaskWriteContext("task-one");
    }

    @Test
    void forgedAndOmittedClientItemsBothUseActualExecutionIdentityWithoutChangingInput() {
        when(engine.requireTaskWriteContext("task-one")).thenReturn(context(true));
        Map<String, Object> forged = Map.of("__subTables__", Map.of(), "_currentItem", Map.of("rowKey", Map.of("key", "sibling")),
                "currentItem", Map.of("rowKey", Map.of("key", "also-sibling")));
        Map<String, Object> resolved = SubTableBatchTaskWriteContext.resolve(request(forged, baseline), engine);
        assertThat(resolved.get("_currentItem")).isEqualTo(Map.of("rowKey", Map.of("renamedKey", "actual-execution-row")));
        assertThat(resolved).doesNotContainKey("currentItem");
        assertThat(forged.get("_currentItem")).isEqualTo(Map.of("rowKey", Map.of("key", "sibling")));
        assertThat(SubTableBatchTaskWriteContext.resolve(request(Map.of("__subTables__", Map.of()), baseline), engine))
                .isEqualTo(resolved);
    }

    @Test
    void actualNormalTaskRemovesForgedMiScopeRatherThanTrustingProcessWideItem() {
        when(engine.requireTaskWriteContext("task-one")).thenReturn(context(false));
        assertThat(SubTableBatchTaskWriteContext.resolve(request(Map.of("__subTables__", Map.of(),
                "_currentItem", Map.of("rowKey", Map.of("key", "forged"))), baseline), engine))
                .containsOnlyKeys("__subTables__");
    }

    @Test
    void absentOrMismatchedAuthorityDoesNotFallBackToClientScope() {
        Map<String, Object> input = Map.of("__subTables__", Map.of());
        when(engine.requireTaskWriteContext("task-one")).thenReturn(Map.of());
        assertThatThrownBy(() -> SubTableBatchTaskWriteContext.resolve(request(input, baseline), engine)).hasMessageContaining("write scope");
        for (String field : java.util.List.of("taskId", "processInstanceId", "processDefinitionId", "taskDefinitionKey", "multiInstance", "currentItem")) {
            var bad = new LinkedHashMap<>((Map<?, ?>) context(true).get("writeContext"));
            bad.remove(field);
            when(engine.requireTaskWriteContext("task-one")).thenReturn(Map.of("writeContext", bad));
            assertThatThrownBy(() -> SubTableBatchTaskWriteContext.resolve(request(input, baseline), engine)).hasMessageContaining("write scope");
        }
    }

    @Test
    void failedEngineAuthorityRemainsAnError() {
        when(engine.requireTaskWriteContext("task-one")).thenThrow(new IllegalStateException("synthetic unavailable"));
        assertThatThrownBy(() -> SubTableBatchTaskWriteContext.resolve(request(Map.of("__subTables__", Map.of()), baseline), engine))
                .hasMessage("synthetic unavailable");
    }

    @Test
    void ownerScopeCannotResurrectStaleBaselineOrTaskInfoParticipant() {
        when(engine.requireTaskWriteContext("task-one")).thenReturn(context(true));
        Map<String, Object> authority = SubTableBatchTaskWriteContext.resolve(request(Map.of("__subTables__", Map.of()), baseline), engine);
        var merged = new LinkedHashMap<String, Object>(Map.of("_currentItem", Map.of("rowKey", Map.of("key", "stale-B")),
                "currentItem", Map.of("rowKey", Map.of("key", "stale-B")), "business", "kept"));
        SubTableBatchTaskWriteContext.applyCurrentItem(authority, merged, true);
        assertThat(merged).containsEntry("_currentItem", Map.of("rowKey", Map.of("renamedKey", "actual-execution-row")))
                .doesNotContainKey("currentItem").containsEntry("business", "kept");
        when(engine.requireTaskWriteContext("task-one")).thenReturn(context(false));
        Map<String, Object> normal = SubTableBatchTaskWriteContext.resolve(request(Map.of("__subTables__", Map.of()), baseline), engine);
        SubTableBatchTaskWriteContext.applyCurrentItem(normal, merged, true);
        assertThat(merged).containsOnlyKeys("business");
    }

    @Test
    void legacyOwnerWithoutClientItemKeepsItsOriginalBaselineAndAliasBehavior() {
        var stale = Map.of("rowKey", Map.of("legacyKey", "legacy-baseline"));
        Map<String, Object> variables = new LinkedHashMap<>(Map.of("_currentItem", stale));
        SubTableBatchTaskWriteContext.applyCurrentItem(Map.of(), variables, false);
        assertThat(variables).containsEntry("_currentItem", stale);
        var alias = Map.of("rowKey", Map.of("legacyKey", "legacy-client"));
        SubTableBatchTaskWriteContext.applyCurrentItem(Map.of("currentItem", alias), variables, false);
        assertThat(variables).containsEntry("_currentItem", alias);
    }

    private static Map<String, Object> context(boolean mi) {
        return Map.of("writeContext", Map.of("taskId", "task-one", "processInstanceId", "process-one",
                "processDefinitionId", "definition:1:frozen", "taskDefinitionKey", "review", "multiInstance", mi,
                "currentItem", mi ? Map.of("rowKey", Map.of("renamedKey", "actual-execution-row")) : Map.of()));
    }

    private static SubTableBatchTaskWriteContext.Request request(Map<String, Object> input, Map<String, Object> variables) {
        return new SubTableBatchTaskWriteContext.Request("task-one", "process-one", "review", input, variables);
    }
}
