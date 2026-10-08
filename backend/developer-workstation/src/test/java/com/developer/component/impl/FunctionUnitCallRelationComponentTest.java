package com.developer.component.impl;

import com.developer.dto.FunctionUnitCallRelations;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.enums.FunctionUnitStartupMode;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.ProcessDefinitionRepository;
import com.developer.repository.VersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Deriving "who calls whom" from design-time BPMN.
 *
 * <p>The multi-instance cases matter most: a call activity's body holds nested
 * elements, so a parser that stops at the first closing tag reports the flag
 * wrongly — silently, and only for the nodes that have extension properties.
 */
@ExtendWith(MockitoExtension.class)
class FunctionUnitCallRelationComponentTest {

    private static final long CALLER_ID = 1L;
    private static final long CALLEE_ID = 2L;

    @Mock private FunctionUnitRepository functionUnitRepository;
    @Mock private ProcessDefinitionRepository processDefinitionRepository;
    @Mock private VersionRepository versionRepository;

    private FunctionUnitCallRelationComponent component;

    @BeforeEach
    void setUp() {
        component = new FunctionUnitCallRelationComponent(
                functionUnitRepository, processDefinitionRepository, versionRepository);
    }

    private static FunctionUnit unit(Long id, String code, String name, FunctionUnitStartupMode mode) {
        FunctionUnit fu = new FunctionUnit();
        fu.setId(id);
        fu.setCode(code);
        fu.setName(name);
        fu.setStartupMode(mode);
        return fu;
    }

    private static ProcessDefinition process(String xml) {
        ProcessDefinition pd = new ProcessDefinition();
        pd.setBpmnXml(xml);
        return pd;
    }

    private void givenCatalog(FunctionUnit... units) {
        lenient().when(functionUnitRepository.findAll()).thenReturn(List.of(units));
        for (FunctionUnit u : units) {
            lenient().when(functionUnitRepository.findById(u.getId())).thenReturn(Optional.of(u));
        }
    }

    private void givenBpmn(long functionUnitId, String xml) {
        lenient().when(processDefinitionRepository.findByFunctionUnitId(functionUnitId))
                .thenReturn(xml == null ? Optional.empty() : Optional.of(process(xml)));
    }

    @Test
    void reportsASingleCallAndItsTarget() {
        givenCatalog(
                unit(CALLER_ID, "fu-caller", "Caller", FunctionUnitStartupMode.STANDALONE),
                unit(CALLEE_ID, "fu-callee", "Callee", FunctionUnitStartupMode.CALLABLE));
        givenBpmn(CALLER_ID, """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_1" name="Check Vendor" calledElement="fu-callee" />
                  </bpmn:process>
                </bpmn:definitions>
                """);
        givenBpmn(CALLEE_ID, null);

        FunctionUnitCallRelations relations = component.resolve(CALLER_ID);

        assertThat(relations.getCalls()).singleElement().satisfies(call -> {
            assertThat(call.getCallActivityId()).isEqualTo("Call_1");
            assertThat(call.getCallActivityName()).isEqualTo("Check Vendor");
            assertThat(call.getCode()).isEqualTo("fu-callee");
            assertThat(call.getName()).isEqualTo("Callee");
            assertThat(call.getId()).isEqualTo(CALLEE_ID);
            assertThat(call.isCallable()).isTrue();
            assertThat(call.isMultiInstance()).isFalse();
        });
    }

    /**
     * The regression this test exists for: the call activity's body carries
     * extension properties before the loop characteristics, so a parser that
     * stopped at the first {@code </} reported multiInstance as false.
     */
    @Test
    void detectsMultiInstanceEvenWhenTheBodyHasNestedElementsFirst() {
        givenCatalog(
                unit(CALLER_ID, "fu-caller", "Caller", FunctionUnitStartupMode.STANDALONE),
                unit(CALLEE_ID, "fu-callee", "Callee", FunctionUnitStartupMode.CALLABLE));
        givenBpmn(CALLER_ID, """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:flowable="http://flowable.org/bpmn"
                                  xmlns:custom="http://workflow.platform/schema/custom">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_Many" name="Check Each" calledElement="fu-callee">
                      <bpmn:extensionElements>
                        <custom:properties>
                          <custom:property name="childFormName" value="Review Form" />
                        </custom:properties>
                      </bpmn:extensionElements>
                      <bpmn:multiInstanceLoopCharacteristics isSequential="false"
                          flowable:collection="rows" flowable:elementVariable="currentRow" />
                    </bpmn:callActivity>
                  </bpmn:process>
                </bpmn:definitions>
                """);
        givenBpmn(CALLEE_ID, null);

        FunctionUnitCallRelations relations = component.resolve(CALLER_ID);

        assertThat(relations.getCalls()).singleElement()
                .satisfies(call -> assertThat(call.isMultiInstance()).isTrue());
    }

