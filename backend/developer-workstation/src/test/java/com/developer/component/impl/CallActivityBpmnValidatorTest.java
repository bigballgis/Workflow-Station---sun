package com.developer.component.impl;

import com.developer.dto.ValidationResult;
import com.developer.entity.FormDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.enums.FunctionUnitStartupMode;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.ProcessDefinitionRepository;
import com.developer.repository.TableDefinitionRepository;
import com.developer.entity.TableDefinition;
import com.developer.enums.TableType;
import com.developer.util.XmlEncodingUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Design-time rules for calling one Function Unit from another.
 *
 * <p>Also pins the isolation that matters most: a multi-instance sub-process is not a
 * cross-FU call, and must not be validated as one.
 */
@ExtendWith(MockitoExtension.class)
class CallActivityBpmnValidatorTest {

    private static final long CALLER_ID = 1L;
    private static final String CALLER_CODE = "fu-caller";
    private static final long CALLEE_ID = 2L;
    private static final String CALLEE_CODE = "fu-callee";

    @Mock private FunctionUnitRepository functionUnitRepository;
    @Mock private ProcessDefinitionRepository processDefinitionRepository;
    @Mock private FormDefinitionRepository formDefinitionRepository;
    @Mock private TableDefinitionRepository tableDefinitionRepository;

