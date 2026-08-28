package com.admin.component;

import com.admin.exception.AdminBusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportBpmnStructureValidatorTest {

    private final ImportBpmnStructureValidator validator = new ImportBpmnStructureValidator();

    @Test
    void oneNoneStart_passes() {
        validator.validate("""
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <process id="p">
                    <startEvent id="start"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """, "process.bpmn");
    }

    @Test
    void twoNoneStarts_fails() {
        assertThatThrownBy(() -> validator.validate("""
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <process id="p">
                    <startEvent id="a"/>
                    <startEvent id="b"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """, "process.bpmn"))
                .isInstanceOf(AdminBusinessException.class)
                .extracting(ex -> ((AdminBusinessException) ex).getErrorCode())
                .isEqualTo("FU_IMPORT_BPMN_START_EVENTS");
    }

    /**
     * An embedded sub-process has its own start event by BPMN definition. Counting start events
     * document-wide made every multi-instance Function Unit undeployable ("found 2") — the shape
     * below is the MI Subtask Demo's: one process start plus one inside the {@code multiInstance}
     * sub-process.
     */
    @Test
    void startEventInsideMultiInstanceSubProcess_isNotCountedAgainstTheProcess() {
        validator.validate("""
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:custom="http://workflow.platform/schema/custom"
                                  xmlns:flowable="http://flowable.org/bpmn">
                  <bpmn:process id="fu-mi">
                    <bpmn:startEvent id="StartEvent_1"/>
                    <bpmn:subProcess id="Activity_1m8yirt" name="multi">
                      <bpmn:extensionElements>
                        <custom:properties>
                          <custom:property name="multiInstance" value="true"/>
                        </custom:properties>
                      </bpmn:extensionElements>
                      <bpmn:multiInstanceLoopCharacteristics flowable:collection="participants"/>
                      <bpmn:startEvent id="Event_03ygley"/>
                      <bpmn:userTask id="MI_UserTask_1"/>
                      <bpmn:endEvent id="Event_subEnd"/>
                    </bpmn:subProcess>
                    <bpmn:endEvent id="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """, "process.bpmn");
    }

    /** Two start events on the PROCESS itself are still rejected — the rule is not weakened. */
    @Test
    void twoProcessLevelStarts_stillFails_evenWithASubProcessPresent() {
        assertThatThrownBy(() -> validator.validate("""
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="p">
                    <bpmn:startEvent id="a"/>
                    <bpmn:startEvent id="b"/>
                    <bpmn:subProcess id="sub">
                      <bpmn:startEvent id="subStart"/>
                    </bpmn:subProcess>
                    <bpmn:endEvent id="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """, "process.bpmn"))
                .isInstanceOf(AdminBusinessException.class)
                .extracting(ex -> ((AdminBusinessException) ex).getErrorCode())
                .isEqualTo("FU_IMPORT_BPMN_START_EVENTS");
    }

    /** A process whose ONLY start event sits in a sub-process has no entry point — still invalid. */
    @Test
    void noProcessLevelStart_fails_evenWhenASubProcessHasOne() {
        assertThatThrownBy(() -> validator.validate("""
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="p">
                    <bpmn:subProcess id="sub">
                      <bpmn:startEvent id="subStart"/>
                    </bpmn:subProcess>
                    <bpmn:endEvent id="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """, "process.bpmn"))
                .isInstanceOf(AdminBusinessException.class)
                .extracting(ex -> ((AdminBusinessException) ex).getErrorCode())
                .isEqualTo("FU_IMPORT_BPMN_START_EVENTS");
    }

    /** A timer/message start on the process is not a "none" start — unchanged by the scoping fix. */
    @Test
    void nonNoneStartOnProcess_isNotCounted() {
        assertThatThrownBy(() -> validator.validate("""
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="p">
                    <bpmn:startEvent id="timer">
                      <bpmn:timerEventDefinition/>
                    </bpmn:startEvent>
                    <bpmn:endEvent id="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """, "process.bpmn"))
                .isInstanceOf(AdminBusinessException.class)
                .extracting(ex -> ((AdminBusinessException) ex).getErrorCode())
                .isEqualTo("FU_IMPORT_BPMN_START_EVENTS");
    }

    /**
     * The real exported BPMN of the "Multi-Instance Subtask Demo" Function Unit, which deploy
     * rejected with "must have exactly one none start event (found 2)". Guards the fix against a
     * hand-written fixture drifting from what the designer actually emits.
     */
    @Test
    void realMultiInstanceSubtaskDemoBpmn_passes() throws Exception {
        String xml;
        try (var in = getClass().getResourceAsStream("/bpmn/mi-subtask-demo.bpmn.xml")) {
            assertThat(in).as("fixture /bpmn/mi-subtask-demo.bpmn.xml").isNotNull();
            xml = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        // Precondition: this file really does carry the two start events that used to trip the check.
        assertThat(xml.split("<bpmn:startEvent", -1).length - 1).isEqualTo(2);
        validator.validate(xml, "process.bpmn");
    }

    @Test
    void wrongCustomNamespace_fails() {
        assertThatThrownBy(() -> validator.validate("""
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:custom="http://workflow.platform/schema/bpmn">
                  <process id="p">
                    <startEvent id="start" custom:assigneeType="INITIATOR"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """, "process.bpmn"))
                .isInstanceOf(AdminBusinessException.class)
                .extracting(ex -> ((AdminBusinessException) ex).getErrorCode())
                .isEqualTo("FU_IMPORT_BPMN_CUSTOM_NS");
    }

    @Test
    void designerCustomNamespace_passes() {
        validator.validate("""
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:custom="http://workflow.platform/schema/custom">
                  <process id="p">
                    <startEvent id="start" custom:assigneeType="INITIATOR"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """, "process.bpmn");
        assertThat(ImportBpmnStructureValidator.DESIGNER_CUSTOM_NS)
                .isEqualTo("http://workflow.platform/schema/custom");
    }
}