    /** A call activity with extension properties but no loop is NOT multi-instance. */
    @Test
    void doesNotReportMultiInstanceForAPlainCallWithExtensions() {
        givenCatalog(
                unit(CALLER_ID, "fu-caller", "Caller", FunctionUnitStartupMode.STANDALONE),
                unit(CALLEE_ID, "fu-callee", "Callee", FunctionUnitStartupMode.CALLABLE));
        givenBpmn(CALLER_ID, """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:custom="http://workflow.platform/schema/custom">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_One" name="Check Once" calledElement="fu-callee">
                      <bpmn:extensionElements>
                        <custom:properties>
                          <custom:property name="childFormName" value="Review Form" />
                        </custom:properties>
                      </bpmn:extensionElements>
                    </bpmn:callActivity>
                  </bpmn:process>
                </bpmn:definitions>
                """);
        givenBpmn(CALLEE_ID, null);

        assertThat(component.resolve(CALLER_ID).getCalls()).singleElement()
                .satisfies(call -> assertThat(call.isMultiInstance()).isFalse());
    }

    /**
     * A pinned call reports the version it is bound to, and whether that version
     * still exists — a pin that no longer resolves fails the deploy, so it is worth
     * surfacing while the designer is still looking at the diagram.
     */
    @Test
    void reportsAPinnedVersionAndWhetherItStillExists() {
        FunctionUnit callee = unit(CALLEE_ID, "fu-callee", "Callee", FunctionUnitStartupMode.CALLABLE);
        callee.setCurrentVersion("2.0.0");
        givenCatalog(
                unit(CALLER_ID, "fu-caller", "Caller", FunctionUnitStartupMode.STANDALONE),
                callee);
        givenBpmn(CALLER_ID, """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:custom="http://workflow.platform/schema/custom">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_1" calledElement="fu-callee">
                      <bpmn:extensionElements>
                        <custom:properties>
                          <custom:property name="calledVersion" value="1.2.0" />
                        </custom:properties>
                      </bpmn:extensionElements>
                    </bpmn:callActivity>
                  </bpmn:process>
                </bpmn:definitions>
                """);
        givenBpmn(CALLEE_ID, null);
        lenient().when(versionRepository.findByFunctionUnitIdAndVersionNumber(CALLEE_ID, "1.2.0"))
                .thenReturn(Optional.of(new com.developer.entity.Version()));

        assertThat(component.resolve(CALLER_ID).getCalls()).singleElement().satisfies(call -> {
            assertThat(call.getPinnedVersion()).isEqualTo("1.2.0");
            assertThat(call.isPinnedVersionAvailable()).isTrue();
            assertThat(call.getCurrentVersion()).isEqualTo("2.0.0");
        });
    }

    /** A pin to a version the target no longer has must be flagged, not hidden. */
    @Test
    void flagsAPinnedVersionThatNoLongerExists() {
        FunctionUnit callee = unit(CALLEE_ID, "fu-callee", "Callee", FunctionUnitStartupMode.CALLABLE);
        callee.setCurrentVersion("2.0.0");
        givenCatalog(
                unit(CALLER_ID, "fu-caller", "Caller", FunctionUnitStartupMode.STANDALONE),
                callee);
        givenBpmn(CALLER_ID, """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:custom="http://workflow.platform/schema/custom">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_1" calledElement="fu-callee">
                      <bpmn:extensionElements>
                        <custom:properties>
                          <custom:property name="calledVersion" value="9.9.9" />
                        </custom:properties>
                      </bpmn:extensionElements>
                    </bpmn:callActivity>
                  </bpmn:process>
                </bpmn:definitions>
                """);
        givenBpmn(CALLEE_ID, null);
        lenient().when(versionRepository.findByFunctionUnitIdAndVersionNumber(CALLEE_ID, "9.9.9"))
                .thenReturn(Optional.empty());

        assertThat(component.resolve(CALLER_ID).getCalls()).singleElement()
                .satisfies(call -> assertThat(call.isPinnedVersionAvailable()).isFalse());
    }