    private CallActivityBpmnValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CallActivityBpmnValidator(
                functionUnitRepository, processDefinitionRepository, formDefinitionRepository,
                tableDefinitionRepository);
    }

    private static FunctionUnit unit(Long id, String code, String name, FunctionUnitStartupMode mode) {
        FunctionUnit fu = new FunctionUnit();
        fu.setId(id);
        fu.setCode(code);
        fu.setName(name);
        fu.setStartupMode(mode);
        return fu;
    }

    private static String bpmnCalling(String calledElement) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="fu-caller">
                    <bpmn:startEvent id="Start_1" />
                    <bpmn:callActivity id="Call_1" name="Check" calledElement="%s" />
                    <bpmn:endEvent id="End_1" />
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(calledElement);
    }

    private void callerExists() {
        lenient().when(functionUnitRepository.findById(CALLER_ID))
                .thenReturn(Optional.of(unit(CALLER_ID, CALLER_CODE, "Caller", FunctionUnitStartupMode.STANDALONE)));
        lenient().when(functionUnitRepository.findByCode(CALLER_CODE))
                .thenReturn(Optional.of(unit(CALLER_ID, CALLER_CODE, "Caller", FunctionUnitStartupMode.STANDALONE)));
    }

    @Test
    void acceptsCallToCallableFunctionUnit() {
        callerExists();
        when(functionUnitRepository.findByCode(CALLEE_CODE))
                .thenReturn(Optional.of(unit(CALLEE_ID, CALLEE_CODE, "Callee", FunctionUnitStartupMode.CALLABLE)));
        when(processDefinitionRepository.findByFunctionUnitId(CALLEE_ID)).thenReturn(Optional.empty());

        ValidationResult result = validator.validateCallActivities(bpmnCalling(CALLEE_CODE), CALLER_ID);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void acceptsCallToUnitThatIsBothStandaloneAndCallable() {
        callerExists();
        when(functionUnitRepository.findByCode(CALLEE_CODE))
                .thenReturn(Optional.of(unit(CALLEE_ID, CALLEE_CODE, "Callee", FunctionUnitStartupMode.BOTH)));
        when(processDefinitionRepository.findByFunctionUnitId(CALLEE_ID)).thenReturn(Optional.empty());

        assertThat(validator.validateCallActivities(bpmnCalling(CALLEE_CODE), CALLER_ID).isValid()).isTrue();
    }

    @Test
    void rejectsCallToStandaloneOnlyFunctionUnit() {
        when(functionUnitRepository.findByCode(CALLEE_CODE))
                .thenReturn(Optional.of(unit(CALLEE_ID, CALLEE_CODE, "Callee", FunctionUnitStartupMode.STANDALONE)));

        ValidationResult result = validator.validateCallActivities(bpmnCalling(CALLEE_CODE), CALLER_ID);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).singleElement()
                .satisfies(e -> assertThat(e.getCode()).isEqualTo("CALL_TARGET_NOT_CALLABLE"));
    }

    @Test
    void rejectsCallToUnknownFunctionUnit() {
        when(functionUnitRepository.findByCode("fu-does-not-exist")).thenReturn(Optional.empty());

        ValidationResult result = validator.validateCallActivities(bpmnCalling("fu-does-not-exist"), CALLER_ID);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).singleElement()
                .satisfies(e -> assertThat(e.getCode()).isEqualTo("CALL_TARGET_NOT_FOUND"));
    }

    @Test
    void rejectsCallActivityWithoutTarget() {
        String bpmn = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_1" name="Unconfigured" />
                  </bpmn:process>
                </bpmn:definitions>
                """;

        ValidationResult result = validator.validateCallActivities(bpmn, CALLER_ID);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).singleElement().satisfies(e -> {
            assertThat(e.getCode()).isEqualTo("CALL_TARGET_MISSING");
            assertThat(e.getElementId()).isEqualTo("Call_1");
        });
    }

    /** The child form belongs to the CALLED unit — the opposite of every other node type. */
    @Test
    void rejectsChildFormThatIsNotInTheCalledUnit() {
        callerExists();
        when(functionUnitRepository.findByCode(CALLEE_CODE))
                .thenReturn(Optional.of(unit(CALLEE_ID, CALLEE_CODE, "Callee", FunctionUnitStartupMode.CALLABLE)));
        when(formDefinitionRepository.findByFunctionUnitIdAndFormName(CALLEE_ID, "Ghost Form"))
                .thenReturn(Optional.empty());

        String bpmn = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_1" calledElement="fu-callee">
                      <bpmn:extensionElements>
                        <custom:properties xmlns:custom="http://workflow.platform/schema/custom">
                          <custom:property name="childFormName" value="Ghost Form" />
                        </custom:properties>
                      </bpmn:extensionElements>
                    </bpmn:callActivity>
                  </bpmn:process>
                </bpmn:definitions>
                """;

        ValidationResult result = validator.validateCallActivities(bpmn, CALLER_ID);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).singleElement()
                .satisfies(e -> assertThat(e.getCode()).isEqualTo("CALL_FORM_NOT_IN_TARGET"));
    }

    @Test
    void acceptsChildFormThatExistsInTheCalledUnit() {
        callerExists();
        when(functionUnitRepository.findByCode(CALLEE_CODE))
                .thenReturn(Optional.of(unit(CALLEE_ID, CALLEE_CODE, "Callee", FunctionUnitStartupMode.CALLABLE)));
        when(formDefinitionRepository.findByFunctionUnitIdAndFormName(CALLEE_ID, "Review Form"))
                .thenReturn(Optional.of(new FormDefinition()));
        when(processDefinitionRepository.findByFunctionUnitId(CALLEE_ID)).thenReturn(Optional.empty());

        String bpmn = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_1" calledElement="fu-callee">
                      <bpmn:extensionElements>
                        <custom:properties xmlns:custom="http://workflow.platform/schema/custom">
                          <custom:property name="childFormName" value="Review Form" />
                        </custom:properties>
                      </bpmn:extensionElements>
                    </bpmn:callActivity>
                  </bpmn:process>
                </bpmn:definitions>
                """;

        assertThat(validator.validateCallActivities(bpmn, CALLER_ID).isValid()).isTrue();
    }

    /** A -> B -> A would start sub-processes endlessly at runtime. */
    @Test
    void rejectsCallCycle() {
        callerExists();
        when(functionUnitRepository.findByCode(CALLEE_CODE))
                .thenReturn(Optional.of(unit(CALLEE_ID, CALLEE_CODE, "Callee", FunctionUnitStartupMode.CALLABLE)));

        ProcessDefinition calleeProcess = new ProcessDefinition();
        calleeProcess.setBpmnXml(XmlEncodingUtil.encode("""
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="fu-callee">
                    <bpmn:callActivity id="Call_Back" calledElement="fu-caller" />
                  </bpmn:process>
                </bpmn:definitions>
                """));
        when(processDefinitionRepository.findByFunctionUnitId(CALLEE_ID))
                .thenReturn(Optional.of(calleeProcess));

        ValidationResult result = validator.validateCallActivities(bpmnCalling(CALLEE_CODE), CALLER_ID);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).singleElement()
                .satisfies(e -> assertThat(e.getCode()).isEqualTo("CALL_CYCLE_DETECTED"));
    }

    /**
     * A multi-instance sub-process is a different mechanism entirely. It must pass through this
     * validator untouched, or adding cross-FU calls would start breaking existing Function Units.
     */
    @Test
    void ignoresMultiInstanceSubProcessesEntirely() {
        String miBpmn = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:flowable="http://flowable.org/bpmn">
                  <bpmn:process id="fu-caller">
                    <bpmn:subProcess id="Activity_mi" name="multi">
                      <bpmn:multiInstanceLoopCharacteristics flowable:collection="rows"
                                                            flowable:elementVariable="currentItem" />
                      <bpmn:userTask id="Task_inner" name="sub form1" />
                    </bpmn:subProcess>
                  </bpmn:process>
                </bpmn:definitions>
                """;

        ValidationResult result = validator.validateCallActivities(miBpmn, CALLER_ID);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void ignoresDiagramsWithoutCallActivities() {
        String plain = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="fu-caller">
                    <bpmn:startEvent id="Start_1" />
                    <bpmn:userTask id="Task_1" name="Approve" />
                    <bpmn:endEvent id="End_1" />
                  </bpmn:process>
                </bpmn:definitions>
                """;

        assertThat(validator.validateCallActivities(plain, CALLER_ID).isValid()).isTrue();
    }

    @Test
    void toleratesNullAndBlankBpmn() {
        assertThat(validator.validateCallActivities(null, CALLER_ID).isValid()).isTrue();
        assertThat(validator.validateCallActivities("   ", CALLER_ID).isValid()).isTrue();
    }

    /** Several call activities in one diagram are each validated. */
    @Test
    void reportsEveryInvalidCallActivity() {
        when(functionUnitRepository.findByCode("fu-missing-a")).thenReturn(Optional.empty());
        when(functionUnitRepository.findByCode("fu-missing-b")).thenReturn(Optional.empty());

        String bpmn = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_1" calledElement="fu-missing-a" />
                    <bpmn:callActivity id="Call_2" calledElement="fu-missing-b" />
                  </bpmn:process>
                </bpmn:definitions>
                """;

        ValidationResult result = validator.validateCallActivities(bpmn, CALLER_ID);

        assertThat(result.getErrors()).hasSize(2);
    }

    private static String bpmnCallingPerRow(String rowsTableProperty) {
        return """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:custom="http://custom.bpmn.io/schema">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_rows" name="Check each" calledElement="%s">
                      <bpmn:extensionElements>
                        <custom:properties>%s</custom:properties>
                      </bpmn:extensionElements>
                      <bpmn:multiInstanceLoopCharacteristics isSequential="false" />
                    </bpmn:callActivity>
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(CALLEE_CODE, rowsTableProperty);
    }

    private void calleeIsCallable() {
        callerExists();
        lenient().when(functionUnitRepository.findByCode(CALLEE_CODE))
                .thenReturn(Optional.of(unit(CALLEE_ID, CALLEE_CODE, "Callee", FunctionUnitStartupMode.CALLABLE)));
        lenient().when(processDefinitionRepository.findByFunctionUnitId(CALLEE_ID)).thenReturn(Optional.empty());
    }

    private static TableDefinition table(String name, TableType type) {
        TableDefinition table = new TableDefinition();
        table.setTableName(name);
        table.setTableType(type);
        return table;
    }

    /** Without a row source the engine has nothing to call for and stops the request at the step. */
    @Test
    void rejectsAPerRowCallThatDoesNotSayWhichRows() {
        calleeIsCallable();

        ValidationResult result = validator.validateCallActivities(bpmnCallingPerRow(""), CALLER_ID);

        assertThat(result.getErrors()).singleElement()
                .satisfies(e -> assertThat(e.getCode()).isEqualTo("CALL_ROWS_TABLE_MISSING"));
    }

    @Test
    void rejectsRowsFromATableThatIsNotOneOfThisUnitsSubTables() {
        calleeIsCallable();
        when(tableDefinitionRepository.findByFunctionUnitIdAndTableName(CALLER_ID, "purchase_request"))
                .thenReturn(Optional.of(table("purchase_request", TableType.MAIN)));

        ValidationResult result = validator.validateCallActivities(
                bpmnCallingPerRow("<custom:property name=\"callRowsTable\" value=\"purchase_request\" />"), CALLER_ID);

        assertThat(result.getErrors()).singleElement()
                .satisfies(e -> assertThat(e.getCode()).isEqualTo("CALL_ROWS_TABLE_NOT_FOUND"));
    }

    @Test
    void acceptsAPerRowCallOverOneOfThisUnitsSubTables() {
        calleeIsCallable();
        when(tableDefinitionRepository.findByFunctionUnitIdAndTableName(CALLER_ID, "extra_vendors"))
                .thenReturn(Optional.of(table("extra_vendors", TableType.SUB)));

        ValidationResult result = validator.validateCallActivities(
                bpmnCallingPerRow("<custom:property name=\"callRowsTable\" value=\"extra_vendors\" />"), CALLER_ID);

        assertThat(result.isValid()).isTrue();
    }
}
