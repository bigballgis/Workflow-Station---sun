package com.developer.property;

import com.developer.dto.DecisionTableModel;
import com.developer.validation.DmnXmlParser;
import net.jqwik.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * DMN XML 解析器属性测试
 * Feature: dmn-decision-table-integration
 * Property 7: DMN XML 解析/序列化往返
 *
 * Validates: Requirements 5.3
 */
public class DmnXmlParserPropertyTest {

    private final DmnXmlParser parser = new DmnXmlParser();

    // ========== Property 7: DMN XML 解析/序列化往返 ==========

    /**
     * Property 7: For any valid DMN XML, parsing to DecisionTableModel, serializing
     * back to XML, and parsing again should produce an equivalent DecisionTableModel.
     * (decisionKey, decisionName, hitPolicy, inputs, outputs, rules all equal)
     *
     * **Validates: Requirements 5.3**
     */
    @Property(tries = 100)
    void parseSerializeRoundTripProducesEquivalentModel(
            @ForAll("validDmnXml") String dmnXml) {

        // First parse
        DecisionTableModel model1 = parser.parseToModel(dmnXml);

        // Serialize back to XML
        String regeneratedXml = parser.toXml(model1);

        // Second parse
        DecisionTableModel model2 = parser.parseToModel(regeneratedXml);

        // Verify equivalence of all fields
        assertThat(model2.getDecisionKey())
                .as("decisionKey should survive round-trip")
                .isEqualTo(model1.getDecisionKey());

        assertThat(model2.getDecisionName())
                .as("decisionName should survive round-trip")
                .isEqualTo(model1.getDecisionName());

        assertThat(model2.getHitPolicy())
                .as("hitPolicy should survive round-trip")
                .isEqualTo(model1.getHitPolicy());

        // Inputs
        assertThat(model2.getInputs())
                .as("inputs should survive round-trip")
                .hasSameSizeAs(model1.getInputs());
        for (int i = 0; i < model1.getInputs().size(); i++) {
            DecisionTableModel.InputColumn in1 = model1.getInputs().get(i);
            DecisionTableModel.InputColumn in2 = model2.getInputs().get(i);
            assertThat(in2.getLabel()).isEqualTo(in1.getLabel());
            assertThat(in2.getInputExpression()).isEqualTo(in1.getInputExpression());
            assertThat(in2.getTypeRef()).isEqualTo(in1.getTypeRef());
        }

        // Outputs
        assertThat(model2.getOutputs())
                .as("outputs should survive round-trip")
                .hasSameSizeAs(model1.getOutputs());
        for (int i = 0; i < model1.getOutputs().size(); i++) {
            DecisionTableModel.OutputColumn out1 = model1.getOutputs().get(i);
            DecisionTableModel.OutputColumn out2 = model2.getOutputs().get(i);
            assertThat(out2.getLabel()).isEqualTo(out1.getLabel());
            assertThat(out2.getName()).isEqualTo(out1.getName());
            assertThat(out2.getTypeRef()).isEqualTo(out1.getTypeRef());
        }

        // Rules
        assertThat(model2.getRules())
                .as("rules should survive round-trip")
                .hasSameSizeAs(model1.getRules());
        for (int i = 0; i < model1.getRules().size(); i++) {
            DecisionTableModel.Rule r1 = model1.getRules().get(i);
            DecisionTableModel.Rule r2 = model2.getRules().get(i);
            assertThat(r2.getInputEntries()).isEqualTo(r1.getInputEntries());
            assertThat(r2.getOutputEntries()).isEqualTo(r1.getOutputEntries());
        }
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<String> validDmnXml() {
        return Combinators.combine(
                validDecisionKeys(),
                validDecisionNames(),
                validHitPolicies(),
                Arbitraries.integers().between(1, 5),
                Arbitraries.integers().between(1, 3),
                Arbitraries.integers().between(0, 5)
        ).as(DmnXmlParserPropertyTest::buildValidDmnXml);
    }

    // ========== Helper Arbitraries ==========

    private Arbitrary<String> validDecisionKeys() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1)
                .ofMaxLength(20)
                .map(s -> "dk_" + s);
    }

    private Arbitrary<String> validDecisionNames() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1)
                .ofMaxLength(30)
                .map(s -> "Decision " + s);
    }

    private Arbitrary<String> validHitPolicies() {
        return Arbitraries.of("FIRST", "UNIQUE", "ANY", "PRIORITY",
                "COLLECT", "RULE_ORDER", "OUTPUT_ORDER");
    }

    // ========== XML Builder Helper ==========

    private static String buildValidDmnXml(String key, String name, String hitPolicy,
                                           int inputCount, int outputCount, int ruleCount) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<definitions xmlns=\"https://www.omg.org/spec/DMN/20191111/MODEL/\"");
        xml.append(" id=\"definitions\" name=\"definitions\"");
        xml.append(" namespace=\"http://camunda.org/schema/1.0/dmn\">\n");
        xml.append("  <decision id=\"").append(escapeXml(key)).append("\"");
        xml.append(" name=\"").append(escapeXml(name)).append("\">\n");
        xml.append("    <decisionTable id=\"dt_1\" hitPolicy=\"").append(hitPolicy).append("\">\n");

        for (int i = 1; i <= inputCount; i++) {
            xml.append("      <input id=\"input_").append(i).append("\" label=\"Input ").append(i).append("\">\n");
            xml.append("        <inputExpression id=\"ie_").append(i).append("\" typeRef=\"string\">\n");
            xml.append("          <text>var").append(i).append("</text>\n");
            xml.append("        </inputExpression>\n");
            xml.append("      </input>\n");
        }

        for (int i = 1; i <= outputCount; i++) {
            xml.append("      <output id=\"output_").append(i).append("\"");
            xml.append(" label=\"Output ").append(i).append("\"");
            xml.append(" name=\"out").append(i).append("\"");
            xml.append(" typeRef=\"string\" />\n");
        }

        for (int r = 1; r <= ruleCount; r++) {
            xml.append("      <rule id=\"rule_").append(r).append("\">\n");
            for (int i = 1; i <= inputCount; i++) {
                xml.append("        <inputEntry id=\"ie_").append(r).append("_").append(i).append("\">\n");
                xml.append("          <text>\"value").append(r).append("\"</text>\n");
                xml.append("        </inputEntry>\n");
            }
            for (int o = 1; o <= outputCount; o++) {
                xml.append("        <outputEntry id=\"oe_").append(r).append("_").append(o).append("\">\n");
                xml.append("          <text>\"result").append(r).append("\"</text>\n");
                xml.append("        </outputEntry>\n");
            }
            xml.append("      </rule>\n");
        }

        xml.append("    </decisionTable>\n");
        xml.append("  </decision>\n");
        xml.append("</definitions>");
        return xml.toString();
    }

    private static String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