    /** Pinned to 1.2.0 while the callee is on 2.0.0: the caller should be told. */
    @Test
    void flagsANewerVersionWhenThePinIsBehind() {
        FunctionUnit callee = unit(CALLEE_ID, "fu-callee", "Callee", FunctionUnitStartupMode.CALLABLE);
        callee.setCurrentVersion("2.0.0");
        givenCatalog(
                unit(CALLER_ID, "fu-caller", "Caller", FunctionUnitStartupMode.STANDALONE),
                callee);
        givenBpmn(CALLER_ID, pinnedCallTo("1.2.0"));
        givenBpmn(CALLEE_ID, null);
        lenient().when(versionRepository.findByFunctionUnitIdAndVersionNumber(CALLEE_ID, "1.2.0"))
                .thenReturn(Optional.of(new com.developer.entity.Version()));

        assertThat(component.resolve(CALLER_ID).getCalls()).singleElement()
                .satisfies(call -> assertThat(call.isNewerVersionAvailable()).isTrue());
    }

    @Test
    void doesNotFlagWhenThePinIsTheCurrentVersion() {
        FunctionUnit callee = unit(CALLEE_ID, "fu-callee", "Callee", FunctionUnitStartupMode.CALLABLE);
        callee.setCurrentVersion("2.0.0");
        givenCatalog(
                unit(CALLER_ID, "fu-caller", "Caller", FunctionUnitStartupMode.STANDALONE),
                callee);
        givenBpmn(CALLER_ID, pinnedCallTo("2.0.0"));
        givenBpmn(CALLEE_ID, null);

        assertThat(component.resolve(CALLER_ID).getCalls()).singleElement()
                .satisfies(call -> assertThat(call.isNewerVersionAvailable()).isFalse());
    }

    /** An unpinned call already follows the newest version, so nothing is "behind". */
    @Test
    void neverFlagsAnUnpinnedCall() {
        FunctionUnit callee = unit(CALLEE_ID, "fu-callee", "Callee", FunctionUnitStartupMode.CALLABLE);
        callee.setCurrentVersion("5.0.0");
        givenCatalog(
                unit(CALLER_ID, "fu-caller", "Caller", FunctionUnitStartupMode.STANDALONE),
                callee);
        givenBpmn(CALLER_ID, """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_1" calledElement="fu-callee" />
                  </bpmn:process>
                </bpmn:definitions>
                """);
        givenBpmn(CALLEE_ID, null);

        assertThat(component.resolve(CALLER_ID).getCalls()).singleElement()
                .satisfies(call -> assertThat(call.isNewerVersionAvailable()).isFalse());
    }

    /** String order would put 1.10.0 below 1.9.0 and report the wrong one as newer. */
    @Test
    void comparesVersionsNumericallyNotAsStrings() {
        assertThat(FunctionUnitCallRelationComponent.compareVersions("1.10.0", "1.9.0")).isPositive();
        assertThat(FunctionUnitCallRelationComponent.compareVersions("2.0.0", "1.99.99")).isPositive();
        assertThat(FunctionUnitCallRelationComponent.compareVersions("1.0.1", "1.0.1")).isZero();
        assertThat(FunctionUnitCallRelationComponent.compareVersions("1.0", "1.0.0")).isZero();
        assertThat(FunctionUnitCallRelationComponent.compareVersions("1.0.0", "1.0.1")).isNegative();
    }

