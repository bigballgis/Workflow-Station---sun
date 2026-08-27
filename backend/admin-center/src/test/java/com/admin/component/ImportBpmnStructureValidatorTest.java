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
