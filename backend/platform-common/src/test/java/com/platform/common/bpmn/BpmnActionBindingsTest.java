package com.platform.common.bpmn;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class BpmnActionBindingsTest {
    @Test
    void platformAndLaterCopiesWinIncludingExplicitEmpty() {
        var document = BpmnActionBindings.parse(xml("<c:properties><c:property name=\"globalActionIds\" value=\"[12]\"/>"
                + "<c:values name=\"globalActionIds\" value=\"[]\"/></c:properties>"
                + "<l:properties><l:property name=\"globalActionIds\" value=\"[35]\"/></l:properties>"));
        var process = BpmnActionBindings.scopes(document).get(0);
        assertThat(BpmnActionBindings.properties(process).get("globalActionIds").value()).isEqualTo("[]");
    }

    @Test
    void supportsTextDefaultNamespaceAndEncodedXml() {
        String xml = xml("<properties xmlns=\"http://workflow.platform/schema/custom\"><values name=\"globalActionIds\">"
                + "<![CDATA[[\" 12 \",22]]]></values></properties>");
        String encoded = java.util.Base64.getEncoder().encodeToString(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var property = BpmnActionBindings.properties(BpmnActionBindings.scopes(BpmnActionBindings.parse(encoded)).get(0))
                .get("globalActionIds");
        assertThat(BpmnActionBindings.ids(property.value())).containsExactly("12", "22");
    }

    @Test
    void doesNotReadDescendantTasksAsProcessPropertiesOrAcceptForeignNamespaces() {
        String xml = xml("<f:property xmlns:f=\"urn:foreign\" name=\"globalActionIds\" value=\"[35]\"/>")
                .replace("</b:process>", "<b:userTask id=\"a\"><b:extensionElements><c:property name=\"actionIds\" value=\"[12]\"/>"
                        + "</b:extensionElements></b:userTask></b:process>");
        var scopes = BpmnActionBindings.scopes(BpmnActionBindings.parse(xml));
        assertThat(BpmnActionBindings.properties(scopes.get(0))).isEmpty();
        assertThat(BpmnActionBindings.idsKey(scopes.get(0))).isEqualTo("globalActionIds");
        assertThat(BpmnActionBindings.idsKey(scopes.get(1))).isEqualTo("actionIds");
        assertThat(BpmnActionBindings.properties(scopes.get(1)).get("actionIds").value()).isEqualTo("[12]");
    }

    @Test
    void trimsIdsAndRejectsMalformedListsInsteadOfSilentlyUnbinding() {
        assertThat(BpmnActionBindings.ids("[reject,12]")).containsExactly("reject", "12");
        assertThat(BpmnActionBindings.ids("reject,12")).containsExactly("reject", "12");
        assertThat(BpmnActionBindings.names("[\"A&B\",\"Name, with comma\"]")).containsExactly("A&B", "Name, with comma");
        for (String invalid : List.of("[12] trailing", "[null]", "[true]", "{\"id\":12}", "[12,]")) {
            assertThatThrownBy(() -> BpmnActionBindings.ids(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsDoctypesWithoutReadingExternalResources() {
        assertThatThrownBy(() -> BpmnActionBindings.parse("<!DOCTYPE b:definitions [<!ENTITY e SYSTEM 'file:///not-readable-test'>]>"
                + xml("<c:property name=\"globalActionIds\">&e;</c:property>")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String xml(String properties) {
        return "<b:definitions xmlns:b=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + " xmlns:c=\"http://workflow.platform/schema/custom\" xmlns:l=\"http://custom.bpmn.io/schema\">"
                + "<b:process id=\"p\"><b:extensionElements>" + properties + "</b:extensionElements></b:process></b:definitions>";
    }
}
