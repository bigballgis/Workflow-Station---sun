package com.admin.component;

import com.admin.entity.FunctionUnit;
import com.admin.entity.FunctionUnitContent;
import com.admin.enums.ContentType;
import com.admin.repository.FunctionUnitContentRepository;
import com.admin.repository.FunctionUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

/**
 * Rewriting version-pinned Function Unit calls at deploy time.
 *
 * <p>The behaviour that matters most is the refusal: if a pin cannot be resolved, the
 * deployment must fail. Falling back to the newest version would hand the caller a
 * different process than the one it was designed against — silently, and only visible
 * once the process runs.
 */
@ExtendWith(MockitoExtension.class)
class CallActivityVersionResolverTest {

    private static final String CALLEE_ID = "callee-uuid";

    @Mock private FunctionUnitRepository functionUnitRepository;
    @Mock private FunctionUnitContentRepository contentRepository;

    private CallActivityVersionResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CallActivityVersionResolver(functionUnitRepository, contentRepository);
    }

    private void givenDeployedVersion(String code, String version, String definitionId) {
        FunctionUnit unit = new FunctionUnit();
        unit.setId(CALLEE_ID);
        unit.setCode(code);
        unit.setVersion(version);
        lenient().when(functionUnitRepository.findByCodeAndVersion(code, version))
                .thenReturn(Optional.of(unit));

        FunctionUnitContent content = new FunctionUnitContent();
        content.setFlowableProcessDefinitionId(definitionId);
        lenient().when(contentRepository.findByFunctionUnitIdAndContentType(CALLEE_ID, ContentType.PROCESS))
                .thenReturn(List.of(content));
    }

    private static String pinnedCall(String code, String version) {
        return """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:custom="http://workflow.platform/schema/custom">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_1" name="Check" calledElement="%s">
                      <bpmn:extensionElements>
                        <custom:properties>
                          <custom:property name="calledVersion" value="%s" />
                        </custom:properties>
                      </bpmn:extensionElements>
                    </bpmn:callActivity>
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(code, version);
    }

    @Test
    void rewritesAPinnedCallToTheExactDeployedDefinition() {
        givenDeployedVersion("fu-callee", "1.2.0", "fu-callee:3:abc-def");

        String out = resolver.resolvePinnedCalls(pinnedCall("fu-callee", "1.2.0"));

        assertThat(out).contains("calledElement=\"fu-callee:3:abc-def\"");
        assertThat(out).contains("flowable:calledElementType=\"id\"");
        // The key form must be gone, or Flowable would still resolve "latest".
        assertThat(out).doesNotContain("calledElement=\"fu-callee\"");
    }

    /** Without a pin the call follows whatever is deployed — the BPMN is untouched. */
    @Test
    void leavesUnpinnedCallsAlone() {
        String bpmn = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_1" calledElement="fu-callee" />
                  </bpmn:process>
                </bpmn:definitions>
                """;

        assertThat(resolver.resolvePinnedCalls(bpmn)).isEqualTo(bpmn);
    }

    @Test
    void leavesDiagramsWithoutCallActivitiesAlone() {
        String bpmn = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="fu-caller">
                    <bpmn:userTask id="Task_1" name="Approve" />
                  </bpmn:process>
                </bpmn:definitions>
                """;

        assertThat(resolver.resolvePinnedCalls(bpmn)).isEqualTo(bpmn);
    }

    /** The core refusal: never quietly fall through to the newest version. */
    @Test
    void refusesWhenThePinnedVersionIsNotDeployed() {
        lenient().when(functionUnitRepository.findByCodeAndVersion("fu-callee", "9.9.9"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolvePinnedCalls(pinnedCall("fu-callee", "9.9.9")))
                .isInstanceOf(CallActivityPinUnresolvableException.class)
                .hasMessageContaining("9.9.9")
                .hasMessageContaining("fu-callee");
    }

    /** The version exists in the catalog but was never deployed to the engine. */
    @Test
    void refusesWhenThePinnedVersionHasNoDeployedProcess() {
        FunctionUnit unit = new FunctionUnit();
        unit.setId(CALLEE_ID);
        unit.setCode("fu-callee");
        unit.setVersion("1.2.0");
        lenient().when(functionUnitRepository.findByCodeAndVersion("fu-callee", "1.2.0"))
                .thenReturn(Optional.of(unit));

        FunctionUnitContent undeployed = new FunctionUnitContent();
        undeployed.setFlowableProcessDefinitionId(null);
        lenient().when(contentRepository.findByFunctionUnitIdAndContentType(CALLEE_ID, ContentType.PROCESS))
                .thenReturn(List.of(undeployed));

        assertThatThrownBy(() -> resolver.resolvePinnedCalls(pinnedCall("fu-callee", "1.2.0")))
                .isInstanceOf(CallActivityPinUnresolvableException.class)
                .hasMessageContaining("no deployed process");
    }

    @Test
    void toleratesNullAndBlankBpmn() {
        assertThat(resolver.resolvePinnedCalls(null)).isNull();
        assertThat(resolver.resolvePinnedCalls("")).isEmpty();
    }

    /** A multi-instance pinned call is rewritten like any other. */
    @Test
    void rewritesAPinnedMultiInstanceCall() {
        givenDeployedVersion("fu-callee", "2.0.0", "fu-callee:7:xyz");

        String bpmn = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:flowable="http://flowable.org/bpmn"
                                  xmlns:custom="http://workflow.platform/schema/custom">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_Many" calledElement="fu-callee">
                      <bpmn:extensionElements>
                        <custom:properties>
                          <custom:property name="calledVersion" value="2.0.0" />
                        </custom:properties>
                      </bpmn:extensionElements>
                      <bpmn:multiInstanceLoopCharacteristics flowable:collection="rows" />
                    </bpmn:callActivity>
                  </bpmn:process>
                </bpmn:definitions>
                """;

        String out = resolver.resolvePinnedCalls(bpmn);

        assertThat(out).contains("calledElement=\"fu-callee:7:xyz\"");
        // The loop characteristics must survive the rewrite untouched.
        assertThat(out).contains("multiInstanceLoopCharacteristics");
    }
}
