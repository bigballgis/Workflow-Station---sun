package com.developer.util;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BpmnActionIdXmlRewriteTest {
    private static final String PLATFORM = "http://workflow.platform/schema/custom";
    private static final String LEGACY = "http://custom.bpmn.io/schema";
    private static final Map<Long, Long> MAPPING = Map.of(12L, 112L, 22L, 122L);

    @Test
    void remapsPairedTagsWithEitherNamespaceAndEncoding() throws Exception {
        for (String namespace : List.of(PLATFORM, LEGACY)) {
            for (String tag : List.of("property", "values")) {
                for (boolean encoded : new boolean[]{false, true}) {
                    String entries = "<c:properties><c:" + tag + " name=\"actionIds\" value=\"[&quot;12&quot;,22]\"></c:" + tag
                            + "></c:properties><c:" + tag + " name=\"globalActionIds\" value=\"[22,12]\"></c:" + tag + ">";
                    String xml = document(namespace, entries);
                    String result = rewrite(encoded ? XmlEncodingUtil.encode(xml) : xml);
                    assertThat(values(result, "actionIds")).containsExactly("[112,122]");
                    assertThat(values(result, "globalActionIds")).containsExactly("[122,112]");
                    if (encoded) assertThat(result).doesNotStartWith("<");
                }
            }
        }
    }

    @Test
    void remapsTextAndCdataWithoutChangingArrayMembership() throws Exception {
        for (String tag : List.of("property", "values")) {
            String xml = document(PLATFORM, "<c:properties><c:" + tag + " name=\"actionIds\"><![CDATA[[\" 12 \",22,\"legacy/12,save\"]]]></c:" + tag
                    + "><c:" + tag + " name=\"globalActionIds\">[22,12]</c:" + tag + "></c:properties>");
            assertThat(values(rewrite(xml), "actionIds")).containsExactly("[112,122,\"legacy/12,save\"]");
            assertThat(values(rewrite(xml), "globalActionIds")).containsExactly("[122,112]");
        }
    }

    @Test
    void remapsDefaultAndUnqualifiedNamespaces() throws Exception {
        for (String namespace : List.of(PLATFORM, LEGACY, "", "http://www.omg.org/spec/BPMN/20100524/MODEL")) {
            String xml = document(PLATFORM, "<properties xmlns=\"" + namespace + "\"><property name=\"actionIds\" value=\"[12,22]\"/>"
                    + "<values name=\"globalActionIds\">[22,12]</values></properties>");
            assertThat(values(rewrite(xml), "actionIds")).containsExactly("[112,122]");
            assertThat(values(rewrite(xml), "globalActionIds")).containsExactly("[122,112]");
        }
    }

    @Test
    void preservesCopiesEmptyValuesAndUnrelatedConfiguration() throws Exception {
        String xml = document(PLATFORM, "<c:properties><c:property name=\"actionIds\" value=\"[12,22]\"/>"
                + "<c:values name=\"actionIds\">[22,12]</c:values><c:values name=\"actionIds\" value=\"[]\"/>"
                + "<c:property name=\"formId\" value=\"99\"/><c:property name=\"actionNames\" value=\"[&quot;A&amp;B&quot;]\"/>"
                + "<c:property name=\"globalActionIds\" value=\"[]\">[12]</c:property></c:properties>");
        String result = rewrite(xml);
        assertThat(values(result, "actionIds")).containsExactly("[112,122]", "[122,112]", "[]");
        assertThat(values(result, "globalActionIds")).containsExactly("[]");
        assertThat(values(result, "formId")).containsExactly("99");
        assertThat(values(result, "actionNames")).containsExactly("[\"A&B\"]");
        assertThat(rewrite(document(PLATFORM, "<c:property name=\"actionIds\" value=\"[999]\"/>")))
                .isEqualTo(document(PLATFORM, "<c:property name=\"actionIds\" value=\"[999]\"/>"));
    }

    @Test
    void ignoresForeignNamespacesAndComments() {
        String xml = document(PLATFORM, "<f:property xmlns:f=\"urn:foreign\" name=\"actionIds\" value=\"[12]\"/>"
                + "<!-- <c:property name=\"actionIds\" value=\"[12]\"/> -->");
        assertThat(rewrite(xml)).isEqualTo(xml);
    }

    @Test
    void rejectsDoctypesRatherThanResolvingExternalEntities() {
        String xml = "<!DOCTYPE b:definitions [<!ENTITY external SYSTEM 'file:///not-readable-action-test'>]>"
                + document(PLATFORM, "<c:property name=\"actionIds\">&external;</c:property>");
        assertThatThrownBy(() -> rewrite(xml)).isInstanceOf(IllegalArgumentException.class);
    }

    private static String rewrite(String xml) {
        return BpmnIdRewriter.rewrite(xml, Map.of(), Map.of(), MAPPING);
    }

    private static String document(String namespace, String entries) {
        return "<b:definitions xmlns:b=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:c=\"" + namespace
                + "\" targetNamespace=\"test\"><b:process id=\"p\"><b:extensionElements>" + entries
                + "</b:extensionElements></b:process></b:definitions>";
    }

    private static List<String> values(String xml, String key) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(XmlEncodingUtil.smartDecode(xml))));
        List<String> values = new ArrayList<>();
        var elements = document.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (key.equals(element.getAttribute("name"))) {
                values.add(element.hasAttribute("value") ? element.getAttribute("value") : element.getTextContent());
            }
        }
        return values;
    }

    @Test
    void remapsQuotedJsonActionIdsAcrossTagsNamespacesAndEncodings() {
        for (String prefix : new String[]{"custom", "custom_1"}) {
            for (String tag : new String[]{"property", "values"}) {
                for (String key : new String[]{"actionIds", "globalActionIds"}) {
                    for (boolean encoded : new boolean[]{false,true}) {
                        String xml = "<" + prefix + ":properties><" + prefix + ":" + tag
                                + " name=\"" + key + "\" value=\"[&quot; 12 &quot;,22,&quot;legacy,12,save&quot;,&quot;999&quot;]\"/></" + prefix + ":properties>";
                        xml = extensionDocument(xml);
                        String result = BpmnIdRewriter.rewrite(encoded ? XmlEncodingUtil.encode(xml) : xml,
                                Map.of(), Map.of(), Map.of(12L,112L,22L,122L));
                        assertThat(XmlEncodingUtil.smartDecode(result))
                                .contains("value=\"[112,122,&quot;legacy,12,save&quot;,&quot;999&quot;]\"");
                        if (encoded) assertThat(result).doesNotStartWith("<");
                    }
                }
            }
        }
    }

    @Test
    void supportsLiteralAndNumericXmlQuotesWithoutChangingOtherProperties() {
        for (String value : new String[]{"'[\"12\",22]'", "\"[&#34;12&#34;,22]\"", "\"[&#x22;12&#x22;,22]\""}) {
            String xml = "<c:properties><c:property name=\"formId\" value=\"99\"/>"
                    + "<c:property value=" + value + " name=\"actionIds\"/></c:properties>";
            xml = extensionDocument(xml);
            String result = BpmnIdRewriter.rewrite(xml,Map.of(),Map.of(),Map.of(12L,112L,22L,122L));
            assertThat(result).contains("[112,122]").contains("name=\"formId\" value=\"99\"");
        }
    }

    @Test
    void preservesAnUnmappedQuotedArrayByteForByte() {
        String xml = "<c:property name=\"actionIds\" value=\"[&quot;999&quot;,&quot;legacy,12,save&quot;]\"/>";
        xml = extensionDocument(xml);
        assertThat(BpmnIdRewriter.rewrite(xml,Map.of(),Map.of(),Map.of(12L,112L))).isEqualTo(xml);
    }

    @Test
    void remapsEachActionCopyWithoutCollapsingItsOrderOrExplicitEmptyValue() {
        for (String key : new String[]{"actionIds", "globalActionIds"}) {
            String xml = "<c:properties><c:property name=\"" + key + "\" value=\"[12,22]\"/>"
                    + "<c:values name=\"" + key + "\" value=\"[35,12]\"/>"
                    + "<c:values name=\"" + key + "\" value=\"[]\"/>"
                    + "<c:property name=\"formId\" value=\"99\"/></c:properties>";
            xml = extensionDocument(xml);
            String result = BpmnIdRewriter.rewrite(xml, Map.of(), Map.of(), Map.of(12L,112L,22L,122L,35L,135L));
            assertThat(result).contains("value=\"[112,122]\"")
                    .contains("value=\"[135,112]\"").contains("value=\"[]\"")
                    .contains("name=\"formId\" value=\"99\"");
        }
    }

    @Test
    void remapsRepeatedPropertyEntriesIndependently() {
        String xml = "<c:properties><c:property name=\"actionIds\" value=\"[12]\"/>"
                + "<c:property name=\"actionIds\" value=\"[22,12]\"/></c:properties>";
        xml = extensionDocument(xml);
        assertThat(BpmnIdRewriter.rewrite(xml, Map.of(), Map.of(), Map.of(12L,112L,22L,122L)))
                .contains("value=\"[112]\"").contains("value=\"[122,112]\"");
    }

    @Test
    void rewriteSubTableIdAndFormIdAndActionIds() {
        String xml = """
                <?xml version="1.0"?>
                <bpmn:userTask>
                  <bpmn:extensionElements>
                    <custom:properties>
                      <custom:property name="subTableId" value="13" />
                      <custom:property name="formId" value="11" />
                      <custom:property name="actionIds" value="[12,34]" />
                    </custom:properties>
                  </bpmn:extensionElements>
                </bpmn:userTask>
                """;

        xml = document(xml.replace("<?xml version=\"1.0\"?>", ""));

        String rewritten = BpmnIdRewriter.rewrite(
                xml,
                Map.of(13L, 113L),
                Map.of(11L, 111L),
                Map.of(12L, 112L, 34L, 134L));

        assertThat(rewritten).contains("name=\"subTableId\" value=\"113\"");
        assertThat(rewritten).contains("name=\"formId\" value=\"111\"");
        assertThat(rewritten).contains("name=\"actionIds\" value=\"[112,134]\"");
        assertThat(rewritten).doesNotContain("value=\"13\"");
        assertThat(rewritten).doesNotContain("value=\"11\"");
        assertThat(rewritten).doesNotContain("[12,34]");
    }

    @Test
    void supportsNamespaceVariantsAndValuesElement() {
        String xml = """
                <bpmn:userTask><bpmn:extensionElements>
                  <custom_1:property name="formId" value="7"/>
                  <custom_1:values name="actionIds" value="[1,2,3]"/>
                </bpmn:extensionElements></bpmn:userTask>
                """;

        xml = document(xml.replace("<?xml version=\"1.0\"?>", ""));

        String rewritten = BpmnIdRewriter.rewrite(
                xml,
                Map.of(),
                Map.of(7L, 700L),
                Map.of(1L, 10L, 2L, 20L, 3L, 30L));

        assertThat(rewritten).contains("name=\"formId\" value=\"700\"");
        assertThat(rewritten).contains("name=\"actionIds\" value=\"[10,20,30]\"");
    }

    @Test
    void preservesNonIdProperties() {
        String xml = """
                <custom:property name="assigneeType" value="ELEMENT_VARIABLE" />
                <custom:property name="subTableName" value="participants" />
                <custom:property name="rowIdVariable" value="currentItem.rowId" />
                """;

        xml = extensionDocument(xml);

        String rewritten = BpmnIdRewriter.rewrite(
                xml, Map.of(1L, 2L), Map.of(3L, 4L), Map.of(5L, 6L));

        assertThat(rewritten).isEqualTo(xml);
    }

    @Test
    void remapsProcessGlobalActionIdsIndependentlyOfNodeActionIds() {
        String xml = """
                <bpmn:process id="p1">
                  <bpmn:extensionElements><custom:properties>
                    <custom:property name="globalActionIds" value="[50]" />
                    <custom:property name="globalActionNames" value="[&quot;Save&quot;]" />
                  </custom:properties></bpmn:extensionElements>
                  <bpmn:userTask id="task1">
                    <bpmn:extensionElements><custom:properties>
                      <custom:property name="actionIds" value="[51]" />
                    </custom:properties></bpmn:extensionElements>
                  </bpmn:userTask>
                </bpmn:process>
                """;

        xml = document(xml.replace("<?xml version=\"1.0\"?>", ""));

        String rewritten = BpmnIdRewriter.rewrite(
                xml, Map.of(), Map.of(), Map.of(50L, 1200L, 51L, 1201L));

        assertThat(rewritten)
                .as("Process Global 写在 globalActionIds，导入必须按动作 id mapping 改写，否则设计器对不上新 id 会显示 Bind to Node")
                .contains("name=\"globalActionIds\" value=\"[1200]\"")
                .contains("name=\"actionIds\" value=\"[1201]\"")
                .doesNotContain("value=\"[50]\"")
                .doesNotContain("value=\"[51]\"");
    }

    @Test
    void remapsUnwrappedGlobalActionIdsByIdMapping() {
        String xml = "<custom:property name=\"globalActionIds\" value=\"[50,51]\" />";

        xml = extensionDocument(xml);

        String rewritten = BpmnIdRewriter.rewrite(
                xml, Map.of(), Map.of(), Map.of(50L, 1200L, 51L, 1201L));

        assertThat(rewritten).contains("name=\"globalActionIds\" value=\"[1200,1201]\"");
    }

    private static String extensionDocument(String extensions) {
        return document("<bpmn:process id=\"fixture\"><bpmn:extensionElements>" + extensions
                + "</bpmn:extensionElements></bpmn:process>");
    }

    private static String document(String content) {
        return "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + " xmlns:c=\"http://workflow.platform/schema/custom\" xmlns:custom=\"http://workflow.platform/schema/custom\""
                + " xmlns:custom_1=\"http://custom.bpmn.io/schema\" targetNamespace=\"test\">" + content + "</bpmn:definitions>";
    }
}
