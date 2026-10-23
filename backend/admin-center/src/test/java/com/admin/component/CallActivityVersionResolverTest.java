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

import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
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
    @Mock private FunctionUnitLifecycleComponent lifecycle;

    private CallActivityVersionResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CallActivityVersionResolver(functionUnitRepository, contentRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(resolver, "lifecycle", lifecycle);
    }

    private FunctionUnit givenDeployedVersion(String code, String version, String definitionId) {
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
        return unit;
    }

    /** The release Portal would start a new request of {@code code} on: deployed, enabled, highest. */
    private void givenActiveRelease(String code, String version, String definitionId) {
        FunctionUnit unit = givenDeployedVersion(code, version, definitionId);
        lenient().when(lifecycle.getActiveCatalogForPortalStart(code)).thenReturn(Optional.of(unit));
    }

    /** What the called instance is handed: target variable → the constant expression. */
    private static java.util.Map<String, String> inParameters(Element call) {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        org.w3c.dom.NodeList ins = call.getElementsByTagNameNS("http://flowable.org/bpmn", "in");
        for (int i = 0; i < ins.getLength(); i++) {
            Element in = (Element) ins.item(i);
            out.put(in.getAttribute("target"), in.getAttribute("sourceExpression"));
        }
        return out;
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

    /**
     * The called instance is handed the release it runs, so whatever it does before the Portal
     * first records it (an email step right at its start) already resolves in that release.
     */
    @Test
    void handsTheCalledInstanceTheReleaseItRuns() throws Exception {
        givenDeployedVersion("fu-callee", "1.2.0", "fu-callee:3:abc-def");

        Element call = parsedCallActivity(resolver.resolvePinnedCalls(pinnedCall("fu-callee", "1.2.0")));

        assertThat(inParameters(call)).containsExactly(
                java.util.Map.entry("functionUnitId", "${'" + CALLEE_ID + "'}"),
                java.util.Map.entry("functionUnitCode", "${'fu-callee'}"),
                java.util.Map.entry("functionUnitVersionLabel", "${'1.2.0'}"));
        // Merged into the designer's own extension block: the engine reads the call's settings there.
        assertThat(call.getElementsByTagNameNS("*", "extensionElements").getLength()).isEqualTo(1);
        assertThat(call.getElementsByTagNameNS("*", "property").getLength()).isEqualTo(1);
    }

    /**
     * A call without a chosen version runs the called unit's release that was active when the
     * caller was deployed — never whatever is newest when a request reaches it, which may be a
     * release published, disabled or rolled back since.
     */
    @Test
    void pinsAnUnpinnedCallToTheReleaseActiveWhenTheCallerIsDeployed() throws Exception {
        givenActiveRelease("fu-callee", "1.3.0", "fu-callee:5:aaa");
        String bpmn = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_1" calledElement="fu-callee" />
                  </bpmn:process>
                </bpmn:definitions>
                """;

        Element call = parsedCallActivity(resolver.resolvePinnedCalls(bpmn));

        assertThat(call.getAttribute("calledElement")).isEqualTo("fu-callee:5:aaa");
        assertThat(call.getAttributeNS("http://flowable.org/bpmn", "calledElementType")).isEqualTo("id");
        assertThat(inParameters(call)).containsEntry("functionUnitVersionLabel", "${'1.3.0'}")
                .containsEntry("functionUnitId", "${'" + CALLEE_ID + "'}");
    }

    private static java.util.List<Element> childElements(Element parent) {
        java.util.List<Element> out = new java.util.ArrayList<>();
        for (org.w3c.dom.Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) out.add(e);
        }
        return out;
    }

    @Test
    void fillsAnEmptySelfClosingExtensionBlock() throws Exception {
        givenActiveRelease("fu-callee", "1.3.0", "fu-callee:5:aaa");
        String bpmn = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_1" calledElement="fu-callee"><bpmn:extensionElements/></bpmn:callActivity>
                  </bpmn:process>
                </bpmn:definitions>
                """;

        Element call = parsedCallActivity(resolver.resolvePinnedCalls(bpmn));

        assertThat(call.getElementsByTagNameNS("*", "extensionElements").getLength()).isEqualTo(1);
        assertThat(inParameters(call)).hasSize(3);
    }

    /**
     * The release goes into the call's own extension block — after its documentation, as the schema
     * orders it — never into a nested element's block, which the engine would not read for the call.
     */
    @Test
    void addsTheCallsOwnExtensionBlockAfterDocumentationNotInsideTheLoop() throws Exception {
        givenActiveRelease("fu-callee", "1.3.0", "fu-callee:5:aaa");
        String bpmn = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:custom="http://workflow.platform/schema/custom">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_1" calledElement="fu-callee">
                      <bpmn:documentation>Checks the vendor</bpmn:documentation>
                      <bpmn:multiInstanceLoopCharacteristics>
                        <bpmn:extensionElements><custom:properties /></bpmn:extensionElements>
                      </bpmn:multiInstanceLoopCharacteristics>
                    </bpmn:callActivity>
                  </bpmn:process>
                </bpmn:definitions>
                """;

        Element call = parsedCallActivity(resolver.resolvePinnedCalls(bpmn));

        java.util.List<Element> children = childElements(call);
        assertThat(children).extracting(Element::getLocalName)
                .containsExactly("documentation", "extensionElements", "multiInstanceLoopCharacteristics");
        assertThat(children.get(1).getElementsByTagNameNS("http://flowable.org/bpmn", "in").getLength()).isEqualTo(3);
        assertThat(children.get(2).getElementsByTagNameNS("http://flowable.org/bpmn", "in").getLength()).isZero();
    }

    @Test
    void pinsEveryCallInTheDiagram() throws Exception {
        givenActiveRelease("fu-callee", "1.3.0", "fu-callee:5:aaa");
        String bpmn = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_1" calledElement="fu-callee" />
                    <bpmn:callActivity id="Call_2" calledElement="fu-callee">
                      <bpmn:extensionElements></bpmn:extensionElements>
                    </bpmn:callActivity>
                  </bpmn:process>
                </bpmn:definitions>
                """;

        String out = resolver.resolvePinnedCalls(bpmn);
        org.w3c.dom.NodeList calls = DocumentBuilderFactory.newDefaultNSInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(out.getBytes(StandardCharsets.UTF_8)))
                .getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "callActivity");

        assertThat(calls.getLength()).isEqualTo(2);
        for (int i = 0; i < calls.getLength(); i++) {
            Element call = (Element) calls.item(i);
            assertThat(call.getAttribute("calledElement")).isEqualTo("fu-callee:5:aaa");
            assertThat(inParameters(call)).hasSize(3);
        }
    }

    /** No active release of the called unit: there is nothing to run, so the caller does not deploy. */
    @Test
    void refusesWhenTheCalledUnitHasNoActiveRelease() {
        lenient().when(lifecycle.getActiveCatalogForPortalStart("fu-callee")).thenReturn(Optional.empty());
        String bpmn = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_1" calledElement="fu-callee"></bpmn:callActivity>
                  </bpmn:process>
                </bpmn:definitions>
                """;

        assertThatThrownBy(() -> resolver.resolvePinnedCalls(bpmn))
                .isInstanceOf(CallActivityPinUnresolvableException.class)
                .hasMessageContaining("fu-callee")
                .hasMessageContaining("Call_1");
    }

    /** A release identity that cannot be written as a plain constant is refused, not escaped. */
    @Test
    void refusesAReleaseIdentityThatIsNotAPlainConstant() {
        givenDeployedVersion("fu-callee", "1.2.0'}${x", "fu-callee:3:abc-def");

        assertThatThrownBy(() -> resolver.resolvePinnedCalls(pinnedCall("fu-callee", "1.2.0'}${x")))
                .isInstanceOf(CallActivityPinUnresolvableException.class);
    }

    /**
     * The designer's BPMN does not always declare the flowable prefix. The rewrite adds a
     * flowable attribute, so the output must still be namespace-well-formed or Flowable
     * rejects it ("prefix flowable ... is not bound").
     */
    @Test
    void rewrittenXmlBindsTheFlowablePrefix() throws Exception {
        givenDeployedVersion("fu-callee", "1.2.0", "fu-callee:3:abc-def");

        String out = resolver.resolvePinnedCalls(pinnedCall("fu-callee", "1.2.0"));

        assertThat(parsedCallActivity(out).getAttributeNS("http://flowable.org/bpmn", "calledElementType"))
                .isEqualTo("id");
    }

    /**
     * When the designer did declare the prefix it must not be declared again: a repeated
     * attribute on the root is just as unreadable as an unbound prefix.
     */
    @Test
    void keepsAnExistingFlowablePrefixDeclarationSingle() throws Exception {
        givenDeployedVersion("fu-callee", "1.2.0", "fu-callee:3:abc-def");
        String declared = pinnedCall("fu-callee", "1.2.0").replace(
                "xmlns:custom=", "xmlns:flowable=\"http://flowable.org/bpmn\"\n  xmlns:custom=");

        String out = resolver.resolvePinnedCalls(declared);

        assertThat(out.split("xmlns:flowable=", -1)).hasSize(2);
        assertThat(parsedCallActivity(out).getAttributeNS("http://flowable.org/bpmn", "calledElementType"))
                .isEqualTo("id");
    }

    private static Element parsedCallActivity(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return (Element) factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                .getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "callActivity")
                .item(0);
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
