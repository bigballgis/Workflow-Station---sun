package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessStartDeploymentPinTest {
    private final ProcessStartComponent component = mock(ProcessStartComponent.class, CALLS_REAL_METHODS);
    private final WorkflowEngineClient engine = mock(WorkflowEngineClient.class);

    private void start(String definitionId, Map<String, Object> ignoredBatchSnapshot) {
        ReflectionTestUtils.setField(component, "workflowEngineClient", engine);
        ReflectionTestUtils.invokeMethod(component, "startPinnedProcess", new ProcessStartComponent.DeploymentPin("synthetic", definitionId),
                "synthetic-business", "synthetic-user", Map.of("businessField", "synthetic"));
    }
    @Test void enhancedStartForwardsOnlyServerDeploymentIdAndBusinessVariables() {
        start("synthetic:1:verified", Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of("schemaVersion", 1)));
        verify(engine).startProcessWithDeploymentPin("synthetic", "synthetic-business", "synthetic-user",
                Map.of("businessField", "synthetic"), "synthetic:1:verified");
        verifyNoMoreInteractions(engine);
    }
    @Test void missingEnhancedDeploymentIdCannotSilentlyFallBackToLatest() {
        for (String id : new String[] { null, "", " " }) {
            assertThatThrownBy(() -> start(id, Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of())))
                    .hasMessageContaining("verified deployment ID");
        }
        verifyNoInteractions(engine);
    }
    @Test void ordinaryStartAlsoUsesTheVerifiedDeployment() {
        start("synthetic:1:verified", Map.of());
        verify(engine).startProcessWithDeploymentPin("synthetic", "synthetic-business", "synthetic-user",
                Map.of("businessField", "synthetic"), "synthetic:1:verified");
        verifyNoMoreInteractions(engine);
    }

    @Test void ordinaryStartWithoutDeploymentMustNotUseLatest() {
        assertThatThrownBy(() -> start(null, Map.of())).hasMessageContaining("verified deployment ID");
        verifyNoInteractions(engine);
    }
}
