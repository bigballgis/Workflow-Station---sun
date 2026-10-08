package com.workflow.util;

import com.workflow.exception.WorkflowBusinessException;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.CallActivity;
import org.flowable.bpmn.model.IOParameter;
import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Designer data settings on a call step → the Flowable XML that actually moves the data.
 *
 * <p>Asserted on the model Flowable itself reads back, not on strings: what matters is that the
 * engine sees the collection and the in/out parameters.
 */
class CallActivityDataMappingCompilerTest {

    private static String bpmn(String callBody, String loop) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:custom="http://custom.bpmn.io/schema"
                                  targetNamespace="t" id="d">
                  <bpmn:process id="fu-purchase" isExecutable="true">
                    <bpmn:startEvent id="s"/>
                    <bpmn:callActivity id="Call_1" name="Check" calledElement="fu-vendor">
                      <bpmn:extensionElements>
                        <custom:properties>%s</custom:properties>
                      </bpmn:extensionElements>
                      %s
                    </bpmn:callActivity>
                    <bpmn:endEvent id="e"/>
                    <bpmn:sequenceFlow id="f1" sourceRef="s" targetRef="Call_1"/>
                    <bpmn:sequenceFlow id="f2" sourceRef="Call_1" targetRef="e"/>
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(callBody, loop);
    }

    private static String prop(String name, String value) {
        return "<custom:property name=\"" + name + "\" value=\"" + value.replace("\"", "&quot;") + "\"/>";
    }

    private static final String PER_ROW = "<bpmn:multiInstanceLoopCharacteristics isSequential=\"false\"/>";

    private static CallActivity readBack(String xml) throws Exception {
        XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(xml));
        BpmnModel model = new BpmnXMLConverter().convertToBpmnModel(reader);
        return (CallActivity) model.getMainProcess().getFlowElement("Call_1");
    }

    private static List<String> describe(List<IOParameter> params) {
        return params.stream()
                .map(p -> (p.getSource() != null ? p.getSource() : p.getSourceExpression()) + "->" + p.getTarget())
                .toList();
    }

    @Test
    void perRowCallReadsItsRowsAndHandsEachRowsFieldsToTheCalledUnit() throws Exception {
        String xml = CallActivityDataMappingCompiler.compile(bpmn(
                prop("callRowsTable", "extra_vendors")
                        + prop("callInputMapping", "[{\"from\":\"row.vendor_name\",\"to\":\"vendor_name\"},"
                        + "{\"from\":\"department\",\"to\":\"category\"}]"),
                PER_ROW));

        CallActivity call = readBack(xml);
        assertThat(call.getLoopCharacteristics().getInputDataItem())
                .isEqualTo("${fuCallRows.of(execution, 'extra_vendors')}");
        assertThat(call.getLoopCharacteristics().getElementVariable()).isEqualTo("callRow");
        assertThat(describe(call.getInParameters()))
                .containsExactly("${callRow['vendor_name']}->vendor_name", "department->category",
                        "callRow->__callRow");
    }

    @Test
    void onceCallPassesValuesInAndCopiesResultsBack() throws Exception {
        String xml = CallActivityDataMappingCompiler.compile(bpmn(
                prop("callInputMapping", "[{\"from\":\"primary_vendor\",\"to\":\"vendor_name\"}]")
                        + prop("callOutputMapping", "[{\"from\":\"check_result\",\"to\":\"vendor_result\"}]"),
                ""));

        CallActivity call = readBack(xml);
        assertThat(describe(call.getInParameters())).containsExactly("primary_vendor->vendor_name");
        assertThat(describe(call.getOutParameters())).containsExactly("check_result->vendor_result");
    }

    @Test
    void leavesDiagramsWithoutCallSettingsByteForByte() {
        String xml = bpmn(prop("childFormName", "Vendor Review Form"), "");
        assertThat(CallActivityDataMappingCompiler.compile(xml)).isSameAs(xml);
    }

    @Test
    void ignoresMappingRowsTheDesignerLeftEmpty() throws Exception {
        String xml = CallActivityDataMappingCompiler.compile(bpmn(
                prop("callInputMapping", "[{\"from\":\"\",\"to\":\"\"},{\"from\":\"a\",\"to\":\"b\"}]"), ""));
        assertThat(describe(readBack(xml).getInParameters())).containsExactly("a->b");
    }

    /** Each row's call copies its result into that row: validated here, written by the portal. */
    @Test
    void perRowCallHandsItsChildTheRowAndLeavesRowCopyBackToThePortal() throws Exception {
        String xml = CallActivityDataMappingCompiler.compile(bpmn(
                prop("callRowsTable", "extra_vendors")
                        + prop("callOutputMapping", "[{\"from\":\"check_result\",\"to\":\"row.check_status\"}]"),
                PER_ROW));

        CallActivity call = readBack(xml);
        assertThat(describe(call.getInParameters())).containsExactly("callRow->__callRow");
        assertThat(call.getOutParameters()).isEmpty();
    }

    @Test
    void refusesToCopyAPerRowResultIntoARequestField() {
        assertThatThrownBy(() -> CallActivityDataMappingCompiler.compile(bpmn(
                prop("callRowsTable", "extra_vendors")
                        + prop("callOutputMapping", "[{\"from\":\"check_result\",\"to\":\"r\"}]"),
                PER_ROW)))
                .isInstanceOf(WorkflowBusinessException.class)
                .hasMessageContaining("Call_1")
                .hasMessageContaining("only copy values back into the row");
    }

    @Test
    void refusesRowCopyBackOnACallThatRunsOnce() {
        assertThatThrownBy(() -> CallActivityDataMappingCompiler.compile(bpmn(
                prop("callOutputMapping", "[{\"from\":\"check_result\",\"to\":\"row.check_status\"}]"), "")))
                .isInstanceOf(WorkflowBusinessException.class)
                .hasMessageContaining("does not run once per row");
    }

    @Test
    void refusesRowFieldsOnACallThatRunsOnce() {
        assertThatThrownBy(() -> CallActivityDataMappingCompiler.compile(bpmn(
                prop("callInputMapping", "[{\"from\":\"row.vendor_name\",\"to\":\"vendor_name\"}]"), "")))
                .isInstanceOf(WorkflowBusinessException.class)
                .hasMessageContaining("does not run once per row");
    }

    @Test
    void refusesARowsTableOnACallThatRunsOnce() {
        assertThatThrownBy(() -> CallActivityDataMappingCompiler.compile(bpmn(
                prop("callRowsTable", "extra_vendors"), "")))
                .isInstanceOf(WorkflowBusinessException.class)
                .hasMessageContaining("does not run once per row");
    }

    /** Names end up inside an engine expression; anything but a plain field name is rejected. */
    @Test
    void refusesNamesThatAreNotPlainFieldNames() {
        assertThatThrownBy(() -> CallActivityDataMappingCompiler.compile(bpmn(
                prop("callRowsTable", "x') + T(java.lang.Runtime).getRuntime() + ('"), PER_ROW)))
                .isInstanceOf(WorkflowBusinessException.class)
                .hasMessageContaining("not a valid field name");
    }

    @Test
    void refusesHalfFilledMappingRows() {
        assertThatThrownBy(() -> CallActivityDataMappingCompiler.compile(bpmn(
                prop("callInputMapping", "[{\"from\":\"a\",\"to\":\"\"}]"), "")))
                .isInstanceOf(WorkflowBusinessException.class)
                .hasMessageContaining("only one side");
    }
}
