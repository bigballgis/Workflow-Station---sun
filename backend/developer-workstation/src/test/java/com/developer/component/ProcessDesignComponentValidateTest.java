package com.developer.component;

import com.developer.component.impl.ProcessDesignComponentImpl;
import com.developer.dto.ValidationResult;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.ProcessDefinitionRepository;
import com.developer.repository.TableDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProcessDesignComponent - validate")
class ProcessDesignComponentValidateTest {

    @Mock
    private ProcessDefinitionRepository processDefinitionRepository;

    @Mock
    private FunctionUnitRepository functionUnitRepository;

    @Mock
    private TableDefinitionRepository tableDefinitionRepository;

    @Mock
    private FormDefinitionRepository formDefinitionRepository;

    private ProcessDesignComponent processDesignComponent;

    @BeforeEach
    void setUp() {
        processDesignComponent = new ProcessDesignComponentImpl(
                processDefinitionRepository,
                functionUnitRepository,
                tableDefinitionRepository,
                formDefinitionRepository
        );
    }

    @Test
    @DisplayName("Should not treat sequence flows or BPMNDI elements as orphan nodes")
    void shouldIgnoreNonFlowNodesWhenCheckingOrphans() {
        String bpmnXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                                  id="Definitions_1">
                  <bpmn:process id="Process_1" isExecutable="true">
                    <bpmn:startEvent id="StartEvent_1">
                      <bpmn:outgoing>Flow_1</bpmn:outgoing>
                    </bpmn:startEvent>
                    <bpmn:userTask id="Task_1" name="Review">
                      <bpmn:incoming>Flow_1</bpmn:incoming>
                      <bpmn:outgoing>Flow_2</bpmn:outgoing>
                    </bpmn:userTask>
                    <bpmn:endEvent id="EndEvent_1">
                      <bpmn:incoming>Flow_2</bpmn:incoming>
                    </bpmn:endEvent>
                    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_1" />
                    <bpmn:sequenceFlow id="Flow_2" sourceRef="Task_1" targetRef="EndEvent_1" />
                  </bpmn:process>
                  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
                    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
                      <bpmndi:BPMNShape id="Task_1_di" bpmnElement="Task_1">
                        <dc:Bounds x="260" y="160" width="100" height="80" />
                      </bpmndi:BPMNShape>
                      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1">
                        <di:waypoint x="216" y="200" />
                        <di:waypoint x="260" y="200" />
                      </bpmndi:BPMNEdge>
                    </bpmndi:BPMNPlane>
                  </bpmndi:BPMNDiagram>
                </bpmn:definitions>
                """;

        ValidationResult result = processDesignComponent.validate(bpmnXml);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getWarnings())
                .noneMatch(warning -> "ORPHAN_NODE".equals(warning.getCode()));
    }

    @Test
    @DisplayName("Should still warn when an executable flow node is orphaned")
    void shouldWarnWhenFlowNodeIsOrphaned() {
        String bpmnXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="Process_1" isExecutable="true">
                    <bpmn:startEvent id="StartEvent_1">
                      <bpmn:outgoing>Flow_1</bpmn:outgoing>
                    </bpmn:startEvent>
                    <bpmn:endEvent id="EndEvent_1">
                      <bpmn:incoming>Flow_1</bpmn:incoming>
                    </bpmn:endEvent>
                    <bpmn:userTask id="Task_Orphaned" name="Orphaned" />
                    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="EndEvent_1" />
                  </bpmn:process>
                </bpmn:definitions>
                """;

        ValidationResult result = processDesignComponent.validate(bpmnXml);

        assertThat(result.getWarnings())
                .anyMatch(warning -> "ORPHAN_NODE".equals(warning.getCode())
                        && "Task_Orphaned".equals(warning.getElementId()));
    }
}
