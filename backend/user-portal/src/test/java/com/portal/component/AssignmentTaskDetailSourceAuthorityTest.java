package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.service.TaskActionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssignmentTaskDetailSourceAuthorityTest {
    @Test void aProtectedDetailCannotResurrectAnEmptyPortalSliceOrPersistAnEngineSnapshot() {
        var fixture=new Fixture(true);
        var task=fixture.detail.getTaskById("task").orElseThrow();
        verify(fixture.hydrator,never()).mergeFromEngine(anyString(),any(),any());
        verify(fixture.repository,never()).save(any());
        assertEquals(Map.of(),task.getVariables().get("__subTables__"));
    }
    @Test void unknownTrackingMetadataIsNotPermissionToPerformALegacyCacheWrite() {
        var fixture=new Fixture(null);
        fixture.detail.getTaskById("task").orElseThrow();
        verify(fixture.hydrator,never()).mergeFromEngine(anyString(),any(),any());
        verify(fixture.repository,never()).save(any());
    }
    @Test void positivelyLegacyDetailsKeepTheirOriginalHydrationBehavior() {
        var fixture=new Fixture(false);
        var task=fixture.detail.getTaskById("task").orElseThrow();
        verify(fixture.repository).save(any());
        assertFalse(((Map<?,?>)task.getVariables().get("__subTables__")).isEmpty());
    }
    static class Fixture {
        final ProcessInstanceRepository repository=mock(ProcessInstanceRepository.class);
        final EngineSubTableHydrator hydrator=mock(EngineSubTableHydrator.class);
        final TaskDetailQueryComponent detail;
        Fixture(Boolean tracked) {
            var engine=mock(WorkflowEngineClient.class); when(engine.isAvailable()).thenReturn(true);
            Map<String,Object> data=new LinkedHashMap<>(Map.of("taskId","task","processInstanceId","instance","variables",Map.of()));
            if (tracked!=null) data.put("assignmentTracked",tracked);
            when(engine.getTaskById("task")).thenReturn(Optional.of(data));
            when(repository.existsById("instance")).thenReturn(true);
            var instance=new ProcessInstance(); instance.setId("instance"); instance.setVariables(new HashMap<>(Map.of("__subTables__",Map.of())));
            when(repository.findById("instance")).thenReturn(Optional.of(instance));
            when(hydrator.mergeFromEngine(anyString(),any(),any())).thenReturn(Optional.of(new EngineSubTableHydrator.MergeResult(
                    Map.of("dw:renamed_rows",List.of(Map.of("platformRowUuid","old-row","reviewer","old-user"))),1)));
            detail=new TaskDetailQueryComponent(repository,engine,hydrator,mock(TaskActionService.class),mock(MiParticipantEnrichmentComponent.class),mock(RequestIdEnricher.class));
            ReflectionTestUtils.setField(detail,"processComponent",mock(ProcessComponent.class));
        }
    }
}
