package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.HashMap;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MiCollectionInstanceBpmnTest {
    private final WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final MiCollectionVariableBuilder builder = new MiCollectionVariableBuilder(engine, jdbc);
    private static final String XML = """
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
              <bpmn:process id="process"><bpmn:userTask id="host"/></bpmn:process>
            </bpmn:definitions>
            """;

    @Test
    void enhancedCompletionReadsOnlyTheActiveInstancesDeployedSource() {
        when(engine.getBpmnXml("process", "instance")).thenReturn(Optional.of(XML));
        builder.injectMiCollectionFromBpmn("process", "host", "instance", new HashMap<>(), true);
        verify(engine).getBpmnXml("process", "instance");
        verify(engine, never()).getBpmnXml(anyString());
        verifyNoInteractions(jdbc);
    }

    @Test
    void unavailableOrWrongTaskSourceBlocksEnhancedCompletion() {
        assertThatThrownBy(() -> builder.injectMiCollectionFromBpmn("process", "host", "instance", new HashMap<>(), true))
                .hasMessageContaining("source cannot be verified");
        when(engine.getBpmnXml("process", "instance")).thenReturn(Optional.of(XML));
        assertThatThrownBy(() -> builder.injectMiCollectionFromBpmn("process", "foreign-node", "instance", new HashMap<>(), true))
                .hasMessageContaining("source cannot be verified");
        verify(engine, never()).getBpmnXml(anyString());
    }

    @Test
    void legacyCompletionRetainsItsOriginalKeyBasedSource() {
        when(engine.getBpmnXml("process")).thenReturn(Optional.of(XML));
        builder.injectMiCollectionFromBpmn("process", "host", "instance", new HashMap<>());
        verify(engine).getBpmnXml("process");
        verify(engine, never()).getBpmnXml(anyString(), anyString());
    }
}