    private static String pinnedCallTo(String version) {
        return """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:custom="http://workflow.platform/schema/custom">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_1" calledElement="fu-callee">
                      <bpmn:extensionElements>
                        <custom:properties>
                          <custom:property name="calledVersion" value="%s" />
                        </custom:properties>
                      </bpmn:extensionElements>
                    </bpmn:callActivity>
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(version);
    }

    /** An unpinned call follows whatever is deployed; that is not a problem to flag. */
    @Test
    void treatsAnUnpinnedCallAsAvailable() {
        givenCatalog(
                unit(CALLER_ID, "fu-caller", "Caller", FunctionUnitStartupMode.STANDALONE),
                unit(CALLEE_ID, "fu-callee", "Callee", FunctionUnitStartupMode.CALLABLE));
        givenBpmn(CALLER_ID, """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_1" calledElement="fu-callee" />
                  </bpmn:process>
                </bpmn:definitions>
                """);
        givenBpmn(CALLEE_ID, null);

        assertThat(component.resolve(CALLER_ID).getCalls()).singleElement().satisfies(call -> {
            assertThat(call.getPinnedVersion()).isNull();
            assertThat(call.isPinnedVersionAvailable()).isTrue();
        });
    }

    /** The direction a designer cannot see from their own diagram. */
    @Test
    void reportsWhoCallsThisUnit() {
        givenCatalog(
                unit(CALLER_ID, "fu-caller", "Caller", FunctionUnitStartupMode.STANDALONE),
                unit(CALLEE_ID, "fu-callee", "Callee", FunctionUnitStartupMode.CALLABLE));
        givenBpmn(CALLER_ID, """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_1" name="Check Vendor" calledElement="fu-callee" />
                  </bpmn:process>
                </bpmn:definitions>
                """);
        givenBpmn(CALLEE_ID, null);

        FunctionUnitCallRelations relations = component.resolve(CALLEE_ID);

        assertThat(relations.getCalls()).isEmpty();
        assertThat(relations.getCalledBy()).singleElement().satisfies(caller -> {
            assertThat(caller.getId()).isEqualTo(CALLER_ID);
            assertThat(caller.getName()).isEqualTo("Caller");
            assertThat(caller.getCallActivityName()).isEqualTo("Check Vendor");
        });
    }

    /** A target that exists but refuses calls would fail deployment — flag it early. */
    @Test
    void marksATargetThatDoesNotAllowBeingCalled() {
        givenCatalog(
                unit(CALLER_ID, "fu-caller", "Caller", FunctionUnitStartupMode.STANDALONE),
                unit(CALLEE_ID, "fu-callee", "Callee", FunctionUnitStartupMode.STANDALONE));
        givenBpmn(CALLER_ID, """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_1" calledElement="fu-callee" />
                  </bpmn:process>
                </bpmn:definitions>
                """);
        givenBpmn(CALLEE_ID, null);

        assertThat(component.resolve(CALLER_ID).getCalls()).singleElement()
                .satisfies(call -> assertThat(call.isCallable()).isFalse());
    }

    /** A dangling code is surfaced rather than dropped: deployment will reject it. */
    @Test
    void surfacesACallToAUnitThatDoesNotExist() {
        givenCatalog(unit(CALLER_ID, "fu-caller", "Caller", FunctionUnitStartupMode.STANDALONE));
        givenBpmn(CALLER_ID, """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_1" calledElement="fu-gone" />
                  </bpmn:process>
                </bpmn:definitions>
                """);

        assertThat(component.resolve(CALLER_ID).getCalls()).singleElement().satisfies(call -> {
            assertThat(call.getCode()).isEqualTo("fu-gone");
            assertThat(call.getId()).isNull();
            assertThat(call.isCallable()).isFalse();
        });
    }

    /** An unconfigured call step is worth showing too — it also fails deployment. */
    @Test
    void surfacesACallStepWithNoTargetYet() {
        givenCatalog(unit(CALLER_ID, "fu-caller", "Caller", FunctionUnitStartupMode.STANDALONE));
        givenBpmn(CALLER_ID, """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_Empty" name="Not configured" />
                  </bpmn:process>
                </bpmn:definitions>
                """);

        assertThat(component.resolve(CALLER_ID).getCalls()).singleElement().satisfies(call -> {
            assertThat(call.getCallActivityId()).isEqualTo("Call_Empty");
            assertThat(call.getCode()).isNull();
        });
    }

    /** A multi-instance SUB-PROCESS is not a call; it must not appear here. */
    @Test
    void ignoresMultiInstanceSubProcesses() {
        givenCatalog(unit(CALLER_ID, "fu-caller", "Caller", FunctionUnitStartupMode.STANDALONE));
        givenBpmn(CALLER_ID, """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:flowable="http://flowable.org/bpmn">
                  <bpmn:process id="fu-caller">
                    <bpmn:subProcess id="Activity_mi" name="multi">
                      <bpmn:multiInstanceLoopCharacteristics flowable:collection="rows" />
                      <bpmn:userTask id="Task_inner" />
                    </bpmn:subProcess>
                  </bpmn:process>
                </bpmn:definitions>
                """);

        FunctionUnitCallRelations relations = component.resolve(CALLER_ID);

        assertThat(relations.getCalls()).isEmpty();
        assertThat(relations.getCalledBy()).isEmpty();
    }

    @Test
    void returnsEmptyRelationsForAUnitWithNoProcess() {
        givenCatalog(unit(CALLER_ID, "fu-caller", "Caller", FunctionUnitStartupMode.STANDALONE));
        givenBpmn(CALLER_ID, null);

        FunctionUnitCallRelations relations = component.resolve(CALLER_ID);

        assertThat(relations.getCalls()).isEmpty();
        assertThat(relations.getCalledBy()).isEmpty();
    }

    @Test
    void returnsEmptyRelationsForAnUnknownFunctionUnit() {
        when(functionUnitRepository.findById(999L)).thenReturn(Optional.empty());

        FunctionUnitCallRelations relations = component.resolve(999L);

        assertThat(relations.getCalls()).isEmpty();
        assertThat(relations.getCalledBy()).isEmpty();
    }
}
